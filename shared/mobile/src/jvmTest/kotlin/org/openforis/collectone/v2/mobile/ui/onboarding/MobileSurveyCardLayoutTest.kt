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
package org.openforis.collectone.v2.mobile.ui.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.seed.SampleSurveysData
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.ui.common.SURVEY_VISIBILITY_ICON_TAG
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.navigation.runMobileNavigationTest
import org.openforis.collectone.v2.mobile.ui.surveys.SwitchDownloadedSurveysSubScreen

@OptIn(ExperimentalTestApi::class)
class MobileSurveyCardLayoutTest {

  @Test
  fun downloadSurveyScreen_cardsHaveConsistentHeightButtonSemanticsAndCatalogFeatures() =
    runMobileNavigationTest {
      val state = show(MobileRoute.DownloadSurvey)
      val surveys = state.viewModels.dashboard.uiState.value.surveys

      // Non-downloaded catalog survey (Mekong Delta) renders map features from SurveyOverview
      // before full entity datasets are downloaded.
      val mekong = surveys.first { it.id == "00000035-caf4-571e-b907-f506f92666ab" }
      assertFalse(mekong.isDownloaded)
      assertTrue(
        mekong.thumbnail.features.isNotEmpty(),
        "Expected catalog survey thumbnail to carry map features from SurveyOverview",
      )

      val kenya = surveys.first { it.id == "00000033-6b50-51cd-ac94-baea2ea06d65" }
      assertTrue(kenya.isDownloaded)

      it.onNodeWithContentDescription("Search surveys").performTextReplacement("Mekong")
      it.waitForIdle()
      it
        .onNodeWithContentDescription(mekong.title, useUnmergedTree = true)
        .assertHeightIsEqualTo(308.dp)
      it.onNodeWithContentDescription("Download ${mekong.title}").assertIsDisplayed()

      it.onNodeWithContentDescription("Search surveys").performTextReplacement("Kenya Coffee")
      it.waitForIdle()
      it
        .onNodeWithContentDescription(kenya.title, useUnmergedTree = true)
        .assertHeightIsEqualTo(308.dp)
      it.onNodeWithContentDescription("Downloaded ${kenya.title}").assertIsDisplayed()
      it.onNodeWithContentDescription("Open ${kenya.title}").assertIsDisplayed()

      // Public survey shows visibility icon with hover tooltip instead of a thumbnail chip.
      val publicSurvey = surveys.first { it.isPublicData }
      it.onNodeWithContentDescription("Search surveys").performTextReplacement(publicSurvey.title)
      it.waitForIdle()
      it.onNodeWithText("Public data", useUnmergedTree = true).assertDoesNotExist()
      val visibilityIcon =
        it.onNodeWithTag("$SURVEY_VISIBILITY_ICON_TAG-${publicSurvey.id}", useUnmergedTree = true)
      visibilityIcon.assertIsDisplayed()
      visibilityIcon.performMouseInput { moveTo(center) }
      it.waitForIdle()
      it.onNodeWithText("Public data").assertIsDisplayed()
    }

  @Test
  fun switchDownloadedSurveysSubScreen_cardsKeepExact260DpHeightAcrossStates() =
    runDesktopComposeUiTest(width = 412, height = 915) {
      val sample = SampleSurveysData.defaultSampleSurveys().first()
      val activeCard =
        sample.copy(
          id = "dl-active",
          title = "Short title",
          description = "",
          isDownloaded = true,
        )
      val inactiveUnavailableCard =
        sample.copy(
          id = "dl-inactive",
          title = "Extended two-line mangrove restoration and coastal carbon plot inventory 2026",
          description =
            "Multi-line description covering transect walks, canopy cover quadrats, and community nursery survival audits across coastal wards.",
          isDownloaded = true,
          state = SurveyLifecycleState.CLOSED,
        )

      setContent {
        AppTheme(darkTheme = false) {
          SwitchDownloadedSurveysSubScreen(
            downloadedSurveys = listOf(activeCard, inactiveUnavailableCard),
            activeSurveyId = activeCard.id,
            onOpenSurvey = {},
            onBrowseMoreSurveys = {},
            onBack = {},
          )
        }
      }
      waitForIdle()

      onNode(hasClickAction() and hasText(activeCard.title, substring = true))
        .assertHeightIsEqualTo(260.dp)
      onNode(hasClickAction() and hasText("Extended two-line mangrove", substring = true))
        .performScrollTo()
        .assertHeightIsEqualTo(260.dp)
      onNodeWithText("Open").assertIsDisplayed()
      onNodeWithText("Switch").assertIsDisplayed()
    }
}
