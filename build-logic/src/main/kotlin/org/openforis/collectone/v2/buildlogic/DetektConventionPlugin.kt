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
package org.openforis.collectone.v2.buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.getSupportedKotlinVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `collectone.detekt`: static analysis with the shared rules in config/detekt/detekt.yml. Findings
 * that predate detekt are listed in the module's detekt-baseline.xml; `./gradlew detekt` fails only
 * on new ones.
 */
class DetektConventionPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit =
    with(target) {
      pluginManager.apply("io.gitlab.arturbosch.detekt")
      extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootDir.resolve("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
      }
      // Multiplatform source sets (commonMain, jvmTest, ...) all live under src/.
      pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<DetektExtension> { source.setFrom("src") }
      }
      // detekt runs its own embedded Kotlin compiler; keep the Kotlin Gradle plugin from aligning
      // it to the build's Kotlin version.
      configurations
        .matching { it.name == "detekt" }
        .configureEach {
          resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
              useVersion(getSupportedKotlinVersion())
            }
          }
        }
    }
}
