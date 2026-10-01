package rw.inama.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rw.inama.app.TestSupport
import rw.inama.app.data.json.Json
import rw.inama.app.data.json.JsonObject
import rw.inama.app.data.json.JsonParseException
import rw.inama.app.data.json.JsonString
import rw.inama.app.data.mapper.ApiMappers
import rw.inama.app.data.mapper.ContentParsers
import rw.inama.app.data.mapper.StorageCodecs
import rw.inama.app.domain.model.AiMode
import rw.inama.app.domain.model.AppSettings
import rw.inama.app.domain.model.CaseStatus
import rw.inama.app.domain.model.ConfidenceLevel
import rw.inama.app.domain.model.EngineMode
import rw.inama.app.domain.model.EvidenceKind
import rw.inama.app.domain.model.FarmActivity
import rw.inama.app.domain.model.FarmTask
import rw.inama.app.domain.model.FeedbackVerdict
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.model.LandType
import rw.inama.app.domain.model.Outcome
import rw.inama.app.domain.model.PhotoQuality
import rw.inama.app.domain.model.TaskSource
import rw.inama.app.domain.model.TextScale
import rw.inama.app.domain.model.Urgency
import rw.inama.app.domain.model.WeatherSource

class JsonAndMappersTest {

    private fun data(resource: String): JsonObject = Json.parseObject(TestSupport.resource(resource)).obj("data")!!

    @Test
    fun jsonRoundTripKeepsTextNumbersAndNesting() {
        val text = """{"a":"line\n\"quoted\" é ✓","n":-12.5,"i":42,"t":true,"z":null,"list":[1,{"k":"v"}],"u":"é"}"""
        val parsed = Json.parseObject(text)
        assertEquals("line\n\"quoted\" é ✓", parsed.string("a"))
        assertEquals(-12.5, parsed.double("n"), 0.0)
        assertEquals(42, parsed.int("i"))
        assertTrue(parsed.bool("t"))
        assertFalse(parsed.has("z"))
        assertEquals("v", parsed.arr("list").objects().first().string("k"))
        assertEquals("é", parsed.string("u"))
        val again = Json.parseObject(Json.stringify(parsed))
        assertEquals(parsed, again)
        assertEquals("42", Json.stringify(Json.of(42)))
    }

    @Test
    fun jsonRejectsBrokenInput() {
        for (bad in listOf("{", "{\"a\":}", "[1,]", "tru", "{\"a\":1} x")) {
            var failed = false
            try {
                Json.parse(bad)
            } catch (e: JsonParseException) {
                failed = true
            }
            assertTrue("should reject $bad", failed)
        }
        assertEquals(JsonString("x"), Json.parse("\"x\""))
    }

    @Test
    fun knowledgeBaseParsesFromSharedFile() {
        val kb = ContentParsers.knowledgeBase(Json.parseObject(TestSupport.sharedFile("knowledge/advisory_kb.en.json").readText()))
        assertEquals("en", kb.language)
        assertEquals(10, kb.crops.size)
        assertTrue(kb.conditions.size >= 25)
        assertEquals(0.75, kb.engine.highThreshold, 0.0)
        val faw = kb.condition("maize_fall_armyworm")!!
        assertEquals(Urgency.TODAY, faw.urgency)
        assertEquals(0.95, faw.cues["holes"]!!, 0.0)
        assertTrue(kb.symptomsFor("maize").any { it.id == "holes" })
        assertFalse(kb.symptomsFor("maize").any { it.id == "powder_orange" })
        val lessons = ContentParsers.lessons(Json.parseObject(TestSupport.sharedFile("content/lessons.en.json").readText()))
        assertTrue(lessons.size >= 6)
        val districts = ContentParsers.districts(Json.parseObject(TestSupport.sharedFile("content/rwanda_districts.json").readText()))
        assertEquals(30, districts.size)
    }

