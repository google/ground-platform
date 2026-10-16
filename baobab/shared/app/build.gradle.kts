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
  id("com.android.library") version "8.5.2"
  id("com.ncorti.ktfmt.gradle") version "0.27.0"
}

group = "org.groundplatform.v2"

version = "2.0.0-SNAPSHOT"

// Formats Kotlin sources with ktfmt (Google style, 2-space indent).
// Run `./gradlew ktfmtFormat` to format or `./gradlew ktfmtCheck` to verify.
ktfmt { googleStyle() }

kotlin {
  androidTarget {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
  }

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
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
        implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
        implementation(compose.ui)
        implementation(compose.components.resources)
        implementation("io.ktor:ktor-client-core:3.6.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
      }
    }
    val androidMain by getting {
      dependencies {
        implementation("io.ktor:ktor-client-cio:3.6.0")
        implementation("androidx.lifecycle:lifecycle-process:2.8.7")
      }
    }
    val jvmMain by getting { dependencies { implementation("io.ktor:ktor-client-cio:3.6.0") } }
    val wasmJsMain by getting { dependencies { implementation("io.ktor:ktor-client-js:3.6.0") } }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(project(":app-testing"))
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
        implementation(kotlin("reflect"))
        implementation("io.ktor:ktor-client-mock:3.6.0")
        // Writes the user data service's proto JSON seed (see UserDataSeedSnapshotTest).
        implementation("com.squareup.wire:wire-moshi-adapter:7.0.3")
        // Headless Compose UI tests (runComposeUiTest) for interaction regressions.
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)
        implementation(compose.desktop.currentOs)
      }
    }
  }
}

// `./gradlew jvmTest -PregenerateLibrarySeed` rewrites the global library seed files
// (shared/assets/library/*.textproto) from the app's Kotlin copy; see GlobalLibrarySeedTest.
// `./gradlew jvmTest -PregenerateUserDataSeed` rewrites the user data service's sample seed
// (server/user-data-service/src/main/resources/seed/user_data_seed.json) from the fixtures in
// app-testing; see UserDataSeedSnapshotTest.
tasks.withType<Test>().configureEach {
  for (flag in listOf("regenerateLibrarySeed", "regenerateUserDataSeed")) {
    if (project.hasProperty(flag)) {
      systemProperty(flag, "true")
      outputs.upToDateWhen { false }
    }
  }
}

android {
  namespace = "org.groundplatform.v2.app"
  compileSdk = 35
  defaultConfig { minSdk = 24 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
