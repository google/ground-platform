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
package org.openforis.collectone.v2.web.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.AuthRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.SurveyRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.domain.model.ConceptLink
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.QuotaCheckResult
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.GenerationRecord
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorForm
import org.openforis.collectone.v2.app.domain.usecase.CreateSurveyUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.domain.model.ImpactMessage
import org.openforis.collectone.v2.web.domain.model.ImpactText
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.domain.model.editor.WizardDraftEdits
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod

class WizardDomainTest {
  private val kfs = SampleOrganizationsData.KENYA_FOREST_SERVICE

  private fun fixedClock(iso: String = "2026-10-05T12:00:00Z") =
    object : Clock {
      override fun now(): Instant = Instant.parse(iso)
    }

  private fun simpleLayer(
    key: String = "d1",
    displayName: String = WizardDraftEdits.DEFAULT_FIELD_LAYER_NAME,
    geometryType: GeometryType = GeometryType.POINT,
    rows: List<EntityRow> = emptyList(),
    generator: SampleDesignConfig? = null,
  ) =
    EntityDataset(
      key = key,
      kind = DatasetKind.MAP_LAYER,
      id = "sites",
      displayName = displayName,
      geometryType = geometryType,
      keyProperty = "id",
      labelProperty = "id",
      properties = listOf(EntityProperty("id", "ID")),
      rows = rows,
      generator = generator,
    )

  @Test
  fun wizardDraftEdits_handlesSharingAndInitialDraft() = runNow {
    val store = seededStore()
    val organizations = OrganizationRepositoryImpl(store).getOrganizations()
    val kenyaOrg = organizations.first { it.id == kfs }
    val iplcOrg = kenyaOrg.copy(organizationType = OrganizationType.IPLC_ORGANIZATION)

    val defaultSharing = SharingSettings(ownerEmail = "maya@example.org")
    val orgSharing = WizardDraftEdits.organizationSharing(defaultSharing, kenyaOrg.organizationType)
    assertEquals(SharingPolicy.ORGANIZATION, orgSharing.policy)
    assertEquals(PeerDataVisibility.ALL, orgSharing.peerDataVisibility)

    val iplcSharing = WizardDraftEdits.organizationSharing(defaultSharing, iplcOrg.organizationType)
    assertEquals(SharingPolicy.RESTRICTED, iplcSharing.policy)
    assertEquals(PeerDataVisibility.OWN_ONLY, iplcSharing.peerDataVisibility)

    val baseDraft = SurveyEditorDraft.blank(surveyId = "s-1", title = "Placeholder")
    val initializedWithOrg = WizardDraftEdits.initialDraft(baseDraft, iplcOrg)
    assertEquals("", initializedWithOrg.details.title)
    assertEquals("", initializedWithOrg.details.description)
    assertEquals(iplcOrg.id, initializedWithOrg.details.organizationId)
    assertEquals(SharingPolicy.RESTRICTED, initializedWithOrg.sharing.policy)

    val initializedNoOrg = WizardDraftEdits.initialDraft(baseDraft, null)
    assertNull(initializedNoOrg.details.organizationId)
    assertEquals(baseDraft.sharing.policy, initializedNoOrg.sharing.policy)
  }

