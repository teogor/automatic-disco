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

package dev.teogor.querent

import com.google.devtools.ksp.gradle.KspTask
import com.google.devtools.ksp.gradle.toSubpluginOptions
import dev.teogor.querent.gradle.KspConfigurations
import dev.teogor.querent.gradle.KspGradleSubplugin
import dev.teogor.querent.gradle.QUERENT_KOTLIN_BASE_VERSION
import dev.teogor.querent.gradle.QUERENT_VERSION
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.transform.TransformSpec
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.SourcesChanges
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.internal.kapt.incremental.CLASS_STRUCTURE_ARTIFACT_TYPE
import org.jetbrains.kotlin.gradle.internal.kapt.incremental.ClasspathSnapshot
import org.jetbrains.kotlin.gradle.internal.kapt.incremental.KaptClasspathChanges
import org.jetbrains.kotlin.gradle.internal.kapt.incremental.StructureTransformAction
import org.jetbrains.kotlin.gradle.internal.kapt.incremental.StructureTransformLegacyAction
import org.jetbrains.kotlin.gradle.plugin.CompilerPluginConfig
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.InternalSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationWithResources
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinCommonCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinSharedNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaCompilation
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.incremental.isJavaFile
import org.jetbrains.kotlin.incremental.isKotlinFile
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.io.File

class MyCompilerPlugin : KotlinCompilerPluginSupportPlugin {
  companion object {
    const val KSP_PLUGIN_ID = "com.google.devtools.ksp.symbol-processing"
    const val KSP_API_ID = "symbol-processing-api"
    const val KSP_COMPILER_PLUGIN_ID = "symbol-processing"
    const val KSP_COMPILER_PLUGIN_ID_NON_EMBEDDABLE = "symbol-processing-cmdline"
    const val KSP_GROUP_ID = "com.google.devtools.ksp"
    const val KSP_PLUGIN_CLASSPATH_CONFIGURATION_NAME = "kspPluginClasspath"
    const val KSP_PLUGIN_CLASSPATH_CONFIGURATION_NAME_NON_EMBEDDABLE =
      "kspPluginClasspathNonEmbeddable"

    @JvmStatic
    fun getKspOutputDir(project: Project, sourceSetName: String, target: String) =
      File(project.project.buildDir, "generated/ksp/$target/$sourceSetName")

    @JvmStatic
    fun getKspClassOutputDir(project: Project, sourceSetName: String, target: String) =
      File(getKspOutputDir(project, sourceSetName, target), "classes")

    @JvmStatic
    fun getKspJavaOutputDir(project: Project, sourceSetName: String, target: String) =
      File(getKspOutputDir(project, sourceSetName, target), "java")

    @JvmStatic
    fun getKspKotlinOutputDir(project: Project, sourceSetName: String, target: String) =
      File(getKspOutputDir(project, sourceSetName, target), "kotlin")

    @JvmStatic
    fun getKspResourceOutputDir(project: Project, sourceSetName: String, target: String) =
      File(getKspOutputDir(project, sourceSetName, target), "resources")

    @JvmStatic
    fun getKspCachesDir(project: Project, sourceSetName: String, target: String) =
      File(project.project.buildDir, "kspCaches/$target/$sourceSetName")

    @JvmStatic
    private fun getSubpluginOptions(
      project: Project,
      sourceSetName: String,
      target: String,
      isIncremental: Boolean,
      allWarningsAsErrors: Boolean,
      commandLineArgumentProviders: ListProperty<CommandLineArgumentProvider>,
      commonSources: List<File>,
    ): List<SubpluginOption> {
      val options = mutableListOf<SubpluginOption>()
      options +=
        InternalSubpluginOption(
          "classOutputDir",
          getKspClassOutputDir(project, sourceSetName, target).path,
        )
      options +=
        InternalSubpluginOption(
          "javaOutputDir",
          getKspJavaOutputDir(project, sourceSetName, target).path,
        )
      options +=
        InternalSubpluginOption(
          "kotlinOutputDir",
          getKspKotlinOutputDir(project, sourceSetName, target).path,
        )
      options += InternalSubpluginOption(
        "resourceOutputDir",
        getKspResourceOutputDir(project, sourceSetName, target).path,
      )
      options += InternalSubpluginOption(
        "cachesDir",
        getKspCachesDir(project, sourceSetName, target).path,
      )
      options += InternalSubpluginOption(
        "kspOutputDir",
        getKspOutputDir(project, sourceSetName, target).path,
      )
      options += SubpluginOption("incremental", isIncremental.toString())
      options += SubpluginOption(
        "incrementalLog",
        project.findProperty("ksp.incremental.log")?.toString() ?: "false",
      )
      options += InternalSubpluginOption("projectBaseDir", project.project.projectDir.canonicalPath)
      options += SubpluginOption("allWarningsAsErrors", allWarningsAsErrors.toString())
      // Turn this on by default to work KT-30172 around. It is off by default in the compiler plugin.
      options += SubpluginOption(
        "returnOkOnError",
        project.findProperty("ksp.return.ok.on.error")?.toString() ?: "true",
      )
      commonSources.ifNotEmpty {
        options += FilesSubpluginOption("commonSources", this)
      }

      options += SubpluginOption(
        "mapAnnotationArgumentsInJava",
        project.findProperty("ksp.map.annotation.arguments.in.java")?.toString() ?: "false",
      )
      commandLineArgumentProviders.get().forEach {
        it.asArguments().forEach { argument ->
          if (!argument.matches(Regex("\\S+=\\S+"))) {
            throw IllegalArgumentException("KSP apoption does not match \\S+=\\S+: $argument")
          }
          options += InternalSubpluginOption("apoption", argument)
        }
      }
      return options
    }
  }

