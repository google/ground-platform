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
package org.openforis.collectone.v2.web.ui.surveys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.seed.SampleSurveysData
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.ui.common.SURVEY_VISIBILITY_ICON_TAG
import org.openforis.collectone.v2.core.forms.ui.AppTheme

@OptIn(ExperimentalLayoutApi::class, ExperimentalTestApi::class)
class WebSurveyCardLayoutTest {

  private val baseSurvey: SurveyPreviewItem = SampleSurveysData.defaultSampleSurveys().first()

  @Test
  fun webSurveyCard_alignsHeightsWithinEachGridRowAndShrinksCompactRows() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val minimalCard = surveyFixture("card-minimal", "Testing", "Arusha, Tanzania", "")
      val closedOutcomeCard =
        surveyFixture(
          id = "card-closed-outcome",
          title =
            "Multi-line regional agroforestry shade canopy and watershed restoration baseline 2026",
          location = "Kakamega, Western Province",
          description =
            "Long multi-line survey description covering plot boundaries, riparian buffer transects, washing stations, and smallholder farmer interviews across multiple wards.",
          state = SurveyLifecycleState.CLOSED,
          sharingPolicy = SharingPolicy.PUBLIC,
        )
      val draftPrivateCard =
        surveyFixture(
          id = "card-draft-private",
          title = "Draft household livelihood and plot boundaries census",
          location = "Nyeri County, Kenya",
          description = "Single line summary.",
          state = SurveyLifecycleState.DRAFT,
          sharingPolicy = SharingPolicy.RESTRICTED,
          peerDataVisibility = PeerDataVisibility.OWN_ONLY,
        )
      val compactCardA =
        surveyFixture("card-a", "Mangrove Plot Check", "Mekong Delta", "Quick sapling check.")
      val compactCardB = surveyFixture("card-b", "Canopy Spot Check", "Pará, Brazil", "")

      var outcomePromptClicked = false
      setContent {
        SurveyCardTestGrid {
          GridCard(minimalCard, SurveyAccessLabel.OWNER, isActive = true)
          GridCard(
            closedOutcomeCard,
            SurveyAccessLabel.ORGANIZATION_MANAGER,
            showOutcomePrompt = true,
            onOutcomePromptClick = { outcomePromptClicked = true },
          )
          GridCard(draftPrivateCard, SurveyAccessLabel.ORGANIZATION_MEMBER)
          GridCard(compactCardA, SurveyAccessLabel.OWNER)
          GridCard(compactCardB, SurveyAccessLabel.SHARED)
        }
      }
      waitForIdle()

      val row1Height = cardHeight(closedOutcomeCard)
      assertTrue(row1Height < 308.dp, "Expected tallest row ($row1Height) < legacy 308.dp")
      for (card in listOf(minimalCard, closedOutcomeCard, draftPrivateCard)) {
        assertCardDimensions(card, expectedHeight = row1Height, expectedTop = 0.dp)
      }

      val row2Height = cardHeight(compactCardA)
      for (card in listOf(compactCardA, compactCardB)) {
        assertCardDimensions(card, expectedHeight = row2Height)
      }
      assertTrue(row2Height < row1Height, "Expected compact row ($row2Height) < ($row1Height)")

      onNodeWithContentDescription("Was this data used?").performClick()
      waitForIdle()
      assertTrue(outcomePromptClicked, "Expected OutcomePromptBadge click handler to fire")

