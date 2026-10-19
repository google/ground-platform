/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.Properties

plugins {
  kotlin("multiplatform") version "2.4.20"
  id("org.jetbrains.compose") version "1.12.0"
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
  id("com.ncorti.ktfmt.gradle") version "0.27.0"
}

group = "org.openforis.collectone.v2.mobileweb"

version = "2.0.0-SNAPSHOT"

ktfmt { googleStyle() }

// `./gradlew wasmJsBrowserDevelopmentRun -Pport=8095` overrides the dev server port.
val devServerPort = project.findProperty("port")?.toString()?.toIntOrNull() ?: 8093

kotlin {
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      commonWebpackConfig {
        outputFileName = "mobileWebApp.js"
        devServer =
          (devServer
              ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer())
            .apply {
              port = devServerPort
              open = false
            }
      }
    }
    binaries.executable()
    compilerOptions {
      sourceMapEmbedSources.set(
        org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
      )
    }
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(project(":workbench"))
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
      }
    }
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink>().configureEach {
  compilerOptions.sourceMapEmbedSources.set(
    org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
  )
  // Kotlin/Wasm's SourceMapGenerator currently ignores sourceMapEmbedSources and emits
  // `"sourcesContent": [null, ...]` with bare filenames for library modules. Hydrate `.wasm.map`
  // with inline source content so Chrome DevTools displays `.kt` sources in
  // `wasmJsBrowserDevelopmentRun`.
  val repoRoot = projectDir.resolve("..").canonicalFile
  val appDir = projectDir.canonicalFile
  doLast {
    val outDir = destinationDirectory.get().asFile
    val wasmMaps = outDir.listFiles { f -> f.name.endsWith(".wasm.map") } ?: return@doLast
    if (wasmMaps.isEmpty()) return@doLast
    val ktFilesByName = mutableMapOf<String, File>()
    listOf(appDir.resolve("src"), repoRoot.resolve("shared")).forEach { root ->
      if (root.exists()) {
        root
          .walkTopDown()
          .filter { it.isFile && it.extension == "kt" }
          .forEach { file -> ktFilesByName.putIfAbsent(file.name, file) }
      }
    }
    val slurper = groovy.json.JsonSlurper()
    for (mapFile in wasmMaps) {
      @Suppress("UNCHECKED_CAST") val json = slurper.parse(mapFile) as MutableMap<String, Any?>
      @Suppress("UNCHECKED_CAST")
      val sources = (json["sources"] as? List<String>)?.toMutableList() ?: continue
      val sourcesContent = MutableList<String?>(sources.size) { null }
      for (i in sources.indices) {
        val src = sources[i]
        val candidate =
          appDir.resolve(src).takeIf { it.isFile }
            ?: repoRoot.resolve(src).takeIf { it.isFile }
            ?: ktFilesByName[File(src).name]
        if (candidate != null && candidate.isFile) {
          sourcesContent[i] = candidate.readText()
          sources[i] = candidate.relativeToOrSelf(repoRoot).invariantSeparatorsPath
        }
      }
      json["sources"] = sources
      json["sourcesContent"] = sourcesContent
      mapFile.writeText(groovy.json.JsonOutput.toJson(json))
    }
  }
}

// Base URL of the user data service (server/userDataService), baked into production bundles as
// `user-data-service-config.js`. Set `USER_DATA_SERVICE_URL` as a Gradle property
// (`-PUSER_DATA_SERVICE_URL=...`), in the environment, or in `local.properties` (this directory's,
// or `../local.properties`), in that order of precedence. It must be reachable from users' browsers
// (HTTPS when the bundle is served over HTTPS). The dev server ignores this copy: it proxies the
// service and points the app at its own origin (see
// webpack.config.d/devServer.js). Empty: the app shows setup instructions instead of starting.
val localProperties =
  Properties().apply {
    listOf(file("../local.properties"), file("local.properties"))
      .filter { it.isFile }
      .forEach { f -> f.inputStream().use(::load) }
  }
val userDataServiceUrl =
  (providers.gradleProperty("USER_DATA_SERVICE_URL").orNull
      ?: providers.environmentVariable("USER_DATA_SERVICE_URL").orNull
      ?: localProperties.getProperty("USER_DATA_SERVICE_URL")
      ?: "")
    .trim()

val generateUserDataServiceConfig by tasks.registering {
  val url = userDataServiceUrl
  val outputDir = layout.buildDirectory.dir("generated/userDataServiceConfig")
  inputs.property("userDataServiceUrl", url)
  outputs.dir(outputDir)
  doLast {
    val value = if (url.isEmpty()) "null" else groovy.json.JsonOutput.toJson(url)
    outputDir
      .get()
      .file("user-data-service-config.js")
      .asFile
      .apply { parentFile.mkdirs() }
      .writeText(
        "// Generated from USER_DATA_SERVICE_URL by build.gradle.kts; do not edit.\n" +
          "window.GROUND_USER_DATA_SERVICE_URL = $value;\n"
      )
  }
}

// Firebase web app config for sign-in (`firebase-auth-bridge.js`), baked in as
// `firebase-config.js`. Set `FIREBASE_WEB_CONFIG` (the config object as JSON) as a Gradle property,
// in the environment, or in `local.properties`, like USER_DATA_SERVICE_URL. No config is checked
// in: unset (or `none`) turns Firebase off, and the app then uses fake sign-in and the user data
// service's development user.
val firebaseWebConfig =
  (providers.gradleProperty("FIREBASE_WEB_CONFIG").orNull
      ?: providers.environmentVariable("FIREBASE_WEB_CONFIG").orNull
      ?: localProperties.getProperty("FIREBASE_WEB_CONFIG")
      ?: "none")
    .trim()

// Development sign-in (`DEV_SIGN_IN`, default true): before signing in, the app asks which role to
// simulate (administrator or regular user) against a user data service in AUTH_MODE=dev. Set it to
// false for builds served to real users.
val devSignIn =
  (providers.gradleProperty("DEV_SIGN_IN").orNull
      ?: providers.environmentVariable("DEV_SIGN_IN").orNull
      ?: localProperties.getProperty("DEV_SIGN_IN")
      ?: "true")
    .trim()
    .toBoolean()

val generateFirebaseConfig by tasks.registering {
  val config = firebaseWebConfig
  val devSignInEnabled = devSignIn
  inputs.property("devSignIn", devSignInEnabled)
  val outputDir = layout.buildDirectory.dir("generated/firebaseConfig")
  inputs.property("firebaseWebConfig", config)
  outputs.dir(outputDir)
  doLast {
    val value =
      if (config.isEmpty() || config == "none") "null"
      else groovy.json.JsonOutput.toJson(groovy.json.JsonSlurper().parseText(config))
    outputDir
      .get()
      .file("firebase-config.js")
      .asFile
      .apply { parentFile.mkdirs() }
      .writeText(
        "// Generated from FIREBASE_WEB_CONFIG and DEV_SIGN_IN by build.gradle.kts; do not edit.\n" +
          "window.GROUND_FIREBASE_CONFIG = $value;\n" +
          "window.GROUND_DEV_SIGN_IN = $devSignInEnabled;\n"
      )
  }
}

tasks.withType<ProcessResources>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from(file("../shared/ui/src/commonMain/composeResources")) {
    into("composeResources/org.openforis.collectone.v2.core.forms.ui.resources")
  }
  // JS bridges loaded by index.html for the shared/app wasmJs actuals (PDF export, media capture).
  from(file("../shared/app/src/wasmJsMain/resources"))
  from(generateUserDataServiceConfig)
  from(generateFirebaseConfig)
}
