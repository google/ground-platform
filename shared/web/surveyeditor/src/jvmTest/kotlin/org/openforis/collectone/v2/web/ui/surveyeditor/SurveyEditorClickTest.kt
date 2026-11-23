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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.PlaceRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleSurveysData
import org.openforis.collectone.v2.app.di.BackendOperationError
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.ui.session.BACKEND_ERROR_BANNER_TAG
import org.openforis.collectone.v2.app.ui.session.BackendErrorBanner
import org.openforis.collectone.v2.web.domain.usecase.SurveyLifecycleFixture
import org.openforis.collectone.v2.web.ui.common.TextFilePickResult

/** Every item in the survey editor's left-hand navigation must select on a single click. */
@OptIn(ExperimentalTestApi::class)
class SurveyEditorClickTest {

  private fun withNavigation(block: ComposeUiTest.(SurveyEditorViewModel) -> Unit) =
    runDesktopComposeUiTest(width = 600, height = 1000) {
      val state = surveyEditorViewModel()
      setContent {
        MaterialTheme {
          val uiState by state.uiState.collectAsState()
          SurveyNavigation(
            uiState = uiState,
            actions = state,
            modifier = Modifier.width(280.dp).fillMaxHeight(),
          )
        }
      }
      block(state)
    }

  @Test
  fun clickWithSlightMouseMovementSelectsForm() = withNavigation { state ->
    val entry = state.ui.forms.first()
    // A real click rarely keeps the mouse perfectly still between press and release.
    onAllNodesWithText(entry.form.title, useUnmergedTree = true).onFirst().performMouseInput {
      moveTo(center)
      press()
      moveBy(Offset(2f, 1f))
      release()
    }
    waitForIdle()
    assertEquals(SurveyEditorSection.Form(entry.key), state.ui.section)
  }

  @Test
  fun clickWithSlightMouseMovementSelectsDataset() = withNavigation { state ->
    val dataset = (state.ui.mapLayers + state.ui.dataTables).first()
    onAllNodesWithText(dataset.displayName, useUnmergedTree = true).onFirst().performMouseInput {
      moveTo(center)
      press()
      moveBy(Offset(1f, 3f))
      release()
    }
    waitForIdle()
    assertEquals(SurveyEditorSection.Dataset(dataset.key), state.ui.section)
  }

  @Test
  fun dragPastSlopStillReorders() = withNavigation { state ->
    val first = state.ui.forms.first()
    if (state.ui.forms.size < 2) return@withNavigation
    onAllNodesWithText(first.form.title, useUnmergedTree = true).onFirst().performMouseInput {
      moveTo(center)
      press()
      repeat(10) { moveBy(Offset(0f, 12f)) }
      release()
    }
    waitForIdle()
    assertEquals(SurveyEditorSection.Details, state.ui.section)
    assertEquals(first.key, state.ui.forms[1].key)
  }

  @Test
  fun addFormButtonOpensDialogAndEmptyFormCreatesForm() = withNavigation { state ->
    val initialCount = state.ui.forms.size
    onNodeWithContentDescription("Add form").performClick()
    waitForIdle()

    onNodeWithText("Empty form").assertIsDisplayed()
    onNodeWithText("From template…").assertIsDisplayed()
    onNodeWithText("Import from file").assertIsDisplayed()

    onNodeWithText("Empty form").performClick()
    waitForIdle()

    assertEquals(initialCount + 1, state.ui.forms.size)
    assertEquals("New form", state.ui.selectedForm?.form?.title)
  }

