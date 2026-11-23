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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget

/**
 * `collectone.kmp.library`: a Kotlin Multiplatform library with the targets every shared module
 * has, JVM (Java 17 bytecode so Android modules can consume pure libraries without AGP; tests run
 * on JUnit 5) and Wasm in the browser. Modules add their other targets (Android through
 * `collectone.kmp.android`, JS, iOS) in their own `kotlin {}` block.
 *
 * JS and Wasm source maps embed the Kotlin sources, so browser developer tools show them.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit =
    with(target) {
      pluginManager.apply("org.jetbrains.kotlin.multiplatform")
      useModuleGroup()
      val serverOnly = isServerOnly()
      extensions.configure<KotlinMultiplatformExtension> {
        jvm {
          compilerOptions { jvmTarget.set(AndroidConfig.jvmTarget) }
          testRuns.named("test") { executionTask.configure { useJUnitPlatform() } }
        }
        if (!serverOnly) {
          @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }
          targets.withType<KotlinJsIrTarget>().configureEach {
            compilerOptions {
              sourceMapEmbedSources.set(JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS)
            }
          }
        }
      }
    }
}
