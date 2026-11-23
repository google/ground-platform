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

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(projects.shared.map.geometry)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.ui)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kermit)
      }
    }
    val androidMain by getting {
      dependencies {
        implementation(libs.mapbox.android)
        implementation(libs.androidsvg)
      }
    }
    val commonTest by getting { dependencies { implementation(kotlin("test")) } }
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

android { namespace = "org.openforis.collectone.v2.map" }
