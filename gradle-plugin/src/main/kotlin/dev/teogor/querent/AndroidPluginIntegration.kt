package dev.teogor.querent

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.SourceKind
import com.google.devtools.ksp.gradle.KspAATask
import com.google.devtools.ksp.gradle.KspTaskJvm
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
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
@Suppress("UnstableApiUsage") // some android APIs are unsable.
object AndroidPluginIntegration {

    private val agpPluginIds = listOf("com.android.application", "com.android.library", "com.android.dynamic-feature")

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
            is CommonExtension<*, *, *, *, *> -> androidExt.sourceSets
            else -> throw RuntimeException("Unsupported Android Gradle plugin version.")
        }
        sourceSets.forEach {
            onSourceSet(it.name)
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
        kspTaskProvider: TaskProvider<*>
    ) {
        kotlinCompilation.androidVariant.getSourceFolders(SourceKind.JAVA).forEach { source ->
          kspTaskProvider.configure(
            object : Action<Task> {
              override fun execute(task: Task) {
                when (task) {
                  is KspTaskJvm -> {
                    task.setSource(source)
                    task.dependsOn(source)
                  }

                  is KspAATask -> {
                    task.kspConfig.javaSourceRoots.from(source)
                    task.dependsOn(source)
                  }

                  else -> Unit
                }
              }
            }
          )
        }
    }

    private fun registerGeneratedSources(
        project: Project,
        kotlinCompilation: KotlinJvmAndroidCompilation,
        kspTaskProvider: TaskProvider<*>,
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
        kspTaskProvider: TaskProvider<*>,
        javaOutputDir: File,
        kotlinOutputDir: File,
        classOutputDir: File,
        resourcesOutputDir: FileCollection
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
            resourcesOutputDir
        )
    }
}
