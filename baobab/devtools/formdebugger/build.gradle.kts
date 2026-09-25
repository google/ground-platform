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
plugins {
  kotlin("multiplatform") version "2.4.20"
  id("org.jetbrains.compose") version "1.12.0"
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}

group = "org.groundplatform.v2.devtools"

version = "2.0.0-SNAPSHOT"

val devServerPort = project.findProperty("port")?.toString()?.toIntOrNull() ?: 8090

kotlin {
  jvm { testRuns["test"].executionTask.configure { useJUnitPlatform() } }

  js(IR) {
    browser {
      commonWebpackConfig {
        outputFileName = "formdebugger.js"
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

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      commonWebpackConfig {
        outputFileName = "formdebugger.js"
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
        implementation("org.groundplatform.v2:protoforms:2.0.0-SNAPSHOT")
        implementation("org.groundplatform.v2:protoforms-ui:2.0.0-SNAPSHOT")
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
      }
    }
    val commonTest by getting { dependencies { implementation(kotlin("test")) } }
    val jvmTest by getting { dependencies { implementation(kotlin("test-junit5")) } }
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink>().configureEach {
  compilerOptions.sourceMapEmbedSources.set(
    org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
  )
  // Kotlin/Wasm's SourceMapGenerator currently ignores sourceMapEmbedSources and emits
  // `"sourcesContent": [null, ...]` with bare filenames for library modules. Hydrate `.wasm.map`
  // with inline source content so Chrome DevTools displays `.kt` sources in `wasmJsBrowserDevelopmentRun`.
  val repoRoot = projectDir.resolve("../..").canonicalFile
  val appDir = projectDir.canonicalFile
  doLast {
    val outDir = destinationDirectory.get().asFile
    val wasmMaps = outDir.listFiles { f -> f.name.endsWith(".wasm.map") } ?: return@doLast
    if (wasmMaps.isEmpty()) return@doLast
    val ktFilesByName = mutableMapOf<String, File>()
    listOf(appDir.resolve("src"), repoRoot.resolve("shared")).forEach { root ->
      if (root.exists()) {
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
          ktFilesByName.putIfAbsent(file.name, file)
        }
      }
    }
    val slurper = groovy.json.JsonSlurper()
    for (mapFile in wasmMaps) {
      @Suppress("UNCHECKED_CAST")
      val json = slurper.parse(mapFile) as MutableMap<String, Any?>
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

tasks.withType<ProcessResources>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from(file("../../shared/ui/src/commonMain/composeResources")) {
    into("composeResources/org.groundplatform.v2.core.forms.ui.resources")
  }
}
