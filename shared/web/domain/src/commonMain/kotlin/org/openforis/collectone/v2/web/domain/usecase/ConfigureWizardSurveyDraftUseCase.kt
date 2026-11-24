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

import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetIds
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorFieldMapping
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorFormTemplates
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorSaveTo
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.FormDatasetLinks
import org.openforis.collectone.v2.app.domain.model.editor.FormIds
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.LayerStyle
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SamplePlotProperties
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.model.editor.SaveToRules
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorForm
import org.openforis.collectone.v2.app.domain.model.editor.SurveyFormTemplates
import org.openforis.collectone.v2.app.domain.model.editor.TemplateOrigin
import org.openforis.collectone.v2.app.domain.model.editor.toEditorDataset
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod

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
    isGeometryTypeOverridden: Boolean,
    isEudrPlotsUnderFourHaConfirmed: Boolean,
    hasPurposeSelectionChanged: Boolean,
    previousSeededFormKeys: Set<String>,
    /** Starter template chosen per selected purpose (others use their default). */
    starterTemplateIds: Map<String, String> = emptyMap(),
  ): WizardDraftSyncResult {
    var datasets = current.datasets
    var forms = current.forms
    var nextId = current.nextKeyId
    var seededFormKeys = previousSeededFormKeys
    var newlySeededFormKeys = emptySet<String>()
    fun newKey(prefix: String) = "$prefix${nextId++}"

    val defaultGeomKind =
      SurveyWizardRules.defaultGeometryType(
        purposeIds = current.details.purposeIds,
        mapFeatureSource = mapFeatureSource,
        eudrPlotsUnderFourHaConfirmed = isEudrPlotsUnderFourHaConfirmed,
        starterTemplateIds = starterTemplateIds,
      )

    var extraTemplates = emptyList<FormTemplate>()
    if (hasPurposeSelectionChanged && startMethod == WizardStartMethod.FROM_SCRATCH) {
      val nonOtherIds =
        current.details.purposeIds.filter { it != SurveyWizardRules.OTHER_PURPOSE_ID }
      val templates = library.templatesForPurposes(nonOtherIds, starterTemplateIds)
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
            templateOrigin = TemplateOrigin.of(primaryTemplate),
          )
        forms = listOf(SurveyEditorForm(existingFormKey, seededForm)) + forms.drop(1)
        extraTemplates = templates.drop(1)
        seededFormKeys = setOf(existingFormKey)
        newlySeededFormKeys = newlySeededFormKeys + existingFormKey
      } else {
        if (primaryEntry != null && primaryEntry.key in previousSeededFormKeys) {
          forms = forms.drop(1)
        }
        seededFormKeys = emptySet()
      }
    }

    val defaultSubPlot = SurveyWizardRules.defaultSubPlotMode(current.details.purposeIds)
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
          val design = SampleDesignConfig(subPlot = defaultSubPlot)
          EntityDataset(
            key = layerKey,
            kind = DatasetKind.MAP_LAYER,
            id = DatasetIds.forName(layerTitle, datasets.map { it.id }),
            displayName = layerTitle,
            description = "Sample plots generated from a statistical sample design.",
            geometryType = design.geometryType,
            keyProperty = SamplePlotProperties.PLOT_ID,
            labelProperty = SamplePlotProperties.PLOT_ID,
            fieldCreationEnabled = effectiveMode.defaultFieldCreationEnabled,
            properties = SamplePlotProperties.schema,
            style = LayerStyle(colorHex = "#F9A825", fillOpacity = 0.15),
            generator = design,
          )
        } else {
          EntityDataset(
            key = layerKey,
            kind = DatasetKind.MAP_LAYER,
            id = DatasetIds.forName(layerTitle, datasets.map { it.id }),
            displayName = layerTitle,
            geometryType = defaultGeomKind,
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
      val design = SampleDesignConfig(subPlot = defaultSubPlot)
      primaryLayer =
        primaryLayer.copy(
          displayName =
            if (primaryLayer.displayName == "Survey sites" || primaryLayer.displayName.isBlank()) {
              "Sample plots"
            } else {
              primaryLayer.displayName
            },
          description = "Sample plots generated from a statistical sample design.",
          geometryType = design.geometryType,
          keyProperty = SamplePlotProperties.PLOT_ID,
          labelProperty = SamplePlotProperties.PLOT_ID,
          fieldCreationEnabled = false,
          linkedFormKey = null,
          properties = SamplePlotProperties.schema,
          style = LayerStyle(colorHex = "#F9A825", fillOpacity = 0.15),
          rows = emptyList(),
          generator = design,
        )
      datasets = datasets.map { if (it.key == primaryLayer.key) primaryLayer else it }
    } else if (
      mapFeatureSource == WizardMapFeatureSource.SAMPLE_PLOTS &&
        hasPurposeSelectionChanged &&
        primaryLayer.isGeneratedButNotRun
    ) {
      primaryLayer =
        primaryLayer.copy(generator = primaryLayer.generator?.copy(subPlot = defaultSubPlot))
      datasets = datasets.map { if (it.key == primaryLayer.key) primaryLayer else it }
    } else if (
      mapFeatureSource != WizardMapFeatureSource.SAMPLE_PLOTS && primaryLayer.isGenerated
    ) {
      primaryLayer =
        primaryLayer.copy(
          displayName = current.details.title.ifBlank { "Survey sites" },
          geometryType = defaultGeomKind,
          keyProperty = "id",
          labelProperty = "id",
          fieldCreationEnabled = true,
          properties = listOf(EntityProperty("id", "ID", PropertyType.TEXT, required = true)),
          rows = emptyList(),
          generator = null,
        )
      datasets = datasets.map { if (it.key == primaryLayer.key) primaryLayer else it }
    } else if (!primaryLayer.isGenerated && !isGeometryTypeOverridden) {
      primaryLayer = primaryLayer.copy(geometryType = defaultGeomKind)
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
      newlySeededFormKeys = newlySeededFormKeys + formKey
    }

    if (extraTemplates.isNotEmpty()) {
      var seeded = current.copy(forms = forms, datasets = datasets, nextKeyId = nextId)
      extraTemplates.forEach { template ->
        val (next, formKey) = SurveyFormTemplates.addTemplate(seeded, template)
        seeded = next
        seededFormKeys = seededFormKeys + formKey
        newlySeededFormKeys = newlySeededFormKeys + formKey
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
    // Plot review decides where its Forms are available, what they save, and their status rules.
    val reviewFormKeys =
      PlotReviewWorkflow.setup(current.copy(forms = forms, datasets = datasets), activeLayer.key)
        .let { setOfNotNull(it.deskFormKey, it.fieldFormKey) }

    forms = forms.mapIndexed { index, entry ->
      if (entry.key in reviewFormKeys) return@mapIndexed entry
      val isNewlySeeded = entry.key in newlySeededFormKeys
      var updatedForm =
        if (isNewlySeeded) entry.form.copy(availability = effectiveMode.formAvailability)
        else entry.form
      if (
        index == 0 &&
          !usesUpdateMode &&
          startMethod == WizardStartMethod.FROM_SCRATCH &&
          !updatedForm.hasGeometry
      ) {
        val geomType =
          when (activeLayer.geometryType) {
            GeometryType.POINT -> EditorQuestionType.LOCATION
            GeometryType.LINE_STRING -> EditorQuestionType.LINE
            GeometryType.POLYGON -> EditorQuestionType.POLYGON
          }
        val geomLabel =
          when (activeLayer.geometryType) {
            GeometryType.POLYGON -> "Record plot boundary"
            GeometryType.LINE_STRING -> "Record transect line"
            GeometryType.POINT -> "Record site location"
          }
        val geomQuestion =
          EditorQuestion(
            key = newKey("q"),
            name = "location",
            type = geomType,
            label = geomLabel,
            required = true,
            capture =
              if (updatedForm.availability.includesWeb) GeometryCapture.GPS_OR_MAP
              else effectiveMode.defaultGeometryCapture,
          )
        updatedForm = updatedForm.copy(questions = listOf(geomQuestion) + updatedForm.questions)
      }
      if (isNewlySeeded && updatedForm.availability.includesWeb) {
        updatedForm =
          updatedForm.copy(
            questions =
              updatedForm.questions.map { q ->
                if (q.type.isGeometry && q.capture == GeometryCapture.GPS_ONLY) {
                  q.copy(capture = GeometryCapture.GPS_OR_MAP)
                } else {
                  q
                }
              }
          )
      }
      if (index == 0) {
        if (usesUpdateMode) {
          val geometryKey = updatedForm.primaryGeometryQuestion?.key
          val mappings =
            SaveToRules.savableQuestions(updatedForm).mapNotNull { q ->
              val property =
                when {
                  q.key == geometryKey ->
                    if (activeLayer.isGenerated) null else SaveToRules.GEOMETRY_PROPERTY
                  q.type.isGeometry -> null
                  else -> q.name
                }
              property?.let { EditorFieldMapping(q.key, it) }
            }
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
            geometryType =
              if (firstForm.form.hasGeometry) FormDatasetLinks.geometryTypeFor(firstForm.form)
              else activeLayer.geometryType,
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
    forms = forms.map { entry ->
      val catalog = datasets.map { it.toEditorDataset(entry.key, forms) }
      if (entry.form.openInGoogleEarth && !entry.form.canOpenInGoogleEarth(catalog)) {
        entry.copy(form = entry.form.copy(openInGoogleEarth = false))
      } else {
        entry
      }
    }

    return WizardDraftSyncResult(
      draft = current.copy(forms = forms, datasets = datasets, nextKeyId = nextId),
      seededFormKeys = seededFormKeys,
    )
  }
}

/** Whether this layer has a sample design whose plots haven't been generated yet. */
private val EntityDataset.isGeneratedButNotRun: Boolean
  get() = isGenerated && generator?.lastRun == null
