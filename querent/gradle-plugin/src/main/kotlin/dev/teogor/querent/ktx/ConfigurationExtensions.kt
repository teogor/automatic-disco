package dev.teogor.querent.ktx

import org.gradle.api.artifacts.Configuration

internal fun Configuration.markResolvable(): Configuration = apply {
  isCanBeResolved = true
  isCanBeConsumed = false
  isVisible = false
}
