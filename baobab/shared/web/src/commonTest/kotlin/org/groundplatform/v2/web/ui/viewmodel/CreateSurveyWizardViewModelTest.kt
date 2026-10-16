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
package org.groundplatform.v2.web.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.datasource.local.store.sampleDataHolder
import org.groundplatform.v2.app.data.seed.GlobalLibrarySeedData
import org.groundplatform.v2.app.data.seed.SampleOrganizationsData
import org.groundplatform.v2.app.data.seed.SampleSurveyEditorData
import org.groundplatform.v2.app.domain.model.OrganizationType
import org.groundplatform.v2.app.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.app.domain.model.editor.FormPreviewTarget
import org.groundplatform.v2.app.domain.model.editor.GeometryCapture
import org.groundplatform.v2.app.domain.model.editor.GeometryKind
import org.groundplatform.v2.app.domain.model.editor.PeerDataVisibility
import org.groundplatform.v2.app.domain.model.editor.SaveToMode
import org.groundplatform.v2.app.domain.model.editor.SharingPolicy
import org.groundplatform.v2.app.domain.model.geometryKind
import org.groundplatform.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.groundplatform.v2.web.domain.model.editor.SurveyCollectionMode
import org.groundplatform.v2.web.domain.model.editor.SurveyWizardRules
import org.groundplatform.v2.web.domain.model.editor.WizardMapFeatureSource
import org.groundplatform.v2.web.domain.model.editor.WizardStartMethod
import org.groundplatform.v2.web.ui.WebAppState
import org.groundplatform.v2.web.ui.WebPage

class CreateSurveyWizardViewModelTest {

