package rw.inama.app.core.config

import rw.inama.app.BuildConfig

/**
 * Build-time configuration. Values come from app/build.gradle.kts (and can be overridden with
 * Gradle properties such as -Pinama.apiBaseUrl=...), never from code. The server URL can also be
 * changed at runtime in Settings › Developer for testing against a local server.
 */
data class AppConfig(
    val defaultApiBaseUrl: String = BuildConfig.API_BASE_URL,
    /** Code accepted when signing in while the server is unreachable (demo / offline only). */
    val offlineDemoCode: String = BuildConfig.OFFLINE_DEMO_CODE,
    val helplineNumber: String = BuildConfig.HELPLINE_NUMBER,
    val versionName: String = BuildConfig.VERSION_NAME,
    val isDebug: Boolean = BuildConfig.DEBUG,
    /** Longest side of photos kept on the phone and uploaded (pixels). */
    val photoMaxSide: Int = 1280,
    val photoMaxSideLowData: Int = 960,
)
