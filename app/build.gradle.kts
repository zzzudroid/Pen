import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Параметры подписи читаем из несинхронизируемого keystore.properties (в .gitignore)
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// Версия сборки для экрана «О программе»: короткий git-хэш + пометка незакоммиченных изменений
fun runGit(vararg args: String): String = try {
    ProcessBuilder(listOf("git", *args))
        .directory(rootDir).redirectErrorStream(true).start()
        .inputStream.bufferedReader().readText().trim()
} catch (e: Exception) { "" }

val gitHash: String = runGit("rev-parse", "--short", "HEAD")
    .takeIf { it.isNotEmpty() && !it.contains(' ') } ?: "unknown"
val gitDirty: Boolean = runGit("status", "--porcelain").isNotEmpty()
val buildTime: String = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())

android {
    namespace = "com.zzzudroid.pen"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zzzudroid.pen"
        // Приложение только для Xiaomi Pad 7 (Android 15) — старые версии не поддерживаем
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        // Данные о сборке для экрана «О программе»
        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
        buildConfigField("boolean", "GIT_DIRTY", "$gitDirty")
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    // Холст: штрихи, кисти, геометрия и отрисовка с низкой задержкой (front buffer)
    implementation(libs.androidx.ink.authoring)
    implementation(libs.androidx.ink.brush)
    implementation(libs.androidx.ink.geometry)
    implementation(libs.androidx.ink.rendering)
    implementation(libs.androidx.ink.strokes)
    implementation(libs.androidx.ink.storage)
    // Распознавание рукописи (поиск по тетрадям), модель скачивается на устройство
    implementation(libs.mlkit.digital.ink)
    implementation(libs.androidx.input.motionprediction)
    debugImplementation(libs.androidx.ui.tooling)

    // Юнит-тесты логики диагностики — чистый JVM, без эмулятора
    testImplementation(libs.junit)
    // org.json в юнит-тестах JVM — настоящая реализация (в android.jar методы — заглушки)
    testImplementation("org.json:json:20240303")
}

// BUILD_TIME должен отражать реальный момент сборки, а не «залипать» от прошлого
// прогона — отключаем up-to-date для генерации BuildConfig (как в VoiceTimer)
tasks.matching { it.name.startsWith("generate") && it.name.endsWith("BuildConfig") }
    .configureEach { outputs.upToDateWhen { false } }
