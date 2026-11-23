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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec

/** Settings of a `collectone.wasm.app` module. */
abstract class WasmAppExtension {
  /**
   * The port of the webpack dev server (`./gradlew :<module>:wasmJsBrowserDevelopmentRun`), unless
   * `-Pport=...` overrides it.
   */
  abstract val devServerPort: Property<Int>
}

/**
 * `collectone.wasm.app`: a Compose Multiplatform app for the browser (Kotlin/Wasm), bundled as
 * `<module>.js` with webpack (`./gradlew :<module>:wasmJsBrowserDistribution`; customize webpack in
 * the module's webpack.config.d/).
 *
 * The bundle includes the shared UI resources, the JS bridges of shared/app's (and
 * shared/app/data's) wasmJs actuals, and two generated scripts with this machine's settings (see
 * [LocalSettings]):
 * - `user-data-service-config.js`: `USER_DATA_SERVICE_URL`, the base URL of the Collect One server
 *   (server/). It must be reachable from users' browsers (HTTPS when the bundle is served over
 *   HTTPS). The dev server ignores this copy: it proxies the service and points the app at its own
 *   origin (see webpack.config.d/devServer.js). Empty: the app shows setup instructions instead of
 *   starting.
 * - `firebase-config.js`: `FIREBASE_WEB_CONFIG` (the Firebase web app config object as JSON) for
 *   sign-in (`firebase-auth-bridge.js`); no config is checked in, and unset (or `none`) turns
 *   Firebase off, so the app uses fake sign-in and the server's development user. Also
 *   `DEV_SIGN_IN` (default true: before signing in, the app asks which role to simulate against a
 *   server in AUTH_MODE=dev; set it to false for builds served to real users), `PASSLIST` (optional
 *   email addresses and domain wildcards, `*@domain`, `*.domain`, or exact email, allowed to sign
 *   in; empty allows any account), and `MAPBOX_ACCESS_TOKEN`.
 */
class WasmAppConventionPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit =
    with(target) {
      pluginManager.apply("collectone.kmp.compose")
      useModuleGroup()
      val wasmApp = extensions.create<WasmAppExtension>("wasmApp")
      val devServerPortOverride = providers.gradleProperty("port").map { it.toIntOrNull() }
      val bundleName = "$name.js"
      extensions.configure<KotlinMultiplatformExtension> {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
          browser {
            commonWebpackConfig {
              outputFileName = bundleName
              devServer =
                (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                  port = devServerPortOverride.orNull ?: wasmApp.devServerPort.get()
                  open = false
                }
            }
          }
          binaries.executable()
        }
      }

      val repoRoot = rootDir
      val appDir = projectDir
      // Source-map embedding and inline source hydration are only used by Chrome DevTools during
      // `wasmJsBrowserDevelopmentRun`; production distributions do not serve `.wasm.map` files.
      tasks.withType<KotlinJsIrLink>().configureEach {
        if (name.contains("Development", ignoreCase = true)) {
          compilerOptions.sourceMapEmbedSources.set(
            JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
          )
          doLast { hydrateWasmSourceMaps(destinationDirectory.get().asFile, appDir, repoRoot) }
        }
      }

      val settings = LocalSettings(this)
      configureBinaryen(settings, repoRoot)
      val userDataServiceConfig = registerUserDataServiceConfig(settings)
      val firebaseConfig = registerFirebaseConfig(settings)
      tasks.withType<ProcessResources>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(repoRoot.resolve("shared/ui/src/commonMain/composeResources")) {
          into("composeResources/org.openforis.collectone.v2.core.forms.ui.resources")
        }
        // JS bridges loaded by index.html for the wasmJs actuals of shared/app/ui-common (media
        // capture)
        // and shared/app/data (sign-in, PDF export).
        from(repoRoot.resolve("shared/app/ui-common/src/wasmJsMain/resources"))
        from(repoRoot.resolve("shared/app/data/src/wasmJsMain/resources"))
        from(userDataServiceConfig)
        from(firebaseConfig)
      }
    }

  /**
   * Configures Binaryen (`wasm-opt`) optimization for production Wasm executables:
   * - `WASM_OPT=full` (default): Kotlin/Wasm's full multi-pass optimization pipeline for live
   *   production deploys.
   * - `WASM_OPT=fast`: single `--closed-world -O1` pass (~20x less CPU, ~5x less RAM, within ~0.8
   *   MB gzip of `full`), intended for staging previews and CI.
   * - `WASM_OPT=none`: skips `wasm-opt` and copies the linked `.wasm` directly for fast local smoke
   *   builds.
   *
   * On non-Windows hosts, `wasm-opt` is invoked through `scripts/wasm-opt-wrapper.sh` to cap
   * `BINARYEN_CORES` (overridable via `-PBINARYEN_CORES=N` or `BINARYEN_CORES`) and serialize
   * concurrent runs across git worktrees via `flock`. `BinaryenExec` is also opted into Gradle's
   * build cache so unchanged Wasm modules (such as `:balloonFormApp`) are reused across worktrees.
   */
  private fun Project.configureBinaryen(settings: LocalSettings, repoRoot: File) {
    val wasmOptMode = (settings.get("WASM_OPT") ?: "full").lowercase()
    if (wasmOptMode !in setOf("full", "fast", "none")) {
      throw GradleException("Unsupported WASM_OPT='$wasmOptMode'; expected full, fast, or none.")
    }
    val binaryenCoresOverride =
      settings.get("BINARYEN_CORES")?.let { raw ->
        raw.toIntOrNull()?.takeIf { it > 0 }
          ?: throw GradleException(
            "Unsupported BINARYEN_CORES='$raw'; expected a positive integer."
          )
      }
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val wrapper = repoRoot.resolve("scripts/wasm-opt-wrapper.sh")

    tasks.withType<BinaryenExec>().configureEach {
      inputs.property("wasmOptMode", wasmOptMode)
      if (wasmOptMode == "none") {
        outputs.cacheIf { false }
        if (!isWindows && wrapper.isFile) {
          executable = wrapper.absolutePath
          binaryenArguments.set(listOf("--collectone-copy-only"))
        } else {
          actions.clear()
          val inputsToCopy = inputFiles
          val targetDir = outputDirectory
          doLast {
            val outDir = targetDir.get().asFile
            outDir.mkdirs()
            val parentDirs = inputsToCopy.files.mapNotNull { it.parentFile }.distinct()
            parentDirs.forEach { dir ->
              dir
                .listFiles()
                ?.filter { it.isFile }
                ?.forEach { src ->
                  src.copyTo(outDir.resolve(src.name), overwrite = true)
                }
            }
          }
        }
        return@configureEach
      }

      outputs.cacheIf { true }
      // Read the task's initial default args without resolving the `binaryenArguments` provider
      // eagerly at configuration time.
      @Suppress("DEPRECATION") val defaultArgs = binaryenArgs.toList()
      val passArgs =
        when (wasmOptMode) {
          "full" -> defaultArgs
          "fast" -> defaultArgs.filterNot { it in HEAVY_BINARYEN_PASSES } + "-O1"
          else -> error("Unreachable WASM_OPT='$wasmOptMode'")
        }
      val realExecutable = executable
      if (!isWindows && wrapper.isFile && realExecutable != null) {
        executable = wrapper.absolutePath
        val prefix = buildList {
          add(realExecutable)
          if (binaryenCoresOverride != null) {
            add("--collectone-cores=$binaryenCoresOverride")
          }
        }
        binaryenArguments.set(prefix + passArgs)
      } else {
        binaryenArguments.set(passArgs)
      }
    }
  }

  /** Registers the task that writes `user-data-service-config.js`. */
  private fun Project.registerUserDataServiceConfig(settings: LocalSettings): TaskProvider<Task> {
    val url = settings.get("USER_DATA_SERVICE_URL") ?: ""
    val outputDir = layout.buildDirectory.dir("generated/userDataServiceConfig")
    return tasks.register("generateUserDataServiceConfig") {
      inputs.property("userDataServiceUrl", url)
      outputs.dir(outputDir)
      doLast {
        val value = if (url.isEmpty()) "null" else JsonOutput.toJson(url)
        outputDir
          .get()
          .file("user-data-service-config.js")
          .asFile
          .apply { parentFile.mkdirs() }
          .writeText(
            "// Generated from USER_DATA_SERVICE_URL by build.gradle.kts; do not edit.\n" +
              "window.COLLECT_ONE_USER_DATA_SERVICE_URL = $value;\n"
          )
      }
    }
  }

  /** Registers the task that writes `firebase-config.js`. */
  private fun Project.registerFirebaseConfig(settings: LocalSettings): TaskProvider<Task> {
    val config = settings.get("FIREBASE_WEB_CONFIG") ?: "none"
    val devSignInEnabled = (settings.get("DEV_SIGN_IN") ?: "true").toBoolean()
    val appEnv = (settings.get("APP_ENV") ?: settings.get("COLLECT_ONE_ENV") ?: "").trim()
    val passlist = settings.get("PASSLIST") ?: ""
    val mapboxToken =
      (providers.gradleProperty("MAPBOX_ACCESS_TOKEN").orNull
          ?: providers.gradleProperty("mapbox.access.token").orNull
          ?: providers.environmentVariable("MAPBOX_ACCESS_TOKEN").orNull)
        ?.trim()
        ?: settings.localProperty("MAPBOX_ACCESS_TOKEN")
        ?: settings.localProperty("mapbox.access.token")
        ?: ""
    val outputDir = layout.buildDirectory.dir("generated/firebaseConfig")
    return tasks.register("generateFirebaseConfig") {
      inputs.property("devSignIn", devSignInEnabled)
      inputs.property("appEnv", appEnv)
      inputs.property("passlist", passlist)
      inputs.property("mapboxAccessToken", mapboxToken)
      inputs.property("firebaseWebConfig", config)
      outputs.dir(outputDir)
      doLast {
        val value =
          if (config.isEmpty() || config == "none") "null"
          else JsonOutput.toJson(JsonSlurper().parseText(config))
        val appEnvJson = if (appEnv.isEmpty()) "null" else JsonOutput.toJson(appEnv)
        val passlistEntries =
          passlist.split(',', ';', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val passlistJson = JsonOutput.toJson(passlistEntries)
        val mapboxTokenJson =
          if (mapboxToken.startsWith("pk.")) JsonOutput.toJson(mapboxToken) else "null"
        outputDir
          .get()
          .file("firebase-config.js")
          .asFile
          .apply { parentFile.mkdirs() }
          .writeText(
            "// Generated from FIREBASE_WEB_CONFIG, DEV_SIGN_IN, APP_ENV, PASSLIST, and MAPBOX_ACCESS_TOKEN by build.gradle.kts; do not edit.\n" +
              "window.COLLECT_ONE_FIREBASE_CONFIG = $value;\n" +
              "window.COLLECT_ONE_DEV_SIGN_IN = $devSignInEnabled;\n" +
              "window.COLLECT_ONE_ENV = $appEnvJson;\n" +
              "window.COLLECT_ONE_PASSLIST = $passlistJson;\n" +
              "if ($mapboxTokenJson) {\n" +
              "  window.MAPBOX_ACCESS_TOKEN = $mapboxTokenJson;\n" +
              "  if (window.mapboxgl) window.mapboxgl.accessToken = $mapboxTokenJson;\n" +
              "}\n"
          )
      }
    }
  }

  private companion object {
    val HEAVY_BINARYEN_PASSES =
      setOf(
        "--type-ssa",
        "--gufa",
        "--type-merging",
        "-O",
        "-O0",
        "-O1",
        "-O2",
        "-O3",
        "-O4",
        "-Os",
        "-Oz",
      )
  }
}
