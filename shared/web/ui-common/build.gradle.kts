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
// The presentation code every feature of the web console shares (see
// docs/technical/client/modules.md): shared composables and browser bridges (ui.common), the
// text mappers of the web domain (ui.text), and the survey outcome card (ui.surveyoutcome).
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
        api(projects.shared.app.uiCommon)
        api(projects.shared.web.domain)
        api(projects.shared.web.resources)
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
        implementation(libs.compose.ui)
        implementation(libs.compose.components.resources)
        implementation(libs.compose.material.icons.core)
        implementation(libs.compose.material.icons.extended)
        // Logging for the platform bridges (file downloads).
        implementation(libs.kermit)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(projects.shared.app.testing)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        // Headless Compose UI tests (runComposeUiTest).
        implementation(libs.compose.ui.test)
      }
    }
  }
}

// The strings and their `Res` accessors come from :shared:web:resources.
compose.resources { generateResClass = never }
