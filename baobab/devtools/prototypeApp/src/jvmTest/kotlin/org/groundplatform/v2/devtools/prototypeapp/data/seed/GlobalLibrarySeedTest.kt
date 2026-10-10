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
package org.groundplatform.v2.devtools.prototypeapp.data.seed

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.core.library.LibraryTextProtoSerializer
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.LibraryProtoMapper
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization

/**
 * Keeps the canonical global library seed files (`shared/assets/library/<vocabulary>.textproto`)
 * and the app's Kotlin copy ([GlobalLibrarySeedData]) identical, by parsing every seed file with
 * the shared `TextProtoParser` (through [LibraryTextProtoSerializer]).
 *
 * Run `./gradlew jvmTest -PregenerateLibrarySeed` to rewrite the seed files from the Kotlin copy.
 */
class GlobalLibrarySeedTest {
  private val seedDir = File("../../shared/assets/library")
  private val regenerate = System.getProperty("regenerateLibrarySeed") != null

  private fun header(vocabulary: String) =
    """
    |# Copyright 2026 The Ground Authors.
    |#
    |# Licensed under the Apache License, Version 2.0 (the "License");
    |# you may not use this file except in compliance with the License.
    |# You may obtain a copy of the License at
    |#
    |#     https://www.apache.org/licenses/LICENSE-2.0
    |#
    |# Unless required by applicable law or agreed to in writing, software
    |# distributed under the License is distributed on an "AS IS" BASIS,
    |# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    |# See the License for the specific language governing permissions and
    |# limitations under the License.
    |
    |# proto-file: shared/protos/library/library_bundle.proto
    |# proto-message: groundplatform.v2.library.LibraryBundle
    |#
    |# Global library seed: the `$vocabulary` vocabulary. Bootstraps the "All users"
    |# organization's library on a new deployment; entries omit organization_id,
    |# which the loader sets to the "All users" organization.
    |#
    |# The prototype app embeds a Kotlin copy (GlobalLibrarySeedData.kt), and its
    |# GlobalLibrarySeedTest fails when the two differ. Template questions carry
    |# their concept links as `ground:concept` foreign attributes on their bindings.
    |
    |"""
      .trimMargin()

  @Test
  fun seedFiles_matchTheKotlinCopy() {
    assertTrue(seedDir.isDirectory, "Run from the prototypeApp project directory")
    for (vocabulary in GlobalLibrarySeedData.VOCABULARIES) {
      val expected =
        LibraryProtoMapper.toBundle(
          GlobalLibrarySeedData.vocabulary(vocabulary),
          includeOrganizationId = false,
        )
      val file = File(seedDir, "$vocabulary.textproto")
      if (regenerate) {
        file.writeText(header(vocabulary) + LibraryTextProtoSerializer.serializeBundle(expected))
      }
      assertTrue(file.isFile, "Missing seed file ${file.path}")
      val parsed = LibraryTextProtoSerializer.deserializeBundle(file.readText())
      assertEquals(
        LibraryTextProtoSerializer.serializeBundle(expected),
        LibraryTextProtoSerializer.serializeBundle(parsed),
        "${file.name} differs from GlobalLibrarySeedData; run jvmTest -PregenerateLibrarySeed",
      )
      assertEquals(expected, parsed)
    }
    val extra =
      seedDir
        .listFiles { f -> f.extension == "textproto" }
        .orEmpty()
        .map { it.nameWithoutExtension }
    assertEquals(GlobalLibrarySeedData.VOCABULARIES.sorted(), extra.sorted())
  }

  @Test
  fun seedFiles_loadIntoTheAllUsersLibrary() {
    val loaded =
      GlobalLibrarySeedData.VOCABULARIES.map { vocabulary ->
        LibraryProtoMapper.fromBundle(
          LibraryTextProtoSerializer.deserializeBundle(
            File(seedDir, "$vocabulary.textproto").readText()
          ),
          Organization.ALL_USERS_ID,
        )
      }
    val kotlin = GlobalLibrarySeedData.library()
    assertEquals(kotlin.concepts, loaded.flatMap { it.concepts })
    assertEquals(kotlin.purposePacks, loaded.flatMap { it.purposePacks })
    assertEquals(kotlin.exportProfiles, loaded.flatMap { it.exportProfiles })
    val templates = loaded.flatMap { it.formTemplates }
    assertEquals(kotlin.formTemplates.map { it.id }, templates.map { it.id })
    for ((seed, parsed) in kotlin.formTemplates.zip(templates)) {
      assertEquals(seed.title, parsed.title)
      assertEquals(
        seed.form.questions.map {
          listOf(it.name, it.type, it.label, it.required, it.choices, it.conceptLink)
        },
        parsed.form.questions.map {
          listOf(it.name, it.type, it.label, it.required, it.choices, it.conceptLink)
        },
      )
    }
    assertTrue(loaded.all { library -> library.integrityError() == null })
  }
}
