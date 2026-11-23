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
// The survey editor feature of the web console (see docs/technical/client/modules.md): the
// Survey editor's ViewModel, UI state, and views (ui.surveyeditor) and its Koin module, on the
// Form editor it opens and the shared web presentation code.
plugins {
  id("collectone.kmp.library")
  id("collectone.kmp.compose")
  id("collectone.detekt")
  id("collectone.kover")
}

kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        api(projects.shared.web.formeditor)
        // The survey map views (ui.map) its layer and area editors draw on.
        api(projects.shared.app.map)
        // The feature's Koin module (its ViewModel definitions).
        implementation(libs.koin.core.viewmodel)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
        implementation(compose.components.resources)
        implementation(libs.compose.material.icons.core)
        implementation(libs.compose.material.icons.extended)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(projects.shared.app.testing)
        // The Survey editor fixtures (surveyEditorViewModel); test-only, so no cycle.
        implementation(projects.shared.web.testing)
        // The assembled app session, for the tests that open the Survey editor in it.
        implementation(projects.shared.app.uiTesting)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        // Headless Compose UI tests (runComposeUiTest).
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)
        implementation(compose.desktop.currentOs)
      }
    }
  }
}

// The strings and their `Res` accessors come from :shared:web:resources.
compose.resources { generateResClass = never }
