package dev.teogor.querent.commons

import dev.teogor.querent.gradle.QUERENT_VERSION
import dev.teogor.querent.gradle.QUERENT_KOTLIN_BASE_VERSION

object QuerentConstants {
  // Plugin IDs
  const val PLUGIN_ID = "dev.teogor.querent.symbol-processing"
  const val API_ID = "symbol-processing-api"
  const val COMPILER_PLUGIN_ID = "symbol-processing"
  const val COMPILER_PLUGIN_ID_NON_EMBEDDABLE = "symbol-processing-cmdline"

  // Group ID
  const val GROUP_ID = "dev.teogor.querent"

  // Configuration names
  const val PLUGIN_CLASSPATH_CONFIGURATION_NAME = "pluginClasspath"
  const val PLUGIN_CLASSPATH_CONFIGURATION_NAME_NON_EMBEDDABLE = "kspPluginClasspathNonEmbeddable"

  // Versions
  const val VERSION = QUERENT_VERSION
  const val KOTLIN_BASE_VERSION = QUERENT_KOTLIN_BASE_VERSION
}

