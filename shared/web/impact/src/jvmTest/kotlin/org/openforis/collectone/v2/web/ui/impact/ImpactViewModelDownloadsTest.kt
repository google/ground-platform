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
package org.openforis.collectone.v2.web.ui.impact

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.testing.awaitItemMatching
import org.openforis.collectone.v2.web.ui.impact.ImpactViewModelTest.Companion.KENYA
import org.openforis.collectone.v2.web.ui.impact.ImpactViewModelTest.Companion.KFS
import org.openforis.collectone.v2.web.ui.impact.ImpactViewModelTest.Companion.LINH
import org.openforis.collectone.v2.web.ui.impact.ImpactViewModelTest.FixedAuthRepository
import org.openforis.collectone.v2.web.ui.impact.ImpactViewModelTest.Fixture

/** PDF downloads of [ImpactViewModel], which load their text (string resources) asynchronously. */
class ImpactViewModelDownloadsTest {
  @Test
  fun downloads_saveAPdfSummary_andSayWhere() = runTest {
    val f = Fixture()

    f.viewModel.downloadSurveySummary(KENYA)
    f.viewModel.downloadOrganizationSummary(KFS)
    f.viewModel.downloadGlobalSummary()
    f.awaitMessageStartingWith("Saved impact-summary-all-collect-one-users.pdf")

    assertEquals(
      listOf(
        "impact-summary-all-form-field-types-showcase-kenya-coffee.pdf",
        "impact-summary-kenya-forest-service.pdf",
        "impact-summary-all-collect-one-users.pdf",
      ),
      f.pdf.saved,
    )
    assertTrue(
      f.state.message.orEmpty().startsWith("Saved impact-summary-all-collect-one-users.pdf")
    )
    f.viewModel.dismissMessage()
    assertNull(f.state.message)
  }

  @Test
  fun downloads_ofPublishedNumbers_sayTheyArePublished() = runTest {
    val f = Fixture(auth = FixedAuthRepository(LINH))

    f.viewModel.downloadGlobalSummary()
    f.viewModel.downloadPublishedOrganizationSummary(KFS)
    // Linh isn't a member of Kenya Forest Service, so there are no full numbers to download.
    f.viewModel.downloadOrganizationSummary(KFS)
    f.awaitMessageStartingWith("Saved impact-summary-kenya-forest-service.pdf")

    assertEquals(
      listOf("impact-summary-all-collect-one-users.pdf", "impact-summary-kenya-forest-service.pdf"),
      f.pdf.saved,
    )
  }

  @Test
  fun downloadOrganizationSummary_withSurveyAndCountryFilters_savesFilteredPdf() = runTest {
    val f = Fixture()
    f.viewModel.setOrganizationSurveyFilter(KFS, KENYA)
    f.viewModel.setOrganizationCountryFilter(KFS, "KE")

    f.viewModel.downloadOrganizationSummary(KFS)
    f.awaitMessageStartingWith("Saved impact-summary-kenya-forest-service.pdf")

    assertEquals(listOf("impact-summary-kenya-forest-service.pdf"), f.pdf.saved)
  }

  /**
   * Waits until a message starting with [prefix] is shown. Each download shows its message once its
   * PDF is saved, and downloads save in the order they were asked for, so the last download's
   * message means every PDF is saved. Any shown message isn't enough: it can still be an earlier
   * download's.
   */
  private suspend fun Fixture.awaitMessageStartingWith(prefix: String) {
    viewModel.uiState.test(timeout = 10.seconds) {
      awaitItemMatching { it.message.orEmpty().startsWith(prefix) }
      cancelAndIgnoreRemainingEvents()
    }
  }
}
