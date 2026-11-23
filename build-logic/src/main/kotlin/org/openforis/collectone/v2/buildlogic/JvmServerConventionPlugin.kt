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
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * `collectone.jvm.server`: a Kotlin/JVM application (`./gradlew :<module>:run`, `installDist`) on
 * the server's JDK, which the build downloads when it isn't installed.
 */
class JvmServerConventionPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit =
    with(target) {
      pluginManager.apply("org.jetbrains.kotlin.jvm")
      pluginManager.apply("application")
      useModuleGroup()
      extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(SERVER_JDK_VERSION) }
    }
}
