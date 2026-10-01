package rw.inama.app.data.repository

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rw.inama.app.core.config.AppConfig
import rw.inama.app.core.image.ImageTools
import rw.inama.app.data.json.Json
import rw.inama.app.data.local.LocalStores
import rw.inama.app.data.mapper.ApiMappers
import rw.inama.app.data.mapper.ContentParsers
import rw.inama.app.data.remote.ApiException
import rw.inama.app.data.remote.ApiUnavailableException
import rw.inama.app.data.remote.InamaApi
import rw.inama.app.domain.model.CaseStatus
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.District
import rw.inama.app.domain.model.ExpertCase
import rw.inama.app.domain.model.KnowledgeBase
import rw.inama.app.domain.model.Lesson
import rw.inama.app.domain.model.OtpChallenge
import rw.inama.app.domain.model.Session
import rw.inama.app.domain.model.WeatherReport
import rw.inama.app.domain.model.WeatherSource
import rw.inama.app.domain.repository.CatalogRepository
import rw.inama.app.domain.repository.DataResetter
import rw.inama.app.domain.repository.DiagnosisRepository
import rw.inama.app.domain.repository.ExpertRepository
import rw.inama.app.domain.repository.SessionRepository
import rw.inama.app.domain.repository.WeatherRepository
import rw.inama.app.domain.weather.DemoWeather
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Reviewed content bundled with the app (copied from the repository's shared/ folder at build
 * time). Loads assets/knowledge/advisory_kb.<lang>.json and falls back to English, so adding a
 * language is a content drop, not a code change.
 */
class CatalogRepositoryImpl(private val assets: AssetManager) : CatalogRepository {
    private val mutex = Mutex()
    private val kbCache = mutableMapOf<String, KnowledgeBase>()
    private val lessonCache = mutableMapOf<String, List<Lesson>>()
    private var districtCache: List<District>? = null

    private suspend fun readAsset(path: String): String? = withContext(Dispatchers.IO) {
        runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
    }

    private suspend fun localized(folder: String, base: String, language: String): String =
        readAsset("$folder/$base.$language.json") ?: readAsset("$folder/$base.en.json") ?: error("Missing $folder/$base.en.json")

    override suspend fun knowledgeBase(language: String): KnowledgeBase = mutex.withLock {
        kbCache.getOrPut(language) { ContentParsers.knowledgeBase(Json.parseObject(localized("knowledge", "advisory_kb", language))) }
    }

    override suspend fun lessons(language: String): List<Lesson> = mutex.withLock {
        lessonCache.getOrPut(language) { ContentParsers.lessons(Json.parseObject(localized("content", "lessons", language))) }
    }

    override suspend fun districts(): List<District> = mutex.withLock {
        districtCache ?: ContentParsers.districts(Json.parseObject(readAsset("content/rwanda_districts.json") ?: "{}")).also { districtCache = it }
    }
}

/**
 * Phone-number sign-in with a one-time code. Uses the Inama server when reachable; when the
 * server can't be reached (no signal, no server in a demo) it signs in on the phone only, with a
 * fixed demo code, so the app still works end to end. Such a session is marked remote = false.
 */
class SessionRepositoryImpl(
    private val stores: LocalStores,
    private val api: InamaApi,
    private val config: AppConfig,
) : SessionRepository {
    override val session: Flow<Session?> = stores.session.data
    private var lastChallengeWasRemote = false

    override suspend fun requestCode(phone: String): OtpChallenge = try {
        val data = api.postJson("/auth/otp/request", Json.obj("phone" to phone))
        lastChallengeWasRemote = true
        ApiMappers.otpChallenge(data, phone)
    } catch (e: ApiUnavailableException) {
        lastChallengeWasRemote = false
        OtpChallenge(phone, maskPhone(phone), 300, devCode = config.offlineDemoCode)
    }

    override suspend fun verifyCode(phone: String, code: String): Session {
        val session = if (lastChallengeWasRemote) {
            ApiMappers.session(api.postJson("/auth/otp/verify", Json.obj("phone" to phone, "code" to code)))
        } else {
            if (code != config.offlineDemoCode) throw ApiException(400, "invalid_code", "That code is not right.")
            Session(farmerId = "local_${UUID.randomUUID()}", phone = phone, token = null, remote = false)
        }
        stores.session.update { session }
        return session
    }

    override suspend fun signOut() {
        stores.session.clear()
    }

    private fun maskPhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 6) "+250 ${digits.takeLast(9).take(1)}•• ••• ${digits.takeLast(3)}" else phone
    }
}

