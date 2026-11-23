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
package org.openforis.collectone.v2.web.ui.wizard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.SampleDeviceStorage
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.data.seed.SampleSurveyEditorData
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.TemplateOrigin
import org.openforis.collectone.v2.app.resources.Res as AppRes
import org.openforis.collectone.v2.app.resources.domain_quota_max_surveys_per_owner
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.model.ImpactMessage
import org.openforis.collectone.v2.web.domain.model.ImpactText
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod
import org.openforis.collectone.v2.web.navigation.CreateSurveyRoute
import org.openforis.collectone.v2.web.navigation.NavigationRecorder
import org.openforis.collectone.v2.web.navigation.NavigationRequest
import org.openforis.collectone.v2.web.navigation.SurveyDashboardRoute
import org.openforis.collectone.v2.web.navigation.SurveyEditorRoute
import org.openforis.collectone.v2.web.navigation.SurveysRoute
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_save_changes
import org.openforis.collectone.v2.web.resources.wizard_step_purpose
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.createSurveyWizard
import org.openforis.collectone.v2.web.ui.surveyEditor
import org.openforis.collectone.v2.web.ui.surveyeditor.SampleGenerationOutcome
import org.openforis.collectone.v2.web.ui.surveyeditor.care.CareGovernanceRubric
import org.openforis.collectone.v2.web.ui.surveyeditor.ui
import org.openforis.collectone.v2.web.ui.text.labelRes

class CreateSurveyWizardViewModelTest {

