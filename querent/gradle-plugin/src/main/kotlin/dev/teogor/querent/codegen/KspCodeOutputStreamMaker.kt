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

package dev.teogor.querent.codegen

import dev.teogor.querent.commons.KSFileSourceMapper
import dev.teogor.querent.codegen.facades.CodeOutputStreamMaker
import dev.teogor.querent.codegen.processing.CodeGenerator
import dev.teogor.querent.codegen.processing.Dependencies
import java.io.OutputStream

class KspCodeOutputStreamMaker(
  private val codeGenerator: CodeGenerator,
  private val sourceMapper: KSFileSourceMapper,
) : CodeOutputStreamMaker {

  override fun makeFile(
    name: String,
    packageName: String,
    extension: String,
    vararg sourceIds: String,
  ): OutputStream {
    val dependencies = if (sourceIds.isEmpty()) {
      Dependencies.ALL_FILES
    } else {
      Dependencies(
        true,
        *sourceIds.mapNotNull { sourceMapper.mapToKSFile(it) }.toTypedArray(),
      )
    }

    return codeGenerator.createNewFile(
      dependencies = dependencies,
      fileName = name,
      packageName = packageName,
      extensionName = extension,
    )
  }
}
