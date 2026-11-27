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

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `collectone.kmp.android`: adds the Android target to a `collectone.kmp.library` module, with the
 * shared SDK levels and Java version. The module sets its own `android { namespace = ... }`.
 */
class KmpAndroidConventionPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit =
    with(target) {
      pluginManager.apply("collectone.kmp.library")
      pluginManager.apply("com.android.library")
      extensions.configure<KotlinMultiplatformExtension> {
        androidTarget { compilerOptions { jvmTarget.set(AndroidConfig.jvmTarget) } }
      }
      extensions.configure<LibraryExtension> {
        compileSdk = AndroidConfig.COMPILE_SDK
        defaultConfig { minSdk = AndroidConfig.MIN_SDK }
        compileOptions {
          sourceCompatibility = AndroidConfig.javaVersion
          targetCompatibility = AndroidConfig.javaVersion
        }
      }
    }
}
