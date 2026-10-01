/*
 * Copyright 2026 The Ground Authors.
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
  kotlin("multiplatform") version "2.4.20"
  id("org.jetbrains.compose") version "1.12.0"
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
  id("com.android.library") version "8.5.2"
}

group = "org.groundplatform.v2"

version = "2.0.0-SNAPSHOT"

kotlin {
  androidTarget {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
  }

  jvm { testRuns["test"].executionTask.configure { useJUnitPlatform() } }

  iosArm64()
  iosSimulatorArm64()

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
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.ui)
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
      }
    }
    val androidMain by getting {
      dependencies {
        implementation("com.mapbox.maps:android:11.31.1")
        implementation("com.caverock:androidsvg-aar:1.4")
      }
    }
    val commonTest by getting { dependencies { implementation(kotlin("test")) } }
    val jvmTest by getting { dependencies { implementation(kotlin("test-junit5")) } }
  }
}

android {
  namespace = "org.groundplatform.v2.map"
  compileSdk = 35
  defaultConfig { minSdk = 24 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
