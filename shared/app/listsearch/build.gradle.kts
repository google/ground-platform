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

// The list search feature of shared/app (see docs/technical/client/modules.md): the
// ListSearchViewModel and its UI state (ui.listsearch), on the shared presentation code.
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
        api(projects.shared.app.uiCommon)
        // The feature's Koin module (its ViewModel definition).
        implementation(libs.koin.core.viewmodel)
        // The Compose compiler infers the stability of the UI state the views consume, as it did
        // in :shared:app.
        implementation(compose.runtime)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        // The assembled app session (sampleDataHolder).
        implementation(projects.shared.app.uiTesting)
      }
    }
    val jvmTest by getting { dependencies { implementation(kotlin("test-junit5")) } }
  }
}

android { namespace = "org.openforis.collectone.v2.app.listsearch" }

// The strings and their `Res` accessors come from :shared:app:resources.
compose.resources { generateResClass = never }
