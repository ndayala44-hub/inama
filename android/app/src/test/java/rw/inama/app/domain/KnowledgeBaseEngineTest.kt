package rw.inama.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rw.inama.app.TestSupport
import rw.inama.app.TestSupport.blocking
import rw.inama.app.data.json.Json
import rw.inama.app.data.mapper.ContentParsers
import rw.inama.app.domain.ai.DiagnosisRequest
import rw.inama.app.domain.ai.ImageFeatures
import rw.inama.app.domain.ai.QuestionRequest
import rw.inama.app.domain.ai.kb.KnowledgeBaseEngine
import rw.inama.app.domain.model.ConfidenceLevel
import rw.inama.app.domain.model.EngineMode
import rw.inama.app.domain.model.Outcome
import rw.inama.app.domain.model.PhotoQuality

/**
 * Runs the SAME shared vectors as the server (server/test/v1/engine.test.js), proving the Kotlin
 * on-device engine and the JavaScript server engine agree.
 */
class KnowledgeBaseEngineTest {
    private val kb = ContentParsers.knowledgeBase(Json.parseObject(TestSupport.sharedFile("knowledge/advisory_kb.en.json").readText()))
    private var counter = 0
    private val engine = KnowledgeBaseEngine({ kb }, clock = { 1_000L }, newId = { p -> "${p}_${++counter}" })

    @Test
    fun sharedVectorsMatchServerExpectations() {
        val vectors = Json.parseObject(TestSupport.sharedFile("knowledge/engine_test_vectors.json").readText()).objects("vectors")
        assertTrue(vectors.size >= 10)
        for (v in vectors) {
            val f = v.obj("features")?.let {
                ImageFeatures(it.double("brownRatio"), it.double("yellowRatio"), it.double("whiteRatio"), it.double("greenRatio"), it.double("brightness"), it.double("sharpness"))
            }
            val a = engine.assess(kb, v.stringOrNull("cropId"), v.strings("symptoms"), f)
            val expect = v.obj("expect")!!
            val name = v.string("name")
            expect.stringOrNull("level")?.let { assertEquals(name, it, a.level.name.lowercase()) }
            expect.stringOrNull("levelNot")?.let { assertNotEquals(name, it, a.level.name.lowercase()) }
            expect.stringOrNull("conditionId")?.let { assertEquals(name, it, a.conditionId) }
            expect.stringOrNull("quality")?.let { assertEquals(name, it, a.quality.wire) }
            if (expect.stringOrNull("level") == "low") assertEquals(name, Outcome.UNCERTAIN, a.outcome)
        }
    }

    @Test
    fun diagnosisUsesReviewedAdviceAndMarksOnDevice(): Unit = blocking {
        val d = engine.diagnose(DiagnosisRequest("/p.jpg", cropId = "maize", fieldId = "f1", symptoms = listOf("holes", "frass"), features = null, language = "en"))
        assertEquals(Outcome.CONDITION, d.outcome)
        assertEquals("Your maize may have fall armyworm", d.headline)
        assertEquals(ConfidenceLevel.HIGH, d.confidence.level)
        assertEquals(2, d.confidence.reasonCount)
        assertEquals(EngineMode.ON_DEVICE, d.engine.mode)
        assertEquals("f1", d.fieldId)
        assertEquals("/p.jpg", d.imagePath)
    }

    @Test
    fun uncertainResultGivesOnlySafeStepsAndEscalates(): Unit = blocking {
        val blurry = ImageFeatures(0.06, 0.04, 0.01, 0.5, 0.5, 0.05)
        val d = engine.diagnose(DiagnosisRequest("/p.jpg", cropId = "beans", fieldId = null, symptoms = listOf("brown_spots"), features = blurry, language = "en"))
        assertEquals(Outcome.UNCERTAIN, d.outcome)
        assertEquals(kb.lowConfidence.steps, d.steps)
        assertTrue(d.escalation.recommended)
        assertEquals(PhotoQuality.BLURRY, d.photoProblem!!.quality)
        assertTrue(d.candidates.isNotEmpty())
    }

    @Test
    fun healthyPhotoSaysSoWithMediumConfidence(): Unit = blocking {
        val healthy = ImageFeatures(0.005, 0.01, 0.01, 0.78, 0.55, 0.55)
        val d = engine.diagnose(DiagnosisRequest("/p.jpg", cropId = "maize", fieldId = null, symptoms = emptyList(), features = healthy, language = "en"))
        assertEquals(Outcome.HEALTHY, d.outcome)
        assertEquals("Your maize looks healthy", d.headline)
        assertEquals(ConfidenceLevel.MEDIUM, d.confidence.level)
    }

    @Test
    fun questionsMatchKeywordsOrFallBackToGeneralAdvice(): Unit = blocking {
        val a = engine.ask(QuestionRequest("My maize has small holes in the leaves", "maize", "en"))
        assertEquals("This sounds like fall armyworm", a.headline)
        assertEquals("kb_match", a.confidence.reasonCode)
        val g = engine.ask(QuestionRequest("hello there", null, "en"))
        assertEquals("general", g.confidence.reasonCode)
        assertEquals(ConfidenceLevel.LOW, g.confidence.level)
    }
}
