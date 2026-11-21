buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.compose.compiler) apply false
}

val ktlintCli =
    configurations.create("ktlintCli") {
        isTransitive = false
    }

val detektCli =
    configurations.create("detektCli") {
        isTransitive = false
    }

dependencies {
    add(ktlintCli.name, variantOf(libs.ktlint) { classifier("all") })
    add(detektCli.name, variantOf(libs.detekt) { classifier("all") })
}

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Check Kotlin source and build script formatting."
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args("**/*.kt", "**/*.kts", "!**/build/**", "!**/.gradle/**")
}

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "Analyze the app Kotlin source."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args("--input", "app/src", "--build-upon-default-config", "--config", "config/detekt.yml")
}
