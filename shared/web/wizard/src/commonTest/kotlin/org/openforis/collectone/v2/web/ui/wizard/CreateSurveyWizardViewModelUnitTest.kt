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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.AuthRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LocalStoreTransactionRunner
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.PlaceRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.data.seed.SampleSurveyEditorData
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewOptions
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.repository.OutboxSyncRepository
import org.openforis.collectone.v2.app.domain.usecase.CreateSurveyUseCase
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.model.editor.CareDefaultsReason
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPreview
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorViewModel
import org.openforis.collectone.v2.web.ui.surveyeditor.care.CareGovernanceRubric

class CreateSurveyWizardViewModelUnitTest {
  private val kfs = SampleOrganizationsData.KENYA_FOREST_SERVICE

  private class Fixture(
    val store: LocalStore = seededStore(),
    val outbox: OutboxSyncRepository? = null,
  ) {
    val surveys = SurveyRepositoryImpl(store)
    val editorRepo = SurveyEditorRepositoryImpl(store)
    val organizations = OrganizationRepositoryImpl(store)
    val auth = AuthRepositoryImpl()
    val libraries = LibraryRepositoryImpl(store)
    val createSurvey =
      CreateSurveyUseCase(
        surveys,
        editorRepo,
        LocalStoreTransactionRunner(store),
        libraryRepository = libraries,
      )
    val surveyEditor =
      SurveyEditorViewModel(
        surveyRepository = surveys,
        surveyEditorRepository = editorRepo,
        organizationRepository = organizations,
        authRepository = auth,
        placeRepository = PlaceRepositoryImpl(store),
        libraryRepository = libraries,
      )
    val wizard =
      CreateSurveyWizardViewModel(
        surveyRepository = surveys,
        surveyEditorRepository = editorRepo,
        organizationRepository = organizations,
        authRepository = auth,
        libraryRepository = libraries,
        createSurveyUseCase = createSurvey,
        surveyEditor = surveyEditor,
        outboxSyncRepository = outbox,
      )
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    val events = mutableListOf<CreateSurveyWizardEvent>()

    init {
      scope.launch { auth.signInWithGoogle() }
      scope.launch { wizard.events.collect { events += it } }
    }
  }

  @Test
  fun startWizard_navigatesStepsValidatesInputsAndCompletesWizard() {
    val f = Fixture()
    val wizard = f.wizard
    wizard.startWizard(kfs)

    assertEquals(CreateSurveyWizardStep.OVERVIEW, wizard.uiState.value.currentStep)
    assertFalse(wizard.uiState.value.canProceedFromCurrentStep)

    wizard.nextStep()
    assertTrue(wizard.uiState.value.isTitleInvalid)
    assertNotNull(wizard.uiState.value.currentStepValidationMessage)

    wizard.updateTitle("Mau Forest Inventory")
    wizard.updateDescription("Annual inventory")
    wizard.selectOrganization(kfs)
    assertFalse(wizard.uiState.value.isTitleInvalid)
    assertTrue(wizard.uiState.value.canProceedFromCurrentStep)

    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.MODE_AND_PURPOSE, wizard.uiState.value.currentStep)

    wizard.nextStep()
    assertTrue(wizard.uiState.value.isPurposeInvalid)
    assertNotNull(wizard.uiState.value.currentStepValidationMessage)

    wizard.togglePurposePack(SurveyWizardRules.OTHER_PURPOSE_ID)
    wizard.nextStep()
    assertNotNull(wizard.uiState.value.currentStepValidationMessage)

    wizard.updateOtherPurpose("Community firebreaks")
    assertTrue(wizard.uiState.value.isPurposeValid)
    wizard.togglePurposePack("eudr_due_diligence")
    wizard.toggleProgram("eudr")

