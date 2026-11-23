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
package org.openforis.collectone.v2.mobile.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem

/**
 * [downloadSurveySections] groups Download survey's list by organization name, in case-insensitive
 * order, with the surveys of no known organization last.
 */
class DownloadSurveySectionsTest {
  private fun survey(id: String, organizationId: String? = null) =
    SurveyPreviewItem(
      id = id,
      title = id,
      description = "",
      location = "",
      isDownloaded = false,
      entityCount = 0,
      organizationId = organizationId,
    )

  private fun List<Pair<String, List<SurveyPreviewItem>>>.ids() = map { (title, surveys) ->
    title to surveys.map { it.id }
  }

  @Test
  fun organizations_sortCaseInsensitively_andPublicComesLast() {
    val sections =
      downloadSurveySections(
        surveys =
          listOf(
            survey("public-1"),
            survey("zeta-1", "z"),
            survey("acme-1", "a"),
            survey("zeta-2", "z"),
          ),
        organizationNames = mapOf("z" to "Zeta Trust", "a" to "acme"),
        publicSectionTitle = "Public",
      )

    assertEquals(
      listOf(
        "acme" to listOf("acme-1"),
        "Zeta Trust" to listOf("zeta-1", "zeta-2"),
        "Public" to listOf("public-1"),
      ),
      sections.ids(),
    )
  }

  @Test
  fun surveyOfAnUnknownOrganization_goesToThePublicSection() {
    val sections =
      downloadSurveySections(
        surveys = listOf(survey("s1", "not-a-member"), survey("s2", "a")),
        organizationNames = mapOf("a" to "Acme"),
        publicSectionTitle = "Public",
      )

    assertEquals(listOf("Acme" to listOf("s2"), "Public" to listOf("s1")), sections.ids())
  }

  @Test
  fun noPublicSurveys_leavesOutThePublicSection() {
    val sections =
      downloadSurveySections(
        surveys = listOf(survey("s1", "a")),
        organizationNames = mapOf("a" to "Acme"),
        publicSectionTitle = "Public",
      )

    assertEquals(listOf("Acme" to listOf("s1")), sections.ids())
  }

  @Test
  fun organizationsWithTheSameName_shareOneSection() {
    val sections =
      downloadSurveySections(
        surveys = listOf(survey("s1", "a"), survey("s2", "b")),
        organizationNames = mapOf("a" to "Forest Service", "b" to "Forest Service"),
        publicSectionTitle = "Public",
      )

    assertEquals(listOf("Forest Service" to listOf("s1", "s2")), sections.ids())
  }

  @Test
  fun noSurveys_noSections() {
    assertTrue(downloadSurveySections(emptyList(), mapOf("a" to "Acme"), "Public").isEmpty())
  }
}
