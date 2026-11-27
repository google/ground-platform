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

plugins { `kotlin-dsl` }

// The plugins the conventions configure are compile-only: the root build script loads them (with
// `apply false`), so every module and convention shares one copy at the catalog's version.
dependencies {
  compileOnly(plugin(libs.plugins.android.application))
  compileOnly(plugin(libs.plugins.compose.multiplatform))
  compileOnly(plugin(libs.plugins.detekt))
  compileOnly(plugin(libs.plugins.kotlin.compose))
  compileOnly(plugin(libs.plugins.kotlin.multiplatform))
  compileOnly(plugin(libs.plugins.kover))
  // The Wire schema API for ProtoSchemaDescriptorsFactory, a Wire schema handler; at runtime the
  // Wire plugin provides it. Not transitive: its Kotlin standard library is newer than the one
  // Gradle embeds for build logic.
  compileOnly(libs.wire.schema) { isTransitive = false }
  compileOnly(libs.okio) { isTransitive = false }
}

/** The marker artifact of a catalog plugin, which depends on the plugin's implementation. */
fun plugin(plugin: Provider<PluginDependency>): Provider<String> = plugin.map {
  "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}"
}

gradlePlugin {
  plugins {
    register("kmpLibrary") {
      id = "collectone.kmp.library"
      implementationClass = "org.openforis.collectone.v2.buildlogic.KmpLibraryConventionPlugin"
    }
    register("kmpAndroid") {
      id = "collectone.kmp.android"
      implementationClass = "org.openforis.collectone.v2.buildlogic.KmpAndroidConventionPlugin"
    }
    register("kmpCompose") {
      id = "collectone.kmp.compose"
      implementationClass = "org.openforis.collectone.v2.buildlogic.KmpComposeConventionPlugin"
    }
    register("wasmApp") {
      id = "collectone.wasm.app"
      implementationClass = "org.openforis.collectone.v2.buildlogic.WasmAppConventionPlugin"
    }
    register("androidApp") {
      id = "collectone.android.app"
      implementationClass = "org.openforis.collectone.v2.buildlogic.AndroidAppConventionPlugin"
    }
    register("jvmServer") {
      id = "collectone.jvm.server"
      implementationClass = "org.openforis.collectone.v2.buildlogic.JvmServerConventionPlugin"
    }
    register("detekt") {
      id = "collectone.detekt"
      implementationClass = "org.openforis.collectone.v2.buildlogic.DetektConventionPlugin"
    }
    register("kover") {
      id = "collectone.kover"
      implementationClass = "org.openforis.collectone.v2.buildlogic.KoverConventionPlugin"
    }
  }
}
