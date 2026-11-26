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
plugins {
  id("collectone.kmp.library")
  id("collectone.kmp.compose")
  id("collectone.detekt")
  id("collectone.kover")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        api(projects.shared.core)
        api(projects.shared.ui)
        api(projects.shared.map)
        api(projects.shared.app)
        api(projects.shared.web.domain)
        api(projects.shared.web.resources)
        api(projects.shared.web.uiCommon)
        api(projects.shared.web.wizard)
        api(projects.shared.web.surveyeditor)
        api(projects.shared.web.formeditor)
        api(projects.shared.web.organization)
        api(projects.shared.web.surveys)
        api(projects.shared.web.impact)
        api(projects.shared.web.timeseries)
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
        implementation(libs.compose.material.icons.core)
        implementation(libs.compose.material.icons.extended)
        implementation(libs.compose.ui)
        implementation(libs.compose.components.resources)
        // Type-safe navigation between the console's pages; routes are @Serializable classes.
        implementation(libs.jetbrains.navigation.compose)
        implementation(libs.jetbrains.navigationevent.compose)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(projects.shared.app.uiTesting)
        implementation(projects.shared.web.testing)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        implementation(libs.konsist)
        // Headless Compose UI tests (runComposeUiTest) for interaction regressions.
        implementation(libs.compose.ui.test)
      }
    }
  }
}

// The strings and their `Res` accessors come from :shared:web:resources.
compose.resources { generateResClass = never }
