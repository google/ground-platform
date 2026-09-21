/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
plugins {
  kotlin("multiplatform") version "2.3.21"
  id("org.jetbrains.compose") version "1.12.0"
  id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
}

group = "org.groundplatform.v2"

version = "2.0.0-SNAPSHOT"

kotlin {
  jvm { testRuns["test"].executionTask.configure { useJUnitPlatform() } }

  iosArm64()
  iosSimulatorArm64()

  js(IR) {
    browser()
    nodejs()
  }

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser()
    nodejs()
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api("org.groundplatform.v2:protoforms:2.0.0-SNAPSHOT")
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
      }
    }
    val commonTest by getting { dependencies { implementation(kotlin("test")) } }
    val jvmTest by getting { dependencies { implementation(kotlin("test-junit5")) } }
  }
}
