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

// Test fixtures for `org.groundplatform.v2:web`: Survey editor and Form editor ViewModel builders
// and fakes. Depend on this from `commonTest` only.
plugins {
  kotlin("multiplatform")
  id("com.android.library")
  id("com.ncorti.ktfmt.gradle")
}

group = "org.groundplatform.v2"

version = "2.0.0-SNAPSHOT"

ktfmt { googleStyle() }

kotlin {
  androidTarget {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
  }

  jvm()

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(project(":"))
        api("org.groundplatform.v2:app-testing:2.0.0-SNAPSHOT")
        implementation(kotlin("test"))
      }
    }
  }
}

android {
  namespace = "org.groundplatform.v2.web.testing"
  compileSdk = 35
  defaultConfig { minSdk = 24 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
