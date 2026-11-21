plugins {
    id("com.android.application")
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
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
    }

    packaging {
        jniLibs {
            // AndroidX ships this library with its debug symbols already stripped.
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    testImplementation(libs.junit)
}
