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
package org.openforis.collectone.v2.web.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.core.forms.ui.AppLightColorScheme
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.web.OrganizationDestination
import org.openforis.collectone.v2.web.SurveyEditorDestination
import org.openforis.collectone.v2.web.WebConsoleApp
import org.openforis.collectone.v2.web.WebDashboardDestination
import org.openforis.collectone.v2.web.navigation.MemoryBrowserHistory
import org.openforis.collectone.v2.web.navigation.RecordingPageNavigation
import org.openforis.collectone.v2.web.ui.dashboard.DASHBOARD_DATA_TABLES_PANEL_TAG
import org.openforis.collectone.v2.web.ui.dashboard.DASHBOARD_RIGHT_CARD_TAG
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorSection

/**
 * Verifies the semantic Material 3 surface tokens across Web Console screens:
 * - Onboarding & directory pages (`WebSignInPage`, `WebSurveysPage`): `surfaceContainer` backdrop,
 *   rounded pill search field (`surfaceContainerHighest` when unfocused, `surfaceContainerLowest`
 *   when focused), and `surface` cards.
 * - Survey dashboard (`WebDashboardPage`): `surface` left side panel, and `surfaceContainerLowest`
 *   bottom data-table panel (tab bar, header, and body) and floating right details/form cards.
 * - Survey editor, organization editor, and admin page (`SurveyEditorPage`, `OrganizationPage`):
 *   `surface` left navigation panel and main canvas.
 */
@OptIn(ExperimentalTestApi::class)
class PanelBackgroundsTest {
  private val screenshotPath: String? = System.getenv("PANEL_BACKGROUNDS_PNG")
  private val whiteArgb = Color.White.toArgb()
  private val surfaceArgb = AppLightColorScheme.surface.toArgb()
  private val surfaceContainerArgb = AppLightColorScheme.surfaceContainer.toArgb()
  private val surfaceContainerHighestArgb = AppLightColorScheme.surfaceContainerHighest.toArgb()