  @Test
  fun startWizard_startsOnOverview() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)

    val ui = state.createSurveyWizard.uiState.value
    assertEquals(CreateSurveyWizardStep.OVERVIEW, ui.currentStep)
    assertEquals(SampleOrganizationsData.KENYA_FOREST_SERVICE, ui.organizationId)
    assertFalse(ui.canProceedFromCurrentStep)
    assertFalse(ui.isTitleInvalid)

    state.createSurveyWizard.nextStep()
    assertEquals(
      CreateSurveyWizardStep.OVERVIEW,
      state.createSurveyWizard.uiState.value.currentStep,
    )
    assertTrue(state.createSurveyWizard.uiState.value.isTitleInvalid)

    state.createSurveyWizard.updateTitle("Nyeri Agroforestry Monitoring")
    assertFalse(state.createSurveyWizard.uiState.value.isTitleInvalid)
    assertTrue(state.createSurveyWizard.uiState.value.canProceedFromCurrentStep)
  }

  @Test
  fun modeAndPurpose_sortsPurposePacksByCollectionModeAffinity_andSeedsTemplates() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Visual LULC Interpretation")
    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.MODE_AND_PURPOSE, wizard.uiState.value.currentStep)

    // Switch to Visual Classification mode: LULC Training Data & REDD+ packs move ahead of field
    // packs.
    wizard.selectCollectionMode(SurveyCollectionMode.VISUAL_CLASSIFICATION)
    val visualUi = wizard.uiState.value
    assertEquals(WizardMapFeatureSource.SAMPLE_PLOTS, visualUi.mapFeatureSource)
    val globalPackIds =
      visualUi.sortedPurposePacks
        .filterNot { visualUi.library.isOrganizationEntry(it.id) }
        .map { it.id }
    val lulcIndex = globalPackIds.indexOf("lulc_training_data")
    val eudrIndex = globalPackIds.indexOf("eudr_due_diligence")
    assertTrue(lulcIndex >= 0 && eudrIndex >= 0 && lulcIndex < eudrIndex)

    // Toggle LULC Training Data pack and advance through Step 3 -> Step 4 -> Step 5.
    wizard.togglePurposePack("lulc_training_data")
    wizard.nextStep() // SURVEY_AREA
    assertFalse(wizard.uiState.value.canSkipCurrentStep)
    assertEquals("Kenya", wizard.uiState.value.surveyArea?.name)
    assertTrue(wizard.uiState.value.canProceedFromCurrentStep)
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    assertTrue(wizard.uiState.value.canProceedFromCurrentStep)
    wizard.nextStep() // MAP_FEATURES

    val mapFeaturesUi = wizard.uiState.value
    assertEquals(CreateSurveyWizardStep.MAP_FEATURES, mapFeaturesUi.currentStep)
    val primaryLayer = assertNotNull(mapFeaturesUi.primaryMapLayer)
    assertTrue(primaryLayer.isGenerated)
    assertFalse(primaryLayer.fieldCreationEnabled)

    wizard.nextStep() // FORM_QUESTIONS
    val formUi = wizard.uiState.value
    assertEquals(CreateSurveyWizardStep.FORM_QUESTIONS, formUi.currentStep)
    val primaryForm = assertNotNull(formUi.primaryForm)
    assertEquals(SaveToMode.UPDATE, primaryForm.form.saveTo.mode)
    val formEditorState = state.surveyEditor.formEditor(primaryForm.key).uiState.value
    assertEquals(FormPreviewTarget.WEB, formEditorState.previewTarget)
    assertTrue(formUi.impactReadiness.linkedConceptCount > 0)
  }

  @Test
  fun purposeSelection_isMultiSelect_andDrivesMapFeatureSourceAndPrimaryLayerDefaults() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Purpose-Driven Survey")
    wizard.nextStep() // PURPOSE
    assertEquals(Res.string.wizard_step_purpose, CreateSurveyWizardStep.MODE_AND_PURPOSE.labelRes)

    // 1. Selecting LULC Training Data sets SAMPLE_PLOTS automatically.
    wizard.togglePurposePack("lulc_training_data")
    assertEquals(listOf("lulc_training_data"), wizard.uiState.value.purposeIds)
    assertEquals(WizardMapFeatureSource.SAMPLE_PLOTS, wizard.uiState.value.mapFeatureSource)

    // 2. Selecting Producer Registration replaces LULC (single-select).
    wizard.togglePurposePack("producer_registration")
    assertEquals(
      listOf("producer_registration"),
      wizard.uiState.value.purposeIds,
    )
    assertEquals(WizardMapFeatureSource.FIELD_FREEFORM, wizard.uiState.value.mapFeatureSource)

    // 3. Deselecting Producer Registration leaves no purpose.
    wizard.togglePurposePack("producer_registration")
    assertEquals(emptyList(), wizard.uiState.value.purposeIds)
    wizard.togglePurposePack("producer_registration")
    assertEquals(listOf("producer_registration"), wizard.uiState.value.purposeIds)

    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES

    val producerLayer = assertNotNull(wizard.uiState.value.primaryMapLayer)
    assertFalse(producerLayer.isGenerated)
    assertEquals(GeometryType.POINT, producerLayer.geometryType)
    val producerForm = assertNotNull(wizard.uiState.value.primaryForm)
    assertEquals("Producer & farm registration", producerForm.form.title)
    assertEquals(SaveToMode.CREATE, producerForm.form.saveTo.mode)
    assertEquals(1, state.surveyEditor.uiState.value.forms.size)

    // 4. Going back to Step 2 and swapping Producer Registration for FERM Restoration updates the
    // primary layer to POLYGON and replaces the single starting form.
    wizard.selectStep(CreateSurveyWizardStep.MODE_AND_PURPOSE)
    wizard.togglePurposePack("ferm_restoration")
    assertEquals(listOf("ferm_restoration"), wizard.uiState.value.purposeIds)
    wizard.selectStep(CreateSurveyWizardStep.MAP_FEATURES)

    val fermLayer = assertNotNull(wizard.uiState.value.primaryMapLayer)
    assertEquals(GeometryType.POLYGON, fermLayer.geometryType)
    val fermForm = assertNotNull(wizard.uiState.value.primaryForm)
    assertEquals("Ecosystem restoration site check", fermForm.form.title)
    assertEquals(1, state.surveyEditor.uiState.value.forms.size)
  }

  @Test
  fun purposeSelection_choosesTheDefaultStarterTemplate_whichPresetsPrograms() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("Single-Purpose Survey")
    wizard.nextStep() // MODE_AND_PURPOSE
    val details = { state.surveyEditor.uiState.value.draft.details }

    // EUDR offers starter templates; the first (EUDR) is chosen and presets its program.
    wizard.togglePurposePack("eudr_due_diligence")
    assertEquals(listOf("eudr_due_diligence"), wizard.uiState.value.purposeIds)
    assertEquals(
      listOf("eudr", "uk_frc"),
      assertNotNull(wizard.uiState.value.selectedPurposePack).effectiveStarterTemplates.map {
        it.id
      },
    )
    assertEquals("eudr", wizard.uiState.value.starterTemplateId)
    assertEquals(listOf("eudr"), details().programIds)
    assertEquals(
      listOf("eudr", "uk_frc", "rainforest_alliance", "fairtrade"),
      wizard.uiState.value.offeredProgramIds,
    )
    assertTrue(wizard.uiState.value.isPurposeValid)

    // A pack without starter templates has a single default one, presetting its only program.
    wizard.togglePurposePack("forest_carbon_inventory")
    assertEquals("", wizard.uiState.value.starterTemplateId)
    assertEquals(listOf("art_trees"), details().programIds)

    // Deselecting the purpose drops its programs and template.
    wizard.togglePurposePack("forest_carbon_inventory")
    assertEquals(emptyList(), wizard.uiState.value.purposeIds)
    assertNull(wizard.uiState.value.selectedPurposePack)
    assertEquals(emptyList(), details().programIds)
    assertEquals("", wizard.uiState.value.starterTemplateId)
  }

  @Test
  fun starterTemplate_picksFormsAndRules_andPresetsEditablePrograms() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("UK FRC Survey")
    wizard.nextStep() // MODE_AND_PURPOSE
    val details = { state.surveyEditor.uiState.value.draft.details }

    wizard.togglePurposePack("eudr_due_diligence")
    wizard.selectStarterTemplate("uk_frc")
    assertEquals("uk_frc", wizard.uiState.value.starterTemplateId)
    assertEquals(listOf("uk_frc"), details().programIds)
    // Program chips are freely editable and only tag the survey.
    wizard.toggleProgram("rainforest_alliance")
    assertEquals(listOf("uk_frc", "rainforest_alliance"), details().programIds)
    wizard.toggleProgram("uk_frc")
    assertEquals(listOf("rainforest_alliance"), details().programIds)
    assertEquals("uk_frc", wizard.uiState.value.starterTemplateId)
    // UK FRC has no polygon rule for plots over 4 ha.
    assertFalse(
      SurveyWizardRules.requiresEudrPolygonGuardrail(
        details().purposeIds,
        mapOf("eudr_due_diligence" to wizard.uiState.value.starterTemplateId),
      )
    )

    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES
    assertEquals(
      "UK forest risk commodity legality check",
      assertNotNull(wizard.uiState.value.primaryForm).form.title,
    )
    // UK FRC has its own (empty) export list rather than EUDR's.
    assertEquals(emptyList(), wizard.uiState.value.impactReadiness.exportPlans)

    // Switching back to EUDR (forms unedited) replaces the form without asking and presets EUDR.
    wizard.selectStep(CreateSurveyWizardStep.MODE_AND_PURPOSE)
    wizard.selectStarterTemplate("eudr")
    assertNull(wizard.uiState.value.pendingStarterTemplateId)
    assertEquals(listOf("eudr"), details().programIds)
    assertEquals(
      "Deforestation compliance plot check",
      assertNotNull(wizard.uiState.value.primaryForm).form.title,
    )
  }

  @Test
  fun starterTemplate_afterEditingTheForms_asksWhetherToReplaceThem() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("Edited Forms Survey")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.togglePurposePack("eudr_due_diligence")
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES
    val primaryForm = { assertNotNull(wizard.uiState.value.primaryForm) }
    val details = { state.surveyEditor.uiState.value.draft.details }

    // The user edits the seeded form.
    val formKey = primaryForm().key
    state.surveyEditor.formEditor(formKey).deleteQuestion(primaryForm().form.questions.last().key)
    val editedQuestions = primaryForm().form.questions
    assertEquals(true, primaryForm().form.templateOrigin?.modified)
    wizard.selectStep(CreateSurveyWizardStep.MODE_AND_PURPOSE)

    // Cancelling keeps everything as it was.
    wizard.selectStarterTemplate("uk_frc")
    assertEquals("uk_frc", wizard.uiState.value.pendingStarterTemplateId)
    assertEquals("eudr", wizard.uiState.value.starterTemplateId)
    wizard.resolveStarterTemplateChange(null)
    assertNull(wizard.uiState.value.pendingStarterTemplateId)
    assertEquals("eudr", wizard.uiState.value.starterTemplateId)
    assertEquals(listOf("eudr"), details().programIds)

    // Keeping the forms switches the template (and its programs) but leaves the edited form.
    wizard.selectStarterTemplate("uk_frc")
    wizard.resolveStarterTemplateChange(false)
    assertEquals("uk_frc", wizard.uiState.value.starterTemplateId)
    assertEquals(listOf("uk_frc"), details().programIds)
    wizard.selectStep(CreateSurveyWizardStep.MAP_FEATURES)
    assertEquals(editedQuestions, primaryForm().form.questions)

    // Replacing them seeds the new template's form.
    wizard.selectStep(CreateSurveyWizardStep.MODE_AND_PURPOSE)
    wizard.selectStarterTemplate("eudr")
    assertEquals("eudr", wizard.uiState.value.pendingStarterTemplateId)
    wizard.resolveStarterTemplateChange(true)
    assertEquals("Deforestation compliance plot check", primaryForm().form.title)
    assertTrue(primaryForm().form.questions.any { it.name == "deforestation_free" })
    // The new form records the template it came from, unmodified.
    assertEquals(
      TemplateOrigin("eudr_plot_registration", 1, modified = false),
      primaryForm().form.templateOrigin,
    )
  }

  @Test
  fun switchingPurpose_replacesStarterTemplatesAndCleansUpSecondaryForms() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("Coop and Restoration")
    wizard.nextStep() // MODE_AND_PURPOSE
    val coopPack =
      wizard.uiState.value.sortedPurposePacks.first {
        it.title.get("en") == "Coop annual certification audit"
      }
    wizard.togglePurposePack(coopPack.id)
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES

    val editor = state.surveyEditor.uiState.value
    assertEquals(2, editor.forms.size)
    val coopSecondaryForm = editor.forms[1]
    assertNotNull(editor.datasets.firstOrNull { it.linkedFormKey == coopSecondaryForm.key })

    // Switching purpose to FERM replaces the starter templates with FERM's single template
    wizard.selectStep(CreateSurveyWizardStep.MODE_AND_PURPOSE)
    wizard.togglePurposePack("ferm_restoration")
    wizard.selectStep(CreateSurveyWizardStep.MAP_FEATURES)
    val afterSwitch = state.surveyEditor.uiState.value
    assertEquals(1, afterSwitch.forms.size)
    assertEquals("Ecosystem restoration site check", afterSwitch.forms[0].form.title)
    assertTrue(afterSwitch.datasets.none { it.linkedFormKey == coopSecondaryForm.key })
  }

  @Test
  fun purposeWithSeveralTemplates_createsSeveralForms() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("Coop Audit")
    wizard.nextStep() // MODE_AND_PURPOSE
    val coopPack =
      wizard.uiState.value.sortedPurposePacks.first {
        it.title.get("en") == "Coop annual certification audit"
      }
    assertEquals(2, coopPack.formTemplateIds.size)

    wizard.togglePurposePack(coopPack.id)
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES

    val forms = state.surveyEditor.uiState.value.forms
    assertEquals(2, forms.size)
    assertEquals("Coop member plot audit", forms[1].form.title)
  }

  @Test
  fun changingPurposes_keepsFormsTheOrganizerAdded() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("Organizer Forms")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.togglePurposePack("producer_registration")
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES
    wizard.nextStep() // FORM_QUESTIONS

    val keysBefore = state.surveyEditor.uiState.value.forms.map { it.key }.toSet()
    state.surveyEditor.addForm()
    val addedKey = state.surveyEditor.uiState.value.forms.map { it.key }.first { it !in keysBefore }

    wizard.selectStep(CreateSurveyWizardStep.MODE_AND_PURPOSE)
    wizard.togglePurposePack("ferm_restoration")
    wizard.selectStep(CreateSurveyWizardStep.FORM_QUESTIONS)

    val forms = state.surveyEditor.uiState.value.forms
    assertEquals(2, forms.size)
    assertEquals("Ecosystem restoration site check", forms[0].form.title)
    assertEquals(addedKey, forms[1].key)
  }

  @Test
  fun eudrPurposePack_enforcesPolygonGuardrail_andComputesExportReadiness() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Kirinyaga EUDR Coffee Plots")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.selectCollectionMode(SurveyCollectionMode.FIELD_SURVEY)
    wizard.togglePurposePack("eudr_due_diligence")

    wizard.nextStep() // SURVEY_AREA (required in all collection modes; defaults to org country)
    assertFalse(wizard.uiState.value.canSkipCurrentStep)
    assertEquals("Kenya", wizard.uiState.value.surveyArea?.name)
    assertTrue(wizard.uiState.value.canProceedFromCurrentStep)
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES

    val layer = assertNotNull(wizard.uiState.value.primaryMapLayer)
    assertEquals(GeometryType.POLYGON, layer.geometryType)

    wizard.nextStep() // FORM_QUESTIONS
    val readiness = wizard.uiState.value.impactReadiness
    val eudrPlan = readiness.exportPlans.firstOrNull { it.profile.id == "eudr_geojson" }
    assertNotNull(eudrPlan)
    assertTrue(eudrPlan.fields.any { it.isMapped })
  }

  @Test
  fun careSovereigntyDefaults_applyForIplcOrganizationsOrTenureConcepts() {
    assertTrue(
      SurveyWizardRules.usesIplcCareDefaults(OrganizationType.IPLC_ORGANIZATION, emptySet())
    )
    assertTrue(
      SurveyWizardRules.usesIplcCareDefaults(
        OrganizationType.NGO,
        setOf("iplc.customary_rights"),
      )
    )
    assertFalse(SurveyWizardRules.usesIplcCareDefaults(OrganizationType.NGO, emptySet()))
    assertEquals(5, CareGovernanceRubric.DEFAULT_CRITERIA.size)

    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("Community Forest Tenure Survey")
    wizard.applyCareSovereigntyDefaults()

    val sharing = state.surveyEditor.uiState.value.sharing
    assertEquals(SharingPolicy.RESTRICTED, sharing.policy)
    assertEquals(PeerDataVisibility.OWN_ONLY, sharing.peerDataVisibility)
    assertTrue(wizard.uiState.value.collectorConsentRequired)
    assertEquals(
      SurveyWizardRules.DEFAULT_FPIC_CONSENT_TEXT,
      wizard.uiState.value.collectorConsentText,
    )
    assertTrue(wizard.uiState.value.impactReadiness.careSovereigntyApplied)
  }

  @Test
  fun startMethod_copyExisting_and_importXForms_populateDraft() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val navigation = NavigationRecorder(state, start = CreateSurveyRoute())
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Imported XForms Survey")
    wizard.selectStartMethod(WizardStartMethod.IMPORT_XFORMS)
    assertFalse(wizard.uiState.value.canProceedFromCurrentStep)

    val sampleXml =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Tree Health Form</h:title>
          <model>
            <instance>
              <data id="tree_health">
                <species/>
              </data>
            </instance>
            <bind nodeset="/data/species" type="string" required="true()"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/species">
            <label>Tree species</label>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent()

    wizard.importXFormsXml("tree_health.xml", sampleXml)
    assertTrue(wizard.uiState.value.canProceedFromCurrentStep)

    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.selectCollectionMode(SurveyCollectionMode.BOTH)
    wizard.togglePurposePack(SurveyWizardRules.OTHER_PURPOSE_ID)
    wizard.updateOtherPurpose("Custom tree health inventory")
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES
    wizard.nextStep() // FORM_QUESTIONS

    val primaryForm = assertNotNull(wizard.uiState.value.primaryForm)
    assertEquals("Tree Health Form", primaryForm.form.title)
    assertEquals(
      GeometryCapture.GPS_OR_MAP,
      SurveyCollectionMode.BOTH.defaultGeometryCapture,
    )

    // Clicking "Create survey" commits the draft and lands directly on the survey dashboard.
    wizard.finishWizard()
    assertIs<SurveyDashboardRoute>(navigation.currentRoute)
    assertEquals("Imported XForms Survey", state.surveyEditor.uiState.value.details.title)
    assertEquals(
      org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState.DRAFT,
      state.surveyEditor.uiState.value.lifecycleState,
    )
    assertTrue(state.surveyEditor.uiState.value.isDraft)
    assertEquals(
      Res.string.surveyeditor_save_changes,
      state.surveyEditor.uiState.value.saveActionLabel,
    )
  }

  @Test
  fun importCollectEarthIdm_and_openInSurveyEditor_supportsCepSchemasAndEditorEscapeHatch() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val navigation = NavigationRecorder(state, start = CreateSurveyRoute())
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Imported Collect Earth Survey")
    wizard.selectStartMethod(WizardStartMethod.IMPORT_XFORMS)

    val cepIdmXml =
      """
      <survey xmlns="http://www.openforis.org/idml/3.0">
        <project>Forest Assessment</project>
        <codeLists>
          <list name="land_use">
            <items>
              <item><code>forest</code><label>Forest</label></item>
              <item><code>cropland</code><label>Cropland</label></item>
            </items>
          </list>
        </codeLists>
        <schema>
          <entity name="plot">
            <code name="land_use_category" list="land_use" required="true">
              <label>Land use category</label>
            </code>
            <number name="canopy_cover_pct" type="real">
              <label>Canopy cover (%)</label>
            </number>
          </entity>
        </schema>
      </survey>
      """
        .trimIndent()

    wizard.importXFormsXml("forest_assessment.cep", cepIdmXml)
    assertEquals(null, wizard.uiState.value.importErrorMessage)
    assertTrue(wizard.uiState.value.canProceedFromCurrentStep)

    // Escape hatch: "Open in advanced survey editor" commits the imported form and shows the Survey
    // editor in place of the wizard.
    wizard.openInSurveyEditor()
    val request = assertIs<NavigationRequest.Replace>(navigation.requests.single())
    assertIs<SurveyEditorRoute>(request.route)

    val primaryForm = assertNotNull(wizard.uiState.value.primaryForm)
    assertEquals("Forest Assessment", primaryForm.form.title)
    assertTrue(
      primaryForm.form.questions.any {
        it.name == "land_use_category" && it.type == EditorQuestionType.SELECT_ONE
      }
    )
    assertTrue(
      primaryForm.form.questions.any {
        it.name == "canopy_cover_pct" && it.type == EditorQuestionType.NUMBER
      }
    )
  }

  @Test
  fun modeAndPurpose_requiresAtLeastOnePurpose_orNonBlankOtherPurpose() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Custom Biodiversity Baseline")
    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.MODE_AND_PURPOSE, wizard.uiState.value.currentStep)
    assertFalse(wizard.uiState.value.canProceedFromCurrentStep)
    assertFalse(wizard.uiState.value.isPurposeInvalid)
    val attemptsBefore = wizard.uiState.value.validationAttemptCount

    // Attempting to advance without selecting a purpose stays on Step 2 and flags validation error.
    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.MODE_AND_PURPOSE, wizard.uiState.value.currentStep)
    assertTrue(wizard.uiState.value.isPurposeInvalid)
    assertEquals(attemptsBefore + 1, wizard.uiState.value.validationAttemptCount)
    assertNotNull(wizard.uiState.value.currentStepValidationMessage)

    // Selecting "Other" requires non-blank free text, and clicking Next again increments
    // validationAttemptCount so the UI re-scrolls to the error.
    wizard.togglePurposePack(SurveyWizardRules.OTHER_PURPOSE_ID)
    assertFalse(wizard.uiState.value.canProceedFromCurrentStep)
    assertTrue(wizard.uiState.value.isPurposeInvalid)
    wizard.nextStep()
    assertEquals(attemptsBefore + 2, wizard.uiState.value.validationAttemptCount)

    wizard.updateOtherPurpose(" Amphibian habitat monitoring ")
    assertTrue(wizard.uiState.value.canProceedFromCurrentStep)
    assertFalse(wizard.uiState.value.isPurposeInvalid)
    assertEquals(null, wizard.uiState.value.currentStepValidationMessage)
    assertTrue(
      wizard.uiState.value.impactReadiness.selectedPackTitles.contains(
        ImpactText.of(ImpactMessage.OTHER_PURPOSE_NAMED, "Amphibian habitat monitoring")
      )
    )

    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.SURVEY_AREA, wizard.uiState.value.currentStep)
  }

  @Test
  fun defaultSurveyArea_usesOrganizationCountry_orNullWhenNoCountrySet() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    // Kenya Forest Service has countryCode = "KE", so default survey area is "Kenya" (not org
    // name).
    assertEquals("Kenya", wizard.uiState.value.surveyArea?.name)
    assertTrue(wizard.uiState.value.isSurveyAreaValid)

    // Switching to Mekong Mangrove Alliance (countryCode = "VN") updates default area to "Vietnam".
    wizard.selectOrganization(SampleOrganizationsData.MEKONG_MANGROVE_ALLIANCE)
    assertEquals("Vietnam", wizard.uiState.value.surveyArea?.name)

    // Switching to Open Foris Community (countryCode = null) clears default survey area to null.
    wizard.selectOrganization(SampleOrganizationsData.OPEN_FORIS_COMMUNITY)
    assertNull(wizard.uiState.value.surveyArea)
    assertFalse(wizard.uiState.value.isSurveyAreaValid)

    // Switching to personal survey (organizationId = null) also leaves survey area null.
    wizard.selectOrganization(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    assertEquals("Kenya", wizard.uiState.value.surveyArea?.name)
    wizard.selectOrganization(null)
    assertNull(wizard.uiState.value.surveyArea)

    // Once a custom survey area is chosen, changing the organization preserves the custom area.
    val customArea = SampleSurveyEditorData.kenyaSurveyArea()
    state.surveyEditor.setSurveyArea(customArea)
    wizard.selectOrganization(SampleOrganizationsData.MEKONG_MANGROVE_ALLIANCE)
    assertEquals(customArea, wizard.uiState.value.surveyArea)
  }

  @Test
  fun surveyArea_isRequiredInAllModes_andCannotBeSkipped() {
    assertTrue(SurveyCollectionMode.entries.all { it.surveyAreaRequired })

    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.OPEN_FORIS_COMMUNITY)
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Field Survey Requiring Area")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.selectCollectionMode(SurveyCollectionMode.FIELD_SURVEY)
    wizard.togglePurposePack("eudr_due_diligence")
    wizard.nextStep() // SURVEY_AREA

    assertEquals(CreateSurveyWizardStep.SURVEY_AREA, wizard.uiState.value.currentStep)
    assertNull(wizard.uiState.value.surveyArea)
    assertFalse(wizard.uiState.value.canSkipCurrentStep)
    assertFalse(wizard.uiState.value.canProceedFromCurrentStep)
    assertFalse(wizard.uiState.value.isSurveyAreaValid)
    assertFalse(wizard.uiState.value.isSurveyAreaInvalid)

    // Attempting to advance or skip without setting a survey area stays on Step 3 and flags error.
    wizard.skipStep()
    assertEquals(CreateSurveyWizardStep.SURVEY_AREA, wizard.uiState.value.currentStep)
    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.SURVEY_AREA, wizard.uiState.value.currentStep)
    assertTrue(wizard.uiState.value.isSurveyAreaInvalid)

    // Setting a survey area clears validation error and allows advancing to Step 4.
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    assertTrue(wizard.uiState.value.isSurveyAreaValid)
    assertFalse(wizard.uiState.value.isSurveyAreaInvalid)
    assertTrue(wizard.uiState.value.canProceedFromCurrentStep)

    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.MAP_FEATURES, wizard.uiState.value.currentStep)
  }

  @Test
  fun collectionMode_and_mapFeatureSource_exposeGroundAndCollectEarthOnlineTags() {
    assertEquals(listOf("Ground"), SurveyCollectionMode.FIELD_SURVEY.tags)
    assertEquals(listOf("Collect Earth Online"), SurveyCollectionMode.VISUAL_CLASSIFICATION.tags)
    assertEquals(listOf("Ground", "Collect Earth Online"), SurveyCollectionMode.BOTH.tags)

    assertEquals(listOf("Ground"), WizardMapFeatureSource.FIELD_FREEFORM.tags)
    assertEquals(listOf("Collect Earth Online"), WizardMapFeatureSource.SAMPLE_PLOTS.tags)
    assertTrue(WizardMapFeatureSource.IMPORT_FILE.tags.isEmpty())
  }

  @Test
  fun cancelWizard_discardsWorkingSurvey_andReturnsToPreviousPage() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    val initialSurveyIds = state.viewModels.dashboard.uiState.value.surveys.map { it.id }.toSet()
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val navigation = NavigationRecorder(state, start = CreateSurveyRoute())

    val workingId = state.createSurveyWizard.uiState.value.workingSurveyId
    assertTrue(workingId.isNotBlank())
    assertTrue(state.viewModels.dashboard.uiState.value.surveys.any { it.id == workingId })

    state.createSurveyWizard.updateTitle("Discarded Survey")
    state.createSurveyWizard.cancelWizard()

    assertFalse(state.viewModels.dashboard.uiState.value.surveys.any { it.id == workingId })
    assertEquals(
      initialSurveyIds,
      state.viewModels.dashboard.uiState.value.surveys.map { it.id }.toSet(),
    )
    assertEquals(NavigationRequest.Back(fallback = SurveysRoute), navigation.requests.single())
  }

  @Test
  fun samplePlots_deferredUntilPreviewClickedOrWizardSaved() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    val workingId = wizard.uiState.value.workingSurveyId

    wizard.updateTitle("Deferred Sample Plots Survey")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.selectCollectionMode(SurveyCollectionMode.VISUAL_CLASSIFICATION)
    wizard.togglePurposePack(SurveyWizardRules.OTHER_PURPOSE_ID)
    wizard.updateOtherPurpose("Canopy interpretation")
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES

    val layerBeforeFinish = assertNotNull(wizard.uiState.value.primaryMapLayer)
    assertTrue(layerBeforeFinish.isGenerated)
    assertTrue(layerBeforeFinish.rows.isEmpty())
    assertEquals(null, layerBeforeFinish.generator?.lastRun)

    wizard.nextStep() // FORM_QUESTIONS
    wizard.nextStep() // ACCESS_AND_SHARING
    wizard.finishWizard()

    val savedLayer =
      assertNotNull(
        state.surveyEditor.uiState.value.datasets.firstOrNull { it.key == layerBeforeFinish.key }
      )
    assertTrue(savedLayer.rows.isNotEmpty())
    assertNotNull(savedLayer.generator?.lastRun)
    val surveyCard =
      assertNotNull(
        state.viewModels.dashboard.uiState.value.surveys.firstOrNull { it.id == workingId }
      )
    assertEquals(savedLayer.rows.size, surveyCard.entityCount)
  }

  @Test
  fun importCollectEarthCepBundle_parsesQuestionsRelevancePlotsAndAllowsProjectSwitch() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    wizard.selectStartMethod(WizardStartMethod.IMPORT_XFORMS)

    val cepBundle =
      """
      <!--COLLECT_EARTH_FILE_NAME:umd_congo_deforestation.cep-->
      <!--COLLECT_EARTH_PROPERTIES
      distance_to_plot_boundaries=24
      csv=grid/plotsCongo_10000_1.csv
      -->
      <!--COLLECT_EARTH_PLOT_CSV
      "id","YCoordinate","XCoordinate","country","stratum"
      "10","8.574875","13.597625","Cameroon","1"
      "20","9.268375","13.228375","Cameroon","2"
      -->
      <survey xmlns="http://www.openforis.org/idml/3.0">
        <project>Collect Earth - Forest Disturbance of the Congo Basin</project>
        <description>Congo Basin study</description>
        <language>en</language>
        <language>fr</language>
        <codeLists>
          <list id="1016" name="years">
            <items>
              <item id="1017"><code>2000</code><label>2000</label></item>
              <item id="1018"><code>2001</code><label>2001</label></item>
            </items>
          </list>
        </codeLists>
        <schema>
          <entity id="1" name="plot">
            <text id="2" name="id" required="true" key="true" type="short"><label>Id</label></text>
            <date id="9" name="actively_saved_on" relevant="false()"><label>Saved on</label></date>
            <text id="1002" name="country" collectearth:fromcsv="true"><label>Country</label></text>
            <boolean id="1010" name="forest_loss" required="true"><label>Forest Loss</label></boolean>
            <code id="1015" name="forest_loss_year" relevant="forest_loss" required="true" list="years">
              <label>Forest Loss Year</label>
            </code>
          </entity>
        </schema>
      </survey>
      <!--COLLECT_EARTH_PROJECT_SEPARATOR-->
      <!--COLLECT_EARTH_FILE_NAME:umd_brazil_deforestation.cep-->
      <!--COLLECT_EARTH_PROPERTIES
      distance_to_plot_boundaries=24
      csv=grid/plotsBazil_10000_1.csv
      -->
      <!--COLLECT_EARTH_PLOT_CSV
      "id","YCoordinate","XCoordinate","state"
      "10","-13.963375","-51.975625","Mato Grosso"
      -->
      <survey xmlns="http://www.openforis.org/idml/3.0">
        <project>Forest Disturbance of the BLA</project>
        <description>Brazilian Legal Amazon study</description>
        <language>en</language>
        <language>es</language>
        <codeLists>
          <list id="1043" name="forest_cover">
            <items>
              <item id="1045"><code>prim</code><label>Primary forest</label></item>
            </items>
          </list>
        </codeLists>
        <schema>
          <entity id="1" name="plot">
            <boolean id="1002" name="forest_loss" required="true"><label>Forest Loss</label></boolean>
            <code id="1042" name="pre_disturbance_forest_cover" relevant="forest_loss" required="true" list="forest_cover">
              <label>Pre-Disturbance Forest Cover</label>
            </code>
          </entity>
        </schema>
      </survey>
      """
        .trimIndent()

    wizard.importXFormsXml("UMD_Brazil_Congo_surveys_and_data.cep.zip", cepBundle)

    val uiAfterImport = wizard.uiState.value
    assertEquals(2, uiAfterImport.importedCepProjects.size)
    assertEquals(
      "Collect Earth - Forest Disturbance of the Congo Basin",
      uiAfterImport.title,
    )
    assertEquals(2, uiAfterImport.importedXForms?.form?.questions?.size)
    val yearQuestion = uiAfterImport.importedXForms?.form?.questions?.get(1)
    assertNotNull(yearQuestion?.relevance)
    assertEquals("yes", yearQuestion?.relevance?.value)
    assertEquals(2, uiAfterImport.selectedCepProject?.plotDataset?.rows?.size)
    assertNotNull(uiAfterImport.surveyArea)

    // Switch to the second Collect Earth project in the archive.
    wizard.selectImportedCepProject("umd_brazil_deforestation.cep")
    assertEquals("Forest Disturbance of the BLA", wizard.uiState.value.title)
    assertEquals(1, wizard.uiState.value.selectedCepProject?.plotDataset?.rows?.size)
  }

  @Test
  fun finishWizard_whenSurveyCreationLimitExceeded_abortsAndShowsErrorMessage() {
    // Local data only, so the edits below aren't replaced by the sample service's view.
    val dataHolder = AppDataHolder(localStore = seededStore())
    runDirect {
      dataHolder.localStore.transaction {
        updateOrganization(Organization.ALL_USERS_ID) { org ->
          val currentLimits = org.quotaLimits ?: QuotaLimitsConfig.BASIC
          org.copy(
            quotaLimits = currentLimits.copy(maxSurveysPerOwner = 1),
            // The sample user is a platform admin, who isn't limited; make them a regular user.
            members = org.members.map { it.copy(role = OrganizationRole.MEMBER) },
          )
        }
      }
    }
    val state = WebAppState(dataHolder = dataHolder)
    state.createSurveyWizard.startWizard(null)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("Survey Exceeding Limits")
    wizard.finishWizard()
    val ui = wizard.uiState.value
    assertEquals(
      AppRes.string.domain_quota_max_surveys_per_owner,
      assertIs<UiText.Resource>(ui.importErrorMessage).resource,
    )
  }

  @Test
  fun finishWizard_platformAdminPastTheSurveyLimit_createsTheSurvey() {
    val dataHolder = sampleDataHolder()
    runDirect {
      dataHolder.organizationRepository.updateOrganization(Organization.ALL_USERS_ID) { org ->
        val currentLimits = org.quotaLimits ?: QuotaLimitsConfig.BASIC
        org.copy(quotaLimits = currentLimits.copy(maxSurveysPerOwner = 1))
      }
    }
    val state = WebAppState(dataHolder = dataHolder)
    state.createSurveyWizard.startWizard(null)
    val wizard = state.createSurveyWizard
    val surveyId = wizard.uiState.value.workingSurveyId
    wizard.updateTitle("Admin Survey Past The Limit")
    wizard.finishWizard()

    assertNull(wizard.uiState.value.importErrorMessage)
    val created = state.viewModels.dashboard.uiState.value.surveys.first { it.id == surveyId }
    assertEquals("Admin Survey Past The Limit", created.title)
  }

  @Test
  fun finishWizard_withSyncEngineAndSyncedWorkingCopy_savesPurposePackFormsAndMapLayers() {
    val service = org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService()
    val dataHolder =
      org.openforis.collectone.v2.app.di.AppDataHolder(
        localStore = org.openforis.collectone.v2.app.data.datasource.local.store.seededStore(),
        grpcClient = service.client(),
        enableSyncEngine = true,
      )
    try {
      val state = WebAppState(dataHolder = dataHolder)
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val wizard = state.createSurveyWizard
      val surveyId = wizard.uiState.value.workingSurveyId

      // Step 1: Overview
      wizard.updateTitle("Synced EUDR Survey")
      wizard.nextStep()

      // Step 2: Mode & Purpose — select EUDR purpose pack
      wizard.togglePurposePack("eudr_due_diligence")
      wizard.nextStep()

      // Step 3: Survey area -> Step 4: Map features (seeds primary layer and form)
      wizard.nextStep()
      assertTrue(state.surveyEditor.uiState.value.draft.forms.isNotEmpty())
      assertTrue(state.surveyEditor.uiState.value.draft.datasets.isNotEmpty())

      // Simulate the 1.5s debounced working-copy autosave completing against the server while the
      // user is still in the wizard.
      runDirect {
        dataHolder.localStore.transaction {
          val pendingForSurvey =
            outbox().filter {
              (it.target
                  as?
                  org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget.Survey)
                ?.surveyId == surveyId
            }
          val folded =
            org.openforis.collectone.v2.app.data.sync.RecordFolds.survey(
                surveyBase(surveyId),
                surveyId,
                pendingForSurvey,
              )
              .value!!
          val syncedBase =
            folded.copy(
              saved = folded.saved?.copy(record_version = 1L),
              workingCopy = folded.workingCopy?.copy(record_version = 2L),
            )
          putOutbox(outbox() - pendingForSurvey.toSet())
          org.openforis.collectone.v2.app.data.sync
            .Rebaser(this)
            .acceptSurvey(surveyId, syncedBase, replace = true)
        }
      }
      val baseBeforeFinish = runDirect {
        dataHolder.localStore.transaction { surveyBase(surveyId) }
      }
      assertNotNull(baseBeforeFinish?.workingCopy)

      // Finish the wizard and verify both local runtime collections and the outbox mutations that
      // publish the saved survey to the server.
      wizard.finishWizard()

      val savedForms = runDirect { dataHolder.localStore.transaction { forms(surveyId) } }
      val savedLayers = runDirect { dataHolder.localStore.transaction { mapLayers(surveyId) } }
      val savedConfig = runDirect { dataHolder.localStore.transaction { surveyConfig(surveyId) } }
      assertTrue(savedForms.isNotEmpty(), "Expected forms to be saved after finishing wizard")
      assertTrue(savedLayers.isNotEmpty(), "Expected map layers to be saved after finishing wizard")
      assertEquals(
        listOf("eudr_due_diligence"),
        savedConfig?.purposeIds,
      )
      val serverAfterPublish = runDirect {
        dataHolder.localStore.transaction {
          val pendingForSurvey =
            outbox().filter {
              (it.target
                  as?
                  org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget.Survey)
                ?.surveyId == surveyId
            }
          org.openforis.collectone.v2.app.data.sync.RecordFolds.survey(
              surveyBase(surveyId),
              surveyId,
              pendingForSurvey,
            )
            .value
        }
      }
      val serverSaved = assertNotNull(serverAfterPublish?.saved)
      assertNull(serverAfterPublish?.workingCopy)
      assertTrue(serverSaved.forms.isNotEmpty())
      assertTrue(serverSaved.map_config?.layers.orEmpty().isNotEmpty())
      assertEquals(
        listOf("eudr_due_diligence"),
        serverSaved.purpose_ids,
      )
    } finally {
      dataHolder.syncCoordinator?.stop()
    }
  }

  /** A new app session (as after a page reload) on [holder]'s stored data. */
  private fun reloaded(holder: AppDataHolder) =
    WebAppState(
      dataHolder =
        AppDataHolder(
          localStore = holder.localStore,
          grpcClient = SeedUserDataService().client(),
          deviceStorageClient = { SampleDeviceStorage },
        )
    )

  @Test
  fun reloadingWithUnfinishedWizardSurvey_restoresItsDetailsAndSettings() {
    val holder = sampleDataHolder()
    val state = WebAppState(dataHolder = holder)
    val wizard = state.createSurveyWizard
    wizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val workingId = wizard.uiState.value.workingSurveyId
    wizard.updateTitle("Resumable Survey")
    wizard.updateDescription("Kept across reloads")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.togglePurposePack("producer_registration")
    wizard.nextStep() // SURVEY_AREA
    wizard.nextStep() // MAP_FEATURES
    // Sample plots aren't the purpose's default, so this is the organizer's own choice.
    wizard.selectMapFeatureSource(WizardMapFeatureSource.SAMPLE_PLOTS)
    assertTrue(assertNotNull(wizard.uiState.value.primaryMapLayer).isGenerated)

    val resumed = reloaded(holder)
    val resumedWizard = resumed.createSurveyWizard
    resumedWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    assertTrue(resumedWizard.uiState.value.isPromptingUnsavedDraft)
    assertEquals(workingId, resumedWizard.uiState.value.workingSurveyId)

    resumedWizard.continueUnsavedDraft()
    val ui = resumedWizard.uiState.value
    assertEquals("Resumable Survey", ui.title)
    assertEquals("Kept across reloads", ui.description)
    assertEquals(SampleOrganizationsData.KENYA_FOREST_SERVICE, ui.organizationId)
    assertEquals(listOf("producer_registration"), ui.purposeIds)
    assertEquals(WizardMapFeatureSource.SAMPLE_PLOTS, ui.mapFeatureSource)

    // Moving on keeps the organizer's choices instead of re-applying the purpose's defaults.
    resumedWizard.selectStep(CreateSurveyWizardStep.MAP_FEATURES)
    val layer = assertNotNull(resumedWizard.uiState.value.primaryMapLayer)
    assertTrue(layer.isGenerated)
    assertEquals(1, resumed.surveyEditor.uiState.value.forms.size)
  }

  @Test
  fun returningToWizardWithOnlyAutoFilledSettings_startsFreshWithoutPrompting() {
    val holder = sampleDataHolder()
    val state = WebAppState(dataHolder = holder)
    val wizard = state.createSurveyWizard
    wizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val firstWorkingId = wizard.uiState.value.workingSurveyId
    // As when organizations load after the wizard opened: the default organization (with its
    // survey area and sharing) is applied by the wizard, leaving a working copy behind.
    state.surveyEditor.setOrganization(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    assertNotNull(runDirect { holder.surveyEditorRepository.getWorkingCopyDraft(firstWorkingId) })

    // After a reload, the empty unfinished survey is replaced by a fresh one without asking.
    val resumed = reloaded(holder)
    resumed.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val ui = resumed.createSurveyWizard.uiState.value
    assertFalse(ui.isPromptingUnsavedDraft)
    assertTrue(ui.workingSurveyId.isNotBlank() && ui.workingSurveyId != firstWorkingId)
    assertFalse(resumed.viewModels.dashboard.uiState.value.surveys.any { it.id == firstWorkingId })
  }

  @Test
  fun returningToWizardWithUnsavedDraft_promptsToContinueOrDiscard() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    val firstWorkingId = wizard.uiState.value.workingSurveyId

    wizard.updateTitle("Unsaved Wizard Draft")
    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.MODE_AND_PURPOSE, wizard.uiState.value.currentStep)

    // Leaving the wizard without canceling (e.g., clicking the logo) and re-opening it prompts
    // whether to continue or discard the unsaved draft.
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    assertTrue(wizard.uiState.value.isPromptingUnsavedDraft)
    assertEquals(firstWorkingId, wizard.uiState.value.workingSurveyId)
    assertEquals("Unsaved Wizard Draft", wizard.uiState.value.title)
    assertEquals(CreateSurveyWizardStep.MODE_AND_PURPOSE, wizard.uiState.value.currentStep)

    // Continuing keeps the previous draft and step.
    wizard.continueUnsavedDraft()
    assertFalse(wizard.uiState.value.isPromptingUnsavedDraft)
    assertEquals(firstWorkingId, wizard.uiState.value.workingSurveyId)
    assertEquals("Unsaved Wizard Draft", wizard.uiState.value.title)

    // Leaving and re-opening again, then discarding deletes the old draft and starts fresh.
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    assertTrue(wizard.uiState.value.isPromptingUnsavedDraft)

    wizard.discardUnsavedDraft()
    assertFalse(wizard.uiState.value.isPromptingUnsavedDraft)
    assertFalse(state.viewModels.dashboard.uiState.value.surveys.any { it.id == firstWorkingId })
    val newWorkingId = wizard.uiState.value.workingSurveyId
    assertTrue(newWorkingId.isNotBlank() && newWorkingId != firstWorkingId)
    assertEquals("", wizard.uiState.value.title)
    assertEquals(CreateSurveyWizardStep.OVERVIEW, wizard.uiState.value.currentStep)
  }

  @Test
  fun sampleDesignPreview_withSyncEngineAndWorkingCopyAutosave_preservesAreaAndEnablesRegenerate() {
    val service = org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService()
    val dataHolder =
      org.openforis.collectone.v2.app.di.AppDataHolder(
        localStore = org.openforis.collectone.v2.app.data.datasource.local.store.seededStore(),
        grpcClient = service.client(),
        enableSyncEngine = true,
      )
    try {
      val state = WebAppState(dataHolder = dataHolder)
      state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
      val wizard = state.createSurveyWizard
      val surveyId = wizard.uiState.value.workingSurveyId

      wizard.updateTitle("Synced Sampling Preview")
      wizard.nextStep() // MODE_AND_PURPOSE
      wizard.togglePurposePack("lulc_training_data")
      wizard
        .nextStep() // SURVEY_AREA (keep default Kenya survey area without calling setSurveyArea)
      assertNotNull(wizard.uiState.value.surveyArea)
      wizard.nextStep() // MAP_FEATURES
      wizard.selectMapFeatureSource(WizardMapFeatureSource.SAMPLE_PLOTS)

      val layerKey = assertNotNull(wizard.uiState.value.primaryMapLayer).key
      // Generate initial preview
      val firstOutcome = runDirect { state.surveyEditor.generateSample(layerKey) }
      val firstGenerated = kotlin.test.assertIs<SampleGenerationOutcome.Generated>(firstOutcome)
      assertTrue(firstGenerated.plotCount > 0)

      // Simulate debounced working-copy autosave syncing to the server while in Step 4
      runDirect {
        dataHolder.localStore.transaction {
          val pendingForSurvey =
            outbox().filter {
              (it.target
                  as?
                  org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget.Survey)
                ?.surveyId == surveyId
            }
          val folded =
            org.openforis.collectone.v2.app.data.sync.RecordFolds.survey(
                surveyBase(surveyId),
                surveyId,
                pendingForSurvey,
              )
              .value!!
          val syncedBase =
            folded.copy(
              saved = folded.saved?.copy(record_version = 1L),
              workingCopy = folded.workingCopy?.copy(record_version = 2L),
            )
          putOutbox(outbox() - pendingForSurvey.toSet())
          org.openforis.collectone.v2.app.data.sync
            .Rebaser(this)
            .acceptSurvey(surveyId, syncedBase, replace = true)
        }
      }

      // Change sampling design parameters
      state.surveyEditor.updateSampleDesign(layerKey) {
        it.copy(
          method = org.openforis.collectone.v2.app.domain.model.editor.SampleMethod.SIMPLE_RANDOM,
          count = 25,
        )
      }

      val updatedDataset = assertNotNull(wizard.uiState.value.primaryMapLayer)
      val updatedConfig = assertNotNull(updatedDataset.generator)
      assertNotNull(updatedConfig.lastRun)
      assertTrue(state.surveyEditor.isDesignStale(updatedDataset))
      assertNull(state.surveyEditor.uiState.value.regenerateBlockedReason(updatedDataset))
      kotlin.test.assertIs<
        org.openforis.collectone.v2.web.domain.model.editor.SamplingAreaResult.Ready
      >(
        state.surveyEditor.samplingArea(updatedConfig)
      )

      // Regenerating ("Update preview") succeeds with the new count
      val secondOutcome = runDirect { state.surveyEditor.regenerateSample(layerKey) }
      val secondGenerated = kotlin.test.assertIs<SampleGenerationOutcome.Generated>(secondOutcome)
      assertEquals(25, secondGenerated.plotCount)

      // The regenerated plots replace the previous ones in the layer the wizard's map shows.
      val regenerated = assertNotNull(wizard.uiState.value.primaryMapLayer)
      assertEquals(25, regenerated.rows.size)
      assertEquals(
        org.openforis.collectone.v2.app.domain.model.editor.SampleMethod.SIMPLE_RANDOM,
        regenerated.generator?.method,
      )
      assertFalse(state.surveyEditor.isDesignStale(regenerated))
      val editorLayer =
        assertNotNull(state.surveyEditor.uiState.value.datasets.firstOrNull { it.key == layerKey })
      assertEquals(regenerated.rows, editorLayer.rows)
    } finally {
      dataHolder.syncCoordinator?.stop()
    }
  }

  @Test
  fun artTreesPurposePacks_seedActivityDataAndBiomassInventoryTemplatesWithArtTreesProgram() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Jurisdictional REDD+ MRV")
    wizard.nextStep() // MODE_AND_PURPOSE

    // 1. Selecting Deforestation & land-use change (Activity Data) offers only art_trees,
    // defaults to SAMPLE_PLOTS + VISUAL_CLASSIFICATION, and enables ART-TREES Activity Data export.
    wizard.togglePurposePack("deforestation_activity_data")
    assertEquals(listOf("deforestation_activity_data"), wizard.uiState.value.purposeIds)
    assertEquals(listOf("art_trees"), wizard.uiState.value.offeredProgramIds)
    assertEquals(
      listOf("art_trees"),
      state.surveyEditor.uiState.value.draft.details.programIds,
    )
    assertEquals(WizardMapFeatureSource.SAMPLE_PLOTS, wizard.uiState.value.mapFeatureSource)

    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES

    val activityUi = wizard.uiState.value
    assertEquals(
      "Forest change & activity data plot",
      assertNotNull(activityUi.primaryForm).form.title,
    )
    assertEquals(
      listOf("art_trees_activity_data_geojson"),
      activityUi.impactReadiness.exportPlans.map { it.profile.id },
    )

    // 2. Switching to Forest carbon & biomass inventory (Emission Factors) also offers only
    // art_trees, keeps SAMPLE_PLOTS, and seeds the ground biomass inventory plot form.
    wizard.selectStep(CreateSurveyWizardStep.MODE_AND_PURPOSE)
    wizard.togglePurposePack("forest_carbon_inventory")
    assertEquals(listOf("forest_carbon_inventory"), wizard.uiState.value.purposeIds)
    assertEquals(listOf("art_trees"), wizard.uiState.value.offeredProgramIds)
    assertEquals(
      listOf("art_trees"),
      state.surveyEditor.uiState.value.draft.details.programIds,
    )
    assertEquals(WizardMapFeatureSource.SAMPLE_PLOTS, wizard.uiState.value.mapFeatureSource)

    wizard.selectStep(CreateSurveyWizardStep.MAP_FEATURES)
    val biomassUi = wizard.uiState.value
    assertEquals(
      "Forest carbon & biomass inventory plot",
      assertNotNull(biomassUi.primaryForm).form.title,
    )
    assertEquals(
      listOf("art_trees_biomass_inventory_geojson"),
      biomassUi.impactReadiness.exportPlans.map { it.profile.id },
    )
  }

  /** A data holder whose organizations load only when the returned callback runs. */
  private fun dataHolderWithLateOrganizations() =
    sampleDataHolder().let { holder ->
      val organizations = runDirect { holder.localStore.transaction { organizations() } }
      runDirect {
        holder.localStore.transaction { organizations.forEach { deleteOrganization(it.id) } }
      }
      holder to
        {
          runDirect {
            holder.localStore.transaction { organizations.forEach { upsertOrganization(it) } }
          }
        }
    }

  @Test
  fun startWizard_beforeOrganizationsLoad_selectsDefaultOrganizationOnceTheyDo() {
    val (dataHolder, loadOrganizations) = dataHolderWithLateOrganizations()
    val state = WebAppState(dataHolder = dataHolder)
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    assertNull(wizard.uiState.value.organizationId)
    assertNull(wizard.uiState.value.surveyArea)

    loadOrganizations()

    assertEquals(SampleOrganizationsData.KENYA_FOREST_SERVICE, wizard.uiState.value.organizationId)
    assertNotNull(wizard.uiState.value.surveyArea)
    assertEquals(
      SharingPolicy.ORGANIZATION,
      state.surveyEditor.uiState.value.draft.sharing.policy,
    )
  }

  @Test
  fun startWizard_beforeOrganizationsLoad_keepsTheOrganizersOwnChoice() {
    val (dataHolder, loadOrganizations) = dataHolderWithLateOrganizations()
    val state = WebAppState(dataHolder = dataHolder)
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    wizard.selectOrganization(null)
    loadOrganizations()

    assertNull(wizard.uiState.value.organizationId)
  }

  @Test
  fun formQuestionsStep_andFinishWizard_blockVariantIncompatibleFormsUntilResolved() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Variant Guardrail Survey")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.selectCollectionMode(SurveyCollectionMode.VISUAL_CLASSIFICATION)
    wizard.togglePurposePack("lulc_training_data")
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES
    wizard.nextStep() // FORM_QUESTIONS
    assertEquals(CreateSurveyWizardStep.FORM_QUESTIONS, wizard.uiState.value.currentStep)

    val primaryFormKey = assertNotNull(wizard.uiState.value.primaryForm).key
    val formEditor = state.surveyEditor.formEditor(primaryFormKey)

    // 1. Add a GPS-only location question to the web-enabled form: advancing and saving succeed.
    formEditor.addQuestion(EditorQuestionType.LOCATION)
    val gpsKey = assertNotNull(formEditor.uiState.value.selectedKey)
    formEditor.updateQuestionLabel(gpsKey, "GPS stake")
    formEditor.updateCapture(gpsKey, GeometryCapture.GPS_ONLY)
    formEditor.selectFormSettings()

    assertFalse(wizard.uiState.value.hasFormVariantIncompatibility)
    assertTrue(wizard.uiState.value.canProceedFromCurrentStep)

    // 2. Remove the location question, then enable Open in Google Earth with a Photo question:
    // advancing and saving are blocked.
    formEditor.deleteQuestion(gpsKey)

    formEditor.addQuestion(EditorQuestionType.PHOTO)
    val photoKey = assertNotNull(formEditor.uiState.value.selectedKey)
    formEditor.updateQuestionLabel(photoKey, "Plot photo")
    formEditor.updateOpenInGoogleEarth(true)
    formEditor.selectFormSettings()

    assertTrue(wizard.uiState.value.hasFormVariantIncompatibility)
    assertFalse(wizard.uiState.value.canProceedFromCurrentStep)
    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.FORM_QUESTIONS, wizard.uiState.value.currentStep)
    assertTrue(
      formEditor.uiState.value.selectedKey in
        formEditor.uiState.value.form.googleEarthIncompatibleQuestions().map { it.key }
    )

    // 3. Turn off Open in Google Earth: advancing to SHARING and finishing now succeed.
    formEditor.updateOpenInGoogleEarth(false)
    assertFalse(wizard.uiState.value.hasFormVariantIncompatibility)
    assertTrue(wizard.uiState.value.canProceedFromCurrentStep)

    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.SHARING, wizard.uiState.value.currentStep)
    wizard.finishWizard()
    assertEquals("", wizard.uiState.value.workingSurveyId)
  }
}