  @Test
  fun openCreateSurveyWizard_navigatesToCreateSurveyPage_andStartsOnOverview() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)

    assertEquals(WebPage.CREATE_SURVEY, state.activePage)
    assertEquals(
      WebPage.CREATE_SURVEY,
      WebPage.fromHash("#create-survey"),
    )
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
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
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
    assertFalse(wizard.uiState.value.canProceedFromCurrentStep)
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
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Purpose-Driven Survey")
    wizard.nextStep() // PURPOSE
    assertEquals("Purpose", CreateSurveyWizardStep.MODE_AND_PURPOSE.label)

    // 1. Selecting LULC Training Data sets SAMPLE_PLOTS automatically.
    wizard.togglePurposePack("lulc_training_data")
    assertEquals(listOf("lulc_training_data"), wizard.uiState.value.purposeIds)
    assertEquals(WizardMapFeatureSource.SAMPLE_PLOTS, wizard.uiState.value.mapFeatureSource)

    // 2. Selecting Producer Registration adds it alongside LULC (multi-select).
    wizard.togglePurposePack("producer_registration")
    assertEquals(
      listOf("lulc_training_data", "producer_registration"),
      wizard.uiState.value.purposeIds,
    )

    // 3. Deselecting LULC leaves Producer Registration, which switches to FIELD_FREEFORM + POINT.
    wizard.togglePurposePack("lulc_training_data")
    assertEquals(listOf("producer_registration"), wizard.uiState.value.purposeIds)
    assertEquals(WizardMapFeatureSource.FIELD_FREEFORM, wizard.uiState.value.mapFeatureSource)

    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES

    val producerLayer = assertNotNull(wizard.uiState.value.primaryMapLayer)
    assertFalse(producerLayer.isGenerated)
    assertEquals(GeometryKind.POINT, producerLayer.geometryKind)
    val producerForm = assertNotNull(wizard.uiState.value.primaryForm)
    assertEquals("Producer & farm registration", producerForm.form.title)
    assertEquals(SaveToMode.CREATE, producerForm.form.saveTo.mode)
    assertEquals(1, state.surveyEditor.uiState.value.forms.size)

    // 4. Going back to Step 2 and swapping Producer Registration for FERM Restoration updates the
    // primary layer to POLYGON and replaces the single starting form.
    wizard.selectStep(CreateSurveyWizardStep.MODE_AND_PURPOSE)
    wizard.togglePurposePack("producer_registration")
    wizard.togglePurposePack("ferm_restoration")
    assertEquals(listOf("ferm_restoration"), wizard.uiState.value.purposeIds)
    wizard.selectStep(CreateSurveyWizardStep.MAP_FEATURES)

    val fermLayer = assertNotNull(wizard.uiState.value.primaryMapLayer)
    assertEquals(GeometryKind.POLYGON, fermLayer.geometryKind)
    val fermForm = assertNotNull(wizard.uiState.value.primaryForm)
    assertEquals("Ecosystem restoration site check", fermForm.form.title)
    assertEquals(1, state.surveyEditor.uiState.value.forms.size)
  }

  @Test
  fun purposeSelection_multiplePacks_mergeProgramsAndDropOnlyDeselectedPackPrograms() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("Multi-Purpose Survey")
    wizard.nextStep() // MODE_AND_PURPOSE

    val library = wizard.uiState.value.library
    val eudrPrograms = library.purposePack("eudr_due_diligence")?.programIds.orEmpty()
    val fermPrograms = library.purposePack("ferm_restoration")?.programIds.orEmpty()

    wizard.togglePurposePack("eudr_due_diligence")
    wizard.togglePurposePack("ferm_restoration")
    assertEquals(
      listOf("eudr_due_diligence", "ferm_restoration"),
      wizard.uiState.value.purposeIds,
    )
    assertEquals(
      (eudrPrograms + fermPrograms).distinct(),
      state.surveyEditor.uiState.value.draft.details.programIds,
    )
    assertTrue(wizard.uiState.value.isPurposeValid)

    wizard.togglePurposePack("eudr_due_diligence")
    assertEquals(listOf("ferm_restoration"), wizard.uiState.value.purposeIds)
    assertEquals(fermPrograms, state.surveyEditor.uiState.value.draft.details.programIds)
  }

  @Test
  fun multiplePurposes_createOneFormPerTemplate_eachWithItsOwnDataset() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("Producers and Restoration")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.togglePurposePack("producer_registration")
    wizard.togglePurposePack("ferm_restoration")
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES

    val editor = state.surveyEditor.uiState.value
    assertEquals(
      listOf("Producer & farm registration", "Ecosystem restoration site check"),
      editor.forms.map { it.form.title },
    )
    val primaryForm = editor.forms[0]
    val fermForm = editor.forms[1]
    // The primary Form stays wired to the primary Map layer; the other gets its own dataset.
    assertEquals(primaryForm.key, wizard.uiState.value.primaryForm?.key)
    val primaryLayer = assertNotNull(wizard.uiState.value.primaryMapLayer)
    assertTrue(
      editor.datasets.none { it.key != primaryLayer.key && it.linkedFormKey == primaryForm.key }
    )
    assertNotNull(editor.datasets.firstOrNull { it.linkedFormKey == fermForm.key })

    // Deselecting FERM removes its Form and dataset, keeping the primary Form and Map layer.
    wizard.selectStep(CreateSurveyWizardStep.MODE_AND_PURPOSE)
    wizard.togglePurposePack("ferm_restoration")
    wizard.selectStep(CreateSurveyWizardStep.MAP_FEATURES)
    val afterDeselect = state.surveyEditor.uiState.value
    assertEquals(listOf(primaryForm.key), afterDeselect.forms.map { it.key })
    assertTrue(afterDeselect.datasets.none { it.linkedFormKey == fermForm.key })
    assertEquals(primaryLayer.key, wizard.uiState.value.primaryMapLayer?.key)
  }

  @Test
  fun purposeWithSeveralTemplates_createsSeveralForms_sharedTemplatesOnce() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("Coop Audit")
    wizard.nextStep() // MODE_AND_PURPOSE
    val coopPack =
      wizard.uiState.value.sortedPurposePacks.first {
        it.title.get("en") == "Coop annual certification audit"
      }
    assertEquals(2, coopPack.formTemplateIds.size)

    // The coop pack and EUDR both include the EUDR plot registration template; it's added once.
    wizard.togglePurposePack(coopPack.id)
    wizard.togglePurposePack("eudr_due_diligence")
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
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("Organizer Forms")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.togglePurposePack("producer_registration")
    wizard.togglePurposePack("ferm_restoration")
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
    assertEquals("Producer & farm registration", forms[0].form.title)
    assertEquals(addedKey, forms[1].key)
  }

  @Test
  fun eudrPurposePack_enforcesPolygonGuardrail_andComputesExportReadiness() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Kirinyaga EUDR Coffee Plots")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.selectCollectionMode(SurveyCollectionMode.FIELD_SURVEY)
    wizard.togglePurposePack("eudr_due_diligence")

    wizard.nextStep() // SURVEY_AREA (required in all collection modes)
    assertFalse(wizard.uiState.value.canSkipCurrentStep)
    assertFalse(wizard.uiState.value.canProceedFromCurrentStep)
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES

    val layer = assertNotNull(wizard.uiState.value.primaryMapLayer)
    assertEquals(GeometryKind.POLYGON, layer.geometryKind)

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
    assertEquals(5, SurveyWizardRules.CARE_GOVERNANCE_CRITERIA.size)

    val state = WebAppState(dataHolder = sampleDataHolder())
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
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
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
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
    assertEquals(WebPage.WEB_DASHBOARD, state.activePage)
    assertEquals("Imported XForms Survey", state.surveyEditor.uiState.value.details.title)
    assertEquals(
      org.groundplatform.v2.app.domain.model.SurveyLifecycleState.DRAFT,
      state.surveyEditor.uiState.value.lifecycleState,
    )
    assertTrue(state.surveyEditor.uiState.value.isDraft)
    assertEquals("Save draft", state.surveyEditor.uiState.value.saveActionLabel)
  }

  @Test
  fun importCollectEarthIdm_and_openInSurveyEditor_supportsCepSchemasAndEditorEscapeHatch() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
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

    // Escape hatch: "Open in advanced survey editor" commits the imported form and keeps activePage
    // on SURVEY_EDITOR.
    wizard.openInSurveyEditor()
    assertEquals(WebPage.SURVEY_EDITOR, state.activePage)

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
  fun complianceFrameworkPack_partitionsRegulatoryPacksFromDomainPurposes() {
    val complianceIds =
      GlobalLibrarySeedData.library()
        .purposePacks
        .filter { SurveyWizardRules.isComplianceFrameworkPack(it) }
        .map { it.id }
        .toSet()
    assertEquals(setOf("eudr_due_diligence", "timber_traceability"), complianceIds)
  }

  @Test
  fun modeAndPurpose_requiresAtLeastOnePurpose_orNonBlankOtherPurpose() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
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
        "Other: Amphibian habitat monitoring"
      )
    )

    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.SURVEY_AREA, wizard.uiState.value.currentStep)
  }

  @Test
  fun surveyArea_isRequiredInAllModes_andCannotBeSkipped() {
    assertTrue(SurveyCollectionMode.entries.all { it.surveyAreaRequired })

    val state = WebAppState(dataHolder = sampleDataHolder())
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Field Survey Requiring Area")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.selectCollectionMode(SurveyCollectionMode.FIELD_SURVEY)
    wizard.togglePurposePack("eudr_due_diligence")
    wizard.nextStep() // SURVEY_AREA

    assertEquals(CreateSurveyWizardStep.SURVEY_AREA, wizard.uiState.value.currentStep)
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
    val initialSurveyIds = state.surveys.map { it.id }.toSet()
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)

    val workingId = state.createSurveyWizard.uiState.value.workingSurveyId
    assertTrue(workingId.isNotBlank())
    assertTrue(state.surveys.any { it.id == workingId })

    state.createSurveyWizard.updateTitle("Discarded Survey")
    state.createSurveyWizard.cancelWizard()

    assertFalse(state.surveys.any { it.id == workingId })
    assertEquals(initialSurveyIds, state.surveys.map { it.id }.toSet())
    assertEquals(WebPage.WEB_SURVEYS, state.activePage)
  }

  @Test
  fun samplePlots_deferredUntilPreviewClickedOrWizardSaved() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
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
    val surveyCard = assertNotNull(state.surveys.firstOrNull { it.id == workingId })
    assertEquals(savedLayer.rows.size, surveyCard.entityCount)
  }

  @Test
  fun importCollectEarthCepBundle_parsesQuestionsRelevancePlotsAndAllowsProjectSwitch() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.openCreateSurveyWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
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
}