    wizard.selectStarterTemplate("uk_frc")
    assertEquals("uk_frc", wizard.uiState.value.starterTemplateId)
    wizard.selectStarterTemplate("eudr")

    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.SURVEY_AREA, wizard.uiState.value.currentStep)
    f.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())

    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.MAP_FEATURES, wizard.uiState.value.currentStep)
    wizard.confirmEudrPlotsUnderFourHa(true)
    assertTrue(wizard.uiState.value.eudrPlotsUnderFourHaConfirmed)
    wizard.updatePrimaryLayerGeometryType(GeometryType.POINT)
    assertEquals(GeometryType.POINT, wizard.uiState.value.primaryMapLayer?.geometryType)
    wizard.updatePrimaryLayerDisplayName("Coffee plots")
    wizard.updatePrimaryLayerFieldCreation(false)
    assertFalse(wizard.uiState.value.primaryMapLayer!!.fieldCreationEnabled)

    wizard.nextStep()
    assertEquals(CreateSurveyWizardStep.FORM_QUESTIONS, wizard.uiState.value.currentStep)
    wizard.previousStep()
    assertEquals(CreateSurveyWizardStep.MAP_FEATURES, wizard.uiState.value.currentStep)
    wizard.selectStep(CreateSurveyWizardStep.SHARING)
    assertEquals(CreateSurveyWizardStep.SHARING, wizard.uiState.value.currentStep)

    wizard.applyCareSovereigntyDefaults()
    assertTrue(wizard.uiState.value.impactReadiness.careSovereigntyApplied)
    assertEquals(SharingPolicy.RESTRICTED, f.surveyEditor.uiState.value.draft.sharing.policy)
    assertEquals(
      PeerDataVisibility.OWN_ONLY,
      f.surveyEditor.uiState.value.draft.sharing.peerDataVisibility,
    )
    assertTrue(wizard.uiState.value.collectorConsentRequired)

    wizard.updateCollectorConsentText("Custom consent notice")
    assertEquals("Custom consent notice", wizard.uiState.value.collectorConsentText)
    wizard.updateCollectorConsentRequired(false)
    assertFalse(wizard.uiState.value.collectorConsentRequired)
    wizard.updateCollectorConsentRequired(true)

    wizard.finishWizard()
    assertIs<CreateSurveyWizardEvent.OpenSurveyEditor>(f.events.last())
  }

  @Test
  fun startMethods_coverCopyExistingImportXFormsAndSamplePlotsValidation() {
    val f = Fixture()
    val wizard = f.wizard
    wizard.startWizard(kfs)
    wizard.updateTitle("Imported Survey")

    wizard.selectStartMethod(WizardStartMethod.COPY_EXISTING)
    val sourceId = wizard.uiState.value.existingSurveys.first().id
    wizard.selectCopySourceSurvey(sourceId)
    assertEquals(sourceId, wizard.uiState.value.copySourceSurveyId)

    wizard.selectStartMethod(WizardStartMethod.IMPORT_XFORMS)
    wizard.importXFormsXml("bad.xml", "<not-an-xform/>")
    assertNotNull(wizard.uiState.value.importErrorMessage)

    val validXForm =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Tree Form</h:title>
          <model>
            <instance><data id="tree_form"><species/></data></instance>
            <bind nodeset="/data/species" type="string"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/species"><label>Species</label></input>
        </h:body>
      </h:html>
      """
        .trimIndent()
    wizard.importXFormsXml("tree.xml", validXForm)
    assertNull(wizard.uiState.value.importErrorMessage)
    assertEquals("tree.xml", wizard.uiState.value.importedXFormsFileName)
    wizard.clearImportedXForms()
    assertNull(wizard.uiState.value.importedXFormsFileName)

    wizard.selectStartMethod(WizardStartMethod.FROM_SCRATCH)
    wizard.selectCollectionMode(SurveyCollectionMode.BOTH)
    wizard.togglePurposePack("lulc_training_data")
    wizard.selectMapFeatureSource(WizardMapFeatureSource.SAMPLE_PLOTS)
    wizard.setPlotFieldValidation(true)
    assertNotNull(wizard.uiState.value.plotReview)
    wizard.updatePlotFieldValidation(PlotReviewOptions())
    wizard.setPlotFieldValidation(false)

    val geoJson =
      """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"id":"P1"},"geometry":{"type":"Point","coordinates":[36.8,-1.2]}}]}"""
    val plan = assertNotNull(MapLayerImportPreview.of("sites.geojson", geoJson).plan)
    wizard.importPrimaryMapLayer(plan)
    assertEquals(WizardMapFeatureSource.IMPORT_FILE, wizard.uiState.value.mapFeatureSource)

    wizard.createDatasetForChoiceQuestion(0, DatasetKind.DATA_TABLE)
    assertEquals(5, CareGovernanceRubric.DEFAULT_CRITERIA.size)
  }

  @Test
  fun unfinishedDraftAndQuotaAndCancel_areHandledCleanly() {
    val store = seededStore()
    runDirect {
      OrganizationRepositoryImpl(store).updateOrganization(kfs) {
        it.copy(organizationType = OrganizationType.IPLC_ORGANIZATION)
      }
    }
    val f = Fixture(store)
    val wizard = f.wizard
    wizard.startWizard(kfs)
    assertEquals(SharingPolicy.RESTRICTED, f.surveyEditor.uiState.value.draft.sharing.policy)

    wizard.updateTitle("Coop Survey")
    wizard.togglePurposePack("eudr_due_diligence")
    wizard.selectStep(CreateSurveyWizardStep.MAP_FEATURES)
    f.surveyEditor.updateDetails {
      it.copy(quotaLimits = QuotaLimitsConfig(maxQuestionsPerForm = 1))
    }
    wizard.finishWizard()
    assertNotNull(wizard.uiState.value.importErrorMessage)

    // Remove the per-survey question cap so openInSurveyEditor succeeds.
    f.surveyEditor.updateDetails { it.copy(quotaLimits = QuotaLimitsConfig()) }
    wizard.openInSurveyEditor()
    assertIs<CreateSurveyWizardEvent.OpenSurveyEditor>(f.events.last())

    wizard.startWizard(kfs)
    val workingId = wizard.uiState.value.workingSurveyId
    wizard.cancelWizard()
    assertIs<CreateSurveyWizardEvent.Cancelled>(f.events.last())
    assertTrue(runDirect { f.surveys.getSurveys() }.none { it.id == workingId })

    runDirect {
      val orphan =
        f.surveys.getSurveys().first { it.ownerEmail == SampleOrganizationsData.SIGNED_IN_EMAIL }
      f.surveys.setSurveyState(orphan.id, SurveyLifecycleState.DRAFT, null)
      val saved = f.editorRepo.getDraft(orphan.id)
      val blankPurpose =
        saved.copy(details = saved.details.copy(purposeIds = emptyList(), otherPurpose = ""))
      f.editorRepo.saveDraft(orphan.id, blankPurpose)
      f.editorRepo.autosaveDraft(
        orphan.id,
        blankPurpose.copy(details = blankPurpose.details.copy(otherPurpose = "Unsaved progress")),
      )
      // A titled draft with unsaved edits wasn't left behind by the wizard: no prompt.
      wizard.startWizard(kfs)
      assertFalse(wizard.uiState.value.isPromptingUnsavedDraft)
      wizard.cancelWizard()

      // The wizard's own surveys keep the placeholder title until it finishes.
      f.surveys.updateSurvey(orphan.id) { it.copy(title = CreateSurveyUseCase.DEFAULT_TITLE) }
      wizard.startWizard(kfs)
      assertTrue(wizard.uiState.value.isPromptingUnsavedDraft)
      assertEquals(orphan.id, wizard.uiState.value.workingSurveyId)
      wizard.continueUnsavedDraft()
      assertFalse(wizard.uiState.value.isPromptingUnsavedDraft)
    }
  }

  /** An outbox whose survey saves end with [saveError], recording what the wizard asked of it. */
  private class FakeOutbox(var saveError: DomainError?) : OutboxSyncRepository {
    val awaited = mutableListOf<String>()
    val retried = mutableListOf<String>()

    override suspend fun syncNow() = Unit

    override suspend fun retryRecord(surveyId: String, entityId: String, submissionId: String?) = 0

    override suspend fun retryRejected(surveyId: String): Int {
      retried += surveyId
      return 0
    }

    override suspend fun discardRejected(
      surveyId: String,
      entityId: String,
      submissionId: String?,
    ) = 0

    override suspend fun awaitSurveySaved(surveyId: String): DomainError? {
      awaited += surveyId
      return saveError
    }
  }

  @Test
  fun finishWizard_staysOnTheWizardUntilTheServerAcceptsTheSurvey() {
    val outbox = FakeOutbox(saveError = DomainError.Validation)
    val f = Fixture(outbox = outbox)
    val wizard = f.wizard
    wizard.startWizard(kfs)
    val workingId = wizard.uiState.value.workingSurveyId
    wizard.updateTitle("Sampled Survey")
    wizard.togglePurposePack(SurveyWizardRules.OTHER_PURPOSE_ID)
    wizard.updateOtherPurpose("Plot sampling")
    wizard.selectStep(CreateSurveyWizardStep.SHARING)
    val stepBeforeFinishing = wizard.uiState.value.currentStep

    wizard.finishWizard()

    // The server rejected a change: no navigation, the error is shown, and nothing is lost.
    assertTrue(f.events.none { it is CreateSurveyWizardEvent.OpenSurveyEditor })
    assertNotNull(wizard.uiState.value.importErrorMessage)
    assertFalse(wizard.uiState.value.isFinishing)
    assertEquals(workingId, wizard.uiState.value.workingSurveyId)
    assertEquals(stepBeforeFinishing, wizard.uiState.value.currentStep)
    assertEquals(listOf(workingId), outbox.awaited)

    // Finishing again resends rejected changes and, once they're accepted, opens the survey.
    outbox.saveError = null
    wizard.finishWizard()

    assertEquals(listOf(workingId, workingId), outbox.retried)
    val opened = assertIs<CreateSurveyWizardEvent.OpenSurveyEditor>(f.events.last())
    assertEquals(workingId, opened.surveyId)
    assertNull(wizard.uiState.value.importErrorMessage)
  }

  @Test
  fun careDefaults_forIplcOrganization_explainWhyTheyWereApplied() {
    val store = seededStore()
    runDirect {
      OrganizationRepositoryImpl(store).updateOrganization(kfs) {
        it.copy(organizationType = OrganizationType.IPLC_ORGANIZATION)
      }
    }
    val f = Fixture(store)
    val wizard = f.wizard
    wizard.startWizard(kfs)
    wizard.updateTitle("Community Survey")
    wizard.togglePurposePack("eudr_due_diligence")
    assertNull(wizard.uiState.value.careDefaultsAutoReason)

    wizard.selectStep(CreateSurveyWizardStep.SHARING)
    assertTrue(wizard.uiState.value.impactReadiness.careSovereigntyApplied)
    assertEquals(CareDefaultsReason.IplcOrganization, wizard.uiState.value.careDefaultsAutoReason)
    // Applied on the organizer's behalf, so the CARE card opens to show what changed.
    assertTrue(wizard.uiState.value.isCareCardExpanded)

    // Relaxing a default means CARE is no longer in effect, so there's nothing left to explain.
    f.surveyEditor.updateSharing { it.copy(policy = SharingPolicy.ORGANIZATION) }
    assertFalse(wizard.uiState.value.impactReadiness.careSovereigntyApplied)
    assertNull(wizard.uiState.value.careDefaultsAutoReason)

    // Applied by the organizer: no automatic reason.
    wizard.applyCareSovereigntyDefaults()
    assertTrue(wizard.uiState.value.impactReadiness.careSovereigntyApplied)
    assertNull(wizard.uiState.value.careDefaultsAutoReason)
  }

  @Test
  fun careDefaults_forIplcConcepts_nameTheLinkedConcepts() {
    val f = Fixture()
    val wizard = f.wizard
    wizard.startWizard(kfs)
    wizard.updateTitle("Community Land Survey")
    wizard.togglePurposePack("iplc_land_mapping")

    wizard.selectStep(CreateSurveyWizardStep.SHARING)

    val reason =
      assertIs<CareDefaultsReason.IplcConcepts>(wizard.uiState.value.careDefaultsAutoReason)
    assertTrue(reason.conceptIds.isNotEmpty())
    assertTrue(reason.conceptIds.all { it.startsWith("iplc.") })
    assertEquals(reason.conceptIds.sorted(), reason.conceptIds)
  }

  @Test
  fun careDefaults_withoutIplcContext_areNotAppliedAutomatically() {
    val f = Fixture()
    val wizard = f.wizard
    wizard.startWizard(kfs)
    wizard.updateTitle("Coffee Plots")
    wizard.togglePurposePack("eudr_due_diligence")

    wizard.selectStep(CreateSurveyWizardStep.SHARING)

    assertFalse(wizard.uiState.value.impactReadiness.careSovereigntyApplied)
    assertNull(wizard.uiState.value.careDefaultsAutoReason)
    assertFalse(wizard.uiState.value.isCareCardExpanded)
  }

  @Test
  fun careCard_expandedOrCollapsedByTheOrganizer_staysThatWayAcrossSteps() {
    val f = Fixture()
    val wizard = f.wizard
    wizard.startWizard(kfs)
    wizard.updateTitle("Coffee Plots")
    wizard.togglePurposePack("eudr_due_diligence")
    wizard.selectStep(CreateSurveyWizardStep.SHARING)
    assertFalse(wizard.uiState.value.isCareCardExpanded)

    wizard.setCareCardExpanded(true)
    wizard.selectStep(CreateSurveyWizardStep.OVERVIEW)
    wizard.selectStep(CreateSurveyWizardStep.SHARING)
    assertTrue(wizard.uiState.value.isCareCardExpanded)

    // Applying CARE by hand doesn't change how the card is shown.
    wizard.setCareCardExpanded(false)
    wizard.applyCareSovereigntyDefaults()
    assertFalse(wizard.uiState.value.isCareCardExpanded)
  }
}
