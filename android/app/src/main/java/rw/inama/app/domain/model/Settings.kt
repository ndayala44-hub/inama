package rw.inama.app.domain.model

/**
 * Languages Inama is designed for. Only English ships in the MVP; the others are declared so the
 * whole app (settings, content loading, AI requests, speech) already routes by language tag.
 * To enable one: add res/values-<tag>/strings.xml, shared/knowledge/advisory_kb.<tag>.json,
 * shared/content/lessons.<tag>.json, then flip [availableInThisVersion].
 */
enum class AppLanguage(val tag: String, val nativeName: String, val availableInThisVersion: Boolean) {
    ENGLISH("en", "English", true),
    KINYARWANDA("rw", "Ikinyarwanda", false),
    FRENCH("fr", "Français", false);

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

/** How advice is produced. AUTO = Inama server (Gemini) with automatic on-device fallback. */
enum class AiMode { AUTO, ON_DEVICE_ONLY }

enum class TextScale(val factor: Float) { NORMAL(1.0f), LARGE(1.15f), EXTRA_LARGE(1.3f) }

enum class SpeechRate(val rate: Float) { SLOW(0.8f), NORMAL(1.0f), FAST(1.2f) }

data class AppSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val readAloud: Boolean = true,
    val speechRate: SpeechRate = SpeechRate.NORMAL,
    val textScale: TextScale = TextScale.NORMAL,
    val highContrast: Boolean = false,
    val lowDataMode: Boolean = true,
    val aiMode: AiMode = AiMode.AUTO,
    /** Overrides BuildConfig.API_BASE_URL for testing against a local server; null = default. */
    val serverUrlOverride: String? = null,
    val onboardingComplete: Boolean = false,
    val shareAnonymously: Boolean = true,
    val promoterCanSeeFields: Boolean = true,
    val smsCopies: Boolean = true,
)