  @Test
  fun wizardDraftEdits_transformsLayersAndForms() {
    val freeformLayer = simpleLayer(rows = listOf(EntityRow("r1", mapOf("id" to "1"))))

    val plotsLayer = WizardDraftEdits.asSamplePlotsLayer(freeformLayer)
    assertEquals(WizardDraftEdits.DEFAULT_SAMPLE_PLOTS_LAYER_NAME, plotsLayer.displayName)
    assertTrue(plotsLayer.isGenerated)
    assertTrue(plotsLayer.rows.isEmpty())
    assertEquals(SamplePlotProperties.PLOT_ID, plotsLayer.keyProperty)

    val customNamedPlotsLayer =
      WizardDraftEdits.asSamplePlotsLayer(freeformLayer.copy(displayName = "Coffee stands"))
    assertEquals("Coffee stands", customNamedPlotsLayer.displayName)

    val backToField =
      WizardDraftEdits.asFieldFreeformLayer(
        customNamedPlotsLayer,
        surveyTitle = "  ",
        geometryType = GeometryType.LINE_STRING,
      )
    assertEquals(WizardDraftEdits.DEFAULT_FIELD_LAYER_NAME, backToField.displayName)
    assertEquals(GeometryType.LINE_STRING, backToField.geometryType)
    assertFalse(backToField.isGenerated)

    val formWithoutGeom =
      EditorForm("f1", "Form", listOf(EditorQuestion("q1", "notes", EditorQuestionType.TEXT, "N")))
    assertEquals(
      formWithoutGeom,
      WizardDraftEdits.withPrimaryGeometryType(formWithoutGeom, GeometryType.POLYGON),
    )

    val formWithGeom =
      EditorForm(
        "f1",
        "Form",
        listOf(
          EditorQuestion("q1", "loc", EditorQuestionType.LOCATION, "Loc"),
          EditorQuestion("q2", "notes", EditorQuestionType.TEXT, "Notes"),
        ),
      )
    assertEquals(
      EditorQuestionType.POLYGON,
      WizardDraftEdits.withPrimaryGeometryType(formWithGeom, GeometryType.POLYGON)
        .questions
        .first()
        .type,
    )
    assertEquals(
      EditorQuestionType.LINE,
      WizardDraftEdits.withPrimaryGeometryType(formWithGeom, GeometryType.LINE_STRING)
        .questions
        .first()
        .type,
    )
  }

  @Test
  fun wizardDraftEdits_buildsFinishedSurveyPreviewWithLocationFallbacks() = runNow {
    val store = seededStore()
    val kenyaOrg = OrganizationRepositoryImpl(store).getOrganizations().first { it.id == kfs }
    val freeformLayer = simpleLayer(rows = listOf(EntityRow("r1", mapOf("id" to "1"))))
    val baseDraft = SurveyEditorDraft.blank(surveyId = "s-1")
    val surveyPreview = SurveyRepositoryImpl(store).getSurveys().first()

    val finishedWithArea =
      WizardDraftEdits.finishedSurveyPreview(
        preview = surveyPreview,
        surveyId = surveyPreview.id,
        title = "Final title",
        description = "Final desc",
        organizationId = kfs,
        organization = kenyaOrg,
        savedDraft =
          baseDraft.copy(
            details =
              baseDraft.details.copy(
                surveyArea = SurveyArea("Mau Forest", listOf(listOf(LatLng(0.0, 35.0))))
              ),
            datasets = listOf(freeformLayer),
          ),
      )
    assertEquals("Mau Forest", finishedWithArea.location)
    assertEquals(1, finishedWithArea.entityCount)

    val finishedWithOrgCountry =
      WizardDraftEdits.finishedSurveyPreview(
        preview = surveyPreview,
        surveyId = surveyPreview.id,
        title = "Final title",
        description = "Final desc",
        organizationId = kfs,
        organization = kenyaOrg,
        savedDraft = baseDraft.copy(details = baseDraft.details.copy(surveyArea = null)),
      )
    assertEquals("Kenya", finishedWithOrgCountry.location)

    val finishedWithoutLocation =
      WizardDraftEdits.finishedSurveyPreview(
        preview = surveyPreview,
        surveyId = surveyPreview.id,
        title = "Final title",
        description = "Final desc",
        organizationId = null,
        organization = null,
        savedDraft = baseDraft.copy(details = baseDraft.details.copy(surveyArea = null)),
      )
    assertEquals(WizardDraftEdits.NO_SURVEY_AREA_LOCATION, finishedWithoutLocation.location)
  }

