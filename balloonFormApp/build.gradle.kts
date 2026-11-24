/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
// Standalone lightweight Kotlin/Wasm form runner embedded inside KML placemark balloons.
// Depends only on :shared:core and :shared:ui (CompactFormRunner + AppTheme), keeping SQLDelight,
// Mapbox GL, browser history, and OAuth popups out of the compiled Wasm binary so it boots fast and
// runs cleanly inside sandboxed iframes (such as Google Earth Web's KML balloons).
plugins {
  id("collectone.wasm.app")
  alias(libs.plugins.kotlin.serialization)
}

wasmApp { devServerPort = 8096 }

kotlin {
  jvm()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(projects.shared.core)
        implementation(projects.shared.ui)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
        implementation(compose.components.resources)
        implementation(libs.compose.material.icons.extended)
        implementation(libs.jetbrains.lifecycle.viewmodel)
        implementation(libs.kotlinx.serialization.json)
      }
    }
    val wasmJsMain by getting { dependencies { implementation(libs.kotlinx.browser) } }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.turbine)
        implementation(projects.shared.ui.testing)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)
        implementation(compose.desktop.currentOs)
      }
    }
  }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
