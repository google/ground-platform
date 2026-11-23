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
package org.openforis.collectone.v2.app.ui.datacollection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.AuthProfile
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationMember
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.QuotaCheckResult
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.repository.SurveyContent

/** The pure functions behind [DataCollectionViewModel]'s screen state and form launches. */
class DataCollectionUiStateBuilderTest {
  private val owner = "owner@example.com"
  private val stranger = "stranger@example.com"

  private fun survey(
    id: String = "survey-1",
    state: SurveyLifecycleState = SurveyLifecycleState.PUBLISHED,
    policy: SharingPolicy = SharingPolicy.RESTRICTED,
    organizationId: String? = null,
    quotaLimits: QuotaLimitsConfig = QuotaLimitsConfig(),
  ) =
    SurveyPreviewItem(
      id = id,
      title = "Survey $id",
      description = "",
      location = "",
      isDownloaded = true,
      entityCount = 0,
      ownerEmail = owner,
      organizationId = organizationId,
      state = state,
      sharingPolicy = policy,
      quotaLimits = quotaLimits,
    )

  private fun submission(id: String, email: String, entityId: String = "") =
    SubmissionPreviewItem(
      id = id,
      entityId = entityId,
      formId = "form",
      formTitle = "Form",
      formVersion = "1",
      collectorName = email.substringBefore('@'),
      collectorEmail = email,
      fields = emptyList(),
    )

  private fun entity(
    id: String,
    datasetId: String = "plots",
    submissions: List<SubmissionPreviewItem> = emptyList(),
    isFieldCreated: Boolean = false,
  ) =
    GeospatialEntityItem(
      id = id,
      label = "Feature $id",
      datasetId = datasetId,
      datasetName = datasetId,
      layerId = "layer-$datasetId",
      geoId = "",
      geometry = null,
      areaHectares = 0.0,
      perimeterMeters = 0,
      colorHex = 0xFF2E7D32L,
      properties = emptyMap(),
      submissions = submissions,
      isFieldCreated = isFieldCreated,
    )

  private fun form(id: String, targetDatasetId: String? = null) =
    FormPreviewItem(
      id = id,
      title = "Form $id",
      description = "",
      version = "1",
      targetDatasetId = targetDatasetId.orEmpty(),
      targetDatasetName = targetDatasetId.orEmpty(),
      questionCount = 1,
      ctaLabel = "",
    )

  // --- canCollectData ---

  @Test
  fun canCollectData_isFalseWithoutAnActiveSurvey() {
    assertFalse(canCollectData(null, owner, emptyList()))
  }

  @Test
  fun canCollectData_isFalseInClosedAndArchivedSurveys_evenForTheOwner() {
    assertFalse(canCollectData(survey(state = SurveyLifecycleState.CLOSED), owner, emptyList()))
    assertFalse(canCollectData(survey(state = SurveyLifecycleState.ARCHIVED), owner, emptyList()))
  }

  @Test
  fun canCollectData_whileNobodyIsSignedIn_allowsAnyOpenSurvey() {
    assertTrue(canCollectData(survey(), email = "", organizations = emptyList()))
  }

  @Test
  fun canCollectData_followsTheSurveysSharing() {
    assertTrue(canCollectData(survey(), owner, emptyList()))
    assertFalse(canCollectData(survey(), stranger, emptyList()))
  }

  @Test
  fun canCollectData_letsPlatformAdminsCollectInRestrictedSurveys() {
    val platform =
      Organization(
        id = Organization.ALL_USERS_ID,
        name = "All users",
        members = listOf(OrganizationMember(stranger, role = OrganizationRole.MANAGER)),
      )
    val plainMember =
      platform.copy(members = listOf(OrganizationMember(stranger, role = OrganizationRole.MEMBER)))

    assertTrue(canCollectData(survey(), stranger, listOf(platform)))
    assertFalse(canCollectData(survey(), stranger, listOf(plainMember)))
  }

  // --- buildDataCollectionUiState ---

  @Test
  fun uiState_promptsForConsentOnlyWhileALaunchIsPending() {
    val data = DataCollectionData(surveys = listOf(survey()), activeSurveyId = "survey-1")
    val drawing = WebMapDrawingHost(controller = { null }, onFrame = { _, _ -> })
    fun build(session: DataCollectionSession) =
      buildDataCollectionUiState(data, session, PdfExportState(), drawing, canSharePdfFiles = false)

    assertNull(build(DataCollectionSession()).pendingCollectorConsent)
    val prompt =
      build(DataCollectionSession(pendingConsentLaunch = PendingFormLaunch.ForEntity("e", "f")))
        .pendingCollectorConsent
    assertEquals("survey-1", prompt?.surveyId)
    assertEquals("Survey survey-1", prompt?.surveyTitle)
  }

  @Test
  fun uiState_fallsBackToTheFirstSurvey_whenTheActiveIdIsUnknown() {
    val data = DataCollectionData(surveys = listOf(survey("a"), survey("b")), activeSurveyId = "x")
    val drawing = WebMapDrawingHost(controller = { null }, onFrame = { _, _ -> })

    val ui =
      buildDataCollectionUiState(
        data,
        DataCollectionSession(),
        PdfExportState(),
        drawing,
        canSharePdfFiles = true,
      )

    assertEquals("x", ui.activeSurveyId)
    assertEquals("Survey a", ui.activeSurveyTitle)
    assertTrue(ui.canSharePdfFiles)
  }