  @Test
  fun addFormDialogImportsFromXmlAfterPreview() =
    runDesktopComposeUiTest(width = 800, height = 800) {
      val state = surveyEditorViewModel()
      val initialCount = state.ui.forms.size
      val xml =
        """
        <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
          <h:head>
            <h:title>Imported tree survey</h:title>
            <model>
              <instance><data id="imported_tree_survey"><species/></data></instance>
              <bind nodeset="/data/species" type="string"/>
            </model>
          </h:head>
          <h:body>
            <input ref="/data/species"><label>Tree species</label></input>
          </h:body>
        </h:html>
        """
          .trimIndent()
      setContent {
        MaterialTheme {
          AddFormDialog(
            actions = state,
            onDismiss = {},
            pickTextFile = { _, onResult ->
              onResult(TextFilePickResult.Picked("tree_survey.xml", xml))
            },
          )
        }
      }

      onNodeWithText("From template…").assertIsDisplayed()
      onNodeWithText("Import from file").performClick()
      waitForIdle()

      onNodeWithText("Import tree_survey.xml").assertIsDisplayed()
      onNodeWithText("Imported tree survey").assertIsDisplayed()
      onNodeWithText("Import").performClick()
      waitForIdle()

      assertEquals(initialCount + 1, state.ui.forms.size)
      assertEquals("Imported tree survey", state.ui.selectedForm?.form?.title)
    }

  @Test
  fun generalAccessSlidersUpdateViewAndCollectPermissionsAndHideFromDirectory() =
    runDesktopComposeUiTest(width = 1000, height = 1600) {
      val state = seededSurveyEditorViewModel(seededStore())
      state.select(SurveyEditorSection.Sharing)
      setContent {
        MaterialTheme {
          val uiState by state.uiState.collectAsState()
          SharingContent(uiState = uiState, actions = state)
        }
      }
      waitForIdle()

      // Column headers should be displayed with constant-length labels.
      onNodeWithText("Contributors & organizers").assertIsDisplayed()
      onNodeWithText("Invited people").assertIsDisplayed()
      onNodeWithText("Anyone in your organization").assertIsDisplayed()
      onNodeWithText("Anyone").assertIsDisplayed()

      // Click "Anyone in your organization" on "Who can collect data".
      onNodeWithContentDescription("Who can collect data: Anyone in your organization")
        .performClick()
      waitForIdle()
      assertEquals(SharingPolicy.ORGANIZATION, state.ui.sharing.policy)
      // Hints mention the organization name (Kenya Forest Service) and contributor identity rules.
      onNodeWithText(
          "Anyone in Kenya Forest Service can view all map features, answers, photos, and other " +
            "uploaded media. Only survey organizers can view contributor identities."
        )
        .assertIsDisplayed()
      onNodeWithText(
          "Members of Kenya Forest Service can open this survey and collect data. " +
            "Managers of Kenya Forest Service can also edit the survey."
        )
        .assertIsDisplayed()

      // Click "Contributors & organizers" on "Who can view data" to restrict viewing to own data.
      onNodeWithContentDescription("Who can view data: Contributors & organizers").performClick()
      waitForIdle()
      assertEquals(PeerDataVisibility.OWN_ONLY, state.ui.sharing.peerDataVisibility)
      assertEquals(SharingPolicy.ORGANIZATION, state.ui.sharing.policy)

      // Click "Anyone" on "Who can collect data" -> opens to PUBLIC with private submissions.
      onNodeWithContentDescription("Who can collect data: Anyone").performClick()
      waitForIdle()
      assertEquals(SharingPolicy.PUBLIC, state.ui.sharing.policy)
      assertEquals(PeerDataVisibility.OWN_ONLY, state.ui.sharing.peerDataVisibility)
      assertTrue(state.ui.sharing.isPrivateSubmissions)
      assertFalse(state.ui.sharing.isPublicData)

      // Change "Who can find this survey" -> switches policy to ANYONE_WITH_LINK.
      onNodeWithText("Who can find this survey").assertIsDisplayed()
      onNodeWithText("Public (listed in the survey directory)").performClick()
      waitForIdle()
      onNodeWithText("Unlisted (only people with a link or QR code)").performClick()
      waitForIdle()
      assertEquals(SharingPolicy.ANYONE_WITH_LINK, state.ui.sharing.policy)

      // Click "Anyone" on "Who can view data" -> makes data public while keeping ANYONE_WITH_LINK.
      onNodeWithContentDescription("Who can view data: Anyone").performClick()
      waitForIdle()
      assertEquals(SharingPolicy.ANYONE_WITH_LINK, state.ui.sharing.policy)
      assertEquals(PeerDataVisibility.ALL, state.ui.sharing.peerDataVisibility)
      assertTrue(state.ui.sharing.isPublicData)
    }

