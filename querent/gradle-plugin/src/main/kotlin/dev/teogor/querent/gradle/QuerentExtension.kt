package dev.teogor.querent.gradle

import org.gradle.api.GradleException
import org.gradle.process.CommandLineArgumentProvider

open class QuerentExtension {
  internal val apOptions = mutableMapOf<String, String>()
  internal val commandLineArgumentProviders = mutableListOf<CommandLineArgumentProvider>()
  internal val excludedProcessors = mutableSetOf<String>()

  open val arguments: Map<String, String> get() = apOptions.toMap()

  open fun arg(k: String, v: String) {
    if ('=' in k) {
      throw GradleException("'=' is not allowed in custom option's name.")
    }
    apOptions.put(k, v)
  }

  open fun arg(arg: CommandLineArgumentProvider) {
    commandLineArgumentProviders.add(arg)
  }

  @Deprecated("KSP will stop supporting other compiler plugins in KSP's Gradle tasks after 1.0.8.")
  open var blockOtherCompilerPlugins: Boolean = true

  // Instruct KSP to pickup sources from compile tasks, instead of source sets.
  // Note that it depends on behaviors of other Gradle plugins, that may bring surprises and can be hard to debug.
  // Use your discretion.
  open var allowSourcesFromOtherPlugins: Boolean = false

  // Treat all warning as errors.
  open var allWarningsAsErrors: Boolean = false

  // Keep processor providers from being called. Providers will still be loaded if they're in classpath.
  open fun excludeProcessor(fullyQualifiedName: String) {
    excludedProcessors.add(fullyQualifiedName)
  }
}
