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

import java.util.Properties
import org.gradle.api.Project

/**
 * The machine-specific settings of a build (service URLs, Firebase config, tokens), kept out of the
 * repository. Each is read from a Gradle property (`-PNAME=...`), the environment, or
 * `local.properties` (the module's own, then the repository root's), in that order of precedence.
 */
internal class LocalSettings(private val project: Project) {
  private val localProperties: Properties =
    Properties().apply {
      // The module's own file loads last, so its values win over the root's.
      for (dir in listOf(project.layout.settingsDirectory, project.layout.projectDirectory)) {
        val file = dir.file("local.properties")
        project.providers.fileContents(file).asText.orNull?.let { load(it.reader()) }
      }
    }

  /** The trimmed value of the setting [name], or null when it isn't set anywhere. */
  fun get(name: String): String? =
    (project.providers.gradleProperty(name).orNull
        ?: project.providers.environmentVariable(name).orNull)
      ?.trim() ?: localProperty(name)

  /** The trimmed value of [name] from `local.properties`. */
  fun localProperty(name: String): String? = localProperties.getProperty(name)?.trim()
}