  @Test
  fun checkWizardSurveyQuota_enforcesOwnerAndDesignQuotas_andExemptsPlatformAdmins() = runNow {
    val store = seededStore()
    val organizations = OrganizationRepositoryImpl(store).getOrganizations()
    val surveys = SurveyRepositoryImpl(store).getSurveys()
    val useCase = CheckWizardSurveyQuotaUseCase()

    val constrainedOrg =
      organizations
        .first { it.id == kfs }
        .copy(quotaLimits = QuotaLimitsConfig(maxSurveysPerOwner = 1, maxFormsPerSurvey = 1))
    val orgList = organizations.map { if (it.id == kfs) constrainedOrg else it }

    val blank = SurveyEditorDraft.blank(surveyId = "working-survey")
    val draft = blank.copy(details = blank.details.copy(organizationId = kfs))
    val nonAdminOrgs = orgList.map { if (it.isSynthetic) it.copy(members = emptyList()) else it }
    val ownerExceeded =
      useCase(
        draft = draft,
        organizations = nonAdminOrgs,
        existingSurveys = surveys,
        signedInUserEmail = SampleOrganizationsData.SIGNED_IN_EMAIL,
        workingSurveyId = "working-survey",
      )
    assertIs<QuotaCheckResult.Exceeded>(ownerExceeded)

    val allowedForAdmin =
      useCase(
        draft = draft,
        organizations = orgList,
        existingSurveys = surveys,
        signedInUserEmail = SampleOrganizationsData.SIGNED_IN_EMAIL,
        workingSurveyId = "working-survey",
      )
    assertEquals(QuotaCheckResult.Allowed, allowedForAdmin)

    val twoFormsDraft =
      draft.copy(
        forms =
          listOf(
            SurveyEditorForm("f1", EditorForm("id1", "F1", emptyList())),
            SurveyEditorForm("f2", EditorForm("id2", "F2", emptyList())),
          )
      )
    val designExceeded =
      useCase(
        draft = twoFormsDraft,
        organizations = orgList,
        existingSurveys = surveys,
        signedInUserEmail = SampleOrganizationsData.SIGNED_IN_EMAIL,
        workingSurveyId = "working-survey",
      )
    assertIs<QuotaCheckResult.Exceeded>(designExceeded)
  }

  @Test
  fun findUnfinishedWizardSurvey_identifiesOrphanedDraftWithWorkingCopyAndNoPurpose() = runNow {
    val store = seededStore()
    val surveysRepo = SurveyRepositoryImpl(store)
    val editorRepo = SurveyEditorRepositoryImpl(store)
    val useCase = FindUnfinishedWizardSurveyUseCase(editorRepo)
    val owner = SampleOrganizationsData.SIGNED_IN_EMAIL

    assertNull(useCase(surveysRepo.getSurveys(), owner))

    val draftSurvey =
      surveysRepo.getSurveys().first { it.ownerEmail.equals(owner, ignoreCase = true) }
    surveysRepo.setSurveyState(draftSurvey.id, SurveyLifecycleState.DRAFT, null)
    val saved = editorRepo.getDraft(draftSurvey.id)
    val withoutPurpose =
      saved.copy(details = saved.details.copy(purposeIds = emptyList(), otherPurpose = ""))
    editorRepo.saveDraft(draftSurvey.id, withoutPurpose)
    editorRepo.autosaveDraft(
      draftSurvey.id,
      withoutPurpose.copy(details = withoutPurpose.details.copy(otherPurpose = "In progress")),
    )

    // A titled draft with unsaved Survey editor edits wasn't made by the wizard.
    assertNull(useCase(surveysRepo.getSurveys(), owner))

    // The wizard's survey keeps the placeholder title until the wizard finishes.
    surveysRepo.updateSurvey(draftSurvey.id) { it.copy(title = CreateSurveyUseCase.DEFAULT_TITLE) }
    val found = useCase(surveysRepo.getSurveys(), owner)
    assertNotNull(found)
    assertEquals(draftSurvey.id, found.survey.id)
    assertEquals("In progress", found.workingCopy.details.otherPurpose)

    editorRepo.saveDraft(
      draftSurvey.id,
      saved.copy(details = saved.details.copy(purposeIds = listOf("eudr_due_diligence"))),
    )
    assertNull(useCase(surveysRepo.getSurveys(), owner))
  }