  private lateinit var kspConfigurations: KspConfigurations
  override fun apply(target: Project) {
    kspConfigurations = KspConfigurations(target)
  }

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
    val project = kotlinCompilation.target.project
    val kspVersion = ApiVersion.parse(QUERENT_KOTLIN_BASE_VERSION)!!
    val kotlinVersion = ApiVersion.parse(project.getKotlinPluginVersion())!!

    // Check version and show warning by default.
    val noVersionCheck = project.findProperty("ksp.version.check")?.toString()?.toBoolean() == false
    if (!noVersionCheck) {
      if (kspVersion < kotlinVersion) {
        project.logger.warn(
          "querent-$QUERENT_VERSION is too old for kotlin-$kotlinVersion. " +
            "Please upgrade querent or downgrade kotlin-gradle-plugin to $QUERENT_KOTLIN_BASE_VERSION.",
        )
      }
      if (kspVersion > kotlinVersion) {
        project.logger.warn(
          "querent-$QUERENT_VERSION is too new for kotlin-$kotlinVersion. " +
            "Please upgrade kotlin-gradle-plugin to $QUERENT_KOTLIN_BASE_VERSION.",
        )
      }
    }

    return true
  }

  @OptIn(ExperimentalBuildToolsApi::class)
  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>,
  ): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project
    val kotlinCompileProvider: TaskProvider<AbstractKotlinCompileTool<*>> =
      project.locateTask(kotlinCompilation.compileKotlinTaskName)
        ?: return project.provider { emptyList() }
    val kspConfigurations = kspConfigurations.find(kotlinCompilation)
    val nonEmptyKspConfigurations = kspConfigurations.filter { it.allDependencies.isNotEmpty() }
    if (nonEmptyKspConfigurations.isEmpty()) {
      // return project.provider { emptyList() }
    }
    if (kotlinCompileProvider.name == "compileKotlinMetadata") {
      return project.provider { emptyList() }
    }
    if ((kotlinCompilation as? KotlinSharedNativeCompilation)?.platformType == KotlinPlatformType.common) {
      return project.provider { emptyList() }
    }

    val target = kotlinCompilation.target.name
    val sourceSetName = kotlinCompilation.defaultSourceSet.name
    val classOutputDir = getKspClassOutputDir(project, sourceSetName, target)
    val javaOutputDir = getKspJavaOutputDir(project, sourceSetName, target)
    val kotlinOutputDir = getKspKotlinOutputDir(project, sourceSetName, target)
    val resourceOutputDir = getKspResourceOutputDir(project, sourceSetName, target)
    val kspOutputDir = getKspOutputDir(project, sourceSetName, target)

    println("target=$target")
    println("kotlinCompilationTarget=${kotlinCompilation.target}")

    findJavaTaskForKotlinCompilation(kotlinCompilation)?.configure(
      object : Action<JavaCompile> {
        override fun execute(javaCompile: JavaCompile) {
          val generatedJavaSources = javaCompile.project.fileTree(javaOutputDir)
          generatedJavaSources.include("**/*.java")
          javaCompile.source(generatedJavaSources)
          javaCompile.classpath += project.files(classOutputDir)
        }
      },
    )

    val processingModel = project.findProperty(
      "querent.experimental.processing.model",
    )?.toString() ?: "traditional"

    assert(kotlinCompileProvider.name.startsWith("compile"))
    val kspTaskName = kotlinCompileProvider.name.replaceFirst("compile", "ksp")

    val processorClasspath = project.configurations
      .maybeCreate("${kspTaskName}ProcessorClasspath")
      .extendsFrom(*nonEmptyKspConfigurations.toTypedArray()).markResolvable()

    fun configureAsKspTask(kspTask: Task, isIncremental: Boolean) {
      // depends on the processor; if the processor changes, it needs to be reprocessed.
      kspTask.dependsOn(processorClasspath.buildDependencies)
      // kspTask.commandLineArgumentProviders.addAll(kspExtension.commandLineArgumentProviders)

      // kspTask.options.addAll(
      //   kspTask.project.provider {
      //     getSubpluginOptions(
      //       project = project,
      //       sourceSetName = sourceSetName,
      //       target = target,
      //       isIncremental = isIncremental,
      //       allWarningsAsErrors = true,
      //       commandLineArgumentProviders = kspTask.commandLineArgumentProviders,
      //       commonSources = emptyList(),
      //     )
      //   }
      // )
      // kspTask.inputs.property("apOptions", kspExtension.arguments)
      kspTask.inputs.files(processorClasspath).withNormalizer(ClasspathNormalizer::class.java)
    }
    //
    // fun configureAsAbstractKotlinCompileTool(kspTask: AbstractKotlinCompileTool<*>) {
    //   kspTask.destinationDirectory.set(kspOutputDir)
    //   disableRunViaBuildToolsApi(kspTask)
    //   kspTask.outputs.dirs(
    //     kotlinOutputDir,
    //     javaOutputDir,
    //     classOutputDir,
    //     resourceOutputDir
    //   )
    //
    //   val kotlinCompileTask = kotlinCompileProvider.get()
    //   if (kspExtension.allowSourcesFromOtherPlugins) {
    //     fun setSource(source: FileCollection) {
    //       // kspTask.setSource(source) would create circular dependency.
    //       // Therefore we need to manually extract input deps, filter them, and tell kspTask.
    //       kspTask.setSource(project.provider { source.files })
    //       kspTask.dependsOn(project.provider { source.nonSelfDeps(kspTaskName) })
    //     }
    //
    //     setSource(
    //       kotlinCompileTask.sources.filter {
    //         !kotlinOutputDir.isParentOf(it) && !javaOutputDir.isParentOf(it)
    //       }
    //     )
    //     if (kotlinCompileTask is KotlinCompile) {
    //       setSource(
    //         kotlinCompileTask.javaSources.filter {
    //           !kotlinOutputDir.isParentOf(it) && !javaOutputDir.isParentOf(it)
    //         }
    //       )
    //     }
    //   } else {
    //     kotlinCompilation.allKotlinSourceSetsObservable.forAll { sourceSet ->
    //       kspTask.setSource(
    //         sourceSet.kotlin.srcDirs.filter {
    //           !kotlinOutputDir.isParentOf(it) && !javaOutputDir.isParentOf(it)
    //         }
    //       )
    //       kspTask.dependsOn(sourceSet.kotlin.nonSelfDeps(kspTaskName))
    //     }
    //   }
    //
    //   kspTask.libraries.setFrom(
    //     kotlinCompileTask.project.files(
    //       Callable {
    //         kotlinCompileTask.libraries.filter {
    //           // manually exclude KAPT generated class folder from class path snapshot.
    //           // TODO: remove in 1.9.0.
    //
    //           !kspOutputDir.isParentOf(it) && !(it.isDirectory && it.listFiles()?.isEmpty() == true)
    //         }
    //       }
    //     )
    //   )
    //   // kotlinc's incremental compilation isn't compatible with symbol processing in a few ways:
    //   // * It doesn't consider private / internal changes when computing dirty sets.
    //   // * It compiles iteratively; Sources can be compiled in different rounds.
    //   (kspTask as? AbstractKotlinCompile<*>)?.incremental = false
    // }

    fun configurePluginOptions(kspTask: BaseKotlinCompile) {
      kspTask.pluginOptions.add(
        project.provider {
          CompilerPluginConfig().apply {
            (kspTask as KspTask).options.get().forEach {
              addPluginArgument(KSP_PLUGIN_ID, it)
            }
          }
        },
      )
    }

    fun configureLanguageVersion(kspTask: KotlinCompilationTask<*>) {
      kspTask.compilerOptions.useK2.value(false)
      val languageVersion = kotlinCompilation.compilerOptions.options.languageVersion
      val progressiveMode = kotlinCompilation.compilerOptions.options.progressiveMode
      kspTask.compilerOptions.languageVersion.value(
        project.provider {
          languageVersion.orNull?.let { version ->
            if (version >= KotlinVersion.KOTLIN_2_0) {
              KotlinVersion.KOTLIN_1_9
            } else {
              version
            }
          } ?: KotlinVersion.KOTLIN_1_9
        },
      )

      // Turn off progressive mode if we need to downgrade language version.
      kspTask.compilerOptions.progressiveMode.value(
        project.provider {
          val compileLangVer = languageVersion.orNull ?: KotlinVersion.DEFAULT
          if (compileLangVer >= KotlinVersion.KOTLIN_2_0) {
            false
          } else {
            progressiveMode.orNull
          }
        },
      )
    }

    val isIncremental = project.findProperty("ksp.incremental")?.toString()?.toBoolean() ?: true
    val isIntermoduleIncremental =
      (
        project.findProperty("ksp.incremental.intermodule")?.toString()?.toBoolean()
          ?: true
        ) && isIncremental
    val useKSP2 = project.findProperty("ksp.useKSP2")?.toString()?.toBoolean() ?: false

    // Create and configure KSP tasks.
    val kspTaskProvider = project.tasks.register(kspTaskName)
    configureAsKspTask(kspTaskProvider.get(), true)
    // val kspTaskProvider = if (useKSP2) {
    //   KspAATask.registerKspAATask(
    //     kotlinCompilation,
    //     kotlinCompileProvider,
    //     processorClasspath,
    //     kspExtension,
    //   )
    // } else {
    //   when (kotlinCompilation.platformType) {
    //     KotlinPlatformType.jvm, KotlinPlatformType.androidJvm -> {
    //       KotlinFactories.registerKotlinJvmCompileTask(project, kspTaskName, kotlinCompilation)
    //         .also {
    //           it.configure(
    //             object : Action<KspTaskJvm> {
    //               override fun execute(kspTask: KspTaskJvm) {
    //                 val kotlinCompileTask = kotlinCompileProvider.get() as KotlinCompile
    //                 // maybeBlockOtherPlugins(kspTask as BaseKotlinCompile)
    //                 // configureAsKspTask(kspTask, isIncremental)
    //                 // configureAsAbstractKotlinCompileTool(kspTask as AbstractKotlinCompileTool<*>)
    //                 configurePluginOptions(kspTask)
    //                 configureLanguageVersion(kspTask)
    //                 if (kspTask.classpathSnapshotProperties.useClasspathSnapshot.get() == false) {
    //                   kspTask.compilerOptions.moduleName.convention(
    //                     kotlinCompileTask.compilerOptions.moduleName.map { "$it-ksp" },
    //                   )
    //                 }
    //
    //                 kspTask.destination.value(kspOutputDir)
    //
    //                 val classStructureFiles = getClassStructureFiles(project, kspTask.libraries)
    //                 kspTask.incrementalChangesTransformers.add(
    //                   createIncrementalChangesTransformer(
    //                     isIncremental,
    //                     isIntermoduleIncremental,
    //                     getKspCachesDir(project, sourceSetName, target),
    //                     project.provider { classStructureFiles },
    //                     project.provider { kspTask.libraries },
    //                     project.provider { processorClasspath },
    //                   ),
    //                 )
    //               }
    //               // Don't support binary generation for non-JVM platforms yet.
    //               // FIXME: figure out how to add user generated libraries.
    //               kotlinCompilation.output.classesDirs.from(classOutputDir)
    //             }
    //           )
    //         }
    //     }
    //
    //     KotlinPlatformType.js, KotlinPlatformType.wasm -> {
    //       KotlinFactories.registerKotlinJSCompileTask(project, kspTaskName, kotlinCompilation)
    //         .also {
    //           it.configure(
    //             object : Action<KspTaskJS> {
    //               override fun execute(kspTask: KspTaskJS) {
    //                 val kotlinCompileTask = kotlinCompileProvider.get() as Kotlin2JsCompile
    //                 // maybeBlockOtherPlugins(kspTask as BaseKotlinCompile)
    //                 // configureAsKspTask(kspTask, isIncremental)
    //                 // configureAsAbstractKotlinCompileTool(kspTask as AbstractKotlinCompileTool<*>)
    //                 configurePluginOptions(kspTask)
    //                 configureLanguageVersion(kspTask)
    //
    //                 kspTask.incrementalChangesTransformers.add(
    //                   createIncrementalChangesTransformer(
    //                     isIncremental,
    //                     false,
    //                     getKspCachesDir(project, sourceSetName, target),
    //                     project.provider { project.files() },
    //                     project.provider { project.files() },
    //                     project.provider { processorClasspath },
    //                   ),
    //                 )
    //               }
    //             },
    //           )
    //         }
    //     }
    //
    //     KotlinPlatformType.common -> {
    //       KotlinFactories.registerKotlinMetadataCompileTask(project, kspTaskName, kotlinCompilation)
    //         .also {
    //           it.configure(
    //             object : Action<KspTaskMetadata> {
    //               override fun execute(kspTask: KspTaskMetadata) {
    //                 // maybeBlockOtherPlugins(kspTask as BaseKotlinCompile)
    //                 // configureAsKspTask(kspTask, isIncremental)
    //                 // configureAsAbstractKotlinCompileTool(kspTask as AbstractKotlinCompileTool<*>)
    //                 configurePluginOptions(kspTask)
    //                 configureLanguageVersion(kspTask)
    //
    //                 kspTask.incrementalChangesTransformers.add(
    //                   createIncrementalChangesTransformer(
    //                     isIncremental,
    //                     false,
    //                     getKspCachesDir(project, sourceSetName, target),
    //                     project.provider { project.files() },
    //                     project.provider { project.files() },
    //                     project.provider { processorClasspath },
    //                   ),
    //                 )
    //               }
    //             },
    //           )
    //         }
    //     }
    //
    //     KotlinPlatformType.native -> {
    //       KotlinFactories.registerKotlinNativeCompileTask(project, kspTaskName, kotlinCompilation)
    //         .also {
    //           it.configure(
    //             object : Action<KspTaskNative> {
    //               override fun execute(kspTask: KspTaskNative) {
    //                 val kotlinCompileTask = kotlinCompileProvider.get() as KotlinNativeCompile
    //                 configureAsKspTask(kspTask, false)
    //                 configureAsAbstractKotlinCompileTool(kspTask)
    //
    //                 val useEmbeddable =
    //                   project.findProperty("kotlin.native.useEmbeddableCompilerJar")
    //                     ?.toString()?.toBoolean() ?: true
    //                 val classpathCfg = if (useEmbeddable) {
    //                   kspClasspathCfg
    //                 } else {
    //                   kspClasspathCfgNonEmbeddable
    //                 }
    //                 // KotlinNativeCompile computes -Xplugin=... from compilerPluginClasspath.
    //                 if (kspExtension.blockOtherCompilerPlugins) {
    //                   kspTask.compilerPluginClasspath = classpathCfg
    //                 } else {
    //                   kspTask.compilerPluginClasspath =
    //                     classpathCfg + kotlinCompileTask.compilerPluginClasspath!!
    //                   kspTask.compilerPluginOptions.addPluginArgument(kotlinCompileTask.compilerPluginOptions)
    //                 }
    //                 kspTask.commonSources.from(kotlinCompileTask.commonSources)
    //                 kspTask.options.add(
    //                   FileCollectionSubpluginOption.create(
    //                     project = project,
    //                     name = "apclasspath",
    //                     classpath = processorClasspath,
    //                   ),
    //                 )
    //                 kspTask.compilerOptions.freeCompilerArgs.addAll(
    //                   kspTask.options.map {
    //                     it.flatMap { listOf("-P", it.toArg()) }
    //                   },
    //                 )
    //                 kspTask.compilerOptions.freeCompilerArgs.addAll(
    //                   kotlinCompileTask.compilerOptions.freeCompilerArgs,
    //                 )
    //                 configureLanguageVersion(kspTask)
    //                 // Cannot use lambda; See below for details.
    //                 // https://docs.gradle.org/7.2/userguide/validation_problems.html#implementation_unknown
    //                 kspTask.doFirst(
    //                   object : Action<Task> {
    //                     override fun execute(t: Task) {
    //                       kspOutputDir.deleteRecursively()
    //                     }
    //                   },
    //                 )
    //               }
    //             },
    //           )
    //         }
    //     }
    //     // No else; The cases should be exhaustive
    //   }
    // }

    val generatedSources = arrayOf(
      project.files(kotlinOutputDir), /* .builtBy(kspTaskProvider) */
      project.files(javaOutputDir), /* .builtBy(kspTaskProvider) */
    )
    if (kotlinCompilation is KotlinCommonCompilation) {
      // Do not add generated sources to common source sets.
      // They will be observed by downstreams and violate current build scheme.
      kotlinCompileProvider.configure(
        object : Action<AbstractKotlinCompileTool<*>> {
          override fun execute(t: AbstractKotlinCompileTool<*>) {
            t.source(*generatedSources)
          }
        },
      )
    } else {
      kotlinCompilation.defaultSourceSet.kotlin.srcDirs(*generatedSources)
    }

    kotlinCompileProvider.configure {
      when (this) {
        is AbstractKotlinCompile<*> -> libraries.from(project.files(classOutputDir))
      }
    }

    val processResourcesTaskName =
      (kotlinCompilation as? KotlinCompilationWithResources)?.processResourcesTaskName
        ?: "processResources"
    project.locateTask<ProcessResources>(processResourcesTaskName)?.let { provider ->
      provider.configure {
        from(project.files(resourceOutputDir))
      }
    }
    if (kotlinCompilation is KotlinJvmAndroidCompilation) {
      AndroidPluginIntegration.syncSourceSets(
        project = project,
        kotlinCompilation = kotlinCompilation,
        kspTaskProvider = kspTaskProvider,
        javaOutputDir = javaOutputDir,
        kotlinOutputDir = kotlinOutputDir,
        classOutputDir = classOutputDir,
        resourcesOutputDir = project.files(resourceOutputDir),
      )
    }

    return project.provider { emptyList() }
  }

  override fun getCompilerPluginId() = KspGradleSubplugin.KSP_PLUGIN_ID
  override fun getPluginArtifact() = SubpluginArtifact(
    groupId = "com.google.devtools.ksp",
    artifactId = KspGradleSubplugin.KSP_COMPILER_PLUGIN_ID,
    version = "1.9.23-1.0.20", // KSP_VERSION,
  )
}

