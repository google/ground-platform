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
// Placeholder Android application build script wrapping `:shared:mobile`.
import java.util.Properties

plugins { id("collectone.android.app") }

kotlin {
  sourceSets {
    val androidMain by getting {
      dependencies {
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.lifecycle.process)
        implementation(libs.androidx.work.runtime.ktx)
        implementation(libs.firebase.messaging)
        implementation(libs.kotlinx.coroutines.play.services)
      }
    }
    // JVM unit tests of the app's own logic (./gradlew :androidApp:testDebugUnitTest).
    val androidUnitTest by getting { dependencies { implementation(kotlin("test")) } }
    val commonMain by getting {
      dependencies {
        implementation(projects.shared.mobile)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
      }
    }
  }
}

// The repository root's local.properties, then this module's own (whose values win).
val localProperties =
  Properties().apply {
    for (dir in listOf(layout.settingsDirectory, layout.projectDirectory)) {
      providers.fileContents(dir.file("local.properties")).asText.orNull?.let { load(it.reader()) }
    }
  }

val mapboxAccessToken =
  (localProperties.getProperty("MAPBOX_ACCESS_TOKEN")
      ?: localProperties.getProperty("mapbox.access.token")
      ?: providers.gradleProperty("MAPBOX_ACCESS_TOKEN").orNull
      ?: providers.gradleProperty("mapbox.access.token").orNull
      ?: System.getenv("MAPBOX_ACCESS_TOKEN")
      ?: "")
    .trim()

// Base URL of the Collect One server (server/) the app syncs from, e.g.
// http://10.0.2.2:8080 for a service running on the emulator's host. A Gradle property or the
// environment overrides local.properties. Empty: the app shows setup instructions instead of
// starting.
val userDataServiceUrl =
  (providers.gradleProperty("USER_DATA_SERVICE_URL").orNull
      ?: providers.environmentVariable("USER_DATA_SERVICE_URL").orNull
      ?: localProperties.getProperty("USER_DATA_SERVICE_URL")
      ?: "")
    .trim()

// Firebase web app config JSON (`FIREBASE_WEB_CONFIG`), read from Gradle properties, the
// environment, or `local.properties` so no `google-services.json` or API keys are committed to the
// repository. When present, `MobileSyncRuntime` initializes `FirebaseApp` at startup so FCM topic
// subscriptions (`survey_<id>_def`, `survey_<id>_entities`) are active; when absent or `"none"`,
// the app falls back to foreground polling.
val firebaseWebConfig =
  (providers.gradleProperty("FIREBASE_WEB_CONFIG").orNull
      ?: providers.environmentVariable("FIREBASE_WEB_CONFIG").orNull
      ?: localProperties.getProperty("FIREBASE_WEB_CONFIG")
      ?: "")
    .trim()
    .let { if (it == "none") "" else it }

val appEnvironment =
  (providers.gradleProperty("APP_ENV").orNull
      ?: providers.gradleProperty("COLLECT_ONE_ENV").orNull
      ?: providers.environmentVariable("APP_ENV").orNull
      ?: providers.environmentVariable("COLLECT_ONE_ENV").orNull
      ?: localProperties.getProperty("APP_ENV")
      ?: localProperties.getProperty("COLLECT_ONE_ENV")
      ?: "")
    .trim()

val appVersionName =
  (providers.gradleProperty("APP_VERSION").orNull
      ?: providers.environmentVariable("APP_VERSION").orNull
      ?: project.version.toString())
    .trim()

android {
  namespace = "org.openforis.collectone.v2.android"
  defaultConfig {
    applicationId = "org.openforis.collectone.v2.android"
    versionCode = 1
    versionName = appVersionName
    resValue("string", "mapbox_access_token", mapboxAccessToken)
    resValue("string", "user_data_service_url", userDataServiceUrl)
    resValue("string", "app_environment", appEnvironment)
    resValue(
      "string",
      "firebase_web_config",
      firebaseWebConfig.replace("\\", "\\\\").replace("\"", "\\\""),
    )
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
}
