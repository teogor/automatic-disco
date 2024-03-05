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
import dev.teogor.xenoglot.Country
import dev.teogor.xenoglot.Language
import dev.teogor.xenoglot.territorialize

val kotlinVersion = libs.versions.kotlin.asProvider().get()

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  id("dev.teogor.querent")
  id("dev.teogor.querent.x")
}

querent {
  buildFeatures {
    buildProfile = true
    xmlResources = true
    languagesSchema = true
  }

  languagesSchemaOptions {
    unqualifiedResLocale = Language.English territorialize Country.UnitedStates
    addSupportedLanguages {
      +(Language.Romanian territorialize Country.Romania)
      +(Language.English territorialize Country.UnitedKingdom)
      +(Language.Korean territorialize Country.SouthKorea)
      +(Language.Dutch territorialize Country.Netherlands)
      +(Language.German territorialize Country.Germany)
      +(Language.Chinese territorialize Country.China)
      +Language.Japanese
      +Language.Spanish
      +Language.Hindi
      +Language.Arabic
    }
  }
}

android {
  namespace = "com.zeoowl.live.demo"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.zeoowl.live.demo"
    minSdk = 24
    targetSdk = 34
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_17.toString()

    freeCompilerArgs += listOf(
      "-P",
      "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=$kotlinVersion",
    )
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    // kotlinCompilerExtensionVersion = "1.5.11-dev-k2.0.0-Beta4-21f5e479a96"
    kotlinCompilerExtensionVersion = "1.5.8"
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }

// Define the output directory for generated files
  val generatedSrcDir = "$buildDir/generated/beta/querent/kotlin"

  // Configure source sets
  sourceSets {
    names.forEach {
      named(it) {
        println("sourceSet -> $it")
        kotlin.srcDirs(generatedSrcDir)
      }
    }
    // main {
    //   // Add the generated source directory to the main source set
    //   kotlin.srcDir(generatedSrcDir)
    // }
  }
}

dependencies {
  implementation(platform("dev.teogor.ceres:bom:1.0.0-alpha04"))
  implementation("dev.teogor.ceres:core-register")
  implementation("dev.teogor.ceres:core-foundation")
  implementation("dev.teogor.ceres:core-runtime")
  implementation("dev.teogor.ceres:core-startup")
  implementation("dev.teogor.ceres:core-common")

  implementation("org.threeten:threetenbp:1.6.8")

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}