  @Test
  fun configureWizardSurveyDraft_copiesStructureAndSynchronizesModes() = runNow {
    val store = seededStore()
    val libraries = LibraryRepositoryImpl(store).observeLibraries().first()
    val library = ResolveLibraryUseCase().forOrganization(libraries, kfs)
    val useCase = ConfigureWizardSurveyDraftUseCase()

    val baseSource = SurveyEditorDraft.blank(surveyId = "src", title = "Source")
    val sourceDraft =
      baseSource.copy(
        details =
          baseSource.details.copy(
            defaultLanguage = "fr",
            supportedLanguages = listOf("fr", "en"),
            purposeIds = listOf("eudr_due_diligence"),
            otherPurpose = "Custom",
            programIds = listOf("eudr"),
          ),
        forms = listOf(SurveyEditorForm("f1", EditorForm("fid-1", "Form 1", emptyList()))),
        datasets =
          listOf(
            simpleLayer(
              rows = listOf(EntityRow("r1", mapOf("id" to "1"))),
              generator =
                SampleDesignConfig(lastRun = GenerationRecord("2026-01-01T00:00:00Z", "h", 1)),
            )
          ),
        nextKeyId = 120,
      )
    val copied =
      useCase.copyStructureFromSource(SurveyEditorDraft.blank(surveyId = "target"), sourceDraft)
    assertEquals("fr", copied.details.defaultLanguage)
    assertEquals(listOf("eudr_due_diligence"), copied.details.purposeIds)
    assertEquals("Custom", copied.details.otherPurpose)
    assertTrue(copied.datasets.single().rows.isEmpty())
    assertNull(copied.datasets.single().generator?.lastRun)
    assertEquals(130, copied.nextKeyId)

    val visualSync =
      useCase.synchronizePrimaryLayerAndForms(
        current = SurveyEditorDraft.blank(surveyId = "s-vis"),
        library = library,
        startMethod = WizardStartMethod.FROM_SCRATCH,
        effectiveMode = SurveyCollectionMode.VISUAL_CLASSIFICATION,
        mapFeatureSource = WizardMapFeatureSource.SAMPLE_PLOTS,
        isGeometryTypeOverridden = false,
        isEudrPlotsUnderFourHaConfirmed = false,
        hasPurposeSelectionChanged = false,
        previousSeededFormKeys = emptySet(),
      )
    val visualLayer = visualSync.draft.datasets.single()
    assertTrue(visualLayer.isGenerated)
    assertEquals("Plot interpretation", visualSync.draft.forms.single().form.title)
    assertEquals(SaveToMode.UPDATE, visualSync.draft.forms.single().form.saveTo.mode)

    val freeformSync =
      useCase.synchronizePrimaryLayerAndForms(
        current =
          visualSync.draft.copy(
            forms = emptyList(),
            datasets =
              listOf(visualLayer.copy(generator = null, geometryType = GeometryType.LINE_STRING)),
          ),
        library = library,
        startMethod = WizardStartMethod.FROM_SCRATCH,
        effectiveMode = SurveyCollectionMode.FIELD_SURVEY,
        mapFeatureSource = WizardMapFeatureSource.FIELD_FREEFORM,
        isGeometryTypeOverridden = true,
        isEudrPlotsUnderFourHaConfirmed = false,
        hasPurposeSelectionChanged = false,
        previousSeededFormKeys = emptySet(),
      )
    val lineForm = freeformSync.draft.forms.single().form
    assertEquals(FormAvailability.BOTH, lineForm.availability)
    assertEquals(EditorQuestionType.LINE, lineForm.questions.first().type)
    assertEquals(GeometryCapture.GPS_OR_MAP, lineForm.questions.first().capture)
    assertEquals(SaveToMode.CREATE, lineForm.saveTo.mode)
  }

