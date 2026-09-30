import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// qwen.json in the root (git-ignored, qwen.example.json is the shape): the family build's Qwen
// gateway, model and key, baked into BuildConfig. No file, no Qwen: the app is the public build.
val qwenFile = rootProject.file("qwen.json")
val qwen: Map<String, String> = if (!qwenFile.exists()) emptyMap() else {
    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(qwenFile) as Map<String, Any?>
    val q = listOf("base_url", "model", "api_key").associateWith { json[it]?.toString()?.trim().orEmpty() }
    // Half a Qwen would quietly build the public app; better to stop here.
    val missing = q.filterValues { it.isEmpty() }.keys
    if (missing.isNotEmpty()) throw GradleException("qwen.json: fill in ${missing.joinToString()} or delete the file")
    q
}
fun qwenField(name: String) = "\"" + qwen[name].orEmpty().replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.tidyorwhiny.app"
    compileSdk = 35

    signingConfigs {
        // Same key on every machine: a phone refuses an update signed by a different debug key.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.tidyorwhiny.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 6
        versionName = "1.05"

        buildConfigField("boolean", "FAMILY", qwen.isNotEmpty().toString())
        buildConfigField("String", "QWEN_BASE_URL", qwenField("base_url"))
        buildConfigField("String", "QWEN_MODEL", qwenField("model"))
        buildConfigField("String", "QWEN_API_KEY", qwenField("api_key"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "TidyOrWhiny_${variant.versionName}.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