      // Active cards no longer replace the location count with an "Open now" label.
      onNodeWithText("Open now", useUnmergedTree = true).assertDoesNotExist()
    }

  @Test
  fun webSurveyCard_flowsTitleLocationDescriptionAndFooterWithoutDeadVoids() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val testingCard = surveyFixture("card-testing", "Testing", "Unspecified", "")
      val simplePointCard =
        surveyFixture(
          id = "card-simple-point",
          title = "Simple Point & Land Use Survey",
          location = "Arusha, Tanzania",
          description =
            "Single GPS point (map pan allowed, <= 10m accuracy required) and land-use classification. Forms auto-create land-use plots.",
        )
      val multiLineCard =
        surveyFixture(
          id = "card-multiline",
          title = "All Form Field Types Showcase (Kenya Coffee)",
          location = "Nyeri County, Kenya",
          description =
            "EUDR traceability polygon mapping, shade-tree biodiversity inventory, and all 20+ XForms field types.",
        )

      setContent {
        SurveyCardTestGrid {
          GridCard(testingCard, SurveyAccessLabel.OWNER)
          GridCard(simplePointCard, SurveyAccessLabel.ORGANIZATION_MEMBER)
          GridCard(multiLineCard, SurveyAccessLabel.ORGANIZATION_MANAGER, isActive = true)
        }
      }
      waitForIdle()

      assertCohesiveBodyRhythm(
        titleText = "Testing",
        locationText = "Unspecified",
        descriptionSubstring = "No description yet.",
        footerText = "Owner",
      )
      assertCohesiveBodyRhythm(
        titleText = "Simple Point & Land Use Survey",
        locationText = "Arusha, Tanzania",
        descriptionSubstring = "Single GPS point",
        footerText = "Organization member",
      )
    }

  @Test
  fun webSurveyCard_showsVisibilityIconTopRightWithHoverTooltipInsteadOfChip() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val publicCard =
        surveyFixture(
          id = "card-public",
          title = "Public Land Use Survey",
          location = "Arusha, Tanzania",
          description = "Open public survey.",
          sharingPolicy = SharingPolicy.PUBLIC,
          peerDataVisibility = PeerDataVisibility.ALL,
        )
      val privateCard =
        surveyFixture(
          id = "card-private",
          title = "Private Plot Survey",
          location = "Ashanti Region, Ghana",
          description = "Private submissions survey.",
          sharingPolicy = SharingPolicy.ANYONE_WITH_LINK,
          peerDataVisibility = PeerDataVisibility.OWN_ONLY,
        )
      val restrictedCard =
        surveyFixture(
          id = "card-restricted",
          title = "Restricted Team Survey",
          location = "Nyeri County, Kenya",
          description = "Invited members only.",
          sharingPolicy = SharingPolicy.RESTRICTED,
          peerDataVisibility = PeerDataVisibility.ALL,
        )

      setContent {
        SurveyCardTestGrid {
          GridCard(publicCard, SurveyAccessLabel.OWNER)
          GridCard(privateCard, SurveyAccessLabel.OWNER)
          GridCard(restrictedCard, SurveyAccessLabel.OWNER)
        }
      }
      waitForIdle()

      // Visibility text chips are removed from the thumbnail overlay.
      onNodeWithText("Public data", useUnmergedTree = true).assertDoesNotExist()
      onNodeWithText("Private submissions", useUnmergedTree = true).assertDoesNotExist()

      // Restricted surveys have no visibility icon; public and private-submissions surveys do.
      onNodeWithTag("$SURVEY_VISIBILITY_ICON_TAG-${restrictedCard.id}", useUnmergedTree = true)
        .assertDoesNotExist()
      val publicIcon =
        onNodeWithTag("$SURVEY_VISIBILITY_ICON_TAG-${publicCard.id}", useUnmergedTree = true)
      val privateIcon =
        onNodeWithTag("$SURVEY_VISIBILITY_ICON_TAG-${privateCard.id}", useUnmergedTree = true)
      publicIcon.assertIsDisplayed()
      privateIcon.assertIsDisplayed()

      // Icon is top-right aligned to the title area inside the card body.
      val publicTitleBounds =
        onNodeWithText(publicCard.title, useUnmergedTree = true).getUnclippedBoundsInRoot()
      val publicIconBounds = publicIcon.getUnclippedBoundsInRoot()
      assertEquals(publicTitleBounds.top, publicIconBounds.top)
      assertTrue(
        publicIconBounds.left >= publicTitleBounds.right,
        "Expected visibility icon to the right of title ($publicIconBounds vs $publicTitleBounds)",
      )

      // Hovering over each icon reveals its visibility text in a tooltip.
      publicIcon.performMouseInput { enter(center) }
      waitForIdle()
      onNodeWithText("Public data").assertIsDisplayed()
      publicIcon.performMouseInput { exit() }
      waitForIdle()

      privateIcon.performMouseInput { enter(center) }
      waitForIdle()
      onNodeWithText("Private submissions").assertIsDisplayed()
    }

  @Test
  fun webSurveyCard_rendersClosedSurveyInGrayscaleWithLighterThumbnailScrim() =
    runDesktopComposeUiTest(width = 800, height = 400) {
      val openCard =
        surveyFixture(
          id = "card-open",
          title = "Open Canopy Survey",
          location = "Kakamega, Kenya",
          description = "Active survey.",
          state = SurveyLifecycleState.PUBLISHED,
        )
      val closedCard =
        surveyFixture(
          id = "card-closed",
          title = "Closed Canopy Survey",
          location = "Kakamega, Kenya",
          description = "Completed survey.",
          state = SurveyLifecycleState.CLOSED,
        )

      setContent {
        SurveyCardTestGrid {
          GridCard(openCard, SurveyAccessLabel.OWNER)
          GridCard(closedCard, SurveyAccessLabel.OWNER)
        }
      }
      waitForIdle()

      val rootBounds = onRoot().getUnclippedBoundsInRoot()
      val bitmap = onRoot().captureToImage().asSkiaBitmap()
      val scale = bitmap.width / rootBounds.width.value

      fun pixelAt(xDp: Float, yDp: Float): Int =
        bitmap.getColor((xDp * scale).roundToInt(), (yDp * scale).roundToInt())

      fun isGrayscale(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return abs(r - g) <= 2 && abs(g - b) <= 2 && abs(r - b) <= 2
      }

      fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r + g + b) / 3
      }

      val openBounds = onNodeWithContentDescription(openCard.title).getUnclippedBoundsInRoot()
      val closedBounds = onNodeWithContentDescription(closedCard.title).getUnclippedBoundsInRoot()

      // Closed card thumbnail is grayscale and lighter than the open card's thumbnail.
      val closedThumbPixel = pixelAt(closedBounds.right.value - 12f, closedBounds.top.value + 12f)
      val openThumbPixel = pixelAt(openBounds.right.value - 12f, openBounds.top.value + 12f)
      assertTrue(
        isGrayscale(closedThumbPixel),
        "Expected closed card thumbnail to be grayscale, got 0x${closedThumbPixel.toString(16)}",
      )
      assertTrue(
        luminance(closedThumbPixel) > luminance(openThumbPixel) + 15,
        "Expected closed thumbnail (${luminance(closedThumbPixel)}) to be noticeably lighter " +
          "than open thumbnail (${luminance(openThumbPixel)})",
      )

      // The closed card's footer Owner badge stays in color (only thumbnail and text are
      // grayscale).
      val closedOwnerPixel = pixelAt(closedBounds.left.value + 20f, closedBounds.bottom.value - 20f)
      assertTrue(
        !isGrayscale(closedOwnerPixel),
        "Expected closed card footer Owner badge to remain in color, got 0x${closedOwnerPixel.toString(16)}",
      )
    }

  private fun surveyFixture(
    id: String,
    title: String,
    location: String,
    description: String,
    state: SurveyLifecycleState = SurveyLifecycleState.PUBLISHED,
    sharingPolicy: SharingPolicy = SharingPolicy.ORGANIZATION,
    peerDataVisibility: PeerDataVisibility = PeerDataVisibility.ALL,
  ): SurveyPreviewItem =
    baseSurvey.copy(
      id = id,
      title = title,
      location = location,
      description = description,
      state = state,
      sharingPolicy = sharingPolicy,
      peerDataVisibility = peerDataVisibility,
    )

  private fun ComposeUiTest.cardHeight(card: SurveyPreviewItem) =
    onNodeWithContentDescription(card.title).getUnclippedBoundsInRoot().height

  private fun ComposeUiTest.assertCardDimensions(
    card: SurveyPreviewItem,
    expectedHeight: androidx.compose.ui.unit.Dp,
    expectedTop: androidx.compose.ui.unit.Dp? = null,
  ) {
    val node = onNodeWithContentDescription(card.title)
    node.assertWidthIsEqualTo(320.dp).assertHeightIsEqualTo(expectedHeight)
    if (expectedTop != null) {
      assertEquals(expectedTop, node.getUnclippedBoundsInRoot().top)
    }
  }

  private fun ComposeUiTest.assertCohesiveBodyRhythm(
    titleText: String,
    locationText: String,
    descriptionSubstring: String,
    footerText: String,
  ) {
    val titleBounds = onNodeWithText(titleText, useUnmergedTree = true).getUnclippedBoundsInRoot()
    val locationBounds =
      onNodeWithText(locationText, useUnmergedTree = true).getUnclippedBoundsInRoot()
    val descBounds =
      onNodeWithText(descriptionSubstring, substring = true, useUnmergedTree = true)
        .getUnclippedBoundsInRoot()
    val footerBounds = onNodeWithText(footerText, useUnmergedTree = true).getUnclippedBoundsInRoot()

    val titleToLocation = locationBounds.top - titleBounds.bottom
    val locationToDesc = descBounds.top - locationBounds.bottom
    val descToFooter = footerBounds.top - descBounds.bottom

    assertTrue(titleToLocation in 2.dp..8.dp, "Unexpected title→location gap: $titleToLocation")
    assertTrue(
      locationToDesc in 3.dp..10.dp,
      "Unexpected location→description gap: $locationToDesc",
    )
    assertTrue(descToFooter in 8.dp..18.dp, "Unexpected description→footer gap: $descToFooter")
  }

  @Test
  fun webSurveyCard_usesHandPointerCursor() =
    runDesktopComposeUiTest(width = 600, height = 400) {
      val survey = surveyFixture("card-cursor", "Cursor Check", "Nairobi, Kenya", "Summary.")
      setContent { SurveyCardTestGrid { GridCard(survey, SurveyAccessLabel.OWNER) } }
      waitForIdle()

      val modifiers =
        onNodeWithContentDescription("Cursor Check")
          .fetchSemanticsNode()
          .layoutInfo
          .getModifierInfo()
          .map { it.modifier }
      assertTrue(
        Modifier.pointerHoverIcon(PointerIcon.Hand) in modifiers,
        "Expected hand pointer cursor on WebSurveyCard, found: $modifiers",
      )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SurveyCardTestGrid(content: @Composable FlowRowScope.() -> Unit) {
  // 3 cards per row (3 * 320dp + 2 * 16dp = 992dp).
  AppTheme(darkTheme = false) {
    FlowRow(
      modifier = Modifier.width(1000.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      content = content,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.GridCard(
  survey: SurveyPreviewItem,
  access: SurveyAccessLabel,
  isActive: Boolean = false,
  showOutcomePrompt: Boolean = false,
  onOutcomePromptClick: () -> Unit = {},
) {
  WebSurveyCard(
    survey = survey,
    access = access,
    isActive = isActive,
    onClick = {},
    modifier = Modifier.fillMaxRowHeight(),
    showOutcomePrompt = showOutcomePrompt,
    onOutcomePromptClick = onOutcomePromptClick,
  )
}
