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
package org.openforis.collectone.v2.core.library

import kotlin.test.Test
import kotlin.test.assertEquals
import openforis.collectone.v1.library.FormTemplateDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LibraryStatus
import openforis.collectone.v1.library.LocalizedText

class TemplateVersioningTest {
  private val v1 =
    FormTemplateDef(id = "eudr", title = LocalizedText(mapOf("en" to "EUDR")), version = 1)

  @Test
  fun newTemplates_startAtVersionOne() {
    assertEquals(1, TemplateVersioning.nextVersion(null, v1.copy(version = 0)))
  }

  @Test
  fun editedTemplates_getTheNextVersion_unchangedOnesKeepTheirs() {
    val edited = v1.copy(title = LocalizedText(mapOf("en" to "EUDR plot check")))
    assertEquals(2, TemplateVersioning.nextVersion(v1, edited))
    // A seed still at version 1 doesn't take a stored version 2 back.
    assertEquals(2, TemplateVersioning.nextVersion(v1.copy(version = 2), v1))
    // Status and order aren't edits.
    assertEquals(
      1,
      TemplateVersioning.nextVersion(
        v1,
        v1.copy(status = LibraryStatus.DEPRECATED, sort_order = 3),
      ),
    )
  }

  @Test
  fun withVersions_appliesToEveryTemplateOfTheBundle() {
    val previous = LibraryBundle(form_templates = listOf(v1))
    val next =
      LibraryBundle(
        form_templates =
          listOf(
            v1.copy(description = LocalizedText(mapOf("en" to "New"))),
            FormTemplateDef(id = "uk_frc"),
          )
      )
    assertEquals(
      listOf(2, 1),
      TemplateVersioning.withVersions(previous, next).form_templates.map { it.version },
    )
  }
}
