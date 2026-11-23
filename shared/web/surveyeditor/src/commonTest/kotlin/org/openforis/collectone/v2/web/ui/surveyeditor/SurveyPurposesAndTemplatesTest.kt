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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.AuthRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LocalStoreTransactionRunner
import org.openforis.collectone.v2.app.data.repository.MutationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.domain.model.CachedProfile
import org.openforis.collectone.v2.app.domain.model.ConceptLink
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SurveyFormTemplates
import org.openforis.collectone.v2.app.domain.model.editor.TemplateOrigin
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository
import org.openforis.collectone.v2.app.domain.usecase.CreateSurveyUseCase
import org.openforis.collectone.v2.app.domain.usecase.ExportSurveyDataUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.app.domain.usecase.SyncMutationsUseCase
import org.openforis.collectone.v2.app.ui.dashboard.DashboardViewModel
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.usecase.ManageLibraryUseCase
import org.openforis.collectone.v2.web.ui.formeditor.SaveAsTemplateUnavailableReason
import org.openforis.collectone.v2.web.ui.formeditor.canSaveAsTemplate
import org.openforis.collectone.v2.web.ui.formeditor.saveAsTemplateUnavailableReason

/** Survey purposes, adding Forms from templates, and saving Forms as templates. */
class SurveyPurposesAndTemplatesTest {
  private val kfs = SampleOrganizationsData.KENYA_FOREST_SERVICE
  private val mekong = SampleOrganizationsData.MEKONG_MANGROVE_ALLIANCE
  private val store = seededStore()
  private val libraries = LibraryRepositoryImpl(store)
  private val editor = seededSurveyEditorViewModel(store, libraries)

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
    // A survey can have several purposes in the editor.
    editor.togglePurpose("ferm_restoration")
    assertEquals(listOf("eudr_due_diligence", "ferm_restoration"), editor.ui.details.purposeIds)
    assertTrue("eudr.commodity" in editor.ui.library.purposeConceptIds)
    editor.togglePurpose("eudr_due_diligence")
    assertEquals(listOf("ferm_restoration"), editor.ui.details.purposeIds)
    assertFalse("eudr.commodity" in editor.ui.library.purposeConceptIds)
    editor.updateOtherPurpose("Watershed monitoring")
    assertEquals("Watershed monitoring", editor.ui.details.otherPurpose)
    editor.toggleProgram("ferm")
    editor.toggleProgram("eudr")
    assertEquals(listOf("ferm"), editor.ui.details.programIds)

