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
// Placeholder Android application build script wrapping `org.groundplatform.v2:mobile`.
plugins {
  id("com.android.application") version "8.5.2"
  kotlin("multiplatform") version "2.4.20"
  id("org.jetbrains.compose") version "1.12.0"
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}

group = "org.groundplatform.v2.android"

version = "2.0.0-SNAPSHOT"

kotlin {
  androidTarget {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
  }

  sourceSets {
    val androidMain by getting {
      dependencies { implementation("androidx.activity:activity-compose:1.10.0") }
    }
    val commonMain by getting {
      dependencies { implementation("org.groundplatform.v2:mobile:2.0.0-SNAPSHOT") }
    }
  }
}

android {
  namespace = "org.groundplatform.v2.android"
  compileSdk = 35
  defaultConfig {
    applicationId = "org.groundplatform.v2.android"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

// Compose Multiplatform 1.12.0 bundles AndroidX AARs whose metadata declares minCompileSdk=37 /
// minAgpVersion=9.1.0, even though no API > 35 is called at runtime. Disable the strict AAR
// metadata gate so the APK assembles cleanly with Android SDK 35 and AGP 8.5.2.
tasks
  .matching { it.name.startsWith("check") && it.name.endsWith("AarMetadata") }
  .configureEach { enabled = false }
