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

    val xmlContent = """
    |<?xml version="1.0" encoding="utf-8"?>
    |<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    |    <locale android:name="ar"/>
    |    <!-- Deutsch (Deutschland) -->
    |    <locale android:name="de-DE"/>
    |    <!-- English (United Kingdom) -->
    |    <locale android:name="en-GB"/>
    |    <!-- English (United States) -->
    |    <locale android:name="en-US"/>
    |    <!-- español -->
    |    <locale android:name="es"/>
    |    <!-- हिन्दी -->
    |    <locale android:name="hi"/>
    |    <!-- 日本語 -->
    |    <locale android:name="ja"/>
    |    <!-- 한국어 (대한민국) -->
    |    <locale android:name="ko-KR"/>
    |    <!-- Nederlands (Nederland) -->
    |    <locale android:name="nl-NL"/>
    |    <!-- română (România) -->
    |    <locale android:name="ro-RO"/>
    |    <!-- 中文 (中国) -->
    |    <locale android:name="zh-CN"/>
    |</locale-config>
    """.trimMargin()
    codeOutputStreamMaker.makeFile(
      name = "xml/locale_config",
      packageName = "",
      extension = "xml"
    ).use { outputStream ->
      outputStream.write(xmlContent.toByteArray())
    }
  }
}
