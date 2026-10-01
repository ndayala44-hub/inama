package rw.inama.app.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import rw.inama.app.core.config.AppConfig
import rw.inama.app.core.image.ImageTools
import rw.inama.app.core.network.ConnectivityObserver
import rw.inama.app.core.speech.SpeechInput
import rw.inama.app.core.speech.SpeechOutput
import rw.inama.app.data.local.LocalStores
import rw.inama.app.data.remote.InamaApi
import rw.inama.app.data.remote.RemoteAdvisoryEngine
import rw.inama.app.data.repository.AnswerRepositoryImpl
import rw.inama.app.data.repository.CatalogRepositoryImpl
import rw.inama.app.data.repository.DataResetterImpl
import rw.inama.app.data.repository.DiagnosisRepositoryImpl
import rw.inama.app.data.repository.ExpertRepositoryImpl
import rw.inama.app.data.repository.FieldRepositoryImpl
import rw.inama.app.data.repository.ProfileRepositoryImpl
import rw.inama.app.data.repository.ServerRepositoryImpl
import rw.inama.app.data.repository.SessionRepositoryImpl
import rw.inama.app.data.repository.SettingsRepositoryImpl
import rw.inama.app.data.repository.TaskRepositoryImpl
import rw.inama.app.data.repository.WeatherRepositoryImpl
import rw.inama.app.domain.ai.AdvisoryEngine
import rw.inama.app.domain.ai.FallbackAdvisoryEngine
import rw.inama.app.domain.ai.SwitchingAdvisoryEngine
import rw.inama.app.domain.ai.kb.KnowledgeBaseEngine
import rw.inama.app.domain.repository.AnswerRepository
import rw.inama.app.domain.repository.CatalogRepository
import rw.inama.app.domain.repository.DataResetter
import rw.inama.app.domain.repository.DiagnosisRepository
import rw.inama.app.domain.repository.ExpertRepository
import rw.inama.app.domain.repository.FieldRepository
import rw.inama.app.domain.repository.ProfileRepository
import rw.inama.app.domain.repository.ServerRepository
import rw.inama.app.domain.repository.SessionRepository
import rw.inama.app.domain.repository.SettingsRepository
import rw.inama.app.domain.repository.TaskRepository
import rw.inama.app.domain.repository.WeatherRepository
import rw.inama.app.domain.usecase.AddAdviceToTasksUseCase
import rw.inama.app.domain.usecase.AskAdvisorUseCase
import rw.inama.app.domain.usecase.DiagnoseCropUseCase

/**
 * Manual dependency injection: the one place where implementations are chosen.
 * Kept explicit (no Hilt/kapt) for a lean MVP build; each screen's ViewModel receives only the
 * interfaces it needs from here. Swapping the AI engine, the storage or the API client is a
 * change in this file only.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val config = AppConfig()

    // ---- platform services
    val connectivity = ConnectivityObserver(appContext, appScope)
    val speechOutput = SpeechOutput(appContext)
    val speechInput = SpeechInput(appContext)
    val imageTools = ImageTools(appContext)

    // ---- storage & network
    private val stores = LocalStores(appContext)
    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(stores)

    private val api = InamaApi(
        baseUrl = { settingsRepository.current().serverUrlOverride?.takeIf { it.isNotBlank() } ?: config.defaultApiBaseUrl },
        token = { stores.session.get()?.token },
        language = { settingsRepository.current().language.tag },
    )
    val serverRepository: ServerRepository = ServerRepositoryImpl(api) { url ->
        InamaApi(baseUrl = { url }, token = { null }, language = { settingsRepository.current().language.tag })
    }

    // ---- repositories
    val catalogRepository: CatalogRepository = CatalogRepositoryImpl(appContext.assets)
    val sessionRepository: SessionRepository = SessionRepositoryImpl(stores, api, config)
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(stores, api) { id ->
        catalogRepository.districts().firstOrNull { it.id == id }?.name
    }
    val fieldRepository: FieldRepository = FieldRepositoryImpl(stores)
    val diagnosisRepository: DiagnosisRepository = DiagnosisRepositoryImpl(stores, api)
    val answerRepository: AnswerRepository = AnswerRepositoryImpl(stores)
    val taskRepository: TaskRepository = TaskRepositoryImpl(stores)
    val weatherRepository: WeatherRepository = WeatherRepositoryImpl(stores, api)
    val expertRepository: ExpertRepository = ExpertRepositoryImpl(stores, api, diagnosisRepository, catalogRepository)
    val dataResetter: DataResetter = DataResetterImpl(stores, imageTools)

    // ---- AI: server (Gemini) first, on-device knowledge base when offline or chosen in Settings
    val onDeviceEngine = KnowledgeBaseEngine(knowledgeBase = { lang -> catalogRepository.knowledgeBase(lang) })
    val advisoryEngine: AdvisoryEngine = SwitchingAdvisoryEngine(
        mode = { settingsRepository.current().aiMode },
        auto = FallbackAdvisoryEngine(primary = RemoteAdvisoryEngine(api), fallback = onDeviceEngine),
        onDevice = onDeviceEngine,
    )

    // ---- use cases
    val diagnoseCrop = DiagnoseCropUseCase(advisoryEngine, diagnosisRepository)
    val askAdvisor = AskAdvisorUseCase(advisoryEngine, answerRepository)
    val addAdviceToTasks = AddAdviceToTasksUseCase(taskRepository)
}
