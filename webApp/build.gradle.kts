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
// The web console in the browser: hosts `WebConsoleApp()` from shared/web. Bundling, the dev
// server, and the machine-specific settings baked into the bundle (USER_DATA_SERVICE_URL,
// FIREBASE_WEB_CONFIG, ...) are configured by the collectone.wasm.app convention in build-logic/.
import org.gradle.api.tasks.Sync

plugins { id("collectone.wasm.app") }

// `./gradlew :webApp:wasmJsBrowserDevelopmentRun -Pport=8095` overrides the dev server port.
wasmApp { devServerPort = 8092 }

kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(projects.shared.web)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
      }
    }
  }
}

// Serve the standalone KML balloon form Wasm bundle alongside the web console under `/balloon/`,
// so KML files exported from this origin can frame `${origin}/balloon/balloon-form.html`.
val balloonProductionDist = project(":balloonFormApp").tasks.named("wasmJsBrowserDistribution")

tasks.named<Sync>("wasmJsBrowserDistribution") {
  from(balloonProductionDist) { into("balloon") }
}
