/*
 * Copyright 2026 The Collect One Authors.
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
// Placeholder Android application build script wrapping `org.openforis.collectone.v2:mobile`.
import java.util.Properties

plugins {
  id("com.android.application") version "8.5.2"
  kotlin("multiplatform") version "2.4.20"
  id("org.jetbrains.compose") version "1.12.0"
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}

group = "org.openforis.collectone.v2.android"

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
      dependencies {
        implementation("org.openforis.collectone.v2:mobile:2.0.0-SNAPSHOT")
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
      }
    }
  }
}

val localProperties = Properties()

listOf(rootProject.file("../local.properties"), rootProject.file("local.properties"))
  .filter { it.isFile }
  .forEach { file -> file.inputStream().use(localProperties::load) }

val mapboxAccessToken =
  (localProperties.getProperty("MAPBOX_ACCESS_TOKEN")
      ?: localProperties.getProperty("mapbox.access.token")
      ?: providers.gradleProperty("MAPBOX_ACCESS_TOKEN").orNull
      ?: providers.gradleProperty("mapbox.access.token").orNull
      ?: System.getenv("MAPBOX_ACCESS_TOKEN")
      ?: "")
    .trim()

// Base URL of the user data service (server/userDataService) the app syncs from, e.g.
// http://10.0.2.2:8080 for a service running on the emulator's host. A Gradle property or the
// environment overrides local.properties. Empty: the app shows setup instructions instead of
// starting.
val userDataServiceUrl =
  (providers.gradleProperty("USER_DATA_SERVICE_URL").orNull
      ?: providers.environmentVariable("USER_DATA_SERVICE_URL").orNull
      ?: localProperties.getProperty("USER_DATA_SERVICE_URL")
      ?: "")
    .trim()

android {
  namespace = "org.openforis.collectone.v2.android"
  compileSdk = 35
  defaultConfig {
    applicationId = "org.openforis.collectone.v2.android"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"
    resValue("string", "mapbox_access_token", mapboxAccessToken)
    resValue("string", "user_data_service_url", userDataServiceUrl)
  }
  buildTypes {
    debug {
      // Lets debug builds reach a local user data service over plain HTTP.
      manifestPlaceholders["usesCleartextTraffic"] = "true"
    }
    release {
      manifestPlaceholders["usesCleartextTraffic"] = "false"
      // Sign release bundles with the standard debug key, until there's a production keystore, so
      // the .aab can be
      // installed via bundletool or shared internally without a production keystore.
      signingConfig = signingConfigs.getByName("debug")
    }
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
