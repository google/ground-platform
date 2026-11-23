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
package org.openforis.collectone.v2.app.ui.datacollection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_pdf_share_title_map_feature
import org.openforis.collectone.v2.app.resources.app_pdf_share_title_submission
import org.openforis.collectone.v2.app.ui.text.UiText

/** The English titles of shared PDF reports, as the ViewModel wrote them before. */
class PdfShareTitleTest {
  private fun english(text: UiText): String = runBlocking { text.resolveString() }

  @Test
  fun mapFeatureReport_isTitledAfterTheFeatureType() {
    assertEquals(
      "Coffee parcel report",
      english(UiText.Resource(Res.string.app_pdf_share_title_map_feature, listOf("Coffee parcel"))),
    )
  }

  @Test
  fun submissionReport_isTitledAfterTheForm() {
    assertEquals(
      "Baseline survey submission",
      english(
        UiText.Resource(Res.string.app_pdf_share_title_submission, listOf("Baseline survey"))
      ),
    )
  }
}
