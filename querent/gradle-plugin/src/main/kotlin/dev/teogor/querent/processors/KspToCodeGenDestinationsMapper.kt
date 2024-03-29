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

package dev.teogor.querent.processors

import com.google.devtools.ksp.symbol.KSFile
import dev.teogor.querent.commons.KSFileSourceMapper

class KspToCodeGenDestinationsMapper : KSFileSourceMapper {
  private val sourceFilesById = mutableMapOf<String, KSFile?>()

  override fun mapToKSFile(sourceId: String): KSFile? {
    return sourceFilesById[sourceId]
  }
}
