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
package org.groundplatform.v2.web.domain.usecase

import org.groundplatform.v2.app.domain.model.FormTemplate
import org.groundplatform.v2.app.domain.model.editor.ChoiceSource
import org.groundplatform.v2.app.domain.model.editor.DatasetKind
import org.groundplatform.v2.app.domain.model.editor.EditorChoice
import org.groundplatform.v2.app.domain.model.editor.EditorForm
import org.groundplatform.v2.app.domain.model.editor.EditorFormTemplates
import org.groundplatform.v2.app.domain.model.editor.EditorQuestion
import org.groundplatform.v2.app.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.app.domain.model.editor.EditorSaveTo
import org.groundplatform.v2.app.domain.model.editor.EntityDataset
import org.groundplatform.v2.app.domain.model.editor.EntityProperty
import org.groundplatform.v2.app.domain.model.editor.FormDatasetLinks
import org.groundplatform.v2.app.domain.model.editor.FormIds
import org.groundplatform.v2.app.domain.model.editor.GeometryKind
import org.groundplatform.v2.app.domain.model.editor.LayerStyle
import org.groundplatform.v2.app.domain.model.editor.PropertyType
import org.groundplatform.v2.app.domain.model.editor.SampleDesignConfig
import org.groundplatform.v2.app.domain.model.editor.SamplePlotProperties
import org.groundplatform.v2.app.domain.model.editor.SaveToMode
import org.groundplatform.v2.app.domain.model.editor.SaveToRules
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDerivation
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorForm
import org.groundplatform.v2.app.domain.model.editor.SurveyFormTemplates
import org.groundplatform.v2.app.domain.model.editor.slugify
import org.groundplatform.v2.app.domain.model.editor.toEditorDataset
import org.groundplatform.v2.app.domain.model.geometryKind
import org.groundplatform.v2.app.domain.model.hasGeometry
import org.groundplatform.v2.app.domain.usecase.ResolvedLibrary
import org.groundplatform.v2.web.domain.model.editor.SurveyCollectionMode
import org.groundplatform.v2.web.domain.model.editor.SurveyWizardRules
import org.groundplatform.v2.web.domain.model.editor.WizardMapFeatureSource
import org.groundplatform.v2.web.domain.model.editor.WizardStartMethod

/** Result of synchronizing the wizard's working draft with selected purpose packs and mode. */
data class WizardDraftSyncResult(
  val draft: SurveyEditorDraft,
  val seededFormKeys: Set<String>,
)

/**
 * Pure domain transformations that configure and synchronize a [SurveyEditorDraft] as the user
 * progresses through the 6-step Survey Creation Wizard.
 */
class ConfigureWizardSurveyDraftUseCase {
  /**
   * Clones forms, map layer schemas, survey area, languages, and purpose/program selections from
   * [sourceDraft] into [targetDraft] without copying collected feature rows.
   */
  fun copyStructureFromSource(
    targetDraft: SurveyEditorDraft,
    sourceDraft: SurveyEditorDraft,
  ): SurveyEditorDraft {
    val copiedDatasets =
      sourceDraft.datasets.map { dataset ->
        dataset.copy(
          rows = emptyList(),
          generator = dataset.generator?.copy(lastRun = null),
        )
      }
    val copiedForms =
      sourceDraft.forms.map { entry ->
        entry.copy(form = entry.form.copy(formId = FormIds.newFormId()))
      }
    return targetDraft.copy(
      details =
        targetDraft.details.copy(
          defaultLanguage = sourceDraft.details.defaultLanguage,
          supportedLanguages = sourceDraft.details.supportedLanguages,
          surveyArea = sourceDraft.details.surveyArea ?: targetDraft.details.surveyArea,
          purposeIds = (targetDraft.details.purposeIds + sourceDraft.details.purposeIds).distinct(),
          otherPurpose =
            sourceDraft.details.otherPurpose.ifBlank { targetDraft.details.otherPurpose },
          programIds = (targetDraft.details.programIds + sourceDraft.details.programIds).distinct(),
        ),
      forms = copiedForms,
      datasets = copiedDatasets,
      nextKeyId = maxOf(targetDraft.nextKeyId, sourceDraft.nextKeyId + 10),
    )
  }

