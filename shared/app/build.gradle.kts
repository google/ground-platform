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
        api(projects.shared.app.data)
        api(projects.shared.app.resources)
        api(projects.shared.app.uiCommon)
        api(projects.shared.app.map)
        api(projects.shared.app.datacollection)
        api(projects.shared.app.dashboard)
        api(projects.shared.app.onboarding)
        api(projects.shared.app.listsearch)
        api(projects.shared.app.settings)
        api(projects.shared.core)
        api(projects.shared.ui)
        api(projects.shared.map)
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
        implementation(libs.compose.material.icons.core)
        implementation(libs.compose.material.icons.extended)
        implementation(libs.compose.ui)
        implementation(libs.compose.components.resources)
        // Lifecycle-aware ViewModels (viewModelScope, SavedStateHandle) and Koin dependency
        // injection, exposed to the clients and hosts that build on this module.
        api(libs.jetbrains.lifecycle.viewmodel)
        api(libs.jetbrains.lifecycle.viewmodel.compose)
        api(libs.jetbrains.lifecycle.viewmodel.savedstate)
        api(libs.koin.compose)
        api(libs.koin.compose.viewmodel)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(projects.shared.app.uiTesting)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        implementation(libs.konsist)
        implementation(kotlin("reflect"))
        implementation(libs.koin.test)
        // Headless Compose UI tests (runComposeUiTest) for interaction regressions.
        implementation(libs.compose.ui.test)
      }
    }
  }
}

android { namespace = "org.openforis.collectone.v2.app" }

// The UI strings and their `Res` accessors live in :shared:app:resources; this module generates
// none, so its `Res` doesn't clash with that module's.
compose.resources { generateResClass = never }
