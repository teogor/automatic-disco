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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package dev.teogor.querent.gradle

import com.google.devtools.ksp.gradle.KspGradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool
import java.io.File
import java.nio.file.Paths

internal fun SubpluginOption.toArg() = "plugin:${KspGradleSubplugin.KSP_PLUGIN_ID}:$key=$value"

internal fun File.isParentOf(childCandidate: File): Boolean {
  val parentPath = Paths.get(this.absolutePath).normalize()
  val childCandidatePath = Paths.get(childCandidate.absolutePath).normalize()

  return childCandidatePath.startsWith(parentPath)
}

internal fun disableRunViaBuildToolsApi(kspTask: AbstractKotlinCompileTool<*>) {
  kspTask.runViaBuildToolsApi.value(false).disallowChanges()
}
