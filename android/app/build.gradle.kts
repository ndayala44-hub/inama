plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Configuration comes from gradle.properties / -P flags, never from code (see AppConfig.kt).
fun prop(name: String, default: String): String = (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: default
val apiBaseUrl = prop("inama.apiBaseUrl", "http://10.0.2.2:5055/api/v1")
val offlineDemoCode = prop("inama.offlineDemoCode", "123456")
val helpline = prop("inama.helpline", "+250 788 000 000")

android {
    namespace = "rw.inama.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "rw.inama.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "OFFLINE_DEMO_CODE", "\"$offlineDemoCode\"")
        buildConfigField("String", "HELPLINE_NUMBER", "\"$helpline\"")
    }

    buildTypes {
        debug {
            // Lets the app reach a development server over plain http (emulator / LAN).
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            // Signed with the debug key so demo builds install; replace with a real keystore before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            // The reviewed knowledge base and lessons are shared with the server: one source of truth.
            assets.srcDirs("src/main/assets", "../../shared")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