  private fun ComposeUiTest.captureBitmap(name: String): Bitmap {
    val bitmap = onRoot().captureToImage().asSkiaBitmap()
    screenshotPath?.let { path ->
      val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)
      File(path.removeSuffix(".png") + "_$name.png").writeBytes(png!!.bytes)
    }
    return bitmap
  }

  private fun Bitmap.pixelAtDp(xDp: Float, yDp: Float, densityScale: Float): Int =
    getColor((xDp * densityScale).roundToInt(), (yDp * densityScale).roundToInt())

  @Test
  fun mainMapPanels_leftBottomAndRight_useSemanticSurfaceHierarchy() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val entity = state.viewModels.dataCollection.uiState.value.entities.first()
      state.viewModels.surveyMap.selectEntity(entity.id)
      state.viewModels.dashboard.showDatasetInTable(
        checkNotNull(state.viewModels.dataCollection.uiState.value.selectedEntity).datasetId
      )
      // Expand the left side panel if collapsed so all three panels are visible together.
      if (!state.viewModels.dashboard.uiState.value.isSidePanelExpanded) {
        state.viewModels.dashboard.toggleSidePanel()
      }

      setContent {
        AppTheme(darkTheme = false) {
          ProvideWebSession(state) {
            WebDashboardDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      val rootBounds = onRoot().getUnclippedBoundsInRoot()
      val bitmap = captureBitmap("dashboard_entity")
      val scale = bitmap.width / rootBounds.width.value

      // Left panel margin inside DashboardSidePanel (x = 8dp, y = bottom - 24dp).
      assertEquals(
        surfaceArgb,
        bitmap.pixelAtDp(8f, rootBounds.height.value - 24f, scale),
        "Left side panel background should use surface",
      )

      // Left panel draggable separator matches the left panel's surface with no resting line.
      val sidePanelWidth = state.viewModels.dashboard.uiState.value.sidePanelWidthDp
      for (offsetDp in listOf(1f, 4f, 7f)) {
        assertEquals(
          surfaceArgb,
          bitmap.pixelAtDp(sidePanelWidth + offsetDp, rootBounds.height.value - 24f, scale),
          "Left side panel separator at +${offsetDp}dp should use surface with no resting line",
        )
      }

      // Bottom data tables panel tab bar margin (just inside its left edge, vertically centered).
      val tableBounds = onNodeWithTag(DASHBOARD_DATA_TABLES_PANEL_TAG).getUnclippedBoundsInRoot()
      assertEquals(
        whiteArgb,
        bitmap.pixelAtDp(tableBounds.left.value + 6f, tableBounds.top.value + 8f, scale),
        "Bottom data tables tab row background should match the right card (surfaceContainerLowest)",
      )

      // Bottom data tables sticky column header row (below the 48dp tab bar and 1dp divider).
      assertEquals(
        whiteArgb,
        bitmap.pixelAtDp(tableBounds.left.value + 6f, tableBounds.top.value + 57f, scale),
        "Bottom data tables header background should match the right card (surfaceContainerLowest)",
      )

      // Expand the right entity details card (docking the bottom table) and verify it is white.
      state.viewModels.dashboard.expandDetailsPanel()
      waitForIdle()
      val detailsBitmap = captureBitmap("dashboard_details")
      val rightCardBounds = onNodeWithTag(DASHBOARD_RIGHT_CARD_TAG).getUnclippedBoundsInRoot()
      assertEquals(
        whiteArgb,
        detailsBitmap.pixelAtDp(
          rightCardBounds.left.value + 8f,
          rightCardBounds.top.value + 20f,
          scale,
        ),
        "Right entity details panel background should be white (surfaceContainerLowest)",
      )

      // Open a data collection form in the right panel and verify WebFormPanelChrome is also white.
      state.viewModels.dataCollection.launchFormForEntity(
        entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
        formId = "form-shade-canopy-audit",
      )
      waitForIdle()
      onNodeWithContentDescription("Close form").assertExists()

      val formBitmap = captureBitmap("dashboard_form")
      val formCardBounds = onNodeWithTag(DASHBOARD_RIGHT_CARD_TAG).getUnclippedBoundsInRoot()
      assertEquals(
        whiteArgb,
        formBitmap.pixelAtDp(formCardBounds.left.value + 8f, formCardBounds.top.value + 20f, scale),
        "Right data collection form panel background should be white (surfaceContainerLowest)",
      )
    }

  @Test
  fun signInPage_usesSurfaceContainerBackdropAndSurfaceCard() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val signOutState = WebAppState(dataHolder = sampleDataHolder()).apply { signOut() }
      setContent {
        WebConsoleApp(
          state = signOutState,
          browserHistory = MemoryBrowserHistory(initialPath = "/surveys"),
        )
      }
      waitForIdle()

      val rootBounds = onRoot().getUnclippedBoundsInRoot()
      val signInBitmap = captureBitmap("sign_in")
      val scale = signInBitmap.width / rootBounds.width.value

      // Header and page backdrop on Sign In both use surfaceContainer so they match, with a
      // 0/4/12/0 10% black drop shadow cast immediately below the 60dp header.
      assertEquals(
        surfaceContainerArgb,
        signInBitmap.pixelAtDp(300f, 10f, scale),
        "Sign-in page header should use surfaceContainer",
      )
      assertNotEquals(
        surfaceContainerArgb,
        signInBitmap.pixelAtDp(300f, 62f, scale),
        "Sign-in page header should cast a drop shadow below y=60dp",
      )
      assertEquals(
        surfaceContainerArgb,
        signInBitmap.pixelAtDp(48f, 300f, scale),
        "Sign-in page backdrop should use surfaceContainer",
      )

      // Centered sign-in card padding uses surface.
      assertEquals(
        surfaceArgb,
        signInBitmap.pixelAtDp(510f, 220f, scale),
        "Sign-in card background should use surface",
      )
    }

  @Test
  fun surveysPage_usesSurfaceContainerBackdropRoundedPillSearchAndSurfaceCards() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val signedInState =
        WebAppState(dataHolder = sampleDataHolder()).apply {
          viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
          viewModels.onboarding.acceptTermsOfService()
        }
      setContent {
        WebConsoleApp(
          state = signedInState,
          browserHistory = MemoryBrowserHistory(initialPath = "/surveys"),
        )
      }
      waitForIdle()

      val rootBounds = onRoot().getUnclippedBoundsInRoot()
      val surveysBitmap = captureBitmap("surveys")
      val scale = surveysBitmap.width / rootBounds.width.value
      assertEquals(
        surfaceContainerArgb,
        surveysBitmap.pixelAtDp(300f, 10f, scale),
        "Surveys page header should use surfaceContainer",
      )
      assertNotEquals(
        surfaceContainerArgb,
        surveysBitmap.pixelAtDp(300f, 62f, scale),
        "Surveys page header should cast a drop shadow below y=60dp",
      )
      assertEquals(
        surfaceContainerArgb,
        surveysBitmap.pixelAtDp(48f, 300f, scale),
        "Surveys page backdrop should use surfaceContainer",
      )
      val searchNode = onNodeWithContentDescription("Search surveys")
      val searchBounds = searchNode.getUnclippedBoundsInRoot()
      assertEquals(
        surfaceContainerArgb,
        surveysBitmap.pixelAtDp(
          searchBounds.left.value + 2f,
          searchBounds.top.value + 2f,
          scale,
        ),
        "Surveys search field outer corner should show backdrop outside rounded pill",
      )
      assertEquals(
        surfaceContainerHighestArgb,
        surveysBitmap.pixelAtDp(
          searchBounds.right.value - 32f,
          searchBounds.top.value + 28f,
          scale,
        ),
        "Surveys search field container should use surfaceContainerHighest when unfocused",
      )
      val cardBounds =
        onNodeWithContentDescription("Simple Point & Land Use Survey").getUnclippedBoundsInRoot()
      assertEquals(
        surfaceArgb,
        surveysBitmap.pixelAtDp(cardBounds.left.value + 8f, cardBounds.bottom.value - 8f, scale),
        "Survey card body should use surface",
      )

      searchNode.performClick()
      waitForIdle()
      val focusedSurveysBitmap = captureBitmap("surveys_search_focused")
      assertEquals(
        whiteArgb,
        focusedSurveysBitmap.pixelAtDp(
          searchBounds.right.value - 32f,
          searchBounds.top.value + 28f,
          scale,
        ),
        "Surveys search field container should use surfaceContainerLowest when focused",
      )
    }

  @Test
  fun surveyEditor_leftPanelAndMainCanvas_useSurface() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val firstFormKey = state.surveyEditor.uiState.value.forms.first().key
      state.surveyEditor.select(SurveyEditorSection.Form(firstFormKey))

      setContent {
        AppTheme(darkTheme = false) {
          ProvideWebSession(state) {
            SurveyEditorDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      val rootBounds = onRoot().getUnclippedBoundsInRoot()
      val mobileBitmap = captureBitmap("survey_editor_mobile")
      val scale = mobileBitmap.width / rootBounds.width.value

      // Left navigation panel empty area near the bottom-left (x = 16dp, y = height - 24dp).
      assertEquals(
        surfaceArgb,
        mobileBitmap.pixelAtDp(16f, rootBounds.height.value - 24f, scale),
        "Survey editor left panel background should use surface",
      )

      // Form editor main canvas area above the flow cards (just below the FormEditorToolbar).
      val toolbarTextBounds = onNodeWithText("Form settings").getUnclippedBoundsInRoot()
      val sidePanelWidth = state.surveyEditor.uiState.value.sidePanelWidthDp
      val canvasSampleX = sidePanelWidth + 36f
      val canvasSampleY = toolbarTextBounds.bottom.value + 28f
      assertEquals(
        surfaceArgb,
        mobileBitmap.pixelAtDp(canvasSampleX, canvasSampleY, scale),
        "Form preview pane should use surface",
      )

      // Survey details main canvas also uses surface.
      state.surveyEditor.select(SurveyEditorSection.Details)
      waitForIdle()
      val detailsBitmap = captureBitmap("survey_editor_details")
      assertEquals(
        surfaceArgb,
        detailsBitmap.pixelAtDp(rootBounds.width.value - 24f, 120f, scale),
        "Survey editor details main canvas should use surface",
      )

      // Switch to Web preview target and capture screenshot to verify clean uncarded layout.
      state.surveyEditor.select(SurveyEditorSection.Form(firstFormKey))
      val formEditor = state.surveyEditor.formEditor(firstFormKey)
      formEditor.selectPreviewTarget(FormPreviewTarget.WEB)
      waitForIdle()
      captureBitmap("survey_editor_web")
    }

  @Test
  fun orgAndAdminPages_leftPanelAndMainCanvas_useSurface() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state =
        WebAppState(dataHolder = sampleDataHolder()).apply {
          viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
          viewModels.onboarding.acceptTermsOfService()
        }
      val org = state.organization.uiState.value.organizations.first { !it.isSynthetic }
      state.organization.openOrganization(org.id)

      setContent {
        AppTheme(darkTheme = false) {
          ProvideWebSession(state) {
            OrganizationDestination(
              web = state,
              navigation = RecordingPageNavigation(),
              onSignOut = {},
            )
          }
        }
      }
      waitForIdle()

      val rootBounds = onRoot().getUnclippedBoundsInRoot()
      val orgBitmap = captureBitmap("organization_editor")
      val scale = orgBitmap.width / rootBounds.width.value

      assertEquals(
        surfaceArgb,
        orgBitmap.pixelAtDp(16f, rootBounds.height.value - 24f, scale),
        "Organization editor left panel background should use surface",
      )
      assertEquals(
        surfaceArgb,
        orgBitmap.pixelAtDp(rootBounds.width.value - 24f, 120f, scale),
        "Organization editor main canvas should use surface",
      )

      state.organization.openAdminPanel()
      waitForIdle()
      val adminBitmap = captureBitmap("admin_page")
      assertEquals(
        surfaceArgb,
        adminBitmap.pixelAtDp(16f, rootBounds.height.value - 24f, scale),
        "Admin page left panel background should use surface",
      )
      assertEquals(
        surfaceArgb,
        adminBitmap.pixelAtDp(rootBounds.width.value - 24f, 120f, scale),
        "Admin page main canvas should use surface",
      )
    }
}