  /**
   * Ensures [current] has a primary `MAP_LAYER` and a primary `EditorForm` configured according to
   * the selected Purpose Packs, [effectiveMode], and [mapFeatureSource], plus one Form (with its
   * own linked dataset) for each additional template of the selected Purpose Packs.
   */
  fun synchronizePrimaryLayerAndForms(
    current: SurveyEditorDraft,
    library: ResolvedLibrary,
    startMethod: WizardStartMethod,
    effectiveMode: SurveyCollectionMode,
    mapFeatureSource: WizardMapFeatureSource,
    isGeometryKindOverridden: Boolean,
    isEudrPlotsUnderFourHaConfirmed: Boolean,
    hasPurposeSelectionChanged: Boolean,
    previousSeededFormKeys: Set<String>,
  ): WizardDraftSyncResult {
    var datasets = current.datasets
    var forms = current.forms
    var nextId = current.nextKeyId
    var seededFormKeys = previousSeededFormKeys
    fun newKey(prefix: String) = "$prefix${nextId++}"

    val defaultGeomKind =
      SurveyWizardRules.defaultGeometryKind(
        purposeIds = current.details.purposeIds,
        mapFeatureSource = mapFeatureSource,
        eudrPlotsUnderFourHaConfirmed = isEudrPlotsUnderFourHaConfirmed,
      )

    var extraTemplates = emptyList<FormTemplate>()
    if (hasPurposeSelectionChanged && startMethod == WizardStartMethod.FROM_SCRATCH) {
      val nonOtherIds =
        current.details.purposeIds.filter { it != SurveyWizardRules.OTHER_PURPOSE_ID }
      val templates = library.templatesForPurposes(nonOtherIds)
      val primaryEntry = forms.firstOrNull()
      val staleKeys = previousSeededFormKeys - setOfNotNull(primaryEntry?.key)
      forms = forms.filterNot { it.key in staleKeys }
      datasets = datasets.filterNot { d -> d.linkedFormKey?.let { it in staleKeys } == true }
      if (templates.isNotEmpty()) {
        val language = current.details.defaultLanguage.ifBlank { "en" }
        val primaryTemplate = templates.first()
        val baseTitle =
          primaryTemplate.title
            .get(language)
            .ifBlank { primaryTemplate.form.title }
            .ifBlank { "Site assessment" }
        val questions =
          primaryTemplate.form.questions.map { q ->
            if (q.choiceDatasetId != null && datasets.none { it.id == q.choiceDatasetId }) {
              q.copy(
                choiceDatasetId = null,
                choiceSource = ChoiceSource.MANUAL,
                allowAddEntity = false,
              )
            } else {
              q
            }
          }
        val existingFormKey = primaryEntry?.key ?: newKey("f")
        val seededForm =
          primaryTemplate.form.copy(
            formId = primaryEntry?.form?.formId ?: FormIds.newFormId(),
            title = baseTitle,
            questions = questions,
            saveTo = EditorSaveTo(),
          )
        forms = listOf(SurveyEditorForm(existingFormKey, seededForm)) + forms.drop(1)
        extraTemplates = templates.drop(1)
        seededFormKeys = setOf(existingFormKey)
      } else {
        if (primaryEntry != null && primaryEntry.key in previousSeededFormKeys) {
          forms = forms.drop(1)
        }
        seededFormKeys = emptySet()
      }
    }

    var primaryLayer = datasets.firstOrNull { it.kind == DatasetKind.MAP_LAYER }
    if (primaryLayer == null) {
      val layerTitle =
        when (mapFeatureSource) {
          WizardMapFeatureSource.SAMPLE_PLOTS -> "Sample plots"
          else -> current.details.title.ifBlank { "Survey sites" }
        }
      val layerKey = newKey("d")
      primaryLayer =
        if (mapFeatureSource == WizardMapFeatureSource.SAMPLE_PLOTS) {
          EntityDataset(
            key = layerKey,
            kind = DatasetKind.MAP_LAYER,
            id = FormDatasetLinks.uniqueId(slugify(layerTitle), datasets.map { it.id }),
            displayName = layerTitle,
            description = "Sample plots generated from a statistical sample design.",
            geometryKind = SampleDesignConfig().geometryKind,
            keyProperty = SamplePlotProperties.PLOT_ID,
            labelProperty = SamplePlotProperties.PLOT_ID,
            fieldCreationEnabled = effectiveMode.defaultFieldCreationEnabled,
            properties = SamplePlotProperties.schema,
            style = LayerStyle(colorHex = "#F9A825", fillOpacity = 0.15),
            generator = SampleDesignConfig(),
          )
        } else {
          EntityDataset(
            key = layerKey,
            kind = DatasetKind.MAP_LAYER,
            id = FormDatasetLinks.uniqueId(slugify(layerTitle), datasets.map { it.id }),
            displayName = layerTitle,
            geometryKind = defaultGeomKind,
            keyProperty = "id",
            labelProperty = "id",
            fieldCreationEnabled = effectiveMode.defaultFieldCreationEnabled,
            properties = listOf(EntityProperty("id", "ID", PropertyType.TEXT, required = true)),
          )
        }
      datasets = listOf(primaryLayer) + datasets.filter { it.kind == DatasetKind.MAP_LAYER }
    } else if (
      mapFeatureSource == WizardMapFeatureSource.SAMPLE_PLOTS && !primaryLayer.isGenerated
    ) {
      val design = SampleDesignConfig()
      primaryLayer =
        primaryLayer.copy(
          displayName =
            if (primaryLayer.displayName == "Survey sites" || primaryLayer.displayName.isBlank()) {
              "Sample plots"
            } else {
              primaryLayer.displayName
            },
          description = "Sample plots generated from a statistical sample design.",
          geometryKind = design.geometryKind,
          keyProperty = SamplePlotProperties.PLOT_ID,
          labelProperty = SamplePlotProperties.PLOT_ID,
          fieldCreationEnabled = false,
          linkedFormKey = null,
          properties = SamplePlotProperties.schema,
          rows = emptyList(),
          generator = design,
        )
      datasets = datasets.map { if (it.key == primaryLayer.key) primaryLayer else it }
    } else if (
      mapFeatureSource != WizardMapFeatureSource.SAMPLE_PLOTS && primaryLayer.isGenerated
    ) {
      primaryLayer =
        primaryLayer.copy(
          displayName = current.details.title.ifBlank { "Survey sites" },
          geometryKind = defaultGeomKind,
          keyProperty = "id",
          labelProperty = "id",
          fieldCreationEnabled = true,
          properties = listOf(EntityProperty("id", "ID", PropertyType.TEXT, required = true)),
          rows = emptyList(),
          generator = null,
        )
      datasets = datasets.map { if (it.key == primaryLayer.key) primaryLayer else it }
    } else if (!primaryLayer.isGenerated && !isGeometryKindOverridden) {
      primaryLayer = primaryLayer.copy(geometryKind = defaultGeomKind)
      datasets = datasets.map { if (it.key == primaryLayer.key) primaryLayer else it }
    }

    if (forms.isEmpty()) {
      val formKey = newKey("f")
      val formId = FormIds.newFormId()
      val formTitle =
        when (effectiveMode) {
          SurveyCollectionMode.VISUAL_CLASSIFICATION -> "Plot interpretation"
          else -> current.details.title.ifBlank { "Site assessment" }
        }
      val baseForm =
        if (effectiveMode == SurveyCollectionMode.VISUAL_CLASSIFICATION) {
          EditorForm(
            formId = formId,
            title = formTitle,
            availability = effectiveMode.formAvailability,
            questions =
              listOf(
                EditorQuestion(
                  key = "q1",
                  name = "land_cover",
                  type = EditorQuestionType.SELECT_ONE,
                  label = "What is the primary land cover?",
                  required = true,
                  choices =
                    listOf(
                      EditorChoice("forest", "Forest", colorHex = "#3C8D40"),
                      EditorChoice("non_forest", "Non-forest", colorHex = "#F37C22"),
                    ),
                )
              ),
          )
        } else {
          EditorFormTemplates.blank(formId, formTitle)
            .copy(availability = effectiveMode.formAvailability)
        }
      forms = listOf(SurveyEditorForm(formKey, baseForm))
    }

    if (extraTemplates.isNotEmpty()) {
      var seeded = current.copy(forms = forms, datasets = datasets, nextKeyId = nextId)
      extraTemplates.forEach { template ->
        val (next, formKey) = SurveyFormTemplates.addTemplate(seeded, template)
        seeded = next
        seededFormKeys = seededFormKeys + formKey
      }
      forms = seeded.forms
      datasets = seeded.datasets
      nextId = seeded.nextKeyId
    }

    val activeLayer = datasets.first { it.kind == DatasetKind.MAP_LAYER }
    val usesUpdateMode =
      mapFeatureSource == WizardMapFeatureSource.SAMPLE_PLOTS ||
        mapFeatureSource == WizardMapFeatureSource.IMPORT_FILE ||
        effectiveMode.defaultSaveToMode == SaveToMode.UPDATE

    forms = forms.mapIndexed { index, entry ->
      var updatedForm = entry.form.copy(availability = effectiveMode.formAvailability)
      if (
        index == 0 &&
          !usesUpdateMode &&
          startMethod == WizardStartMethod.FROM_SCRATCH &&
          !updatedForm.hasGeometry
      ) {
        val geomType =
          when (activeLayer.geometryKind) {
            GeometryKind.POINT -> EditorQuestionType.LOCATION
            GeometryKind.LINE -> EditorQuestionType.LINE
            GeometryKind.POLYGON -> EditorQuestionType.POLYGON
          }
        val geomLabel =
          when (activeLayer.geometryKind) {
            GeometryKind.POLYGON -> "Record plot boundary"
            GeometryKind.LINE -> "Record transect line"
            GeometryKind.POINT -> "Record site location"
          }
        val geomQuestion =
          EditorQuestion(
            key = newKey("q"),
            name = "location",
            type = geomType,
            label = geomLabel,
            required = true,
            capture = effectiveMode.defaultGeometryCapture,
          )
        updatedForm = updatedForm.copy(questions = listOf(geomQuestion) + updatedForm.questions)
      }
      if (updatedForm.availability.includesWeb) {
        updatedForm =
          updatedForm.copy(
            questions =
              updatedForm.questions.map { q ->
                if (q.type.isGeometry) {
                  q.copy(capture = effectiveMode.defaultGeometryCapture)
                } else {
                  q
                }
              }
          )
      }
      if (index == 0) {
        if (usesUpdateMode) {
          val targetEditorDataset = activeLayer.toEditorDataset(entry.key, forms)
          val mappings =
            SaveToRules.autoMap(updatedForm, targetEditorDataset).filter { it.property != null }
          updatedForm =
            updatedForm.copy(
              saveTo =
                EditorSaveTo(
                  mode = SaveToMode.UPDATE,
                  targetDatasetId = activeLayer.id,
                  mappings = mappings,
                )
            )
        } else {
          updatedForm = updatedForm.copy(saveTo = updatedForm.saveTo.copy(mode = SaveToMode.CREATE))
        }
      }
      entry.copy(form = updatedForm)
    }

    val firstForm = forms.first()
    datasets = datasets.map { d ->
      if (d.key != activeLayer.key) d
      else if (firstForm.form.saveTo.mode == SaveToMode.CREATE && !d.isGenerated) {
        FormDatasetLinks.syncedWithForm(
            d.copy(
              linkedFormKey = firstForm.key,
              fieldCreationEnabled = effectiveMode.defaultFieldCreationEnabled,
            ),
            firstForm.form,
          )
          .copy(
            kind = DatasetKind.MAP_LAYER,
            geometryKind =
              if (firstForm.form.hasGeometry) FormDatasetLinks.geometryKindFor(firstForm.form)
              else activeLayer.geometryKind,
          )
      } else {
        val withMapped = SurveyEditorDerivation.withMappedProperties(d, firstForm.form)
        withMapped.copy(
          kind = DatasetKind.MAP_LAYER,
          linkedFormKey = null,
          fieldCreationEnabled = effectiveMode.defaultFieldCreationEnabled,
        )
      }
    }
    datasets = SaveToRules.inheritConcepts(firstForm.form, firstForm.key, datasets)

    return WizardDraftSyncResult(
      draft = current.copy(forms = forms, datasets = datasets, nextKeyId = nextId),
      seededFormKeys = seededFormKeys,
    )
  }
}
