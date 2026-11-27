import java.security.MessageDigest

plugins {
    id("com.android.application")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

val releaseTag = providers.gradleProperty("perseidReleaseTag").orNull
val releaseVersion =
    releaseTag?.let { tag ->
        val match =
            requireNotNull(Regex("""v(0|[1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)""").matchEntire(tag)) {
                "Expected a stable vX.Y.Z tag with minor and patch versions below 100"
            }
        val (major, minor, patch) = match.destructured
        val majorNumber = requireNotNull(major.toLongOrNull()) { "Release major version is out of range" }
        require(majorNumber <= 210_000) { "Release major version is out of range" }
        val code = majorNumber * 10_000 + minor.toInt() * 100 + patch.toInt()
        require(code in 1..2_100_000_000) { "Release versionCode is out of range" }

        fun revision(ref: String): String =
            providers
                .exec { commandLine("git", "rev-parse", "--verify", ref) }
                .standardOutput.asText
                .get()
                .trim()
        require(revision("refs/tags/$tag^{commit}") == revision("HEAD")) { "Release tag must point at the checked-out commit" }
        tag.removePrefix("v") to code.toInt()
    }
val arm64Only = releaseVersion != null || providers.gradleProperty("perseidArm64").orNull == "true"

val ytDlpVersion = "2026.11.23"
val ytDlpSha256 = "e8665ed64d6b9172773e812f891fb707a0cb1ee8d9ef4fc1acb00cabee4c5256"
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
        versionCode = releaseVersion?.second ?: 1
        versionName = releaseVersion?.first ?: "0.0.0-dev"
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
        resources.pickFirsts += "META-INF/LICENSE.md"
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

androidComponents {
    finalizeDsl { android ->
        if (arm64Only) {
            android.defaultConfig.ndk.abiFilters
                .remove("x86_64")
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
    implementation(libs.bouncycastle.openpgp)
    implementation(libs.commons.compress)
    implementation(libs.commons.io)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    testImplementation(libs.junit)
}
