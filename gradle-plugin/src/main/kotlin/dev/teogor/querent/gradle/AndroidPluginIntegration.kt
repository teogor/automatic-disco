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

package dev.teogor.querent.gradle

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.SourceKind
import com.google.devtools.ksp.gradle.KspAATask
import com.google.devtools.ksp.gradle.KspTaskJvm
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import java.io.File

/**
 * This helper class handles communication with the android plugin.
 * It is isolated in a separate class to avoid adding dependency on the android plugin.
 * Instead, we add a compileOnly dependency to the Android Plugin, which means we can still function
 * without the Android plugin. The downside is that we need to ensure never to access Android
 * plugin APIs directly without checking its existence (we have tests covering that case).
 */
@Suppress("UnstableApiUsage")
object AndroidPluginIntegration {

  private val agpPluginIds = listOf(
    "com.android.application",
    "com.android.library",
    "com.android.dynamic-feature",
  )

  fun forEachAndroidSourceSet(project: Project, onSourceSet: (String) -> Unit) {
    agpPluginIds.forEach { agpPluginId ->
      project.pluginManager.withPlugin(agpPluginId) {
        // for android apps, we need a configuration per source set
        decorateAndroidExtension(project, onSourceSet)
      }
    }
  }

  private fun decorateAndroidExtension(project: Project, onSourceSet: (String) -> Unit) {
    val sourceSets = when (val androidExt = project.extensions.getByName("android")) {
      is BaseExtension -> androidExt.sourceSets
      is CommonExtension<*, *, *, *, *, *> -> androidExt.sourceSets
      else -> throw RuntimeException("Unsupported Android Gradle plugin version.")
    }
    // sourceSets.all {
    //   onSourceSet((this as AndroidSourceSet).name)
    // }
    sourceSets.forEach { sourceSet ->
      onSourceSet(sourceSet.name)
    }
  }

  fun getCompilationSourceSets(kotlinCompilation: KotlinJvmAndroidCompilation): List<String> {
    return kotlinCompilation.androidVariant
      .sourceSets
      .map { it.name }
  }

  /**
   * Support KspTaskJvm and KspAATask tasks
   */
  @Suppress("DEPRECATION")
  private fun tryUpdateKspWithAndroidSourceSets(
    kotlinCompilation: KotlinJvmAndroidCompilation,
    kspTaskProvider: TaskProvider<*>,
  ) {
    kotlinCompilation.androidVariant.getSourceFolders(SourceKind.JAVA).forEach { source ->
      kspTaskProvider.configure {
        if (this as? KspTaskJvm != null) {
          setSource(source)
          dependsOn(source)
        } else if (this as? KspAATask != null) {
          kspConfig.javaSourceRoots.from(source)
          dependsOn(source)
        } else {
          Unit
        }
      }
    }
  }

  private fun registerGeneratedSources(
    project: Project,
    kotlinCompilation: KotlinJvmAndroidCompilation,
    kspTaskProvider: TaskProvider<KspTaskJvm>,
    javaOutputDir: File,
    kotlinOutputDir: File,
    classOutputDir: File,
    resourcesOutputDir: FileCollection,
  ) {
    val kspJavaOutput = project.fileTree(javaOutputDir).builtBy(kspTaskProvider)
    val kspKotlinOutput = project.fileTree(kotlinOutputDir).builtBy(kspTaskProvider)
    val kspClassOutput = project.fileTree(classOutputDir).builtBy(kspTaskProvider)
    kspJavaOutput.include("**/*.java")
    kspKotlinOutput.include("**/*.kt")
    kspClassOutput.include("**/*.class")
    kotlinCompilation.androidVariant.registerExternalAptJavaOutput(kspJavaOutput)
    kotlinCompilation.androidVariant.addJavaSourceFoldersToModel(kspKotlinOutput.dir)
    kotlinCompilation.androidVariant.registerPreJavacGeneratedBytecode(kspClassOutput)
    kotlinCompilation.androidVariant.registerPostJavacGeneratedBytecode(resourcesOutputDir)
  }

  fun syncSourceSets(
    project: Project,
    kotlinCompilation: KotlinJvmAndroidCompilation,
    kspTaskProvider: TaskProvider<KspTaskJvm>,
    javaOutputDir: File,
    kotlinOutputDir: File,
    classOutputDir: File,
    resourcesOutputDir: FileCollection,
  ) {
    // Order is important here as we update task with AGP generated sources and
    // then update AGP with source that KSP will generate.
    // Mixing this up will cause circular dependency in Gradle
    tryUpdateKspWithAndroidSourceSets(kotlinCompilation, kspTaskProvider)

    registerGeneratedSources(
      project,
      kotlinCompilation,
      kspTaskProvider,
      javaOutputDir,
      kotlinOutputDir,
      classOutputDir,
      resourcesOutputDir,
    )
  }
}
