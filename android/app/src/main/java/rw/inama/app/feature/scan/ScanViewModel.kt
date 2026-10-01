package rw.inama.app.feature.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rw.inama.app.core.config.AppConfig
import rw.inama.app.core.image.ImageTools
import rw.inama.app.domain.ai.AnalysisStage
import rw.inama.app.domain.ai.DiagnosisRequest
import rw.inama.app.domain.ai.EngineRejectedException
import rw.inama.app.domain.ai.ImageFeatures
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.model.KnowledgeBase
import rw.inama.app.domain.model.PhotoQuality
import rw.inama.app.domain.repository.CatalogRepository
import rw.inama.app.domain.repository.FieldRepository
import rw.inama.app.domain.repository.SettingsRepository
import rw.inama.app.domain.usecase.DiagnoseCropUseCase
import java.io.File

/** Sample photos bundled for demos (assets/samples). [cropId] pre-selects the crop. */
enum class SamplePhoto(val asset: String, val cropId: String) {
    CASSAVA_SPOTS("cassava_spots.jpg", "cassava"),
    CASSAVA_HEALTHY("cassava_healthy.jpg", "cassava"),
    BEANS_BLURRY("beans_blurry.jpg", "beans"),
}

enum class ScanError { OPEN_FAILED, CAMERA_FAILED }

data class ScanState(
    val kb: KnowledgeBase? = null,
    val fields: List<Field> = emptyList(),
    val fieldId: String? = null,
    val cropId: String? = null,
    val preparing: Boolean = false,
    val photo: File? = null,
    val features: ImageFeatures? = null,
    val quality: PhotoQuality = PhotoQuality.UNKNOWN,
    val acceptedPoorPhoto: Boolean = false,
    val symptoms: Set<String> = emptySet(),
    val error: ScanError? = null,
    val analysing: Boolean = false,
    val stage: AnalysisStage? = null,
    val resultId: String? = null,
    /** Engine rejection code (e.g. unsupported_image) or "failed". */
    val analysisError: String? = null,
    /** Bumped by [start] so screens can tell a new check from the previous one. */
    val session: Int = 0,
) {
    val field: Field? get() = fields.firstOrNull { it.id == fieldId }
    val canAnalyse: Boolean get() = photo != null && cropId != null && !analysing
}

/**
 * The photo check, shared by the camera, review and analysing screens (activity-scoped).
 * Photo preparation and quality measurement happen on the phone; the analysis goes through
 * [DiagnoseCropUseCase] → AdvisoryEngine, so this ViewModel never knows which AI answered.
 */
