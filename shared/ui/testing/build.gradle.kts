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
// Test fixtures for `:shared:ui`: the example forms the form runner tests, the sample data, and the
// mobile workbench presets use. Not for production code.
plugins { id("collectone.kmp.android") }

kotlin {
  iosArm64()
  iosSimulatorArm64()

  js(IR) {
    browser()
    nodejs()
  }

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { nodejs() }

  sourceSets {
    val commonMain by getting { dependencies { api(projects.shared.core) } }
  }
}

android { namespace = "org.openforis.collectone.v2.ui.testing" }
