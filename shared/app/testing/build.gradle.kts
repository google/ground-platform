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
// Data-level test fixtures for the shared/app modules: seeded in-memory stores, sample data, survey
// and form editor samples, and recording fakes, on :shared:app:data only. The fixtures that need
// the
// presentation layer (the app session, ViewModel builders) are in :shared:app:ui-testing. Depend on
// this from `commonTest` only.
plugins { id("collectone.kmp.android") }

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
        api(projects.shared.ui.testing)
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.coroutines.test)
        api(libs.turbine)
        implementation(kotlin("test"))
      }
    }
  }
}

android { namespace = "org.openforis.collectone.v2.app.testing" }
