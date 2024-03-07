/*
 * Copyright 2023 teogor (Teodor Grigor)
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

package dev.teogor.querent

import com.google.devtools.ksp.gradle.KspConfigurations
import com.google.devtools.ksp.gradle.KspGradleSubplugin
import dev.teogor.querent.api.codegen.impl.initializePlugin
import dev.teogor.querent.api.impl.QuerentConfiguratorExtension
import dev.teogor.querent.codegen.CodeGenerator
import dev.teogor.querent.codegen.KspCodeOutputStreamMaker
import dev.teogor.querent.codegen.model.CodeGenConfig
import dev.teogor.querent.common.AnyChanges
import dev.teogor.querent.common.impl.CodeGeneratorImpl
import dev.teogor.querent.processors.KspToCodeGenDestinationsMapper
import dev.teogor.querent.structures.BuildProfile
import dev.teogor.querent.structures.LanguagesSchema
import dev.teogor.querent.structures.XmlResources
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.kotlin.dsl.create
import java.io.File

class Plugin : Plugin<Project> {
  override fun apply(target: Project) {
    // kspConfigurations = KspConfigurations(target)

    // val baseDir = target.project.buildDir.resolve("generated/beta/querent")
    // val classesDir = File(baseDir, "classes")
    // classesDir.mkdirs()
    // val javaDir = File(baseDir, "java")
    // javaDir.mkdirs()
    // val kotlinDir = File(baseDir, "kotlin")
    // kotlinDir.mkdirs()
    // val resourcesDir = File(baseDir, "resources")
    // resourcesDir.mkdirs()
    // CodeGenerator(
    //   codeOutputStreamMaker = KspCodeOutputStreamMaker(
    //     codeGenerator = CodeGeneratorImpl(
    //       classesDir,
    //       { javaDir },
    //       kotlinDir,
    //       resourcesDir,
    //       baseDir,
    //       AnyChanges(baseDir),
    //       emptyList(),
    //       true,
    //     ),
    //     sourceMapper = KspToCodeGenDestinationsMapper(),
    //   ),
    //   codeGenConfig = CodeGenConfig("com.zeoowl.live.demo"),
    // ).generate()

    with(target) {
      extensions.create<QuerentConfiguratorExtension>(
        name = "querent",
      )

      initializePlugin<BuildProfile>()
      initializePlugin<XmlResources>()
      initializePlugin<LanguagesSchema>()
    }
  }
}
