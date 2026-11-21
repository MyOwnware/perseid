plugins {
    id("com.android.application")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
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
