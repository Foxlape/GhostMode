import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

data class ReleaseKey(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

fun releaseKeyOrNull(): ReleaseKey? {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        val properties = Properties().apply { propertiesFile.inputStream().use { load(it) } }
        return ReleaseKey(
            storeFile = rootProject.file(properties.getProperty("storeFile")),
            storePassword = properties.getProperty("storePassword"),
            keyAlias = properties.getProperty("keyAlias"),
            keyPassword = properties.getProperty("keyPassword")
        )
    }
    val envFile = System.getenv("KEYSTORE_FILE")?.let { rootProject.file(it) } ?: return null
    val storePassword = System.getenv("KEYSTORE_PASSWORD") ?: return null
    if (!envFile.exists()) return null
    return ReleaseKey(envFile, storePassword, System.getenv("KEY_ALIAS").orEmpty(), System.getenv("KEY_PASSWORD").orEmpty())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ghostmode.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ghostmode.app"
        minSdk = 26
        targetSdk = 35
        // versionCode = major * 10000 + minor * 100 + patch
        versionCode = 200
        versionName = "0.2.0"
    }

    // Release key from keystore.properties or KEYSTORE_* environment variables. Without it
    // (forks, F-Droid / IzzyOnDroid build servers) the release APK is left unsigned so the
    // builder can sign it with its own key.
    val releaseKey = releaseKeyOrNull()
    if (releaseKey != null) {
        signingConfigs.create("release") {
            storeFile = releaseKey.storeFile
            storePassword = releaseKey.storePassword
            keyAlias = releaseKey.keyAlias
            keyPassword = releaseKey.keyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // The dependency metadata block is encrypted with a Google key and flagged by F-Droid /
    // IzzyOnDroid scanners; it is only useful for Play Console.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // `./gradlew testDebugUnitTest -Pscreenshots` renders store screenshots into fastlane/metadata.
                // Otherwise the Robolectric screenshot suite is skipped entirely (it downloads a full SDK jar).
                if (!project.hasProperty("screenshots")) test.filter.excludeTestsMatching("*ScreenshotTest")
                test.systemProperty("ghost.screenshots", project.hasProperty("screenshots").toString())
                test.systemProperty("roborazzi.test.record", project.hasProperty("screenshots").toString())
                test.systemProperty("ghost.metadataDir", rootProject.file("fastlane/metadata/android").absolutePath)
                test.systemProperty("ghost.screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
            }
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
