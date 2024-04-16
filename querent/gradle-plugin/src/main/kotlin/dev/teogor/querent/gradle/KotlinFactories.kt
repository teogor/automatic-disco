@file:OptIn(ExperimentalBuildToolsApi::class)

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package dev.teogor.querent.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.ExecOperations
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.SourcesChanges
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptionsDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptionsHelper
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptionsDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptionsHelper
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformCommonCompilerOptionsDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformCommonCompilerOptionsHelper
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeCompilerOptionsDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeCompilerOptionsHelper
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationInfo
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.gradle.tasks.TaskOutputsBackup
import org.jetbrains.kotlin.gradle.tasks.configuration.BaseKotlin2JsCompileConfig
import org.jetbrains.kotlin.gradle.tasks.configuration.KotlinCompileCommonConfig
import org.jetbrains.kotlin.konan.target.HostManager
import java.io.File
import javax.inject.Inject

class KotlinFactories {
  companion object {
    fun registerKotlinJvmCompileTask(
      project: Project,
      taskName: String,
      kotlinCompilation: KotlinCompilation<*>,
    ): TaskProvider<out QuerentTaskJvm> {
      return project.tasks.register(taskName, QuerentTaskJvm::class.java).also { querentTaskProvider ->
        KotlinCompileConfig(KotlinCompilationInfo(kotlinCompilation))
          .execute(querentTaskProvider as TaskProvider<KotlinCompile>)

        // useClasspathSnapshot isn't configurable per task.
        // Workaround: enable the other path and ignore irrelevant changes
        // See [KotlinCompileConfig] in for details.
        // FIXME: make it configurable in upstream or support useClasspathSnapshot == true, if possible.
        querentTaskProvider.configure {
          val kCompilerOptions =
            kotlinCompilation.compilerOptions.options as KotlinJvmCompilerOptions
          KotlinJvmCompilerOptionsHelper.syncOptionsAsConvention(
            from = kCompilerOptions,
            into = compilerOptions,
          )

          if (classpathSnapshotProperties.useClasspathSnapshot.get()) {
            classpathSnapshotProperties.classpath.from(project.provider { libraries })
          }
        }
      }
    }

    fun registerKotlinJSCompileTask(
      project: Project,
      taskName: String,
      kotlinCompilation: KotlinCompilation<*>,
    ): TaskProvider<out QuerentTaskJS> {
      return project.tasks.register(taskName, QuerentTaskJS::class.java).also { querentTaskProvider ->
        BaseKotlin2JsCompileConfig<Kotlin2JsCompile>(KotlinCompilationInfo(kotlinCompilation))
          .execute(querentTaskProvider as TaskProvider<Kotlin2JsCompile>)
        querentTaskProvider.configure {
          val kCompilerOptions = kotlinCompilation.compilerOptions.options as KotlinJsCompilerOptions
          KotlinJsCompilerOptionsHelper.syncOptionsAsConvention(
            from = kCompilerOptions,
            into = compilerOptions,
          )

          incrementalJsKlib = false
        }
      }
    }

    fun registerKotlinMetadataCompileTask(
      project: Project,
      taskName: String,
      kotlinCompilation: KotlinCompilation<*>,
    ): TaskProvider<out QuerentTaskMetadata> {
      return project.tasks.register(taskName, QuerentTaskMetadata::class.java).also { querentTaskProvider ->
        KotlinCompileCommonConfig(KotlinCompilationInfo(kotlinCompilation))
          .execute(querentTaskProvider as TaskProvider<KotlinCompileCommon>)

        querentTaskProvider.configure {
          val kCompilerOptions =
            kotlinCompilation.compilerOptions.options as KotlinMultiplatformCommonCompilerOptions
          KotlinMultiplatformCommonCompilerOptionsHelper.syncOptionsAsConvention(
            from = kCompilerOptions,
            into = compilerOptions,
          )
        }
      }
    }

    fun registerKotlinNativeCompileTask(
      project: Project,
      taskName: String,
      kotlinCompilation: KotlinCompilation<*>,
    ): TaskProvider<out QuerentTaskNative> {
      return project.tasks.register(
        taskName,
        QuerentTaskNative::class.java,
        KotlinCompilationInfo(kotlinCompilation),
      ).apply {
        configure(
          object : Action<QuerentTaskNative> {
            override fun execute(querentTask: QuerentTaskNative) {
              val compilerOptions =
                kotlinCompilation.compilerOptions.options as KotlinNativeCompilerOptions
              KotlinNativeCompilerOptionsHelper.syncOptionsAsConvention(
                from = compilerOptions,
                into = querentTask.compilerOptions,
              )
              querentTask.produceUnpackedKlib.set(false)
              querentTask.onlyIf {
                // KonanTarget is not properly serializable, hence we should check by name
                // see https://youtrack.jetbrains.com/issue/KT-61657.
                val konanTargetName = querentTask.konanTarget.name
                HostManager().enabled.any {
                  it.name == konanTargetName
                }
              }
            }

          },
        )
      }
    }
  }
}