  @Test
  fun configureWizardSurveyDraft_preservesCustomizedAvailabilityAndGeometryCapture() = runNow {
    val store = seededStore()
    val libraries = LibraryRepositoryImpl(store).observeLibraries().first()
    val library = ResolveLibraryUseCase().forOrganization(libraries, kfs)
    val useCase = ConfigureWizardSurveyDraftUseCase()

    val seeded =
      useCase.synchronizePrimaryLayerAndForms(
        current = SurveyEditorDraft.blank(surveyId = "s-custom"),
        library = library,
        startMethod = WizardStartMethod.FROM_SCRATCH,
        effectiveMode = SurveyCollectionMode.FIELD_SURVEY,
        mapFeatureSource = WizardMapFeatureSource.FIELD_FREEFORM,
        isGeometryTypeOverridden = false,
        isEudrPlotsUnderFourHaConfirmed = false,
        hasPurposeSelectionChanged = false,
        previousSeededFormKeys = emptySet(),
      )
    val customizedForms =
      seeded.draft.forms.map { entry ->
        entry.copy(
          form =
            entry.form.copy(
              availability = FormAvailability.MOBILE,
              questions =
                entry.form.questions.map { q ->
                  if (q.type.isGeometry) q.copy(capture = GeometryCapture.GPS_ONLY) else q
                },
            )
        )
      }
    val resynced =
      useCase.synchronizePrimaryLayerAndForms(
        current = seeded.draft.copy(forms = customizedForms),
        library = library,
        startMethod = WizardStartMethod.FROM_SCRATCH,
        effectiveMode = SurveyCollectionMode.FIELD_SURVEY,
        mapFeatureSource = WizardMapFeatureSource.FIELD_FREEFORM,
        isGeometryTypeOverridden = false,
        isEudrPlotsUnderFourHaConfirmed = false,
        hasPurposeSelectionChanged = false,
        previousSeededFormKeys = seeded.seededFormKeys,
      )
    val preservedForm = resynced.draft.forms.single().form
    assertEquals(FormAvailability.MOBILE, preservedForm.availability)
    assertEquals(GeometryCapture.GPS_ONLY, preservedForm.questions.first().capture)
  }

  @Test
  fun configureWizardSurveyDraft_seedsMultiplePackTemplatesAndClearsMissingDatasetLinks() = runNow {
    val store = seededStore()
    val libraries = LibraryRepositoryImpl(store).observeLibraries().first()
    val useCase = ConfigureWizardSurveyDraftUseCase()
    val speciesQuestion =
      EditorQuestion(
        key = "q_species",
        name = "species",
        type = EditorQuestionType.SELECT_ONE,
        label = "Species",
        choiceSource = ChoiceSource.DATA_TABLE,
        choiceDatasetId = "non_existent_table",
        allowAddEntity = true,
      )
    val customTemplate =
      FormTemplate(
        id = "org.$kfs.missing_ds_template",
        organizationId = kfs,
        title = LocalizedText.en("Species check"),
        form = EditorForm("t-form", "Species check", listOf(speciesQuestion)),
      )
    val kfsLib = libraries.getValue(kfs)
    val updatedKfsLib =
      kfsLib.copy(
        formTemplates = kfsLib.formTemplates + customTemplate,
        purposePacks =
          kfsLib.purposePacks.map { pack ->
            if (pack.id == "org.$kfs.coop_certification_audit") {
              pack.copy(formTemplateIds = listOf(customTemplate.id, "eudr_plot_registration"))
            } else {
              pack
            }
          },
      )
    val customLibrary =
      ResolveLibraryUseCase().forOrganization(libraries + (kfs to updatedKfsLib), kfs)

    val baseMulti = SurveyEditorDraft.blank(surveyId = "s-multi")
    val multiPackSync =
      useCase.synchronizePrimaryLayerAndForms(
        current =
          baseMulti.copy(
            details =
              baseMulti.details.copy(
                purposeIds =
                  listOf("org.$kfs.coop_certification_audit", SurveyWizardRules.OTHER_PURPOSE_ID)
              )
          ),
        library = customLibrary,
        startMethod = WizardStartMethod.FROM_SCRATCH,
        effectiveMode = SurveyCollectionMode.FIELD_SURVEY,
        mapFeatureSource = WizardMapFeatureSource.FIELD_FREEFORM,
        isGeometryTypeOverridden = false,
        isEudrPlotsUnderFourHaConfirmed = false,
        hasPurposeSelectionChanged = true,
        previousSeededFormKeys = emptySet(),
      )
    val primarySpeciesQ =
      multiPackSync.draft.forms.first().form.questions.first { it.name == "species" }
    assertEquals(ChoiceSource.MANUAL, primarySpeciesQ.choiceSource)
    assertNull(primarySpeciesQ.choiceDatasetId)
    assertFalse(primarySpeciesQ.allowAddEntity)
    assertEquals(2, multiPackSync.seededFormKeys.size)

    val clearedSync =
      useCase.synchronizePrimaryLayerAndForms(
        current =
          multiPackSync.draft.copy(
            details = multiPackSync.draft.details.copy(purposeIds = emptyList())
          ),
        library = customLibrary,
        startMethod = WizardStartMethod.FROM_SCRATCH,
        effectiveMode = SurveyCollectionMode.FIELD_SURVEY,
        mapFeatureSource = WizardMapFeatureSource.FIELD_FREEFORM,
        isGeometryTypeOverridden = false,
        isEudrPlotsUnderFourHaConfirmed = false,
        hasPurposeSelectionChanged = true,
        previousSeededFormKeys = multiPackSync.seededFormKeys,
      )
    assertTrue(clearedSync.seededFormKeys.isEmpty())
  }

