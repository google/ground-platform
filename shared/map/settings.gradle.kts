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
pluginManagement {
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
  repositories {
    google()
    mavenCentral()
    // Mapbox Maps SDK for Android (androidMain). Public; no credentials needed.
    maven("https://api.mapbox.com/downloads/v2/releases/maven") {
      content { includeGroupByRegex("com\\.mapbox\\..*") }
    }
  }
}

rootProject.name = "map"
