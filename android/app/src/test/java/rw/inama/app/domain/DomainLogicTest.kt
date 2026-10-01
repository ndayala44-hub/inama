package rw.inama.app.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rw.inama.app.TestSupport
import rw.inama.app.TestSupport.blocking
import rw.inama.app.data.json.Json
import rw.inama.app.data.mapper.ContentParsers
import rw.inama.app.domain.ai.AdvisoryEngine
import rw.inama.app.domain.ai.AnalysisStage
import rw.inama.app.domain.ai.DiagnosisRequest
import rw.inama.app.domain.ai.EngineRejectedException
import rw.inama.app.domain.ai.EngineUnavailableException
import rw.inama.app.domain.ai.FallbackAdvisoryEngine
import rw.inama.app.domain.ai.QuestionRequest
import rw.inama.app.domain.ai.kb.KnowledgeBaseEngine
import rw.inama.app.domain.farm.CropStageCalculator
import rw.inama.app.domain.farm.Priority
import rw.inama.app.domain.farm.PriorityPicker
import rw.inama.app.domain.image.PhotoAnalyzer
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.CurrentWeather
import rw.inama.app.domain.model.DayForecast
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.EngineInfo
import rw.inama.app.domain.model.EngineMode
import rw.inama.app.domain.model.FarmActivity
import rw.inama.app.domain.model.FarmTask
import rw.inama.app.domain.model.FeedbackVerdict
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.model.Urgency
import rw.inama.app.domain.model.Verdict
import rw.inama.app.domain.model.WeatherCondition
import rw.inama.app.domain.repository.DiagnosisRepository
import rw.inama.app.domain.repository.TaskRepository
import rw.inama.app.domain.usecase.AddAdviceToTasksUseCase
import rw.inama.app.domain.usecase.DiagnoseCropUseCase
import rw.inama.app.domain.weather.DemoWeather
import rw.inama.app.domain.weather.FarmDecisions
import java.time.LocalDate

class DomainLogicTest {
    private val kb = ContentParsers.knowledgeBase(Json.parseObject(TestSupport.sharedFile("knowledge/advisory_kb.en.json").readText()))
    private val kbEngine = KnowledgeBaseEngine({ kb }, clock = { 5_000L })

    private class FakeDiagnoses : DiagnosisRepository {
        val saved = mutableListOf<Diagnosis>()
        override val diagnoses: Flow<List<Diagnosis>> = flowOf(emptyList())
        override suspend fun get(id: String) = saved.firstOrNull { it.id == id }
        override suspend fun save(diagnosis: Diagnosis) {
            saved += diagnosis
        }
        override suspend fun setFeedback(id: String, verdict: FeedbackVerdict) = Unit
        override suspend fun attachCase(id: String, caseId: String) = Unit
    }

    private class FakeTasks : TaskRepository {
        val stored = mutableListOf<FarmTask>()
        override val tasks: Flow<List<FarmTask>> = flowOf(emptyList())
        override suspend fun upsertAll(tasks: List<FarmTask>) {
            stored += tasks
        }
        override suspend fun setDone(id: String, done: Boolean) = Unit
        override suspend fun delete(id: String) = Unit
    }

    private class Unreachable : AdvisoryEngine {
        override val info = EngineInfo("remote", null, EngineMode.REMOTE)
        override suspend fun diagnose(request: DiagnosisRequest): Diagnosis = throw EngineUnavailableException("offline")
        override suspend fun ask(request: QuestionRequest): Answer = throw EngineUnavailableException("offline")
    }

    private class Rejecting : AdvisoryEngine {
        override val info = EngineInfo("remote", null, EngineMode.REMOTE)
        override suspend fun diagnose(request: DiagnosisRequest): Diagnosis = throw EngineRejectedException("image_type", "bad photo")
        override suspend fun ask(request: QuestionRequest): Answer = throw EngineRejectedException("x", "x")
    }

    private val maizeRequest = DiagnosisRequest("/p.jpg", cropId = "maize", fieldId = "f1", symptoms = listOf("holes", "frass"), features = null, language = "en")

    @Test
    fun fallbackEngineAnswersOnDeviceWhenServerIsUnreachable(): Unit = blocking {
        val d = FallbackAdvisoryEngine(Unreachable(), kbEngine).diagnose(maizeRequest)
        assertEquals(EngineMode.ON_DEVICE, d.engine.mode)
        assertEquals("maize_fall_armyworm", d.condition!!.id)
    }

