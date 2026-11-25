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

// The presentation code every feature of shared/app shares (see docs/technical/client/modules.md):
// the domain text mappers (ui.text), shared composables and foreground platform bridges
// (ui.common), the navigation models (ui.navigation), and the ViewModel helpers (ui.viewmodel).
plugins {
  id("collectone.kmp.android")
  id("collectone.kmp.compose")
  id("collectone.detekt")
  id("collectone.kover")
}

val iosEnabled = providers.gradleProperty("collectone.ios").orNull == "true"

kotlin {
  if (iosEnabled) {
    iosArm64()
    iosSimulatorArm64()
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(projects.shared.app.domain)
        api(projects.shared.app.resources)
        api(projects.shared.ui)
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
        implementation(libs.compose.ui)
        implementation(libs.compose.components.resources)
        implementation(libs.compose.material.icons.core)
        implementation(libs.compose.material.icons.extended)
        // Lifecycle-aware ViewModels (viewModelScope, SavedStateHandle) for the ViewModel helpers.
        api(libs.jetbrains.lifecycle.viewmodel)
        api(libs.jetbrains.lifecycle.viewmodel.savedstate)
      }
    }
    val commonTest by getting { dependencies { implementation(kotlin("test")) } }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        // Headless Compose UI tests (runComposeUiTest).
        implementation(libs.compose.ui.test)
      }
    }
  }
}

android { namespace = "org.openforis.collectone.v2.app.uicommon" }

// The strings and their `Res` accessors come from :shared:app:resources.
compose.resources { generateResClass = never }
