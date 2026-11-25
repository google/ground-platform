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
// The mobile app in the browser, inside the device workbench. Bundling, the dev server, and the
// machine-specific settings baked into the bundle (USER_DATA_SERVICE_URL, FIREBASE_WEB_CONFIG, ...)
// are configured by the collectone.wasm.app convention in build-logic/.
plugins { id("collectone.wasm.app") }

// `./gradlew :mobileWebApp:wasmJsBrowserDevelopmentRun -Pport=8095` overrides the dev server port.
wasmApp { devServerPort = 8093 }

kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(projects.mobileWebApp.workbench)
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
        implementation(libs.compose.ui)
      }
    }
  }
}
