/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// The workbench around the mobile app: simulated device chrome, XForms presets, and
// simulation and reset tools. Used by the Wasm mobile build.
plugins {
  kotlin("multiplatform")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.ncorti.ktfmt.gradle")
}

group = "org.openforis.collectone.v2"

version = "2.0.0-SNAPSHOT"

ktfmt { googleStyle() }

kotlin {
  jvm { testRuns["test"].executionTask.configure { useJUnitPlatform() } }

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser()
    compilerOptions {
      sourceMapEmbedSources.set(
        org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
      )
    }
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api("org.openforis.collectone.v2:mobile:2.0.0-SNAPSHOT")
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
        implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
        implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation("org.openforis.collectone.v2:app-testing:2.0.0-SNAPSHOT")
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        implementation(compose.desktop.currentOs)
      }
    }
  }
}
