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
plugins {
  id("collectone.kmp.android")
  id("collectone.kmp.compose")
  id("collectone.kover")
}

kotlin {
  iosArm64()
  iosSimulatorArm64()

  js(IR) {
    browser()
    nodejs()
  }

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { nodejs() }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(projects.shared.core)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        api(libs.compose.material.icons.core)
        implementation(compose.ui)
        implementation(compose.components.resources)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(projects.shared.ui.testing)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        // Headless Compose UI tests (runComposeUiTest) for the form widgets and runners.
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)
        implementation(compose.desktop.currentOs)
      }
    }
  }
}

android { namespace = "org.openforis.collectone.v2.ui" }

compose.resources {
  publicResClass = true
  packageOfResClass = "org.openforis.collectone.v2.core.forms.ui.resources"
}
