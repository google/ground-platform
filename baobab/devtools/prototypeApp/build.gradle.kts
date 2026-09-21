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

group = "org.groundplatform.v2.devtools"

version = "2.0.0-SNAPSHOT"

val devServerPort = project.findProperty("port")?.toString()?.toIntOrNull() ?: 8091

kotlin {
  jvm { testRuns["test"].executionTask.configure { useJUnitPlatform() } }

  js(IR) {
    browser {
      commonWebpackConfig {
        outputFileName = "prototypeapp.js"
        devServer =
          (devServer
              ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer())
            .apply {
              port = devServerPort
              open = false
            }
      }
    }
    binaries.executable()
  }

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      commonWebpackConfig {
        outputFileName = "prototypeapp.js"
        devServer =
          (devServer
              ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer())
            .apply {
              port = devServerPort
              open = false
            }
      }
    }
    binaries.executable()
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation("org.groundplatform.v2:protoforms:2.0.0-SNAPSHOT")
        implementation("org.groundplatform.v2:protoforms-ui:2.0.0-SNAPSHOT")
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
        implementation(compose.ui)
      }
    }
    val commonTest by getting { dependencies { implementation(kotlin("test")) } }
    val jvmTest by getting { dependencies { implementation(kotlin("test-junit5")) } }
  }
}