  // --- checkSubmissionQuota ---

  private fun quotaData(
    entities: List<GeospatialEntityItem>,
    limits: QuotaLimitsConfig,
    activeSurveyId: String = "survey-1",
  ) =
    DataCollectionData(
      surveys = listOf(survey(quotaLimits = limits)),
      activeSurveyId = activeSurveyId,
      content = SurveyContent(entities = entities),
      profile = AuthProfile(displayName = "Owner", email = owner, organizationName = ""),
    )

  @Test
  fun quota_isNotCheckedWhenTheActiveSurveyIsNotOnTheDevice() {
    val data = quotaData(emptyList(), QuotaLimitsConfig(), activeSurveyId = "missing")

    assertNull(
      checkSubmissionQuota(data, activeEntityId = null, wasFormLaunchedWithoutEntity = true)
    )
  }

  @Test
  fun quota_countsOnlyTheCollectorsOwnSubmissionsOnTheFeature() {
    val plot =
      entity(
        "plot",
        submissions =
          listOf(
            submission("s1", owner.uppercase(), "plot"),
            submission("s2", stranger, "plot"),
            submission("s3", stranger, "plot"),
          ),
      )
    val oneEach = QuotaLimitsConfig(maxSubmissionsPerEntityPerUser = 1)
    val twoEach = QuotaLimitsConfig(maxSubmissionsPerEntityPerUser = 2)

    assertIs<QuotaCheckResult.Exceeded>(
      checkSubmissionQuota(quotaData(listOf(plot), oneEach), "plot", false)
    )
    assertEquals(
      QuotaCheckResult.Allowed,
      checkSubmissionQuota(quotaData(listOf(plot), twoEach), "plot", false),
    )
  }

  @Test
  fun quota_countsFieldCreatedFeatures_onlyForNewFeatures() {
    val features =
      listOf(
        entity(
          "mine",
          submissions = listOf(submission("s1", owner, "mine")),
          isFieldCreated = true,
        ),
        entity("empty", isFieldCreated = true),
        entity("theirs", submissions = listOf(submission("s2", stranger)), isFieldCreated = true),
        entity("predefined"),
      )
    val data = { max: Int -> quotaData(features, QuotaLimitsConfig(maxAdhocEntitiesPerUser = max)) }

    // "mine" and "empty" count: 2 field-created features.
    assertIs<QuotaCheckResult.Exceeded>(checkSubmissionQuota(data(2), null, true))
    assertEquals(QuotaCheckResult.Allowed, checkSubmissionQuota(data(3), null, true))
    // A form opened on a feature, or one whose target was picked, adds no new feature.
    assertEquals(QuotaCheckResult.Allowed, checkSubmissionQuota(data(2), null, false))
    assertEquals(QuotaCheckResult.Allowed, checkSubmissionQuota(data(2), "mine", true))
  }

  // --- chooseTestingFormLaunch ---

  private fun ui(
    entities: List<GeospatialEntityItem>,
    forms: List<FormPreviewItem>,
    selectedEntityId: String? = null,
  ) = DataCollectionUiState(entities = entities, forms = forms, selectedEntityId = selectedEntityId)

  @Test
  fun testingLaunch_withoutForms_opensNothing() {
    assertNull(chooseTestingFormLaunch(ui(emptyList(), emptyList())))
    assertNull(chooseTestingFormLaunch(ui(listOf(entity("a")), emptyList())))
  }

  @Test
  fun testingLaunch_withoutFeatures_prefersAStandaloneForm() {
    val forms = listOf(form("on-plots", "plots"), form("standalone"))

    assertEquals(
      TestingFormLaunch.FromFab("standalone"),
      chooseTestingFormLaunch(ui(emptyList(), forms)),
    )
    assertEquals(
      TestingFormLaunch.FromFab("on-plots"),
      chooseTestingFormLaunch(ui(emptyList(), listOf(form("on-plots", "plots")))),
    )
  }

  @Test
  fun testingLaunch_prefersTheSelectedFeature_thenTheWorkbenchDefault_thenAnyFeature() {
    val forms = listOf(form("plot-form", "plots"))
    val first = entity("first")
    val default = entity(WORKBENCH_DEFAULT_ENTITY_ID)
    val selected = entity("selected")
    val entities = listOf(first, default, selected)

    assertEquals(
      TestingFormLaunch.ForEntity("selected", "plot-form"),
      chooseTestingFormLaunch(ui(entities, forms, selectedEntityId = "selected")),
    )
    assertEquals(
      TestingFormLaunch.ForEntity(WORKBENCH_DEFAULT_ENTITY_ID, "plot-form"),
      chooseTestingFormLaunch(ui(entities, forms)),
    )
    assertEquals(
      TestingFormLaunch.ForEntity("first", "plot-form"),
      chooseTestingFormLaunch(ui(listOf(first, selected), forms)),
    )
  }

  @Test
  fun testingLaunch_fallsBackToAFeatureWithAForm_thenToTheFirstForm() {
    val trees = entity("tree", datasetId = "trees")
    val plot = entity("plot")

    assertEquals(
      TestingFormLaunch.ForEntity("plot", "plot-form"),
      chooseTestingFormLaunch(ui(listOf(trees, plot), listOf(form("plot-form", "plots")))),
    )
    assertEquals(
      TestingFormLaunch.FromFab("standalone"),
      chooseTestingFormLaunch(ui(listOf(trees), listOf(form("standalone")))),
    )
  }
}
