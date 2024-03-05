/*
 * Copyright 2024 teogor (Teodor Grigor)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
