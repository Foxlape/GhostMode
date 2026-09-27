import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val envKeystoreFile = System.getenv("KEYSTORE_FILE")?.let { path -> rootProject.file(path) }
            when {
                keystorePropertiesFile.exists() -> {
                    val properties = Properties().apply {
                        keystorePropertiesFile.inputStream().use { stream -> load(stream) }
                    }
                    storeFile = rootProject.file(properties.getProperty("storeFile"))
                    storePassword = properties.getProperty("storePassword")
                    keyAlias = properties.getProperty("keyAlias")
                    keyPassword = properties.getProperty("keyPassword")
                }
                envKeystoreFile != null && envKeystoreFile.exists() && System.getenv("KEYSTORE_PASSWORD") != null -> {
                    storeFile = envKeystoreFile
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEY_ALIAS")
                    keyPassword = System.getenv("KEY_PASSWORD")
                }
                // No release key available (forks, F-Droid build server): fall back to the debug key.
                else -> initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