  @Test
  fun surveyWizardRules_sortsPacksAndResolvesModeAndCareDefaults() = runNow {
    val store = seededStore()
    val libraries = LibraryRepositoryImpl(store).observeLibraries().first()
    val library = ResolveLibraryUseCase().forOrganization(libraries, kfs)

    val fieldSorted = SurveyWizardRules.sortPurposePacks(library, SurveyCollectionMode.FIELD_SURVEY)
    val visualSorted =
      SurveyWizardRules.sortPurposePacks(library, SurveyCollectionMode.VISUAL_CLASSIFICATION)
    val bothSorted = SurveyWizardRules.sortPurposePacks(library, SurveyCollectionMode.BOTH)
    assertEquals("org.$kfs.coop_certification_audit", fieldSorted.first().id)
    assertEquals(fieldSorted.size, visualSorted.size)
    assertEquals(fieldSorted.size, bothSorted.size)

    assertEquals(
      WizardMapFeatureSource.SAMPLE_PLOTS,
      SurveyWizardRules.defaultMapFeatureSource(listOf("lulc_training_data")),
    )
    assertEquals(
      WizardMapFeatureSource.FIELD_FREEFORM,
      SurveyWizardRules.defaultMapFeatureSource(listOf("producer_registration")),
    )
    assertEquals(
      SurveyCollectionMode.VISUAL_CLASSIFICATION,
      SurveyWizardRules.defaultCollectionMode(
        listOf("deforestation_activity_data"),
        WizardMapFeatureSource.FIELD_FREEFORM,
      ),
    )
    assertEquals(
      SurveyCollectionMode.FIELD_SURVEY,
      SurveyWizardRules.defaultCollectionMode(
        listOf("forest_carbon_inventory"),
        WizardMapFeatureSource.SAMPLE_PLOTS,
      ),
    )
    assertTrue(
      SurveyWizardRules.offersPlotFieldValidation(
        WizardMapFeatureSource.SAMPLE_PLOTS,
        SurveyCollectionMode.BOTH,
      )
    )
    assertFalse(
      SurveyWizardRules.offersPlotFieldValidation(
        WizardMapFeatureSource.SAMPLE_PLOTS,
        SurveyCollectionMode.FIELD_SURVEY,
      )
    )
    assertTrue(
      SurveyWizardRules.requiresEudrPolygonGuardrail(
        listOf("eudr_due_diligence"),
        mapOf("eudr_due_diligence" to "eudr"),
      )
    )
    assertFalse(
      SurveyWizardRules.requiresEudrPolygonGuardrail(
        listOf("eudr_due_diligence"),
        mapOf("eudr_due_diligence" to "uk_frc"),
      )
    )
    assertTrue(SurveyWizardRules.usesIplcCareDefaults(null, setOf("iplc.territory_name")))
  }

