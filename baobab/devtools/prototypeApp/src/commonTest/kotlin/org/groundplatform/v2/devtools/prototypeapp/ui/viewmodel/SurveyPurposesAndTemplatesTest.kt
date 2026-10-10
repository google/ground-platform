/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LibraryRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocalStoreTransactionRunner
import org.groundplatform.v2.devtools.prototypeapp.data.repository.MutationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.CreateSurveyUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ManageLibraryUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SyncMutationsUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.formeditor.canSaveAsTemplate
import org.groundplatform.v2.devtools.prototypeapp.ui.formeditor.saveAsTemplateUnavailableReason
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorSection

/** Survey purposes, adding Forms from templates, and saving Forms as templates. */
class SurveyPurposesAndTemplatesTest {
  private val kfs = PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE
  private val mekong = PrototypeFakeOrganizationsData.MEKONG_MANGROVE_ALLIANCE
  private val store = seededStore()
  private val libraries = LibraryRepositoryImpl(store)
  private val editor = seededSurveyEditorViewModel(store, testScope(), libraries)

  // --- Survey editor ---

  @Test
  fun seededSurvey_hasPurposes_whoseConceptsBoostSuggestions() {
    assertEquals(listOf("eudr_due_diligence"), editor.ui.details.purposeIds)
    val boosted = editor.ui.library.purposeConceptIds
    assertTrue("eudr.commodity" in boosted)
    assertTrue("core.area_ha" in boosted)
    val formEditor = editor.formEditor(editor.ui.forms.first().key)
    assertEquals(boosted, formEditor.ui.library.purposeConceptIds)
  }

  @Test
  fun togglePurposeAndProgram_editTheDetails_andPublishToTheSurveyConfig() {
    editor.togglePurpose("ferm_restoration")
    assertEquals(listOf("eudr_due_diligence", "ferm_restoration"), editor.ui.details.purposeIds)
    editor.togglePurpose("eudr_due_diligence")
    assertEquals(listOf("ferm_restoration"), editor.ui.details.purposeIds)
    assertFalse("eudr.commodity" in editor.ui.library.purposeConceptIds)
    editor.toggleProgram("ferm")
    editor.toggleProgram("eudr")
    assertEquals(listOf("ferm"), editor.ui.details.programIds)

    editor.publish()
    val config = runNow { store.transaction { surveyConfig(editor.ui.surveyId) } }
    assertEquals(listOf("ferm_restoration"), config?.purposeIds)
    assertEquals(listOf("ferm"), config?.programIds)
  }

  @Test
  fun addFormFromTemplate_copiesTheTemplate_andOpensIt() {
    val before = editor.ui.forms.size
    val key = assertNotNull(editor.addFormFromTemplate("org.$kfs.coop_member_plot_audit"))
    assertEquals(before + 1, editor.ui.forms.size)
    assertEquals(SurveyEditorSection.Form(key), editor.ui.section)
    val form = assertNotNull(editor.ui.form(key)).form
    assertEquals("Coop member plot audit", form.title)
    assertEquals(
      ConceptLink("org.$kfs.shade_tree_count"),
      form.questions.first { it.name == "shade_tree_count" }.conceptLink,
    )
    assertNotNull(editor.ui.datasets.firstOrNull { it.linkedFormKey == key })
    assertNull(editor.addFormFromTemplate("not_a_template"))
  }

  @Test
  fun hiddenGlobalTemplates_leaveTheTemplatePicker() {
    assertNotNull(editor.ui.library.library.formTemplate("eudr_plot_registration"))
    runNow {
      ManageLibraryUseCase(libraries).setGlobalEntryHidden(kfs, "eudr_plot_registration", true)
    }
    assertNull(editor.ui.library.library.formTemplate("eudr_plot_registration"))
    assertNull(editor.addFormFromTemplate("eudr_plot_registration"))
  }

