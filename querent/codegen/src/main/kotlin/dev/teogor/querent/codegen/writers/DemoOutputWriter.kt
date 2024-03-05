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

package dev.teogor.querent.codegen.writers

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec.Companion.classBuilder
import dev.teogor.querent.codegen.commons.fileBuilder
import dev.teogor.querent.codegen.commons.writeWith
import dev.teogor.querent.codegen.facades.CodeOutputStreamMaker
import dev.teogor.querent.codegen.model.CodeGenConfig
import dev.teogor.querent.codegen.servicelocator.OutputWriter

class DemoOutputWriter(
  private val codeOutputStreamMaker: CodeOutputStreamMaker,
  codeGenConfig: CodeGenConfig,
) : OutputWriter(codeGenConfig) {

  fun write() {
    fileBuilder(
      packageName = "${getPackageName()}.test",
      fileName = "Greeter",
    ) {
      addType(
        classBuilder("Greeter")
          .primaryConstructor(
            FunSpec.constructorBuilder()
              .addParameter("name", String::class)
              .build(),
          )
          .addProperty(
            PropertySpec.builder("name", String::class)
              .initializer("name")
              .build(),
          )
          .addFunction(
            FunSpec.builder("greet")
              .addStatement("println(%P)", "Hello, \$name")
              .build(),
          )
          .build(),
      )
    }.writeWith(codeOutputStreamMaker)
  }
}
