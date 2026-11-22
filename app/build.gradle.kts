import java.security.MessageDigest

plugins {
    id("com.android.application")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

val ytDlpVersion = "2026.10.26"
val ytDlpSha256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"
val ytDlpAsset = layout.projectDirectory.file("src/main/assets/engine/yt-dlp")
val verifyBundledEngine =
    tasks.register("verifyBundledEngine") {
        inputs.file(ytDlpAsset)
        inputs.property("sha256", ytDlpSha256)
        doLast {
            val digest = MessageDigest.getInstance("SHA-256").digest(ytDlpAsset.asFile.readBytes())
            val actual = digest.joinToString("") { "%02x".format(it) }
            check(actual == ytDlpSha256) { "Unexpected SHA-256 for bundled yt-dlp $ytDlpVersion: $actual" }
        }
    }

tasks.named("preBuild") {
    dependsOn(verifyBundledEngine)
}

android {
    namespace = "app.myownware.perseid"
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 2
            }
    }

    defaultConfig {
        applicationId = "app.myownware.perseid"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
        buildConfigField("String", "BUNDLED_YT_DLP_VERSION", "\"$ytDlpVersion\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
    }

    packaging {
        jniLibs {
            // The engine starts its bundled executables through the native library directory.
            useLegacyPackaging = true
            keepDebugSymbols += setOf("**/libpython.so", "**/libpython.zip.so", "**/libqjs.so")
            // AndroidX ships this library with its debug symbols already stripped.
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.work.runtime)
    ksp(libs.room.compiler)
    implementation(libs.youtubedl) {
        exclude(group = "androidx.appcompat", module = "appcompat")
    }
    implementation(libs.jackson.databind)
    implementation(libs.commons.compress)
    implementation(libs.commons.io)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    testImplementation(libs.junit)
}