interface QuerentTask : Task {
  @get:Internal
  val options: ListProperty<SubpluginOption>

  @get:Nested
  val commandLineArgumentProviders: ListProperty<CommandLineArgumentProvider>

  @get:Internal
  val incrementalChangesTransformers: ListProperty<(SourcesChanges) -> List<SubpluginOption>>
}


@CacheableTask
abstract class QuerentTaskJvm @Inject constructor(
  workerExecutor: WorkerExecutor,
  objectFactory: ObjectFactory,
) : KotlinCompile(
  objectFactory.newInstance(KotlinJvmCompilerOptionsDefault::class.java),
  workerExecutor,
  objectFactory,
), QuerentTask {
  @get:OutputDirectory
  abstract val destination: Property<File>

  // Override incrementalProps to exclude irrelevant changes
  override val incrementalProps: List<FileCollection>
    get() = listOf(
      sources,
      javaSources,
      commonSourceSet,
      classpathSnapshotProperties.classpath,
    )

  // Overrding an internal function is hacky.
  // TODO: Ask upstream to open it.
  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "EXPOSED_PARAMETER_TYPE")
  fun `callCompilerAsync$kotlin_gradle_plugin_common`(
    args: K2JVMCompilerArguments,
    inputChanges: InputChanges,
    taskOutputsBackup: TaskOutputsBackup?,
  ) {
    val changedFiles = getChangedFiles(inputChanges, incrementalProps)
    val extraOptions = incrementalChangesTransformers.get().flatMap {
      it(changedFiles)
    }
    args.addPluginOptions(extraOptions)
    super.callCompilerAsync(args, inputChanges, taskOutputsBackup)
  }

  override fun skipCondition(): Boolean = sources.isEmpty && javaSources.isEmpty

  @get:InputFiles
  @get:SkipWhenEmpty
  @get:IgnoreEmptyDirectories
  @get:PathSensitive(PathSensitivity.RELATIVE)
  override val javaSources: FileCollection = super.javaSources.filter {
    !destination.get().isParentOf(it)
  }
}

@CacheableTask
abstract class QuerentTaskJS @Inject constructor(
  objectFactory: ObjectFactory,
  workerExecutor: WorkerExecutor,
) : Kotlin2JsCompile(
  objectFactory.newInstance(KotlinJsCompilerOptionsDefault::class.java),
  objectFactory,
  workerExecutor,
),
  QuerentTask {

  // Overrding an internal function is hacky.
  // TODO: Ask upstream to open it.
  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "EXPOSED_PARAMETER_TYPE")
  fun `callCompilerAsync$kotlin_gradle_plugin_common`(
    args: K2JSCompilerArguments,
    inputChanges: InputChanges,
    taskOutputsBackup: TaskOutputsBackup?,
  ) {
    val changedFiles = getChangedFiles(inputChanges, incrementalProps)
    val extraOptions = incrementalChangesTransformers.get().flatMap {
      it(changedFiles)
    }
    args.addPluginOptions(extraOptions)
    super.callCompilerAsync(args, inputChanges, taskOutputsBackup)
  }
}

@CacheableTask
abstract class QuerentTaskMetadata @Inject constructor(
  workerExecutor: WorkerExecutor,
  objectFactory: ObjectFactory,
) : KotlinCompileCommon(
  objectFactory.newInstance(KotlinMultiplatformCommonCompilerOptionsDefault::class.java),
  workerExecutor,
  objectFactory,
),
  QuerentTask {

  // Overrding an internal function is hacky.
  // TODO: Ask upstream to open it.
  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "EXPOSED_PARAMETER_TYPE")
  fun `callCompilerAsync$kotlin_gradle_plugin_common`(
    args: K2MetadataCompilerArguments,
    inputChanges: InputChanges,
    taskOutputsBackup: TaskOutputsBackup?,
  ) {
    val changedFiles = getChangedFiles(inputChanges, incrementalProps)
    val extraOptions = incrementalChangesTransformers.get().flatMap {
      it(changedFiles)
    }
    args.addPluginOptions(extraOptions)
    super.callCompilerAsync(args, inputChanges, taskOutputsBackup)
  }
}

@CacheableTask
abstract class QuerentTaskNative @Inject internal constructor(
  compilation: KotlinCompilationInfo,
  objectFactory: ObjectFactory,
  providerFactory: ProviderFactory,
  execOperations: ExecOperations,
) : KotlinNativeCompile(
  compilation,
  objectFactory.newInstance(KotlinNativeCompilerOptionsDefault::class.java),
  objectFactory,
  providerFactory,
  execOperations,
), QuerentTask

internal fun CommonCompilerArguments.addPluginOptions(options: List<SubpluginOption>) {
  pluginOptions = (options.map { it.toArg() } + pluginOptions!!).toTypedArray()
}