    @Test
    fun fallbackEngineDoesNotHideRejections() {
        var rejected = false
        try {
            blocking { FallbackAdvisoryEngine(Rejecting(), kbEngine).diagnose(maizeRequest) }
        } catch (e: EngineRejectedException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun diagnoseUseCaseReportsStagesAndSavesHistory(): Unit = blocking {
        val repo = FakeDiagnoses()
        val stages = mutableListOf<AnalysisStage>()
        val d = DiagnoseCropUseCase(kbEngine, repo)(maizeRequest) { stages += it }
        assertEquals(listOf(d), repo.saved)
        assertEquals(AnalysisStage.DONE, stages.last())
        assertTrue(stages.containsAll(AnalysisStage.entries))
    }

    @Test
    fun addingAdviceToTasksDoesNotDuplicate(): Unit = blocking {
        val tasks = FakeTasks()
        val useCase = AddAdviceToTasksUseCase(tasks, clock = { 1L })
        val d = kbEngine.diagnose(maizeRequest)
        val first = useCase.fromDiagnosis(d, emptyList())
        assertEquals(d.steps.size, first)
        val second = useCase.fromDiagnosis(d, tasks.stored)
        assertEquals(0, second)
        assertTrue(tasks.stored.all { it.sourceId == d.id && it.fieldId == "f1" && it.urgency == Urgency.TODAY })
        assertEquals(0, useCase.manual("   ", null, tasks.stored))
    }

    @Test
    fun farmDecisionsMatchServerRules() {
        val rainy = listOf(day(80), day(60), day(30))
        val (d1, a1) = FarmDecisions.decide(CurrentWeather(24, 5, 60, WeatherCondition.STORM, 80), rainy)
        assertEquals(Verdict.WAIT, d1.first { it.activity == FarmActivity.SPRAYING }.verdict)
        assertEquals(Verdict.WAIT, d1.first { it.activity == FarmActivity.WEEDING }.verdict)
        assertTrue(a1.any { it.code == "heavy_rain" })
        val dry = List(5) { day(5) }
        val (d2, a2) = FarmDecisions.decide(CurrentWeather(33, 20, 40, WeatherCondition.SUNNY, 5), dry)
        assertEquals(Verdict.CAREFUL, d2.first { it.activity == FarmActivity.SPRAYING }.verdict)
        assertEquals(Verdict.WAIT, d2.first { it.activity == FarmActivity.PLANTING }.verdict)
        assertTrue(a2.any { it.code == "dry_spell" } && a2.any { it.code == "heat" })
    }

    @Test
    fun demoWeatherIsDeterministicAndHasDecisions() {
        val today = LocalDate.of(2026, 9, 30)
        val a = DemoWeather.forecast("Bugesera", today, 0L)
        assertEquals(a, DemoWeather.forecast("Bugesera", today, 0L))
        assertEquals(7, a.days.size)
        assertEquals("2026-09-30", a.days[0].date)
        assertEquals(4, a.decisions.size)
    }

    @Test
    fun photoAnalyzerSeparatesSharpFromBlurryAndMeasuresColour() {
        val w = 64
        val h = 64
        val green = 0xFF3C9A3C.toInt()
        val brown = 0xFF7A4A22.toInt()
        val sharp = IntArray(w * h) { i -> if (((i % w) / 4 + (i / w) / 4) % 2 == 0) green else brown }
        val flat = IntArray(w * h) { green }
        val dark = IntArray(w * h) { 0xFF0A0F0A.toInt() }
        val s = PhotoAnalyzer.analyze(sharp, w, h)
        val f = PhotoAnalyzer.analyze(flat, w, h)
        val d = PhotoAnalyzer.analyze(dark, w, h)
        assertTrue("sharp ${s.sharpness}", s.sharpness > 0.5)
        assertTrue("flat ${f.sharpness}", f.sharpness < kb.engine.minSharpness)
        assertTrue(s.brownRatio > 0.4 && s.greenRatio > 0.4)
        assertTrue(f.greenRatio > 0.95)
        assertTrue(d.brightness < kb.engine.minBrightness)
    }

    @Test
    fun cropStageAndPriorityFollowTheSeason() {
        val today = LocalDate.of(2026, 9, 29).toEpochDay()
        val maize = Field("f1", "Ku musozi", 20, "maize", today - 14, createdAtMillis = 0L)
        val progress = CropStageCalculator.progress(kb.crop("maize"), maize, today)
        assertEquals(14, progress.dayNumber)
        assertEquals("seedling", progress.stage!!.id)

        val p = PriorityPicker.pick(kb, listOf(maize), emptyList(), emptyList(), null, today, 0L)
        assertEquals(Priority.Kind.CROP_STAGE, p.kind)
        assertEquals("Check your maize for armyworm", p.title)

        val d = blocking { kbEngine.diagnose(maizeRequest) }
        val p2 = PriorityPicker.pick(kb, listOf(maize), listOf(d), emptyList(), null, today, d.createdAtMillis + 1000)
        assertEquals(Priority.Kind.DIAGNOSIS_FOLLOW_UP, p2.kind)
        assertEquals(d.id, p2.diagnosisId)

        val none = PriorityPicker.pick(kb, emptyList(), emptyList(), emptyList(), null, today, 0L)
        assertEquals(Priority.Kind.ALL_GOOD, none.kind)
    }

    private fun day(rain: Int) = DayForecast("2026-10-01", 26, 15, rain, 0, WeatherCondition.fromRainChance(rain))
}