    @Test
    fun apiDiagnosisMapsRealServerResponse() {
        val d = ApiMappers.diagnosis(data("/api/diagnosis_condition.json"), "local_1", "/photos/1.jpg", 0L)
        assertEquals("local_1", d.id)
        assertTrue(d.remoteId!!.startsWith("dg_"))
        assertEquals(Outcome.CONDITION, d.outcome)
        assertEquals("cassava_bacterial_blight", d.condition!!.id)
        assertEquals("Your cassava may have bacterial blight", d.headline)
        assertEquals(ConfidenceLevel.HIGH, d.confidence.level)
        assertEquals("photo_and_signs", d.confidence.reasonCode)
        assertEquals(3, d.confidence.reasonCount)
        assertEquals(Urgency.THIS_WEEK, d.urgency)
        assertEquals("field_1", d.fieldId)
        assertEquals(EngineMode.REMOTE, d.engine.mode)
        assertTrue(d.evidence.any { it.kind == EvidenceKind.SYMPTOM && it.id == "brown_spots" })
        assertTrue(d.createdAtMillis > 0)
        assertEquals("/photos/1.jpg", d.imagePath)

        val u = ApiMappers.diagnosis(data("/api/diagnosis_uncertain.json"), "local_2", null, 0L)
        assertEquals(Outcome.UNCERTAIN, u.outcome)
        assertEquals(PhotoQuality.BLURRY, u.photoProblem!!.quality)
        assertTrue(u.escalation.recommended)
        assertTrue(u.candidates.isNotEmpty())
    }

    @Test
    fun apiAnswerWeatherCaseAndAuthMap() {
        val a = ApiMappers.answer(data("/api/answer.json"), "maize", 0L)
        assertEquals("This sounds like fall armyworm", a.headline)
        assertEquals(ConfidenceLevel.MEDIUM, a.confidence.level)
        assertEquals("maize_fall_armyworm", a.relatedConditionId)

        val w = ApiMappers.weather(data("/api/weather.json"), 0L)
        assertEquals(7, w.days.size)
        assertEquals(WeatherSource.DEMO, w.source)
        assertEquals(listOf(FarmActivity.SPRAYING, FarmActivity.WEEDING, FarmActivity.PLANTING, FarmActivity.DRYING), w.decisions.map { it.activity })

        val sent = ApiMappers.expertCase(data("/api/case_sent.json"), "local_1", 0L)
        assertEquals(CaseStatus.SENT, sent.status)
        val answered = ApiMappers.expertCase(data("/api/case_answered.json"), "local_1", 0L)
        assertEquals(CaseStatus.ANSWERED, answered.status)
        assertNotNull(answered.replyText)

        val otp = ApiMappers.otpChallenge(data("/api/otp_request.json"), "+250788123456")
        assertEquals(6, otp.devCode!!.length)
        val session = ApiMappers.session(data("/api/otp_verify.json"))
        assertEquals("+250788123456", session.phone)
        assertTrue(session.remote)
        assertNotNull(session.token)
    }

    @Test
    fun storageCodecsRoundTrip() {
        val d = ApiMappers.diagnosis(data("/api/diagnosis_condition.json"), "local_1", "/p.jpg", 0L)
            .copy(feedback = FeedbackVerdict.CORRECT, caseId = "case_1")
        assertEquals(d, StorageCodecs.diagnosisFrom(Json.parseObject(Json.stringify(StorageCodecs.diagnosisToJson(d)))))

        val a = ApiMappers.answer(data("/api/answer.json"), "maize", 0L)
        assertEquals(a, StorageCodecs.answerFrom(StorageCodecs.answerToJson(a)))

        val w = ApiMappers.weather(data("/api/weather.json"), 0L)
        assertEquals(w, StorageCodecs.weatherFrom(Json.parseObject(Json.stringify(StorageCodecs.weatherToJson(w)))))

        val task = FarmTask("t1", "Weed the beans", "f1", "beans", Urgency.THIS_WEEK, TaskSource.ADVICE, "dg_1", false, 10L)
        assertEquals(task, StorageCodecs.taskFrom(StorageCodecs.taskToJson(task)))

        val field = Field("f1", "Ku musozi", 20, "maize", 20000L, LandType.HILLSIDE, 5L)
        assertEquals(field, StorageCodecs.fieldFrom(StorageCodecs.fieldToJson(field)))
        val notPlanted = field.copy(plantedOnEpochDay = null)
        assertNull(StorageCodecs.fieldFrom(StorageCodecs.fieldToJson(notPlanted)).plantedOnEpochDay)

        val s = AppSettings(textScale = TextScale.EXTRA_LARGE, aiMode = AiMode.ON_DEVICE_ONLY, serverUrlOverride = "http://10.0.2.2:5055/api/v1", onboardingComplete = true)
        assertEquals(s, StorageCodecs.settingsFrom(StorageCodecs.settingsToJson(s)))
        assertEquals(AppSettings(), StorageCodecs.settingsFrom(Json.parseObject("{}")))
    }
}