    editor.publish()
    val config = runNow { store.transaction { surveyConfig(editor.ui.surveyId) } }
    assertEquals(listOf("ferm_restoration"), config?.purposeIds)
    assertEquals(listOf("ferm"), config?.programIds)
  }

  @Test
  fun addFormsForPurpose_addsTheTemplatesForms_purposeTemplateAndPrograms() {
    val before = editor.ui.forms.size
    val key =
      assertNotNull(
        editor.addFormsForPurpose(
          "eudr_due_diligence",
          "uk_frc",
          listOf("uk_frc", "rainforest_alliance"),
        )
      )
    assertEquals(before + 1, editor.ui.forms.size)
    assertEquals(SurveyEditorSection.Form(key), editor.ui.section)
    assertEquals("UK forest risk commodity legality check", editor.ui.form(key)?.form?.title)
    assertTrue("rainforest_alliance" in editor.ui.details.programIds)
    // The form records the template it came from, unmodified until the user edits it.
    assertEquals(
      TemplateOrigin("uk_frc_plot_registration", 1),
      editor.ui.form(key)?.form?.templateOrigin,
    )
    val firstQuestion = assertNotNull(editor.ui.form(key)).form.questions.first().key
    editor.formEditor(key).deleteQuestion(firstQuestion)
    assertEquals(true, editor.ui.form(key)?.form?.templateOrigin?.modified)

    // The same purpose can contribute forms from another of its templates.
    assertNotNull(editor.addFormsForPurpose("eudr_due_diligence", "eudr", listOf("eudr")))
    assertEquals(listOf("eudr_due_diligence"), editor.ui.details.purposeIds.distinct())
    assertEquals(before + 2, editor.ui.forms.size)

    // A second purpose is added alongside the first, with its default template's forms.
    assertNotNull(editor.addFormsForPurpose("forest_carbon_inventory", "", listOf("art_trees")))
    assertEquals(
      listOf("eudr_due_diligence", "forest_carbon_inventory"),
      editor.ui.details.purposeIds,
    )
    assertTrue("art_trees" in editor.ui.details.programIds)
    assertEquals(before + 3, editor.ui.forms.size)
    assertNull(editor.addFormsForPurpose("not_a_purpose", "", emptyList()))
  }

  @Test
  fun templateOrigin_isModifiedByQuestionEdits_notByEntityChanges() {
    val key = assertNotNull(editor.addFormsForPurpose("eudr_due_diligence", "uk_frc", emptyList()))
    val origin = { editor.ui.form(key)?.form?.templateOrigin }
    // Changing where the form saves (its Map layer) isn't a modification.
    editor.setFormSaveToMode(key, SaveToMode.UPDATE)
    editor.setFormSaveToMode(key, SaveToMode.CREATE)
    assertEquals(false, origin()?.modified)
    // Editing its questions is.
    editor
      .formEditor(key)
      .deleteQuestion(assertNotNull(editor.ui.form(key)).form.questions.last().key)
    assertEquals(true, origin()?.modified)
  }

  @Test
  fun formsCopiedFromAnEditedTemplate_recordItsNewVersion() {
    runNow {
      val useCase = ManageLibraryUseCase(libraries)
      val template =
        assertNotNull(
          libraries.getLibrary(Organization.ALL_USERS_ID).formTemplate("uk_frc_plot_registration")
        )
      useCase.saveTemplate(
        SurveyFormTemplates.edited(template, template.form.copy(questions = emptyList()))
      )
    }
    val key = assertNotNull(editor.addFormsForPurpose("eudr_due_diligence", "uk_frc", emptyList()))
    assertEquals(
      TemplateOrigin("uk_frc_plot_registration", 2),
      editor.ui.form(key)?.form?.templateOrigin,
    )
  }

  @Test
  fun templateOrigins_arePublishedWithTheSurveyConfig() {
    val key = assertNotNull(editor.addFormsForPurpose("eudr_due_diligence", "uk_frc", emptyList()))
    val formId = assertNotNull(editor.ui.form(key)).form.formId
    editor.publish()
    val config = runNow { store.transaction { surveyConfig(editor.ui.surveyId) } }
    val origin = assertNotNull(config?.formTemplateOrigins?.firstOrNull { it.form_id == formId })
    assertEquals("uk_frc_plot_registration", origin.template_id)
    assertEquals(1, origin.template_version)
    assertFalse(origin.modified)
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
  fun addFormFromTemplate_forANewDataset_opensTheDataset() {
    val key =
      assertNotNull(editor.addFormFromTemplate("eudr_plot_registration", openDataset = true))
    val layer = assertNotNull(editor.ui.datasets.firstOrNull { it.linkedFormKey == key })
    assertEquals(SurveyEditorSection.Dataset(layer.key), editor.ui.section)
    assertEquals(DatasetKind.MAP_LAYER, layer.kind)
    assertEquals(GeometryType.POLYGON, layer.geometryType)
    // The production place polygon is each feature's geometry, not a property.
    assertTrue(layer.properties.none { it.name == "production_place" })
    assertEquals("producer_id", layer.labelProperty)
  }

  @Test
  fun templatesFeeding_splitsTheLibraryByTheDatasetTheirFormsAddTo() {
    val library = editor.ui.library
    val layers = library.templatesFeeding(DatasetKind.MAP_LAYER).map { it.id }
    val tables = library.templatesFeeding(DatasetKind.DATA_TABLE).map { it.id }
    assertTrue("eudr_plot_registration" in layers)
    assertTrue(tables.isNotEmpty())
    assertTrue(layers.intersect(tables.toSet()).isEmpty())
    assertEquals(
      library.library.pickableFormTemplates.map { it.id }.toSet(),
      (layers + tables).toSet(),
    )
    assertEquals(library.library.pickableFormTemplates, library.templatesFeeding(null))
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
    // Platform admins ("All users" Managers) may edit every organization's library, so hand that
    // role to someone else first: the signed-in user is then only a Member of the Mekong alliance.
    revokeSignedInUsersPlatformAdmin()
    editor.setOrganization(mekong)
    val member = editor.formEditor(editor.ui.forms.first().key)
    assertFalse(member.ui.library.canAddToDictionary)
    assertNotNull(member.saveAsTemplate("Mangrove visit", ""))
    assertTrue(runNow { libraries.getLibrary(mekong) }.formTemplates.isEmpty())

    editor.setOrganization(null)
    val personal = editor.formEditor(editor.ui.forms.first().key)
    assertNotNull(personal.saveAsTemplate("Mangrove visit", ""))
    // Without "All users" management they can't save anywhere.
    assertEquals(
      SaveAsTemplateUnavailableReason.NoOrganization,
      personal.ui.saveAsTemplateUnavailableReason,
    )
  }

  /** Makes another person the only "All users" Manager, so the signed-in user isn't an admin. */
  private fun revokeSignedInUsersPlatformAdmin() {
    val organizations = OrganizationRepositoryImpl(store)
    val allUsers = Organization.ALL_USERS_ID
    val otherAdmin = "other.admin@example.org"
    runNow {
      assertNotNull(
        organizations.inviteMember(allUsers, otherAdmin, OrganizationRole.MANAGER, token = "t")
      )
      assertNotNull(
        organizations.acceptInvite(allUsers, otherAdmin, "uid-other-admin", CachedProfile("Admin"))
      )
      assertNotNull(organizations.removeMember(allUsers, SampleOrganizationsData.SIGNED_IN_EMAIL))
    }
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
        syncMutationsUseCase =
          SyncMutationsUseCase(mutations, surveys, runner, ConnectivityRepository()),
        libraryRepository = libraries,
        resolveLibraryUseCase = ResolveLibraryUseCase(),
        exportSurveyData = ExportSurveyDataUseCase(),
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
    assertEquals(listOf("Deforestation compliance plot check"), draft.forms.map { it.form.title })
    assertTrue(draft.forms.single().form.questions.count { it.conceptLink != null } >= 5)
  }
}
