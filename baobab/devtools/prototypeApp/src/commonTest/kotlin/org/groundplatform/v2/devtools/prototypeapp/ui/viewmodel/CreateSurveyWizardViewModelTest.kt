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
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.devtools.prototypeapp.PrototypeWorkbenchPage
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeSurveyEditorData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.CreateSurveyWizardStep
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormPreviewTarget
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryCapture
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.PeerDataVisibility
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingPolicy
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyCollectionMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyWizardRules
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.WizardMapFeatureSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.WizardStartMethod
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind

class CreateSurveyWizardViewModelTest {

  @Test
  fun openCreateSurveyWizard_navigatesToCreateSurveyPage_andStartsOnOverview() {
    val state = PrototypeAppState()
    state.openCreateSurveyWizard(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)

    assertEquals(PrototypeWorkbenchPage.CREATE_SURVEY, state.activeWorkbenchPage)
    assertEquals(
      PrototypeWorkbenchPage.CREATE_SURVEY,
      PrototypeWorkbenchPage.fromHash("#create-survey"),
    )
    val ui = state.createSurveyWizard.uiState.value
    assertEquals(CreateSurveyWizardStep.OVERVIEW, ui.currentStep)
    assertEquals(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE, ui.organizationId)
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
    val state = PrototypeAppState()
    state.openCreateSurveyWizard(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)
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
    state.surveyEditor.setSurveyArea(PrototypeFakeSurveyEditorData.kenyaSurveyArea())
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
  fun eudrPurposePack_enforcesPolygonGuardrail_andComputesExportReadiness() {
    val state = PrototypeAppState()
    state.openCreateSurveyWizard(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard

    wizard.updateTitle("Kirinyaga EUDR Coffee Plots")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.selectCollectionMode(SurveyCollectionMode.FIELD_SURVEY)
    wizard.togglePurposePack("eudr_due_diligence")

    wizard.nextStep() // SURVEY_AREA (required in all collection modes)
    assertFalse(wizard.uiState.value.canSkipCurrentStep)
    assertFalse(wizard.uiState.value.canProceedFromCurrentStep)
    state.surveyEditor.setSurveyArea(PrototypeFakeSurveyEditorData.kenyaSurveyArea())
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

    val state = PrototypeAppState()
    state.openCreateSurveyWizard(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    wizard.updateTitle("Community Forest Tenure Survey")
    wizard.applyCareSovereigntyDefaults()

    val sharing = state.surveyEditor.uiState.value.sharing
    assertEquals(SharingPolicy.RESTRICTED, sharing.policy)
    assertEquals(PeerDataVisibility.OWN_ONLY, sharing.peerDataVisibility)
  }

  @Test
  fun startMethod_copyExisting_and_importXForms_populateDraft() {
    val state = PrototypeAppState()
    state.openCreateSurveyWizard(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)
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
    state.surveyEditor.setSurveyArea(PrototypeFakeSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES
    wizard.nextStep() // FORM_QUESTIONS

    val primaryForm = assertNotNull(wizard.uiState.value.primaryForm)
    assertEquals("Tree Health Form", primaryForm.form.title)
    assertEquals(
      GeometryCapture.GPS_OR_MAP,
      SurveyCollectionMode.BOTH.defaultGeometryCapture,
    )

    wizard.finishWizard()
    assertEquals(PrototypeWorkbenchPage.SURVEY_EDITOR, state.activeWorkbenchPage)
    assertEquals("Imported XForms Survey", state.surveyEditor.uiState.value.details.title)
    assertEquals(
      org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyLifecycleState.DRAFT,
      state.surveyEditor.uiState.value.lifecycleState,
    )
    assertTrue(state.surveyEditor.uiState.value.isDraft)
    assertEquals("Save draft", state.surveyEditor.uiState.value.saveActionLabel)

    // Clicking "Save draft" (or "Publish changes") commits edits and transitions to WEB_DASHBOARD.
    state.surveyEditor.publish()
    assertEquals(PrototypeWorkbenchPage.WEB_DASHBOARD, state.activeWorkbenchPage)
  }

  @Test
  fun modeAndPurpose_requiresAtLeastOnePurpose_orNonBlankOtherPurpose() {
    val state = PrototypeAppState()
    state.openCreateSurveyWizard(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)
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

    val state = PrototypeAppState()
    state.openCreateSurveyWizard(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)
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
    state.surveyEditor.setSurveyArea(PrototypeFakeSurveyEditorData.kenyaSurveyArea())
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
    val state = PrototypeAppState()
    val initialSurveyIds = state.surveys.map { it.id }.toSet()
    state.openCreateSurveyWizard(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)

    val workingId = state.createSurveyWizard.uiState.value.workingSurveyId
    assertTrue(workingId.isNotBlank())
    assertTrue(state.surveys.any { it.id == workingId })

    state.createSurveyWizard.updateTitle("Discarded Survey")
    state.createSurveyWizard.cancelWizard()

    assertFalse(state.surveys.any { it.id == workingId })
    assertEquals(initialSurveyIds, state.surveys.map { it.id }.toSet())
    assertEquals(PrototypeWorkbenchPage.WEB_SURVEYS, state.activeWorkbenchPage)
  }

  @Test
  fun samplePlots_deferredUntilPreviewClickedOrWizardSaved() {
    val state = PrototypeAppState()
    state.openCreateSurveyWizard(PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    val workingId = wizard.uiState.value.workingSurveyId

    wizard.updateTitle("Deferred Sample Plots Survey")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.selectCollectionMode(SurveyCollectionMode.VISUAL_CLASSIFICATION)
    wizard.togglePurposePack(SurveyWizardRules.OTHER_PURPOSE_ID)
    wizard.updateOtherPurpose("Canopy interpretation")
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(PrototypeFakeSurveyEditorData.kenyaSurveyArea())
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
}
