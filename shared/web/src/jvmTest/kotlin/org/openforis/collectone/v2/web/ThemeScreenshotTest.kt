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
package org.openforis.collectone.v2.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.editor.EditorStatusBadge
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.ui.common.UserProfileCard
import org.openforis.collectone.v2.core.forms.ui.AppTheme
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.navigation.MemoryBrowserHistory
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.createSurveyWizard
import org.openforis.collectone.v2.web.ui.formeditor.StatusBadgeChip
import org.openforis.collectone.v2.web.ui.impact
import org.openforis.collectone.v2.web.ui.organization
import org.openforis.collectone.v2.web.ui.surveyEditor

/**
 * Golden-screenshot tests that render Collect One web console screens in both light and dark themes
 * and verify them against checked-in golden images in `src/jvmTest/resources/goldens/`.
 *
 * Set `UPDATE_GOLDENS=true` to regenerate the golden images. Set `THEME_SCREENSHOT_DIR=<path>` to
 * also export copies of the rendered PNGs to that directory.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeScreenshotTest {
  private val exportDir: File? = System.getenv("THEME_SCREENSHOT_DIR")?.let(::File)
  private val updateGoldens: Boolean = System.getenv("UPDATE_GOLDENS")?.toBoolean() == true

  /**
   * Scenes that don't match their golden. The test reports them all at the end, not just the first.
   */
  private val mismatches = mutableListOf<String>()

  private data class WebScene(
    val id: String,
    val widthDp: Int = 1440,
    val heightDp: Int = 900,
    val prepare: (dark: Boolean) -> Pair<WebAppState, MemoryBrowserHistory>,
    val afterCompose: ComposeUiTest.(WebAppState) -> Unit = {},
  )

  private fun makeState(dark: Boolean, signedIn: Boolean = true): WebAppState {
    val state = WebAppState(dataHolder = sampleDataHolder())
    if (state.viewModels.settings.uiState.value.isDarkTheme != dark)
      state.viewModels.settings.toggleDarkTheme()
    if (signedIn) {
      state.viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
      state.viewModels.onboarding.acceptTermsOfService()
    } else {
      state.signOut()
    }
    return state
  }

  private val scenes: List<WebScene> =
    listOf(
      WebScene(
        id = "01_web_sign_in",
        prepare = { dark ->
          val state = makeState(dark, signedIn = false)
          state to MemoryBrowserHistory(initialPath = "/surveys")
        },
      ),
      WebScene(
        id = "02_web_surveys",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          state to MemoryBrowserHistory(initialPath = "/surveys")
        },
      ),
      WebScene(
        id = "03_web_dashboard_map_entity",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          val entity = state.viewModels.dataCollection.uiState.value.entities.first()
          state.viewModels.surveyMap.selectEntity(entity.id)
          state.viewModels.dashboard.showDatasetInTable(
            checkNotNull(state.viewModels.dataCollection.uiState.value.selectedEntity).datasetId
          )
          if (!state.viewModels.dashboard.uiState.value.isSidePanelExpanded) {
            state.viewModels.dashboard.toggleSidePanel()
          }
          state to
            MemoryBrowserHistory(
              initialPath =
                "/surveys/${state.viewModels.dashboard.uiState.value.activeSurveyId}/dashboard"
            )
        },
      ),
      WebScene(
        id = "04_web_dashboard_form",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          state.viewModels.surveyMap.selectEntity("0000000a-56d7-5c1d-9457-7457267120a3")
          state.viewModels.dataCollection.launchFormForEntity(
            entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
            formId = "form-shade-canopy-audit",
          )
          state.viewModels.dashboard.updateTimeSeriesExpanded(true)
          state to
            MemoryBrowserHistory(
              initialPath =
                "/surveys/${state.viewModels.dashboard.uiState.value.activeSurveyId}/dashboard"
            )
        },
      ),
      WebScene(
        id = "05_web_survey_editor_details",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          state to
            MemoryBrowserHistory(
              initialPath =
                "/surveys/${state.viewModels.dashboard.uiState.value.activeSurveyId}/editor"
            )
        },
      ),
      WebScene(
        id = "06_web_survey_editor_form_mobile",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          val firstForm = state.surveyEditor.uiState.value.forms.first()
          val editor = state.surveyEditor.formEditor(firstForm.key)
          editor.selectPreviewTarget(FormPreviewTarget.MOBILE)
          firstForm.form.questions.firstOrNull()?.let { editor.select(it.key) }
          state to
            MemoryBrowserHistory(
              initialPath =
                "/surveys/${state.viewModels.dashboard.uiState.value.activeSurveyId}/editor/forms/${firstForm.key}"
            )
        },
      ),
      WebScene(
        id = "07_web_survey_editor_form_web",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          val firstForm = state.surveyEditor.uiState.value.forms.first()
          val editor = state.surveyEditor.formEditor(firstForm.key)
          editor.selectPreviewTarget(FormPreviewTarget.WEB)
          firstForm.form.questions.firstOrNull()?.let { editor.select(it.key) }
          state to
            MemoryBrowserHistory(
              initialPath =
                "/surveys/${state.viewModels.dashboard.uiState.value.activeSurveyId}/editor/forms/${firstForm.key}"
            )
        },
      ),
      WebScene(
        id = "08_web_survey_editor_dataset",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          val firstDataset = state.surveyEditor.uiState.value.datasets.first()
          state to
            MemoryBrowserHistory(
              initialPath =
                "/surveys/${state.viewModels.dashboard.uiState.value.activeSurveyId}/editor/datasets/${firstDataset.key}"
            )
        },
      ),
      WebScene(
        id = "09_web_survey_editor_sharing",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          state to
            MemoryBrowserHistory(
              initialPath =
                "/surveys/${state.viewModels.dashboard.uiState.value.activeSurveyId}/editor/sharing"
            )
        },
      ),
      WebScene(
        id = "10_web_wizard_overview",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          state.createSurveyWizard.startWizard()
          state.createSurveyWizard.updateTitle("Shade Canopy Audit 2026")
          state to MemoryBrowserHistory(initialPath = "/surveys/new")
        },
      ),
      WebScene(
        id = "11_web_wizard_mode_purpose",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          state.createSurveyWizard.startWizard()
          state.createSurveyWizard.updateTitle("Shade Canopy Audit 2026")
          state.createSurveyWizard.selectStep(CreateSurveyWizardStep.MODE_AND_PURPOSE)
          state to MemoryBrowserHistory(initialPath = "/surveys/new/mode-and-purpose")
        },
      ),
      WebScene(
        id = "12_web_wizard_survey_area",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          state.createSurveyWizard.startWizard()
          state.createSurveyWizard.updateTitle("Shade Canopy Audit 2026")
          state.createSurveyWizard.selectStep(CreateSurveyWizardStep.SURVEY_AREA)
          state to MemoryBrowserHistory(initialPath = "/surveys/new/survey-area")
        },
      ),
      WebScene(
        id = "13_web_organizations",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          state to MemoryBrowserHistory(initialPath = "/organizations")
        },
      ),
      WebScene(
        id = "14_web_organization_detail",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          val orgId = state.organization.uiState.value.organizations.first().id
          state.organization.openOrganization(orgId)
          state to MemoryBrowserHistory(initialPath = "/organizations/$orgId")
        },
      ),
      WebScene(
        id = "15_web_impact_dashboard",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          state.impact.openDashboard(null)
          state to MemoryBrowserHistory(initialPath = "/impact")
        },
        // The numbers are computed in the background: capture them once the page shows them, not
        // while it still says "Adding up the numbers…".
        afterCompose = { state ->
          waitUntil(timeoutMillis = 10_000) {
            !state.impact.uiState.value.isLoading &&
              onAllNodesWithText("Adding up the numbers", substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
          }
          waitForIdle()
        },
      ),
      WebScene(
        id = "17_web_about",
        prepare = { dark ->
          val state = makeState(dark, signedIn = true)
          state to MemoryBrowserHistory(initialPath = "/surveys")
        },
        // The About dialog, opened from the account menu.
        afterCompose = {
          onNode(hasContentDescription("Account menu", substring = true)).performClick()
          waitForIdle()
          onNodeWithText("About").performClick()
          waitForIdle()
        },
      ),
    )

  @Test
  fun capturesKeyScreensInBothThemes() {
    exportDir?.mkdirs()
    for (scene in scenes) {
      for (dark in listOf(false, true)) {
        val suffix = if (dark) "dark" else "light"
        val goldenName = "${scene.id}_$suffix.png"
        runDesktopComposeUiTest(width = scene.widthDp, height = scene.heightDp) {
          val (state, history) = scene.prepare(dark)
          setContent {
            WebConsoleApp(state = state, browserHistory = history)
          }
          waitForIdle()
          scene.afterCompose(this, state)
          val bitmap = onAllNodes(isRoot()).onFirst().captureToImage().asSkiaBitmap()
          assertMatchesGolden(bitmap, goldenName)
        }
      }
    }

    // Component sheet verification
    for (dark in listOf(false, true)) {
      val suffix = if (dark) "dark" else "light"
      val goldenName = "16_web_components_$suffix.png"
      runDesktopComposeUiTest(width = 480, height = 640) {
        setContent {
          AppTheme(darkTheme = dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
              Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UserProfileCard(
                  userName = "Ada Lovelace",
                  userEmail = "ada@example.org",
                  userInitials = "AL",
                  organization = "Forest Service",
                  onSignOut = {},
                  onOpenOrganizations = {},
                )
                listOf(
                    EditorStatusBadge.SURVEYED,
                    EditorStatusBadge.IN_PROGRESS,
                    EditorStatusBadge.NEEDS_REVIEW,
                    EditorStatusBadge.FLAGGED,
                    EditorStatusBadge.PENDING,
                  )
                  .forEach { StatusBadgeChip(it) }
              }
            }
          }
        }
        waitForIdle()
        val bitmap = onRoot().captureToImage().asSkiaBitmap()
        assertMatchesGolden(bitmap, goldenName)
      }
    }
    if (mismatches.isNotEmpty()) fail(mismatches.joinToString("\n"))
  }

  private fun assertMatchesGolden(
    actual: Bitmap,
    goldenName: String,
    maxDiffPixelRatio: Double = 0.001,
  ) {
    if (exportDir != null) {
      val exportPng = Image.makeFromBitmap(actual).encodeToData(EncodedImageFormat.PNG)!!
      File(exportDir, goldenName).writeBytes(exportPng.bytes)
    }

    val candidates =
      listOf(
        File("shared/web/src/jvmTest/resources/goldens/$goldenName"),
        File("src/jvmTest/resources/goldens/$goldenName"),
      )
    val targetGoldenFile = candidates.firstOrNull { it.parentFile.exists() } ?: candidates.first()

    val resourceStream = javaClass.classLoader.getResourceAsStream("goldens/$goldenName")
    val goldenBytes =
      when {
        targetGoldenFile.exists() -> targetGoldenFile.readBytes()
        resourceStream != null -> resourceStream.readBytes()
        else -> null
      }

    if (goldenBytes == null || updateGoldens) {
      targetGoldenFile.parentFile.mkdirs()
      val png = Image.makeFromBitmap(actual).encodeToData(EncodedImageFormat.PNG)!!
      targetGoldenFile.writeBytes(png.bytes)
      return
    }

    val expectedImage = Image.makeFromEncoded(goldenBytes)
    val expected = Bitmap.makeFromImage(expectedImage)

    assertEquals(expected.width, actual.width, "Width mismatch for $goldenName")
    assertEquals(expected.height, actual.height, "Height mismatch for $goldenName")

    var diffPixels = 0
    val totalPixels = actual.width * actual.height
    for (y in 0 until actual.height) {
      for (x in 0 until actual.width) {
        if (actual.getColor(x, y) != expected.getColor(x, y)) {
          diffPixels++
        }
      }
    }

    val diffRatio = diffPixels.toDouble() / totalPixels.toDouble()
    if (diffRatio > maxDiffPixelRatio) {
      val reportDir = File("build/reports/goldens/web").apply { mkdirs() }
      val actualPng = Image.makeFromBitmap(actual).encodeToData(EncodedImageFormat.PNG)!!
      File(reportDir, "actual_$goldenName").writeBytes(actualPng.bytes)
      mismatches +=
        "Golden mismatch for $goldenName: diff ratio $diffRatio exceeds threshold $maxDiffPixelRatio " +
          "($diffPixels / $totalPixels pixels differed). Actual screenshot: ${reportDir.resolve("actual_$goldenName").absolutePath}"
    }
  }
}
