package rw.inama.app.domain.repository

import kotlinx.coroutines.flow.Flow
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.AppSettings
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.District
import rw.inama.app.domain.model.ExpertCase
import rw.inama.app.domain.model.FarmTask
import rw.inama.app.domain.model.Farmer
import rw.inama.app.domain.model.FeedbackVerdict
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.model.KnowledgeBase
import rw.inama.app.domain.model.Lesson
import rw.inama.app.domain.model.OtpChallenge
import rw.inama.app.domain.model.Session
import rw.inama.app.domain.model.WeatherReport

// Repository contracts. The data layer implements them (local files/DataStore + Inama API);
// features and use cases depend only on these interfaces.

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface SessionRepository {
    val session: Flow<Session?>
    suspend fun requestCode(phone: String): OtpChallenge
    suspend fun verifyCode(phone: String, code: String): Session
    suspend fun signOut()
}

interface ProfileRepository {
    val farmer: Flow<Farmer?>
    suspend fun current(): Farmer?
    suspend fun save(farmer: Farmer)
}

interface FieldRepository {
    val fields: Flow<List<Field>>
    suspend fun get(id: String): Field?
    suspend fun upsert(field: Field)
    suspend fun delete(id: String)
}

interface DiagnosisRepository {
    val diagnoses: Flow<List<Diagnosis>>
    suspend fun get(id: String): Diagnosis?
    suspend fun save(diagnosis: Diagnosis)
    suspend fun setFeedback(id: String, verdict: FeedbackVerdict)
    suspend fun attachCase(id: String, caseId: String)
}

interface AnswerRepository {
    val answers: Flow<List<Answer>>
    suspend fun get(id: String): Answer?
    suspend fun save(answer: Answer)
}

interface TaskRepository {
    val tasks: Flow<List<FarmTask>>
    suspend fun upsertAll(tasks: List<FarmTask>)
    suspend fun setDone(id: String, done: Boolean)
    suspend fun delete(id: String)
}

interface CatalogRepository {
    suspend fun knowledgeBase(language: String): KnowledgeBase
    suspend fun lessons(language: String): List<Lesson>
    suspend fun districts(): List<District>
}

interface WeatherRepository {
    suspend fun forecast(district: District?, language: String, forceRefresh: Boolean = false): WeatherReport
}

interface ExpertRepository {
    suspend fun escalate(diagnosis: Diagnosis, note: String?): ExpertCase
    suspend fun get(caseId: String): ExpertCase?
}

/** What the Inama server reports about itself (Settings › Developer › Test connection). */
data class ServerHealth(val reachable: Boolean, val aiEngine: String?, val aiModel: String?, val liveWeather: Boolean, val demoMode: Boolean, val languages: List<String>)

interface ServerRepository {
    /** Checks [baseUrl] (or the configured server when null). Never throws. */
    suspend fun health(baseUrl: String? = null): ServerHealth
}

/** Clears everything stored on the phone (Settings › Reset demo data). */
interface DataResetter {
    suspend fun resetAll()
}
