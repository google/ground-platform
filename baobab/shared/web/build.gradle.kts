/*
 * Copyright 2026 The Ground Authors.
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
  kotlin("multiplatform") version "2.4.20"
  id("org.jetbrains.compose") version "1.12.0"
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
  id("com.ncorti.ktfmt.gradle") version "0.27.0"
}

group = "org.groundplatform.v2"

version = "2.0.0-SNAPSHOT"

// Formats Kotlin sources with ktfmt (Google style, 2-space indent).
// Run `./gradlew ktfmtFormat` to format or `./gradlew ktfmtCheck` to verify.
ktfmt { googleStyle() }

kotlin {
  jvm { testRuns["test"].executionTask.configure { useJUnitPlatform() } }

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser()
    compilerOptions {
      sourceMapEmbedSources.set(
        org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
      )
    }
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api("org.groundplatform.v2:protoforms:2.0.0-SNAPSHOT")
        api("org.groundplatform.v2:protoforms-ui:2.0.0-SNAPSHOT")
        api("org.groundplatform.v2:map:2.0.0-SNAPSHOT")
        api("org.groundplatform.v2:app:2.0.0-SNAPSHOT")
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
        implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
        implementation(compose.ui)
        implementation(compose.components.resources)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation("org.groundplatform.v2:app-testing:2.0.0-SNAPSHOT")
        implementation(project(":web-testing"))
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        // Headless Compose UI tests (runComposeUiTest) for interaction regressions.
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)
        implementation(compose.desktop.currentOs)
      }
    }
  }
}
