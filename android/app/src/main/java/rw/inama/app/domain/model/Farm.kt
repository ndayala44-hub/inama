package rw.inama.app.domain.model

data class Farmer(
    val id: String,
    val phone: String,
    val name: String,
    val districtId: String?,
    val sector: String,
    val keepsCrops: Boolean = true,
    val keepsAnimals: Boolean = false,
    val cooperative: String? = null,
) {
    val initials: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }.ifEmpty { "?" }

    val firstName: String get() = name.trim().substringBefore(' ').ifEmpty { name }
}

enum class LandType { HILLSIDE, FLAT, VALLEY }

data class Field(
    val id: String,
    val name: String,
    val areaAres: Int,
    val cropId: String,
    /** Days since 1970-01-01 (java.time.LocalDate.toEpochDay()); null = not planted yet. */
    val plantedOnEpochDay: Long?,
    val landType: LandType = LandType.HILLSIDE,
    val createdAtMillis: Long,
)

enum class TaskSource { ADVICE, MANUAL }

data class FarmTask(
    val id: String,
    val title: String,
    val fieldId: String?,
    val cropId: String?,
    val urgency: Urgency,
    val source: TaskSource,
    /** Diagnosis/answer id the task came from, used to avoid duplicates. */
    val sourceId: String?,
    val done: Boolean,
    val createdAtMillis: Long,
    val doneAtMillis: Long? = null,
)

data class OtpChallenge(val phone: String, val maskedPhone: String, val expiresInSeconds: Int, val devCode: String?)

data class Session(val farmerId: String, val phone: String, val token: String?, val remote: Boolean)
