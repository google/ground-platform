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

// The app's UI strings in all 9 locales (src/commonMain/composeResources) and their generated `Res`
// accessors, in one module that every presentation module of shared/app (and the clients built on
// them) can depend on. No Kotlin code of its own.
plugins {
  id("collectone.kmp.android")
  id("collectone.kmp.compose")
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
        api(compose.runtime)
        api(compose.components.resources)
      }
    }
  }
}

android { namespace = "org.openforis.collectone.v2.app.resources" }

// Generated `Res` accessors in the package the presentation code already imports.
compose.resources {
  publicResClass = true
  packageOfResClass = "org.openforis.collectone.v2.app.resources"
}
