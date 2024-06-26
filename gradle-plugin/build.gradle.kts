/*
 * Copyright 2023 teogor (Teodor Grigor)
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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

tasks.withType<KotlinCompile> {
  compilerOptions.freeCompilerArgs.add("-Xjvm-default=all-compatibility")
}

plugins {
  `kotlin-dsl`
  alias(libs.plugins.gradle.publish)
  alias(libs.plugins.build.config)
  alias(libs.plugins.jetbrains.kotlin.serialization)

  alias(libs.plugins.teogor.winds)
}

dependencies {
  api(project(":gradle-plugin-api"))

  compileOnly(libs.kotlin.gradle.plugin)
  compileOnly(libs.kotlin.gradle.plugin.api)
  compileOnly(libs.kotlin.compiler.embeddable)

  implementation(gradleApi())
  implementation(libs.android.gradle.plugin)
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.ksp.gradle.plugin)
  implementation(libs.symbol.processing.api)

  implementation(libs.kotlin.poet)
  implementation(libs.kotlin.xml.builder)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.gson)
  implementation(libs.jdom2)
}

@Suppress("UnstableApiUsage")
gradlePlugin {
  website.set("https://source.teogor.dev/querent")
  vcsUrl.set("https://github.com/teogor/querent")

  plugins {
    register("querentPlugin") {
      id = "dev.teogor.querent"
      implementationClass = "dev.teogor.querent.Plugin"
      displayName = "Querent Plugin"
      description = "Querent lays the groundwork for your project's resource management, fostering consistency and efficiency across your development workflow."
      tags = listOf("android", "kotlin", "productivity", "tools", "dsl", "code-generation", "teogor")
    }

    register("querentPluginX") {
      id = "dev.teogor.querent.x"
      implementationClass = "dev.teogor.querent.MyCompilerPlugin"
      displayName = "KspGradleSubplugin Plugin"
      description = "Querent lays the groundwork for your project's resource management, fostering consistency and efficiency across your development workflow."
      tags = listOf("android", "kotlin", "productivity", "tools", "dsl", "code-generation", "teogor")
    }

    register("querentPluginKsp") {
      id = "dev.teogor.querent.gradle"
      implementationClass = "dev.teogor.querent.gradle.KspGradleSubplugin"
      displayName = "KspGradleSubplugin Plugin"
      description = "Querent lays the groundwork for your project's resource management, fostering consistency and efficiency across your development workflow."
      tags = listOf("android", "kotlin", "productivity", "tools", "dsl", "code-generation", "teogor")
    }
  }
}

winds {
  mavenPublish {
    displayName = "Gradle Plugin"
    name = "gradle-plugin"
  }
}

afterEvaluate {
  version = "1.0.0-alpha03-SNAPSHOT-8f9a736"
}

buildConfig {
  packageName("dev.teogor.querent")

  afterEvaluate {
    buildConfigField("String", "NAME", "\"${group}\"")
    buildConfigField("String", "VERSION", "\"${version}\"")
  }
}

abstract class WriteVersionSrcTask @Inject constructor(
  @get:Input val querentVersion: String,
  @get:Input val kotlinVersion: String,
  @get:OutputDirectory val outputSrcDir: File
) : DefaultTask() {
  @TaskAction
  fun generate() {
    File(outputSrcDir, "QuerentVersions.kt").writeText(
      """
      |package dev.teogor.querent.gradle
      |
      |val QUERENT_KOTLIN_BASE_VERSION = "$kotlinVersion"
      |val QUERENT_VERSION = "$querentVersion"
      """.trimMargin()
    )
  }
}

val querentVersionDir = File(project.buildDir, "generated/querent-versions")
afterEvaluate {
  val writeVersionSrcTask = tasks.register<WriteVersionSrcTask>(
    "generateQuerentVersions",
    version,
    libs.versions.kotlin.asProvider().get(),
    querentVersionDir,
  )

  kotlin {
    sourceSets {
      main {
        kotlin.srcDir(writeVersionSrcTask.map { it.outputSrcDir })
      }
    }
  }
}
