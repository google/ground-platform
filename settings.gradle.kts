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

// The one Gradle build of the repository. Run every task from the repository root, e.g.
// `./gradlew :shared:app:jvmTest` or `./gradlew jvmTest` for all modules.
pluginManagement {
  // Convention plugins (collectone.*) holding the configuration the modules share.
  includeBuild("build-logic")
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

// Lets Gradle download the JDKs named in gradle/gradle-daemon-jvm.properties when they aren't
// installed locally.
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  // Dependency and plugin versions: gradle/libs.versions.toml (Gradle's default catalog location).
  repositories {
    google()
    mavenCentral()
    // Mapbox Maps SDK for Android (shared/map's androidMain). Public; no credentials needed.
    maven("https://api.mapbox.com/downloads/v2/releases/maven") {
      content { includeGroupByRegex("com\\.mapbox\\..*") }
    }
  }
}

// Module dependencies use type-safe accessors, e.g. `api(projects.shared.core)`.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "collect-one"

// `-Pcollectone.serverOnly=true` (or `collectone.serverOnly=true` in local.properties) configures
// only `:shared:core` and `:server`, so container builds and backend-only IDEs run without the
// client modules or an Android SDK.
val serverOnly =
  (providers.gradleProperty("collectone.serverOnly").orNull
      ?: providers
        .fileContents(layout.rootDirectory.file("local.properties"))
        .asText
        .map { text ->
          java.util.Properties().apply { load(text.reader()) }.getProperty("collectone.serverOnly")
        }
        .orNull)
    ?.trim() == "true"

// Shared Kotlin Multiplatform libraries (see shared/README.md).
include(":shared:core")

// Backend server.
include(":server")

if (!serverOnly) {
  include(":shared:ui")

  // Test fixtures (the example forms) for the tests of shared/ui and the modules built on it, the
  // sample data, and the mobile workbench.
  include(":shared:ui:testing")

  include(":shared:map")

  // The map's geometry types (LatLng, LngLatBounds, Geometry) without Compose, for the domain
  // layer.
  include(":shared:map:geometry")

  include(":shared:app")

  // The layers of shared/app as their own modules (see docs/technical/client/modules.md): the pure
  // Kotlin domain, and the data layer (stores, sync, remote, and device clients) built on it.
  include(":shared:app:domain")

  include(":shared:app:data")

  // The app's UI strings (all 9 locales) and their generated `Res` accessors, shared by the
  // presentation modules.
  include(":shared:app:resources")

  // The presentation modules of shared/app: the code every feature shares (text mappers, shared
  // composables, navigation models, ViewModel helpers).
  include(":shared:app:ui-common")

  // The survey map feature.
  include(":shared:app:map")

  // The data collection feature (forms, record details, searchable list, PDF reports).
  include(":shared:app:datacollection")

  // The settings feature (preferences, language, offline maps).
  include(":shared:app:settings")

  // The searchable list's search feature (map features, places).
  include(":shared:app:listsearch")

  // The onboarding feature (sign-in, survey directory, first sync).
  include(":shared:app:onboarding")

  // The dashboard feature (surveys, organizations, sync, impact).
  include(":shared:app:dashboard")

  // Test fixtures for the tests of shared/app and the modules built on it: data-level ones (sample
  // stores, editor samples, fakes) and app-level ones (the assembled app session, ViewModel
  // builders).
  include(":shared:app:testing")

  include(":shared:app:ui-testing")

  include(":shared:web")

  // The web console's own domain layer (web-only editor models, importers, and use cases).
  include(":shared:web:domain")

  // The web console's UI strings (all 9 locales) and their generated `Res` accessors, shared by its
  // presentation modules.
  include(":shared:web:resources")

  // The presentation code every feature of the web console shares (shared composables, text
  // mappers, and the survey outcome card).
  include(":shared:web:ui-common")

  // The time series feature of the web console (the Time series panel).
  include(":shared:web:timeseries")

  // The impact feature of the web console (the Impact dashboard and its PDF reports).
  include(":shared:web:impact")

  // The surveys list feature of the web console (the Surveys page and the survey outcome prompt).
  include(":shared:web:surveys")

  // The organization feature of the web console (the Organizations and Organization pages).
  include(":shared:web:organization")

  // The form editor feature of the web console (the Form editor).
  include(":shared:web:formeditor")

  // The survey editor feature of the web console (the Survey editor).
  include(":shared:web:surveyeditor")

  // The survey creation wizard of the web console.
  include(":shared:web:wizard")

  // Test fixtures for the tests of shared/web.
  include(":shared:web:testing")

  include(":shared:mobile")

  // Platform hosts.
  include(":androidApp")

  include(":webApp")

  // Standalone lightweight Kotlin/Wasm form runner embedded inside KML placemark balloons (e.g.
  // Google Earth Web), built on :shared:core and :shared:ui without local storage, Mapbox, or OAuth
  // popups so it runs cleanly inside sandboxed iframes.
  include(":balloonFormApp")

  include(":mobileWebApp")

  // The workbench (device preview chrome, XForms presets, and simulation tools) is a library, kept
  // apart from the Wasm host.
  include(":mobileWebApp:workbench")
}