  @Test
  fun evaluateWizardImpactReadiness_summarizesPacksConceptsAndCareStatus() = runNow {
    val store = seededStore()
    val libraries = LibraryRepositoryImpl(store).observeLibraries().first()
    val library = ResolveLibraryUseCase().forOrganization(libraries, kfs)
    val kenyaOrg = OrganizationRepositoryImpl(store).getOrganizations().first { it.id == kfs }
    val evaluate = EvaluateWizardImpactReadinessUseCase()

    val blank = SurveyEditorDraft.blank(surveyId = "s-eval")
    val questions =
      listOf(
        EditorQuestion(
          key = "q1",
          name = "commodity",
          type = EditorQuestionType.SELECT_ONE,
          label = "Commodity",
          conceptLink = ConceptLink("eudr.commodity", 1),
        ),
        EditorQuestion("q2", "intro", EditorQuestionType.NOTE, "Note"),
      )
    val draft =
      blank.copy(
        details =
          blank.details.copy(
            purposeIds = listOf("eudr_due_diligence", SurveyWizardRules.OTHER_PURPOSE_ID),
            otherPurpose = "Community watershed",
            programIds = listOf("eudr"),
          ),
        sharing =
          blank.sharing.copy(
            policy = SharingPolicy.RESTRICTED,
            peerDataVisibility = PeerDataVisibility.OWN_ONLY,
            collectorConsentRequired = true,
          ),
        forms = listOf(SurveyEditorForm("f1", EditorForm("fid", "Form", questions))),
        datasets =
          listOf(
            simpleLayer()
              .copy(
                properties =
                  listOf(
                    EntityProperty("area_ha", "Area", conceptLink = ConceptLink("core.area_ha", 1))
                  )
              )
          ),
      )

    val readiness =
      evaluate(
        draft = draft,
        library = library,
        selectedOrganization = kenyaOrg.copy(excludeFromPlatformAggregates = true),
        language = "en",
        isCareSovereigntyApplied = true,
        isCollectorConsentRequired = true,
      )
    assertEquals(1, readiness.linkedConceptCount)
    assertEquals(1, readiness.totalQuestionCount)
    assertTrue(readiness.careSovereigntyApplied)
    assertTrue(readiness.excludeFromPlatformAggregates)
    assertTrue(
      readiness.selectedPackTitles.contains(
        ImpactText.of(ImpactMessage.OTHER_PURPOSE_NAMED, "Community watershed")
      )
    )
    assertTrue(readiness.readyExportProfileCount >= 0)
  }

  @Test
  fun surveyLifecycleUseCase_transitionsStatesAndSavesOutcomes() = runNow {
    val store = seededStore()
    val surveysRepo = SurveyRepositoryImpl(store)
    val authRepo = AuthRepositoryImpl()
    val clock = fixedClock("2026-10-05T12:00:00Z")
    val lifecycle = SurveyLifecycleUseCase(surveysRepo, authRepo, recordImpactEvent = null, clock)

    val publishedId =
      surveysRepo.getSurveys().first { it.state == SurveyLifecycleState.PUBLISHED }.id
    assertFalse(lifecycle.setState("missing-id", SurveyLifecycleState.CLOSED))
    assertFalse(lifecycle.setState(publishedId, SurveyLifecycleState.PUBLISHED))

    assertTrue(lifecycle.setState(publishedId, SurveyLifecycleState.CLOSED))
    val closedSurvey = surveysRepo.getSurveys().first { it.id == publishedId }
    assertEquals(SurveyLifecycleState.CLOSED, closedSurvey.state)
    assertEquals("2026-10-05T12:00:00Z", closedSurvey.closedAt)

    assertFalse(lifecycle.setState(publishedId, SurveyLifecycleState.ARCHIVED))
    assertEquals(
      "2026-10-05T12:00:00Z",
      surveysRepo.getSurveys().first { it.id == publishedId }.closedAt,
    )

    assertFalse(lifecycle.setState(publishedId, SurveyLifecycleState.PUBLISHED))
    assertNull(surveysRepo.getSurveys().first { it.id == publishedId }.closedAt)

    val combined =
      lifecycle.saveOutcome(
        surveyId = publishedId,
        outcomes = setOf(SurveyOutcomeKind.NOT_YET, SurveyOutcomeKind.SHARED_WITH_BUYERS),
        effortComparison = EffortComparison.LESS,
      )
    assertEquals(setOf(SurveyOutcomeKind.SHARED_WITH_BUYERS), combined.outcomes)
    assertEquals(EffortComparison.LESS, combined.effortComparison)

    val notYetOnly =
      lifecycle.saveOutcome(
        surveyId = publishedId,
        outcomes = setOf(SurveyOutcomeKind.NOT_YET),
        effortComparison = null,
      )
    assertEquals(setOf(SurveyOutcomeKind.NOT_YET), notYetOnly.outcomes)
  }
}
