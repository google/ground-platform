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

/**
 * Hydrates the `.wasm.map` source maps in [outputDir] with inline source content, so Chrome
 * DevTools displays `.kt` sources in `wasmJsBrowserDevelopmentRun`.
 *
 * Kotlin/Wasm's SourceMapGenerator currently ignores sourceMapEmbedSources and emits
 * `"sourcesContent": [null, ...]` with bare filenames for library modules. Each source resolves
 * against [appDir], then [repoRoot], then by file name among the Kotlin files of [appDir]`/src` and
 * [repoRoot]`/shared`.
 */
internal fun hydrateWasmSourceMaps(outputDir: File, appDir: File, repoRoot: File) {
  val wasmMaps = outputDir.listFiles { f -> f.name.endsWith(".wasm.map") } ?: return
  if (wasmMaps.isEmpty()) return
  val ktFilesByName = mutableMapOf<String, File>()
  listOf(appDir.resolve("src"), repoRoot.resolve("shared")).forEach { root ->
    if (root.exists()) {
      root
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .forEach { file -> ktFilesByName.putIfAbsent(file.name, file) }
    }
  }
  val slurper = JsonSlurper()
  for (mapFile in wasmMaps) {
    // parseText, not parse(File): Groovy's streaming parser misreads maps longer than its
    // 10,000-character buffer.
    @Suppress("UNCHECKED_CAST")
    val json = slurper.parseText(mapFile.readText()) as MutableMap<String, Any?>
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
    mapFile.writeText(JsonOutput.toJson(json))
  }
}
