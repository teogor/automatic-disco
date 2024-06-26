import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  repositories {
    google()
    mavenCentral()
  }
}

// Lists all plugins used throughout the project without applying them.
plugins {
  // Kotlin Suite
  alias(libs.plugins.jetbrains.kotlin.jvm)
  alias(libs.plugins.jetbrains.kotlin.serialization) apply false
  alias(libs.plugins.jetbrains.kotlin.android) apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.jetbrains.dokka) apply true
  alias(libs.plugins.jetbrains.api.validator) apply true
}

afterEvaluate {
  val javaVersion = JavaVersion.VERSION_17

  val compileKotlin: KotlinCompile by tasks
  compileKotlin.kotlinOptions {
    jvmTarget = javaVersion.toString()
  }

  val compileTestKotlin: KotlinCompile by tasks
  compileTestKotlin.kotlinOptions {
    jvmTarget = javaVersion.toString()
  }
}

subprojects {
  apply<DokkaPlugin>()
}
