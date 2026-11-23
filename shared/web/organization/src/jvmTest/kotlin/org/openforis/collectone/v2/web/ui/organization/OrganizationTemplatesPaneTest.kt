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
package org.openforis.collectone.v2.web.ui.organization

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.AuthRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.web.domain.usecase.CreateOrganizationUseCase
import org.openforis.collectone.v2.web.domain.usecase.InviteOrganizationMemberUseCase
import org.openforis.collectone.v2.web.domain.usecase.ManageImagerySourcesUseCase
import org.openforis.collectone.v2.web.domain.usecase.ManageLibraryUseCase
import org.openforis.collectone.v2.web.domain.usecase.SearchConceptsUseCase

/**
 * The organization page's Templates tab opens **New template** and **Edit template** in the shared
 * Form editor rather than a separate template question dialog.
 */
@OptIn(ExperimentalTestApi::class)
class OrganizationTemplatesPaneTest {
  private val scope = CoroutineScope(Dispatchers.Unconfined + Job())

  @AfterTest fun tearDown() = scope.cancel()

  private fun createViewModel(): OrganizationViewModel {
    val store = seededStore()
    val organizationRepository = OrganizationRepositoryImpl(store)
    val libraryRepository = LibraryRepositoryImpl(store)
    val authRepository = AuthRepositoryImpl()
    scope.launch { authRepository.signInWithGoogle() }
    return OrganizationViewModel(
      organizationRepository = organizationRepository,
      surveyRepository = SurveyRepositoryImpl(store),
      authRepository = authRepository,
      createOrganizationUseCase = CreateOrganizationUseCase(organizationRepository),
      inviteMemberUseCase = InviteOrganizationMemberUseCase(organizationRepository),
      manageImagerySourcesUseCase = ManageImagerySourcesUseCase(organizationRepository),
      libraryRepository = libraryRepository,
      manageLibraryUseCase = ManageLibraryUseCase(libraryRepository),
      resolveLibraryUseCase = ResolveLibraryUseCase(),
      searchConceptsUseCase = SearchConceptsUseCase(),
    )
  }

  @Test
  fun newAndEditTemplate_openInSharedFormEditor_confirmDiscardAndSave() =
    runDesktopComposeUiTest(width = 1600, height = 1000) {
      val kfsId = SampleOrganizationsData.KENYA_FOREST_SERVICE
      val vm = createViewModel()
      vm.openOrganization(kfsId)
      vm.createPurposePack(kfsId, title = "Canopy Monitoring", icon = "park")

      setContent {
        MaterialTheme {
          val state by vm.uiState.collectAsState()
          val org = state.myOrganizations.first { it.id == kfsId }
          TemplatesPane(library = state.library, actions = vm, organization = org)
        }
      }

      // Click "New template" -> opens the shared Form editor in template mode.
      onNodeWithText("New template").performClick()
      waitForIdle()
      onNodeWithTag(TEMPLATE_EDITOR_TAG).assertIsDisplayed()
      onNodeWithText("Library template", substring = true).assertIsDisplayed()

      // Enter a template title, description, and check a Purpose Pack.
      onNodeWithText("Template title").performTextReplacement("Canopy Audit")
      onNodeWithText("Description (English)").performTextReplacement("Seasonal crown check")
      onNodeWithText("Canopy Monitoring").performClick()
      waitForIdle()

      // Cancelling with unsaved edits asks for confirmation first; Keep editing stays open.
      onAllNodesWithText("Cancel").onFirst().performClick()
      waitForIdle()
      onNodeWithText("Discard changes to this template?").assertIsDisplayed()
      onNodeWithText("Keep editing").performClick()
      waitForIdle()
      onNodeWithTag(TEMPLATE_EDITOR_TAG).assertIsDisplayed()

      // Save persists the new template and associates it with the Purpose Pack.
      onNodeWithText("Save").performClick()
      waitForIdle()
      onAllNodesWithTag(TEMPLATE_EDITOR_TAG).assertCountEquals(0)

      val createdId = "org.$kfsId.canopy_audit"
      val created = vm.uiState.value.library.formTemplates.first { it.entry.id == createdId }.entry
      assertEquals("Canopy Audit", created.title.text)
      assertEquals("Seasonal crown check", created.description.text)
      assertTrue(
        vm.uiState.value.library.purposePacks
          .first { it.entry.id == "org.$kfsId.canopy_monitoring" }
          .entry
          .formTemplateIds
          .contains(createdId)
      )

      // Click "Edit template" on an existing row -> opens in the shared Form editor.
      onAllNodesWithText("Edit template").onFirst().performClick()
      waitForIdle()
      onNodeWithTag(TEMPLATE_EDITOR_TAG).assertIsDisplayed()
      onAllNodesWithText("Cancel").onFirst().performClick()
      waitForIdle()
      onAllNodesWithTag(TEMPLATE_EDITOR_TAG).assertCountEquals(0)
    }
}
