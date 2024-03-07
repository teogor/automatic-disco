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

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinCommonCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation

/**
 * Creates and retrieves ksp-related configurations.
 */
class QuerentConfigurations(private val project: Project) {
  companion object {
    private const val PREFIX = "querent"
  }

  private val allowAllTargetConfiguration = project.findProperty(
    "querent.allow.all.target.configuration",
  )?.let {
    it.toString().toBoolean()
  } ?: true

  // The "ksp" configuration, applied to every compilations.
  private val configurationForAll = project.configurations.create(PREFIX).apply {
    isCanBeConsumed = false
    isCanBeResolved = false
    isVisible = false
  }

  private fun configurationNameOf(vararg parts: String): String {
    return parts.joinToString("") {
      it.replaceFirstChar { it.uppercase() }
    }.replaceFirstChar { it.lowercase() }
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun createConfiguration(
    name: String,
    readableSetName: String,
  ): Configuration {
    // maybeCreate to be future-proof, but we should never have a duplicate with current logic
    return project.configurations.maybeCreate(name).apply {
      description = "KSP dependencies for the '$readableSetName' source set."
      isCanBeResolved = false // we'll resolve the processor classpath config
      isCanBeConsumed = false
      isVisible = false
    }
  }

  private fun getAndroidConfigurationName(target: KotlinTarget, sourceSet: String): String {
    val isMain = sourceSet.endsWith("main", ignoreCase = true)
    val nameWithoutMain = when {
      isMain -> sourceSet.substring(0, sourceSet.length - 4)
      else -> sourceSet
    }
    // Note: on single-platform, target name is conveniently set to "".
    return configurationNameOf(PREFIX, target.name, nameWithoutMain)
  }

  private fun getKotlinConfigurationName(
    compilation: KotlinCompilation<*>,
    sourceSet: KotlinSourceSet,
  ): String {
    val isMain = compilation.name == KotlinCompilation.MAIN_COMPILATION_NAME
    val isDefault =
      sourceSet.name == compilation.defaultSourceSetName && compilation !is KotlinCommonCompilation
    // Note: on single-platform, target name is conveniently set to "".
    val name = if (isMain && isDefault) {
      // For js(IR), js(LEGACY), the target "js" is created.
      //
      // When js(BOTH) is used, target "jsLegacy" and "jsIr" are created.
      // Both targets share the same source set. Therefore configurations other than main compilation
      // are shared. E.g., "kspJsTest".
      // For simplicity and consistency, let's not distinguish them.
      when (val targetName = compilation.target.name) {
        "jsLegacy", "jsIr" -> "js"
        else -> targetName
      }
    } else if (compilation is KotlinCommonCompilation) {
      sourceSet.name + compilation.target.name.capitalize()
    } else {
      sourceSet.name
    }
    return configurationNameOf(PREFIX, name)
  }

  init {
    project.plugins.withType(KotlinBasePluginWrapper::class.java).configureEach {
      // 1.6.0: decorateKotlinProject(project.kotlinExtension)?
      decorateKotlinProject(
        project.extensions.getByName("kotlin") as KotlinProjectExtension,
        project,
      )
    }
  }

  private fun decorateKotlinProject(kotlin: KotlinProjectExtension, project: Project) {
    when (kotlin) {
      is KotlinSingleTargetExtension<*> -> decorateKotlinTarget(kotlin.target)
      is KotlinMultiplatformExtension -> {
        kotlin.targets.configureEach(::decorateKotlinTarget)

        var reported = false
        configurationForAll.dependencies.whenObjectAdded {
          if (!reported) {
            reported = true
            val msg = "The 'ksp' configuration is deprecated in Kotlin Multiplatform projects. " +
              "Please use target-specific configurations like 'kspJvm' instead."

            if (allowAllTargetConfiguration) {
              project.logger.warn(msg)
            } else {
              throw InvalidUserCodeException(msg)
            }
          }
        }
      }
    }
  }

  /**
   * Decorate the [KotlinSourceSet]s belonging to [target] to create one KSP configuration per source set,
   * named ksp<SourceSet>. The only exception is the main source set, for which we avoid using the
   * "main" suffix (so what would be "kspJvmMain" becomes "kspJvm").
   *
   * For Android, we prefer to use AndroidSourceSets from AGP rather than [KotlinSourceSet]s.
   * Even though the Kotlin Plugin does create [KotlinSourceSet]s out of AndroidSourceSets
   * ( https://kotlinlang.org/docs/mpp-configure-compilations.html#compilation-of-the-source-set-hierarchy ),
   * there are slight differences between the two - Kotlin creates some extra sets with unexpected word ordering,
   * and things get worse when you add product flavors. So, we use AGP sets as the source of truth.
   */
  private fun decorateKotlinTarget(target: KotlinTarget) {
    if (target.platformType == KotlinPlatformType.androidJvm) {
      AndroidPluginIntegration.forEachAndroidSourceSet(target.project) { sourceSet ->
        createConfiguration(
          name = getAndroidConfigurationName(target, sourceSet),
          readableSetName = "$sourceSet (Android)",
        )
      }
    } else {
      target.compilations.forEach { compilation ->
        compilation.kotlinSourceSetsObservable.forEach { sourceSet ->
          createConfiguration(
            name = getKotlinConfigurationName(compilation, sourceSet),
            readableSetName = sourceSet.name,
          )
        }
      }
    }
  }

  /**
   * Returns the user-facing configurations involved in the given compilation.
   * We use [KotlinCompilation.kotlinSourceSets], not [KotlinCompilation.allKotlinSourceSets] for a few reasons:
   * 1) consistency with how we created the configurations. For example, all* can return user-defined sets
   *    that don't belong to any compilation, like user-defined intermediate source sets (e.g. iosMain).
   *    These do not currently have their own ksp configuration.
   * 2) all* can return sets belonging to other [KotlinCompilation]s
   *
   * See test: SourceSetConfigurationsTest.configurationsForMultiplatformApp_doesNotCrossCompilationBoundaries
   */
  fun find(compilation: KotlinCompilation<*>): Set<Configuration> {
    val results = mutableListOf<String>()
    if (compilation is KotlinCommonCompilation) {
      results.add(getKotlinConfigurationName(compilation, compilation.defaultSourceSet))
    }
    compilation.kotlinSourceSets.mapTo(results) {
      getKotlinConfigurationName(compilation, it)
    }
    if (compilation.platformType == KotlinPlatformType.androidJvm) {
      compilation as KotlinJvmAndroidCompilation
      AndroidPluginIntegration.getCompilationSourceSets(compilation).mapTo(results) {
        getAndroidConfigurationName(compilation.target, it)
      }
      println("results -> androidJvm")
    }

    // Include the `ksp` configuration, if it exists, for all compilations.
    if (allowAllTargetConfiguration) {
      results.add(configurationForAll.name)
    }

    println("results -> ${results.toList()}")
    return results.mapNotNull {
      compilation.target.project.configurations.findByName(it)
    }.toSet()
  }
}
