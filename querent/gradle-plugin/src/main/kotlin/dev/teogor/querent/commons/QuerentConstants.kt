package dev.teogor.querent.commons

import dev.teogor.querent.gradle.QUERENT_VERSION
import dev.teogor.querent.gradle.QUERENT_KOTLIN_BASE_VERSION
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact

object QuerentConstants {
  // Plugin IDs
  const val PLUGIN_ID = "dev.teogor.querent.symbol-processing"
  const val API_ID = "symbol-processing-api"
  const val COMPILER_PLUGIN_ID = "symbol-processing"

  // Group ID
  const val GROUP_ID = "dev.teogor.querent"

  // Versions
  const val VERSION = QUERENT_VERSION
  const val KOTLIN_BASE_VERSION = QUERENT_KOTLIN_BASE_VERSION

  val PLUGIN_ARTIFACT: SubpluginArtifact
    get() = SubpluginArtifact(
      groupId = GROUP_ID,
      artifactId = COMPILER_PLUGIN_ID,
      version = VERSION
    )
}

