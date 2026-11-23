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
package org.openforis.collectone.v2.web.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnailGenerator
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.LayerStyle
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.usecase.CreateSurveyUseCase
import org.openforis.collectone.v2.map.GeometryType

/**
 * Single-purpose edits the Survey Creation Wizard applies to its working draft, its primary map
 * layer and Form, and the stored survey when it finishes. The wizard's ViewModel decides _when_ to
 * apply them (from its session state); these functions only decide _what_ changes, so they stay
 * pure and testable without the Survey editor.
 *
 * The broader re-seeding of layers and Forms from Purpose Packs lives in
 * `ConfigureWizardSurveyDraftUseCase`.
 */
object WizardDraftEdits {
  /** Name of the primary layer of a survey whose map features are drawn in the field. */
  const val DEFAULT_FIELD_LAYER_NAME = "Survey sites"

  /** Name of the primary layer once it holds generated sample plots. */
  const val DEFAULT_SAMPLE_PLOTS_LAYER_NAME = "Sample plots"

  /** Survey location shown on survey cards when neither the area nor the organization names one. */
  const val NO_SURVEY_AREA_LOCATION = "No survey area yet"

  /**
   * The sharing an organization's surveys start with: IPLC organizations default to CARE data
   * sovereignty (restricted access, collectors see only their own submissions), others share with
   * the whole organization.
   */
  fun organizationSharing(
    sharing: SharingSettings,
    organizationType: OrganizationType?,
  ): SharingSettings {
    val usesCare = SurveyWizardRules.usesIplcCareDefaults(organizationType, emptySet())
    return sharing.copy(
      policy = if (usesCare) SharingPolicy.RESTRICTED else SharingPolicy.ORGANIZATION,
      peerDataVisibility = if (usesCare) PeerDataVisibility.OWN_ONLY else PeerDataVisibility.ALL,
    )
  }

  /**
   * The draft a fresh wizard session stores for [created]: a blank title and description (so Step 1
   * prompts cleanly for a title even though the survey has a placeholder one), English as the only
   * language, and, with an [organization], its default survey area and sharing. Applying it in the
   * same transaction that creates the survey means no half-configured draft is ever synced.
   */
  fun initialDraft(created: SurveyEditorDraft, organization: Organization?): SurveyEditorDraft =
    created.copy(
      details =
        created.details.copy(
          title = "",
          description = "",
          organizationId = organization?.id,
          defaultLanguage = "en",
          supportedLanguages = listOf("en"),
          surveyArea = organization?.defaultSurveyArea(),
        ),
      sharing =
        if (organization != null) {
          organizationSharing(created.sharing, organization.organizationType)
        } else {
          created.sharing
        },
    )

  /**
   * Whether [draft] holds anything the organizer entered in the wizard (a title, description,
   * purpose, program, Form, or dataset), as opposed to only what a fresh wizard session fills in by
   * itself: the organization (possibly applied once organizations load), its default survey area,
   * and its sharing defaults. A wizard survey without organizer input isn't worth prompting about.
   */
  fun hasOrganizerInput(draft: SurveyEditorDraft): Boolean {
    val details = draft.details
    return (details.title.isNotBlank() && details.title != CreateSurveyUseCase.DEFAULT_TITLE) ||
      details.description.isNotBlank() ||
      details.purposeIds.isNotEmpty() ||
      details.otherPurpose.isNotBlank() ||
      details.programIds.isNotEmpty() ||
      draft.forms.isNotEmpty() ||
      draft.datasets.isNotEmpty()
  }

  /**
   * Turns an existing, non-generated primary [layer] into an (empty) sample plot layer with the
   * default [SampleDesignConfig]: plots are generated, never created in the field, and keyed and
   * labeled by their plot ID. A layer still carrying a default name is renamed to "Sample plots"; a
   * name the organizer typed is kept. Existing rows and the Form link are dropped because they
   * described field sites, not plots.
   */
  fun asSamplePlotsLayer(layer: EntityDataset): EntityDataset {
    val design = SampleDesignConfig()
    return layer.copy(
      displayName =
        if (layer.displayName == DEFAULT_FIELD_LAYER_NAME || layer.displayName == layer.id) {
          DEFAULT_SAMPLE_PLOTS_LAYER_NAME
        } else {
          layer.displayName
        },
      geometryType = design.geometryType,
      keyProperty = SamplePlotProperties.PLOT_ID,
      labelProperty = SamplePlotProperties.PLOT_ID,
      fieldCreationEnabled = false,
      linkedFormKey = null,
      properties = SamplePlotProperties.schema,
      style = LayerStyle(colorHex = SAMPLE_PLOTS_COLOR_HEX, fillOpacity = 0.15),
      rows = emptyList(),
      generator = design,
    )
  }

