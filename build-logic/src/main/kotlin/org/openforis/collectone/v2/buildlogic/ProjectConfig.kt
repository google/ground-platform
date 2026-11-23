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

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Sets the module's Maven group from its parent's path (`org.openforis.collectone.v2.shared.ui` for
 * `:shared:ui:testing`). Gradle and Kotlin identify a module by group and name, so the modules that
 * share a name (the `testing` fixtures of shared/ui, shared/app, and shared/web) need distinct
 * groups.
 */
internal fun Project.useModuleGroup() {
  group = "org.openforis.collectone.v2" + path.substringBeforeLast(':').replace(':', '.')
}

/** The Android SDK levels and Java version of every Android module. */
internal object AndroidConfig {
  const val COMPILE_SDK = 35
  const val MIN_SDK = 24
  const val TARGET_SDK = 35
  val javaVersion: JavaVersion = JavaVersion.VERSION_17
  val jvmTarget: JvmTarget = JvmTarget.JVM_17
}

/** The JDK the server compiles and runs on. */
internal const val SERVER_JDK_VERSION = 21

/**
 * True when `-Pcollectone.serverOnly=true` (or `collectone.serverOnly=true` in `local.properties`)
 * restricts the build to `:shared:core` (JVM only) and `:server`.
 */
fun Project.isServerOnly(): Boolean = LocalSettings(this).get("collectone.serverOnly") == "true"
