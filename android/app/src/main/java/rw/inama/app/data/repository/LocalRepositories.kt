package rw.inama.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import rw.inama.app.data.json.Json
import rw.inama.app.data.local.LocalStores
import rw.inama.app.data.remote.InamaApi
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.AppSettings
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.FarmTask
import rw.inama.app.domain.model.Farmer
import rw.inama.app.domain.model.FeedbackVerdict
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.repository.AnswerRepository
import rw.inama.app.domain.repository.DiagnosisRepository
import rw.inama.app.domain.repository.FieldRepository
import rw.inama.app.domain.repository.ProfileRepository
import rw.inama.app.domain.repository.SettingsRepository
import rw.inama.app.domain.repository.TaskRepository

class SettingsRepositoryImpl(private val stores: LocalStores) : SettingsRepository {
    override val settings: Flow<AppSettings> = stores.settings.data
    override suspend fun current(): AppSettings = stores.settings.get()
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        stores.settings.update(transform)
    }
}

/** Profile lives on the phone; it is also sent to the server (best effort) when signed in there. */
class ProfileRepositoryImpl(
    private val stores: LocalStores,
    private val api: InamaApi,
    private val districtName: suspend (String?) -> String?,
) : ProfileRepository {
    override val farmer: Flow<Farmer?> = stores.farmer.data
    override suspend fun current(): Farmer? = stores.farmer.get()

    override suspend fun save(farmer: Farmer) {
        stores.farmer.update { farmer }
        if (stores.session.get()?.remote == true) {
            runCatching {
                api.putJson(
                    "/farmers/me",
                    Json.obj("name" to farmer.name, "district" to districtName(farmer.districtId), "sector" to farmer.sector),
                )
            }
        }
    }
}

class FieldRepositoryImpl(private val stores: LocalStores) : FieldRepository {
    override val fields: Flow<List<Field>> = stores.fields.data.map { list -> list.sortedBy { it.createdAtMillis } }
    override suspend fun get(id: String): Field? = stores.fields.get().firstOrNull { it.id == id }
    override suspend fun upsert(field: Field) {
        stores.fields.update { list -> list.filterNot { it.id == field.id } + field }
    }

    override suspend fun delete(id: String) {
        stores.fields.update { list -> list.filterNot { it.id == id } }
    }
}

class DiagnosisRepositoryImpl(
    private val stores: LocalStores,
    private val api: InamaApi,
) : DiagnosisRepository {
    override val diagnoses: Flow<List<Diagnosis>> = stores.diagnoses.data.map { list -> list.sortedByDescending { it.createdAtMillis } }
    override suspend fun get(id: String): Diagnosis? = stores.diagnoses.get().firstOrNull { it.id == id }
    override suspend fun save(diagnosis: Diagnosis) {
        stores.diagnoses.update { list -> list.filterNot { it.id == diagnosis.id } + diagnosis }
    }

    /** Stored locally; also sent to the server so wrong answers improve the model (feedback loop). */
    override suspend fun setFeedback(id: String, verdict: FeedbackVerdict) {
        var remoteId: String? = null
        stores.diagnoses.update { list ->
            list.map {
                if (it.id == id) {
                    remoteId = it.remoteId
                    it.copy(feedback = verdict)
                } else it
            }
        }
        remoteId?.let { rid -> runCatching { api.postJson("/diagnoses/$rid/feedback", Json.obj("verdict" to verdict.wire), auth = true) } }
    }

    override suspend fun attachCase(id: String, caseId: String) {
        stores.diagnoses.update { list -> list.map { if (it.id == id) it.copy(caseId = caseId) else it } }
    }
}

class AnswerRepositoryImpl(private val stores: LocalStores) : AnswerRepository {
    override val answers: Flow<List<Answer>> = stores.answers.data.map { list -> list.sortedByDescending { it.createdAtMillis } }
    override suspend fun get(id: String): Answer? = stores.answers.get().firstOrNull { it.id == id }
    override suspend fun save(answer: Answer) {
        stores.answers.update { list -> list.filterNot { it.id == answer.id } + answer }
    }
}

class TaskRepositoryImpl(
    private val stores: LocalStores,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : TaskRepository {
    override val tasks: Flow<List<FarmTask>> = stores.tasks.data
    override suspend fun upsertAll(tasks: List<FarmTask>) {
        val ids = tasks.map { it.id }.toSet()
        stores.tasks.update { list -> list.filterNot { it.id in ids } + tasks }
    }

    override suspend fun setDone(id: String, done: Boolean) {
        stores.tasks.update { list -> list.map { if (it.id == id) it.copy(done = done, doneAtMillis = if (done) clock() else null) else it } }
    }

    override suspend fun delete(id: String) {
        stores.tasks.update { list -> list.filterNot { it.id == id } }
    }
}