/** Forecast from the server (live or demo), cached for offline; falls back to demo weather. */
class WeatherRepositoryImpl(
    private val stores: LocalStores,
    private val api: InamaApi,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : WeatherRepository {
    private val maxAgeMillis = 3L * 3600 * 1000

    override suspend fun forecast(district: District?, language: String, forceRefresh: Boolean): WeatherReport {
        val cached = stores.weather.get()
        val place = district?.name ?: "Kigali"
        if (!forceRefresh && cached != null && cached.place.startsWith(place) && clock() - cached.updatedAtMillis < maxAgeMillis) return cached
        return try {
            val query = buildString {
                append("/weather?place=").append(java.net.URLEncoder.encode(place, "UTF-8"))
                if (district != null) append("&lat=").append(district.lat).append("&lon=").append(district.lon)
            }
            val report = ApiMappers.weather(api.get(query), clock())
            stores.weather.update { report }
            report
        } catch (e: Exception) {
            cached?.copy(source = WeatherSource.CACHED)
                ?: DemoWeather.forecast(place, LocalDate.now(ZoneId.of("Africa/Kigali")), clock())
        }
    }
}

/**
 * Sends an uncertain result to a person (farmer promoter → sector agronomist). Uses the server
 * when the check ran there; otherwise keeps a local demo case whose "expert" reply is built from
 * the knowledge base after a short delay, so the whole loop can be demonstrated offline.
 */
class ExpertRepositoryImpl(
    private val stores: LocalStores,
    private val api: InamaApi,
    private val diagnoses: DiagnosisRepository,
    private val catalog: CatalogRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val demoReplyAfterMillis: Long = 90_000,
) : ExpertRepository {

    override suspend fun escalate(diagnosis: Diagnosis, note: String?): ExpertCase {
        val remoteId = diagnosis.remoteId
        val created = if (remoteId != null && stores.session.get()?.remote == true) {
            try {
                ApiMappers.expertCase(api.postJson("/diagnoses/$remoteId/escalate", Json.obj("note" to note), auth = true), diagnosis.id, clock())
            } catch (e: Exception) {
                localCase(diagnosis)
            }
        } else localCase(diagnosis)
        stores.cases.update { list -> list.filterNot { it.id == created.id } + created }
        diagnoses.attachCase(diagnosis.id, created.id)
        return created
    }

    private fun localCase(d: Diagnosis) = ExpertCase(
        id = "case_${UUID.randomUUID()}",
        diagnosisId = d.id,
        status = CaseStatus.SENT,
        expertName = "Your farmer promoter",
        expectedReplyHours = 3,
        createdAtMillis = clock(),
        remote = false,
    )

    override suspend fun get(caseId: String): ExpertCase? {
        val stored = stores.cases.get().firstOrNull { it.id == caseId } ?: return null
        val updated = if (stored.remote) {
            runCatching { ApiMappers.expertCase(api.get("/cases/$caseId", auth = true), stored.diagnosisId, clock()) }.getOrDefault(stored)
        } else progressDemo(stored)
        if (updated != stored) stores.cases.update { list -> list.map { if (it.id == caseId) updated else it } }
        return updated
    }

    private suspend fun progressDemo(c: ExpertCase): ExpertCase {
        if (c.status == CaseStatus.ANSWERED) return c
        val age = clock() - c.createdAtMillis
        return when {
            age >= demoReplyAfterMillis -> {
                val d = diagnoses.get(c.diagnosisId)
                val kb = catalog.knowledgeBase(d?.language ?: "en")
                val condition = kb.condition(d?.candidates?.firstOrNull()?.id ?: d?.condition?.id)
                val text = if (condition != null) {
                    "I looked at your photos. It looks like ${condition.name.lowercase()}. ${condition.steps.first()} Send me a new photo in a week."
                } else {
                    "I looked at your photos and can’t see a serious problem. Keep checking the plants each week."
                }
                c.copy(status = CaseStatus.ANSWERED, replyText = text, repliedAtMillis = clock())
            }
            age >= demoReplyAfterMillis / 4 -> c.copy(status = CaseStatus.SEEN)
            else -> c
        }
    }
}

class DataResetterImpl(private val stores: LocalStores, private val images: ImageTools) : DataResetter {
    override suspend fun resetAll() {
        stores.clearAll()
        withContext(Dispatchers.IO) { images.deletePhotos() }
    }
}

/** Health check against the configured server, or a URL typed in Settings before saving it. */
class ServerRepositoryImpl(
    private val defaultApi: InamaApi,
    private val apiFor: (String) -> InamaApi,
) : rw.inama.app.domain.repository.ServerRepository {
    override suspend fun health(baseUrl: String?): rw.inama.app.domain.repository.ServerHealth {
        val api = baseUrl?.takeIf { it.isNotBlank() }?.let(apiFor) ?: defaultApi
        return try {
            val data = api.get("/health")
            val ai = data.obj("ai")
            rw.inama.app.domain.repository.ServerHealth(
                reachable = data.string("status") == "ok",
                aiEngine = ai?.stringOrNull("engine"),
                aiModel = ai?.stringOrNull("model"),
                liveWeather = data.obj("weather")?.bool("live") ?: false,
                demoMode = data.bool("demoMode"),
                languages = data.strings("languages"),
            )
        } catch (e: Exception) {
            rw.inama.app.domain.repository.ServerHealth(false, null, null, false, false, emptyList())
        }
    }
}