class ScanViewModel(
    private val catalog: CatalogRepository,
    private val fieldRepository: FieldRepository,
    private val settings: SettingsRepository,
    private val imageTools: ImageTools,
    private val config: AppConfig,
    private val diagnose: DiagnoseCropUseCase,
    private val assessQuality: (KnowledgeBase, ImageFeatures?) -> PhotoQuality,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()
    private var analysis: Job? = null

    /** Begins a new check, optionally for a field (its crop is pre-selected). */
    fun start(fieldId: String?) {
        analysis?.cancel()
        val session = _state.value.session + 1
        _state.value = ScanState(fieldId = fieldId, session = session, kb = _state.value.kb)
        viewModelScope.launch {
            val lang = settings.current().language.tag
            val kb = catalog.knowledgeBase(lang)
            val fields = fieldRepository.fields.first()
            val crop = fields.firstOrNull { it.id == fieldId }?.cropId
            _state.update { if (it.session == session) it.copy(kb = kb, fields = fields, cropId = it.cropId ?: crop) else it }
        }
    }

    fun onCameraError() = _state.update { it.copy(error = ScanError.CAMERA_FAILED) }
    fun clearError() = _state.update { it.copy(error = null) }

    /** Camera capture, gallery pick or sample: all become a small upright JPEG + measurements. */
    fun usePhoto(uri: Uri, onReady: () -> Unit, cropHint: String? = null) {
        if (_state.value.preparing) return
        _state.update { it.copy(preparing = true, error = null) }
        viewModelScope.launch {
            try {
                val lowData = settings.current().lowDataMode
                val prepared = imageTools.prepare(
                    uri,
                    maxSide = if (lowData) config.photoMaxSideLowData else config.photoMaxSide,
                    quality = if (lowData) 72 else 85,
                )
                val kb = _state.value.kb ?: catalog.knowledgeBase(settings.current().language.tag)
                _state.update {
                    it.copy(
                        kb = kb,
                        preparing = false,
                        photo = prepared.file,
                        features = prepared.features,
                        quality = assessQuality(kb, prepared.features),
                        acceptedPoorPhoto = false,
                        cropId = it.cropId ?: cropHint,
                        resultId = null,
                        analysisError = null,
                    )
                }
                onReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(preparing = false, error = ScanError.OPEN_FAILED) }
            }
        }
    }

    fun useSample(sample: SamplePhoto, onReady: () -> Unit) {
        if (_state.value.preparing) return
        viewModelScope.launch {
            val uri = runCatching { imageTools.copySampleToCache(sample.asset) }.getOrNull()
            if (uri == null) _state.update { it.copy(error = ScanError.OPEN_FAILED) } else usePhoto(uri, onReady, sample.cropId)
        }
    }

    fun newCaptureFile(): File = imageTools.newCaptureFile()

    fun selectCrop(cropId: String) = _state.update {
        // Signs differ per crop, so a crop change clears the ticked signs.
        if (it.cropId == cropId) it else it.copy(cropId = cropId, symptoms = emptySet())
    }

    fun toggleSymptom(id: String) = _state.update {
        it.copy(symptoms = if (id in it.symptoms) it.symptoms - id else it.symptoms + id)
    }

    fun acceptPoorPhoto() = _state.update { it.copy(acceptedPoorPhoto = true) }

    /** Links the check to a field (or none); picking a field also picks its crop. */
    fun selectField(fieldId: String?) = _state.update { st ->
        val crop = st.fields.firstOrNull { it.id == fieldId }?.cropId
        val cropChanged = crop != null && crop != st.cropId
        st.copy(fieldId = fieldId, cropId = crop ?: st.cropId, symptoms = if (cropChanged) emptySet() else st.symptoms)
    }

    fun analyse() {
        val st = _state.value
        val photo = st.photo ?: return
        if (st.analysing || st.resultId != null) return
        analysis = viewModelScope.launch {
            _state.update { it.copy(analysing = true, stage = AnalysisStage.RECEIVED, analysisError = null) }
            val started = clock()
            try {
                val request = DiagnosisRequest(
                    imagePath = photo.absolutePath,
                    cropId = st.cropId,
                    fieldId = st.fieldId,
                    symptoms = st.symptoms.toList(),
                    features = st.features,
                    language = settings.current().language.tag,
                )
                val result = diagnose(request) { stage -> _state.update { it.copy(stage = stage) } }
                // Keep the progress steps on screen long enough to read (on-device checks are instant).
                val elapsed = clock() - started
                if (elapsed < MIN_ANALYSIS_MILLIS) delay(MIN_ANALYSIS_MILLIS - elapsed)
                _state.update { it.copy(analysing = false, stage = AnalysisStage.DONE, resultId = result.id) }
            } catch (e: CancellationException) {
                _state.update { it.copy(analysing = false, stage = null) }
                throw e
            } catch (e: EngineRejectedException) {
                _state.update { it.copy(analysing = false, stage = null, analysisError = e.code) }
            } catch (e: Exception) {
                _state.update { it.copy(analysing = false, stage = null, analysisError = "failed") }
            }
        }
    }

    fun cancelAnalysis() {
        analysis?.cancel()
        analysis = null
    }

    companion object {
        const val MIN_ANALYSIS_MILLIS = 2_200L
    }
}