  /**
   * Turns a generated sample plot [layer] back into an empty layer of map features that collectors
   * draw in the field, named after [surveyTitle] (or "Survey sites") with the [geometryType] the
   * purposes call for, keyed by a required `id` property.
   */
  fun asFieldFreeformLayer(
    layer: EntityDataset,
    surveyTitle: String,
    geometryType: GeometryType,
  ): EntityDataset =
    layer.copy(
      displayName = surveyTitle.ifBlank { DEFAULT_FIELD_LAYER_NAME },
      geometryType = geometryType,
      keyProperty = FIELD_KEY_PROPERTY,
      labelProperty = FIELD_KEY_PROPERTY,
      fieldCreationEnabled = true,
      properties =
        listOf(EntityProperty(FIELD_KEY_PROPERTY, "ID", PropertyType.TEXT, required = true)),
      rows = emptyList(),
      generator = null,
    )

  /**
   * [form] with its first geometry question asking for a [geometryType] answer (location, line, or
   * polygon) and labeled to match. Forms without a geometry question are returned unchanged: the
   * wizard never adds one behind the organizer's back.
   */
  fun withPrimaryGeometryType(form: EditorForm, geometryType: GeometryType): EditorForm {
    val index = form.questions.indexOfFirst { it.type.isGeometry }
    if (index < 0) return form
    val questionType =
      when (geometryType) {
        GeometryType.POINT -> EditorQuestionType.LOCATION
        GeometryType.LINE_STRING -> EditorQuestionType.LINE
        GeometryType.POLYGON -> EditorQuestionType.POLYGON
      }
    val label =
      if (geometryType == GeometryType.POLYGON) "Record plot boundary" else "Record site location"
    return form.copy(
      questions =
        form.questions.mapIndexed { i, question ->
          if (i == index) question.copy(type = questionType, label = label) else question
        }
    )
  }

  /**
   * The stored survey card of [preview] once the wizard finishes: still a draft, with [title],
   * [description], [organizationId], and the sharing of [savedDraft], a location from the survey
   * area (else [organization]'s country), the number of map features on its map layers, and a
   * thumbnail.
   */
  fun finishedSurveyPreview(
    preview: SurveyPreviewItem,
    surveyId: String,
    title: String,
    description: String,
    organizationId: String?,
    organization: Organization?,
    savedDraft: SurveyEditorDraft,
  ): SurveyPreviewItem =
    preview.copy(
      title = title,
      description = description,
      organizationId = organizationId,
      location =
        savedDraft.details.surveyArea?.name?.ifBlank { null }
          ?: organization?.country?.name
          ?: NO_SURVEY_AREA_LOCATION,
      surveyArea = savedDraft.details.surveyArea ?: preview.surveyArea,
      state = SurveyLifecycleState.DRAFT,
      sharingPolicy = savedDraft.sharing.policy,
      peerDataVisibility = savedDraft.sharing.peerDataVisibility,
      collectorConsentRequired = savedDraft.sharing.collectorConsentRequired,
      collectorConsentText = savedDraft.sharing.collectorConsentText,
      entityCount =
        savedDraft.datasets.filter { it.kind == DatasetKind.MAP_LAYER }.sumOf { it.rows.size },
      thumbnail = SurveyThumbnailGenerator.fromDraft(surveyId, savedDraft),
    )

  private const val FIELD_KEY_PROPERTY = "id"

  /** Amber outline of generated sample plots, so they stand out from field-drawn features. */
  private const val SAMPLE_PLOTS_COLOR_HEX = "#F9A825"
}