// Copied from kotlin-gradle-plugin, because they are internal.
internal inline fun <reified T : Task> Project.locateTask(name: String): TaskProvider<T>? =
  try {
    tasks.withType(T::class.java).named(name)
  } catch (e: UnknownTaskException) {
    null
  }

// Copied from kotlin-gradle-plugin, because they are internal.
internal fun findJavaTaskForKotlinCompilation(
  compilation: KotlinCompilation<*>,
): TaskProvider<out JavaCompile>? =
  when (compilation) {
    is KotlinJvmAndroidCompilation -> compilation.compileJavaTaskProvider
    is KotlinWithJavaCompilation<*, *> -> compilation.compileJavaTaskProvider
    is KotlinJvmCompilation -> compilation.compileJavaTaskProvider // may be null for Kotlin-only JVM target in MPP
    else -> null
  }

internal val artifactType = Attribute.of("artifactType", String::class.java)

internal fun maybeRegisterTransform(project: Project) {
  // Use the same flag with KAPT, so as to share the same transformation in case KAPT and KSP are both enabled.
  if (!project.extensions.extraProperties.has("KaptStructureTransformAdded")) {
    val transformActionClass =
      if (GradleVersion.current() >= GradleVersion.version("5.4")) {
        StructureTransformAction::class.java
      } else {
        StructureTransformLegacyAction::class.java
      }
    project.dependencies.registerTransform(
      transformActionClass,
      object : Action<TransformSpec<TransformParameters.None>> {
        override fun execute(transformSpec: TransformSpec<TransformParameters.None>) {
          transformSpec.from.attribute(artifactType, "jar")
          transformSpec.to.attribute(artifactType, CLASS_STRUCTURE_ARTIFACT_TYPE)
        }
      },
    )

    project.dependencies.registerTransform(
      transformActionClass,
      object : Action<TransformSpec<TransformParameters.None>> {
        override fun execute(transformSpec: TransformSpec<TransformParameters.None>) {
          transformSpec.from.attribute(artifactType, "directory")
          transformSpec.to.attribute(artifactType, CLASS_STRUCTURE_ARTIFACT_TYPE)
        }
      },
    )

    project.extensions.extraProperties["KaptStructureTransformAdded"] = true
  }
}