  @Test
  fun closedSurveyEditorPage_showsReadOnlyBanner_hidesAddControls_andDisablesInputs() =
    runDesktopComposeUiTest(width = 1600, height = 1000) {
      val lifecycle = SurveyLifecycleFixture()
      runNow {
        lifecycle.surveyRepository.setActiveSurveyId(SampleSurveysData.CLOSED_SURVEY_ID)
      }
      val state =
        SurveyEditorViewModel(
          surveyRepository = lifecycle.surveyRepository,
          surveyEditorRepository = SurveyEditorRepositoryImpl(lifecycle.store),
          organizationRepository = OrganizationRepositoryImpl(lifecycle.store),
          authRepository = lifecycle.authRepository,
          placeRepository = PlaceRepositoryImpl(lifecycle.store),
          surveyLifecycle = lifecycle.lifecycle,
          impactEventRepository = lifecycle.impactEventRepository,
        )
      setContent {
        MaterialTheme {
          val uiState by state.uiState.collectAsState()
          SurveyEditorPage(
            uiState = uiState,
            actions = state,
            isDarkTheme = false,
            header = { nav, ctx, actions ->
              nav()
              ctx()
              actions()
            },
          )
        }
      }
      waitForIdle()

      // Read-only banner is displayed and "+" buttons in left navigation are hidden.
      onNodeWithText("This survey is closed", substring = true).assertIsDisplayed()
      onAllNodesWithContentDescription("Add form").assertCountEquals(0)
      onAllNodesWithContentDescription("Add map layer").assertCountEquals(0)
      onAllNodesWithContentDescription("Add data table").assertCountEquals(0)

      // Survey title and description text fields on the Details pane are disabled.
      onNodeWithText("Survey title").assertIsNotEnabled()
      onNodeWithText("Description").assertIsNotEnabled()

      // Navigating to a form remains allowed, while add-question affordances are hidden and
      // the form title text field is disabled.
      val firstForm = state.ui.forms.first()
      onAllNodesWithText(firstForm.form.title, useUnmergedTree = true).onFirst().performClick()
      waitForIdle()
      assertEquals(SurveyEditorSection.Form(firstForm.key), state.ui.section)
      onAllNodesWithContentDescription("Add question").assertCountEquals(0)
      onAllNodesWithContentDescription("Add question here").assertCountEquals(0)
      state.formEditor(firstForm.key).selectForm()
      waitForIdle()
      onNodeWithText("Form title").assertIsNotEnabled()
    }

  @Test
  fun backendErrorBanner_displaysErrorMessage_andInvokesDismissAndRetry() =
    runDesktopComposeUiTest(width = 900, height = 400) {
      var dismissed = false
      var retried = false
      val error =
        BackendOperationError(
          key = "retrying:m1:err",
          error = DomainError.PermissionDenied,
          canRetry = true,
        )
      setContent {
        MaterialTheme {
          BackendErrorBanner(
            error = error,
            onRetry = { retried = true },
            onDismiss = { dismissed = true },
          )
        }
      }
      waitForIdle()

      onNodeWithTag(BACKEND_ERROR_BANNER_TAG).assertIsDisplayed()
      onNodeWithText("Couldn't save changes").assertIsDisplayed()
      onNodeWithText("You don't have permission to do this.").assertIsDisplayed()

      onNodeWithText("Retry").performClick()
      waitForIdle()
      assertTrue(retried)

      onNodeWithText("Close").performClick()
      waitForIdle()
      assertTrue(dismissed)
    }
}