  @Test
  fun saveAsTemplate_managersSaveToTheirOrganization_withUniqueIds() {
    val formEditor = editor.formEditor(editor.ui.forms.first().key)
    assertTrue(formEditor.ui.canSaveAsTemplate)
    assertNull(formEditor.ui.saveAsTemplateUnavailableReason)
    assertNotNull(formEditor.saveAsTemplate(" ", ""))
    assertNull(formEditor.saveAsTemplate("Kenya plot visit", "Yearly visit"))
    assertNull(formEditor.saveAsTemplate("Kenya plot visit", ""))

    val library = runNow { libraries.getLibrary(kfs) }
    val saved = assertNotNull(library.formTemplate("org.$kfs.kenya_plot_visit"))
    assertNotNull(library.formTemplate("org.$kfs.kenya_plot_visit_2"))
    assertEquals(
      editor.ui.forms.first().form.questions.map { it.name },
      saved.form.questions.map { it.name },
    )
    // The saved template is offered in this survey's template picker.
    assertNotNull(editor.ui.library.library.formTemplate(saved.id))
  }

  @Test
  fun saveAsTemplate_toTheGlobalLibrary_needsAllUsersManagers() {
    // The signed-in demo user manages "All users".
    val formEditor = editor.formEditor(editor.ui.forms.first().key)
    assertTrue(formEditor.ui.library.canSaveToGlobalLibrary)
    assertNull(formEditor.saveAsTemplate("Kenya plot visit", "", toGlobal = true))
    val global = runNow { libraries.getLibrary(Organization.ALL_USERS_ID) }
    val saved = assertNotNull(global.formTemplate("kenya_plot_visit"))
    assertTrue(saved.conceptIds.all { !it.startsWith("org.") })
  }

  @Test
  fun saveAsTemplate_isRefusedForMembersAndPersonalSurveys() {
    editor.setOrganization(mekong)
    val member = editor.formEditor(editor.ui.forms.first().key)
    assertFalse(member.ui.library.canAddToDictionary)
    assertNotNull(member.saveAsTemplate("Mangrove visit", ""))
    assertTrue(runNow { libraries.getLibrary(mekong) }.formTemplates.isEmpty())

    editor.setOrganization(null)
    val personal = editor.formEditor(editor.ui.forms.first().key)
    assertNotNull(personal.saveAsTemplate("Mangrove visit", ""))
    // Without All users management they couldn't save anywhere; here only the global option is
    // left.
    assertEquals(
      "Move this survey into an organization to save its forms as templates.",
      personal.ui
        .copy(library = personal.ui.library.copy(canSaveToGlobalLibrary = false))
        .saveAsTemplateUnavailableReason,
    )
  }

  // --- Dashboard ---

  private class Dashboard {
    val store = seededStore()
    val surveys = SurveyRepositoryImpl(store)
    val editorRepository = SurveyEditorRepositoryImpl(store)
    val libraries = LibraryRepositoryImpl(store)
    val mutations = MutationRepositoryImpl(store)
    val runner = LocalStoreTransactionRunner(store)
    val viewModel =
      DashboardViewModel(
        surveyRepository = surveys,
        organizationRepository = OrganizationRepositoryImpl(store),
        authRepository = AuthRepositoryImpl(),
        mutationRepository = mutations,
        createSurveyUseCase =
          CreateSurveyUseCase(surveys, editorRepository, runner, libraryRepository = libraries),
        syncMutationsUseCase = SyncMutationsUseCase(mutations, surveys, runner),
        scope = CoroutineScope(Dispatchers.Unconfined + Job()),
        libraryRepository = libraries,
      )
  }

  @Test
  fun dashboard_offersEachOrganizationsPurposes_andCreatesSeededSurveys() {
    val d = Dashboard()
    val state = d.viewModel.uiState.value
    val kenyaPacks = state.surveyLibrary(kfs).pickablePurposePacks.map { it.id }
    assertEquals("org.$kfs.coop_certification_audit", kenyaPacks.first())
    assertTrue(state.surveyLibrary(null).pickablePurposePacks.all { it.isGlobal })
    assertTrue(
      state.surveyLibrary(mekong).pickablePurposePacks.none { it.id.startsWith("org.$kfs") }
    )

    val id =
      d.viewModel.createSurvey(
        "EUDR plots 2026",
        kfs,
        purposeIds = listOf("eudr_due_diligence"),
        programIds = listOf("eudr"),
      )
    val draft = runNow { d.editorRepository.getDraft(id) }
    assertEquals(listOf("eudr_due_diligence"), draft.details.purposeIds)
    assertEquals(listOf("eudr"), draft.details.programIds)
    assertEquals(listOf("EUDR plot registration"), draft.forms.map { it.form.title })
    assertTrue(draft.forms.single().form.questions.count { it.conceptLink != null } >= 5)
  }
}