internal fun getClassStructureFiles(
  project: Project,
  libraries: ConfigurableFileCollection,
): FileCollection {
  maybeRegisterTransform(project)

  val classStructureIfIncremental = project.configurations.detachedConfiguration(
    project.dependencies.create(project.files(project.provider { libraries })),
  ).markResolvable()

  return classStructureIfIncremental.incoming.artifactView(
    object : Action<ArtifactView.ViewConfiguration> {
      override fun execute(viewConfig: ArtifactView.ViewConfiguration) {
        viewConfig.attributes.attribute(artifactType, CLASS_STRUCTURE_ARTIFACT_TYPE)
      }
    },
  ).files
}

// Reuse Kapt's infrastructure to compute affected names in classpath.
// This is adapted from KaptTask.findClasspathChanges.
@OptIn(ExperimentalBuildToolsApi::class)
internal fun findClasspathChanges(
  changes: SourcesChanges,
  cacheDir: File,
  allDataFiles: Set<File>,
  libs: List<File>,
  processorCP: List<File>,
): KaptClasspathChanges {
  cacheDir.mkdirs()

  val changedFiles =
    (changes as? SourcesChanges.Known)?.let { it.modifiedFiles + it.removedFiles }?.toSet()
      ?: allDataFiles

  val loadedPrevious = ClasspathSnapshot.ClasspathSnapshotFactory.loadFrom(cacheDir)
  val previousAndCurrentDataFiles = lazy { loadedPrevious.getAllDataFiles() + allDataFiles }
  val allChangesRecognized = changedFiles.all {
    val extension = it.extension
    if (extension.isEmpty() || extension == "kt" || extension == "java" || extension == "jar" ||
      extension == "class"
    ) {
      return@all true
    }
    // if not a directory, Java source file, jar, or class, it has to be a structure file, in order to understand changes
    it in previousAndCurrentDataFiles.value
  }
  val previousSnapshot = if (allChangesRecognized) {
    loadedPrevious
  } else {
    ClasspathSnapshot.ClasspathSnapshotFactory.getEmptySnapshot()
  }

  val currentSnapshot =
    ClasspathSnapshot.ClasspathSnapshotFactory.createCurrent(
      cacheDir,
      libs,
      processorCP,
      allDataFiles,
    )

  val classpathChanges = currentSnapshot.diff(previousSnapshot, changedFiles)
  if (classpathChanges is KaptClasspathChanges.Unknown || changes is SourcesChanges.Unknown) {
    cacheDir.deleteRecursively()
    cacheDir.mkdirs()
  }
  currentSnapshot.writeToCache()

  return classpathChanges
}

