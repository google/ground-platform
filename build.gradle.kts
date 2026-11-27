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

import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

// Loads every Gradle plugin the modules use once, at the catalog's version, so all modules share
// one copy of each. Modules apply them by id (or through the collectone.* conventions).
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.firebase.crashlytics) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.kover) apply false
  alias(libs.plugins.sqldelight) apply false
  alias(libs.plugins.wire) apply false
  // Merges the coverage of every included module that applies collectone.kover into one report:
  // `./gradlew :koverHtmlReportJvmTests` (build/reports/kover/htmlJvmTests) or
  // `:koverXmlReportJvmTests` (build/reports/kover/reportJvmTests.xml). Test fixture modules
  // (`*:testing`, `*:ui-testing`) and string-only `*:resources` modules don't apply it; generated
  // code is excluded by collectone.kover.
  id("collectone.kover")
}

// `./gradlew :koverVerifyJvmTests` (run by CI) fails when the merged line coverage drops below the
// minimum. The minimum sits just below the measured coverage, so it catches regressions without
// failing on noise. When coverage goes up, raise it: run `./gradlew :koverLogJvmTests` (or open the
// HTML report), and set the minimum to the new line coverage, rounded down, minus 1. (Set at
// 81.51% line coverage.)
kover {
  reports {
    variant("jvmTests") {
      verify {
        rule("Merged line coverage") {
          minBound(80, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE)
        }
      }
    }
  }
}