@OptIn(ExperimentalBuildToolsApi::class)
internal fun SourcesChanges.hasNonSourceChange(): Boolean {
  if (this !is SourcesChanges.Known) {
    return true
  }

  return !(this.modifiedFiles + this.removedFiles).all {
    it.isKotlinFile(listOf("kt")) || it.isJavaFile()
  }
}

// Return a closure that captures required arguments only.
@OptIn(ExperimentalBuildToolsApi::class)
internal fun createIncrementalChangesTransformer(
  isKspIncremental: Boolean,
  isIntermoduleIncremental: Boolean,
  cacheDir: File,
  classpathStructure: Provider<FileCollection>,
  libraries: Provider<FileCollection>,
  processorCP: Provider<FileCollection>,
): (SourcesChanges) -> List<SubpluginOption> = { changedFiles ->
  val options = mutableListOf<SubpluginOption>()
  val apClasspath = processorCP.get().files.toList()
  if (isKspIncremental) {
    if (isIntermoduleIncremental) {
      // findClasspathChanges may clear caches, if there are
      // 1. unknown changes, or
      // 2. changes in annotation processors.
      val classpathChanges = findClasspathChanges(
        changedFiles,
        cacheDir,
        classpathStructure.get().files,
        libraries.get().files.toList(),
        apClasspath,
      )
      options += classpathChanges.toSubpluginOptions()
    } else {
      if (changedFiles.hasNonSourceChange()) {
        cacheDir.deleteRecursively()
      }
    }
  } else {
    cacheDir.deleteRecursively()
  }
  options += changedFiles.toSubpluginOptions()

  options += FilesSubpluginOption("apclasspath", apClasspath)

  options
}

fun KaptClasspathChanges.toSubpluginOptions(): List<SubpluginOption> {
  return if (this is KaptClasspathChanges.Known) {
    this.names.map { it.replace('/', '.').replace('$', '.') }.ifNotEmpty {
      listOf(SubpluginOption("changedClasses", joinToString(":")))
    } ?: emptyList()
  } else {
    emptyList()
  }
}

@OptIn(ExperimentalBuildToolsApi::class)
fun SourcesChanges.toSubpluginOptions(): List<SubpluginOption> {
  return if (this is SourcesChanges.Known) {
    val options = mutableListOf<SubpluginOption>()
    this.modifiedFiles.filter { it.isKotlinFile(listOf("kt")) || it.isJavaFile() }.ifNotEmpty {
      options += SubpluginOption("knownModified", map { it.path }.joinToString(File.pathSeparator))
    }
    this.removedFiles.filter { it.isKotlinFile(listOf("kt")) || it.isJavaFile() }.ifNotEmpty {
      options += SubpluginOption("knownRemoved", map { it.path }.joinToString(File.pathSeparator))
    }
    options
  } else {
    emptyList()
  }
}
