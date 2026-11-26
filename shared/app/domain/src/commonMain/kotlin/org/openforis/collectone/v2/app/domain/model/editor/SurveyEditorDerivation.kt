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
package org.openforis.collectone.v2.app.domain.model.editor

import openforis.collectone.v1.survey.FormConceptLinks
import org.openforis.collectone.v2.app.domain.model.CachedProfile
import org.openforis.collectone.v2.app.domain.model.EntityShape
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.formatHexColorCss
import org.openforis.collectone.v2.app.domain.model.outline
import org.openforis.collectone.v2.core.forms.model.FormDefinition
import org.openforis.collectone.v2.core.ids.RecordIds
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/**
 * Builds the Survey editor's draft of a survey from the survey's runtime data (its Forms, Map
 * layers, and map features), so the editor always shows what collectors see, even for surveys that
 * were never edited. The inverse, [SurveyEditorProjection], publishes a draft back to the runtime
 * data.
 *
 * Correspondence used in both directions:
 * - A Form's key and ID are the runtime [FormPreviewItem.id]; its questions come from the Form's
 *   XForms via [FormImport].
 * - A Map layer's key is the runtime [MapLayerItem.id] and its dataset ID the layer's
 *   [MapLayerItem.datasetId]. Its properties are the union of the layer's entity property keys,
 *   named by [propertyName], plus [ID_PROPERTY] and [LABEL_PROPERTY] for the entity's ID and label.
 * - A row's key is the runtime [GeospatialEntityItem.id]; its geometry is the feature's drawn shape
 *   ([EntityShape]).
 *
 * Data tables, sharing people, the survey area, sample designs, and question details the XForms
 * can't carry only exist in the stored draft, which is why a saved draft is kept alongside the
 * runtime data and [refresh]ed from it rather than re-derived.
 */
object SurveyEditorDerivation {
  /** Dataset property holding each feature's ID (the ODK Entities `name`). */
  const val ID_PROPERTY = "id"

  /** Dataset property holding each feature's label (the ODK Entities `label`). */
  const val LABEL_PROPERTY = "label"

  /**
   * Entity property keys that describe how a feature is drawn or its workflow marker rather than
   * collected data; they stay on the runtime feature and aren't shown as dataset columns.
   */
  val PRESENTATION_KEYS: Set<String> = setOf("marker-symbol", "marker-color", "stroke", "fill")

  /**
   * Entity property keys that never become dataset columns: the [PRESENTATION_KEYS] and the
   * feature's geometry (written by a Form's `saveto="geometry"`), which the editor shows as the
   * row's geometry rather than as a property.
   */
  val NON_PROPERTY_KEYS: Set<String> = PRESENTATION_KEYS + SaveToRules.GEOMETRY_PROPERTY

  /**
   * Derives the editor draft of [surveyId] from its runtime data. [formDefinition] returns the
   * [FormDefinition] of a Form (or `null` when it has none, in which case a placeholder Form is
   * created so the Form is never dropped).
   */
  fun derive(
    surveyId: String,
    survey: SurveyPreviewItem?,
    forms: List<FormPreviewItem>,
    formDefinition: (FormPreviewItem) -> FormDefinition?,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
    conceptLinks: List<FormConceptLinks> = emptyList(),
    purposeIds: List<String> = emptyList(),
    programIds: List<String> = emptyList(),
    templateOrigins: List<openforis.collectone.v1.survey.FormTemplateOrigin> = emptyList(),
    collaborators: List<Collaborator> = emptyList(),
    ownerProfile: CachedProfile? = null,
  ): SurveyEditorDraft {
    val anchor = SurveyMapAnchor.forSurvey(surveyId)
    val datasets =
      mapLayers
        .map { layer -> deriveDataset(layer, entities.filter { it.layerId == layer.id }) }
        .toMutableList()
    val editorForms = forms.map { form ->
      val imported =
        formDefinition(form)?.let {
          FormImport.fromFormDefinition(it, form.id, form.title, form.availability)
        } ?: placeholder(form)
      val key = form.id
      // The Form list is the source of truth for the title and custom button label; the XML may
      // carry an older title. The survey-level concept links are authoritative over
      // `collectone:concept`
      // attributes.
      var editor =
        FormConceptLinkSync.applyLinks(
          imported.form.copy(
            title = form.title.ifBlank { imported.form.title },
            ctaLabel = form.ctaLabel.takeIf { it != form.title }.orEmpty(),
            openInGoogleEarth = form.openInGoogleEarth && form.availability.includesWeb,
          ),
          conceptLinks.firstOrNull { it.form_id == form.id },
        )
      editor = editor.copy(templateOrigin = TemplateOrigin.fromProto(templateOrigins, form.id))
      val targetId = form.targetDatasetId.ifBlank { null }
      val target = datasets.indexOfFirst { it.id == targetId }.takeIf { it >= 0 }
      if (targetId != null && form.requiresEntity) {
        editor = editor.copy(saveTo = updateSaveTo(editor, imported, key, datasets, targetId))
        // Properties the Form writes but no map feature carries yet are part of the layer schema.
        if (target != null) {
          datasets[target] = withMappedProperties(datasets[target], editor)
        }
      } else {
        editor = editor.copy(saveTo = editor.saveTo.copy(mode = SaveToMode.CREATE))
        val linkIndex =
          target ?: datasets.indexOfFirst { it.id == imported.createDatasetId }.takeIf { it >= 0 }
        if (linkIndex != null && datasets[linkIndex].linkedFormKey == null) {
          val existing = datasets[linkIndex]
          val existingNames = existing.properties.map { it.name }.toSet()
          val missingFormProps =
            FormDatasetLinks.formProperties(editor, existing.kind)
              .filter { it.name !in existingNames }
              .map { it.copy(required = false) }
          datasets[linkIndex] =
            existing.copy(
              linkedFormKey = key,
              properties = existing.properties + missingFormProps,
            )
        }
      }
      val catalog = datasets.map { it.toEditorDataset(key) }
      editor =
        editor.copy(
          openInGoogleEarth = form.openInGoogleEarth && editor.canOpenInGoogleEarth(catalog)
        )
      SurveyEditorForm(key, editor)
    }
    val blank = SurveyEditorDraft.blank(surveyId)
    return SurveyEditorDraft.forSurvey(
      surveyId = surveyId,
      stored =
        blank.copy(
          details =
            blank.details.copy(
              surveyArea =
                survey?.surveyArea
                  ?: if (survey?.isDraft == true || survey?.location == "No survey area yet") null
                  else defaultSurveyArea(survey, anchor),
              purposeIds = purposeIds,
              programIds = programIds,
            ),
          forms = editorForms,
          datasets = datasets.toList(),
          sharing =
            blank.sharing.copy(
              collaborators = collaborators,
              ownerProfile = ownerProfile,
            ),
        ),
      survey = survey,
    )
  }

  /** Default survey area derived from [anchor] for surveys without a survey area of their own. */
  fun defaultSurveyArea(survey: SurveyPreviewItem?, anchor: SurveyMapAnchor): SurveyArea {
    val nw = anchor.toLatLng(0.0, 0.0)
    val ne = anchor.toLatLng(1.0, 0.0)
    val se = anchor.toLatLng(1.0, 1.0)
    val sw = anchor.toLatLng(0.0, 1.0)
    return SurveyArea(
      name = survey?.location?.takeIf { it.isNotBlank() } ?: anchor.label,
      center = anchor.center,
      zoom = anchor.zoom,
      parts = listOf(listOf(nw, ne, se, sw)),
    )
  }

  /**
   * Brings the Map layer rows of a stored [draft] up to date with the runtime map features: rows
   * are added for features created since the draft was saved (for example by submissions), removed
   * for features that no longer exist, and their property values refreshed. Row keys and the
   * geometry drawn in the editor are kept.
   */
  fun refresh(
    draft: SurveyEditorDraft,
    surveyId: String,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
  ): SurveyEditorDraft {
    val datasets =
      draft.datasets.map { dataset ->
        if (dataset.kind != DatasetKind.MAP_LAYER) return@map dataset
        val layer =
          runtimeLayerFor(dataset, mapLayers) ?: return@map normalizeSamplePlotDataset(dataset)
        val layerEntities = entities.filter { it.layerId == layer.id }
        refreshDataset(dataset, surveyId, layerEntities)
      }
    return draft.copy(datasets = datasets)
  }

  /**
   * Refreshes a single Map layer [dataset] against its runtime [layerEntities], normalizing sample
   * plot schemas and avoiding duplicate key/label columns.
   */
  fun refreshDataset(
    dataset: EntityDataset,
    surveyId: String,
    layerEntities: List<GeospatialEntityItem>,
  ): EntityDataset {
    val normalized = normalizeSamplePlotDataset(dataset, layerEntities)
    val declared = normalized.properties
    val runtimeKeys = runtimeKeysFor(normalized, layerEntities)
    val discovered =
      layerEntities
        .flatMap { it.properties.keys }
        .distinct()
        .filterNot {
          it in NON_PROPERTY_KEYS ||
            it in runtimeKeys.values ||
            it == normalized.keyProperty ||
            it == normalized.labelProperty ||
            propertyName(it) == normalized.keyProperty ||
            propertyName(it) == normalized.labelProperty ||
            (normalized.keyProperty != ID_PROPERTY && it == ID_PROPERTY)
        }
    val taken = declared.map { it.name }.toMutableSet()
    val newProperties = discovered.map { key ->
      val name = uniqueName(propertyName(key), taken)
      EntityProperty(name, key, inferType(layerEntities.mapNotNull { it.properties[key] }))
    }
    val properties = declared + newProperties
    val updated = normalized.copy(properties = properties)
    val keys = runtimeKeys + newProperties.associate { it.name to it.label }
    val previousByEntityId = updated.rows.associateBy { entityIdOf(updated, it, surveyId) }
    val rows = layerEntities.map { entity ->
      val previous = previousByEntityId[entity.id]
      val preservedKey =
        previous?.key?.takeIf { !previous.values[updated.keyProperty].isNullOrBlank() }
      val values = rowValues(updated, entity, keys)
      val keyVal = values[updated.keyProperty]?.takeIf { it.isNotBlank() }
      val defaultRowKey =
        if (
          updated.keyProperty == SamplePlotProperties.PLOT_ID &&
            keyVal != null &&
            keyVal != entity.id
        ) {
          "${updated.key}-$keyVal"
        } else {
          entity.id
        }
      EntityRow(
        key = preservedKey ?: defaultRowKey,
        values = values,
        geometry = previous?.geometry ?: rowGeometry(entity),
      )
    }
    return updated.copy(rows = rows)
  }

  /**
   * Normalizes [dataset]'s key, label, and property list when it is a generated sample plot layer
   * or a `plot_id`-keyed layer, stripping any `geometry` property or unreferenced `id`/`label`
   * columns previously synthesized by [deriveDataset] and restoring [SamplePlotProperties.schema]
   * on generated layers.
   */
  fun normalizeSamplePlotDataset(
    dataset: EntityDataset,
    entities: List<GeospatialEntityItem> = emptyList(),
  ): EntityDataset {
    val withoutGeometry = dataset.properties.filterNot { it.name == SaveToRules.GEOMETRY_PROPERTY }
    if (!dataset.isGenerated && dataset.keyProperty != SamplePlotProperties.PLOT_ID) {
      return if (withoutGeometry.size == dataset.properties.size) dataset
      else dataset.copy(properties = withoutGeometry)
    }
    val keyProperty = normalizeSamplePlotKeyProperty(dataset)
    val labelProperty = normalizeSamplePlotLabelProperty(dataset, keyProperty)
    val hasExplicitId = hasExplicitProperty(ID_PROPERTY, keyProperty, dataset, entities)
    val hasExplicitLabel = hasExplicitProperty(LABEL_PROPERTY, labelProperty, dataset, entities)
    val filtered = withoutGeometry.filterNot { prop ->
      (prop.name == ID_PROPERTY && !hasExplicitId) ||
        (prop.name == LABEL_PROPERTY && !hasExplicitLabel)
    }
    val normalizedProperties = normalizeSamplePlotProperties(dataset.isGenerated, filtered)
    return dataset.copy(
      keyProperty = keyProperty,
      labelProperty = labelProperty,
      properties = normalizedProperties,
    )
  }

  private fun normalizeSamplePlotKeyProperty(dataset: EntityDataset): String =
    if (dataset.isGenerated) {
      dataset.keyProperty.takeUnless { it.isBlank() || it == ID_PROPERTY }
        ?: SamplePlotProperties.PLOT_ID
    } else {
      dataset.keyProperty
    }

  private fun normalizeSamplePlotLabelProperty(
    dataset: EntityDataset,
    keyProperty: String,
  ): String =
    if (dataset.isGenerated) {
      dataset.labelProperty.takeUnless {
        it.isBlank() || it == ID_PROPERTY || it == LABEL_PROPERTY
      } ?: keyProperty
    } else {
      dataset.labelProperty
    }

  private fun hasExplicitProperty(
    propertyName: String,
    referencedName: String,
    dataset: EntityDataset,
    entities: List<GeospatialEntityItem>,
  ): Boolean =
    referencedName == propertyName ||
      entities.any { propertyName in it.properties } ||
      dataset.rows.any { !it.values[propertyName].isNullOrBlank() }

  private fun normalizeSamplePlotProperties(
    isGenerated: Boolean,
    filtered: List<EntityProperty>,
  ): List<EntityProperty> =
    if (isGenerated) {
      val byName = filtered.associateBy { it.name }
      val canonicalSampleProps =
        SamplePlotProperties.schema.map { schemaProp ->
          val existing = byName[schemaProp.name]
          if (existing?.conceptLink != null) {
            schemaProp.copy(conceptLink = existing.conceptLink)
          } else {
            schemaProp
          }
        }
      val extraProps = filtered.filterNot { it.name in SamplePlotProperties.all }
      canonicalSampleProps + extraProps
    } else {
      filtered.map { prop ->
        val needsPlotIdLabel =
          prop.name == SamplePlotProperties.PLOT_ID &&
            (prop.label.isBlank() || prop.label == SamplePlotProperties.PLOT_ID)
        if (needsPlotIdLabel) prop.copy(label = "Plot ID", required = true) else prop
      }
    }

  private fun isSamplePlotEntities(entities: List<GeospatialEntityItem>): Boolean =
    entities.any { it.isGenerated } ||
      (entities.isNotEmpty() &&
        entities.all {
          !it.properties[SamplePlotProperties.PLOT_ID].isNullOrBlank() &&
            ID_PROPERTY !in it.properties
        })

  /** The editor's Map layer for a runtime [layer] and its map features. */
  fun deriveDataset(
    layer: MapLayerItem,
    entities: List<GeospatialEntityItem>,
  ): EntityDataset {
    val geometryType = layer.geometryType ?: GeometryType.POINT
    if (isSamplePlotEntities(entities)) {
      return deriveSamplePlotDataset(layer, entities, geometryType)
    }
    val keys =
      entities
        .flatMap { it.properties.keys }
        .distinct()
        .filterNot { it in NON_PROPERTY_KEYS || it == ID_PROPERTY }
    val taken = mutableSetOf(ID_PROPERTY, LABEL_PROPERTY)
    val properties =
      listOf(
        EntityProperty(ID_PROPERTY, "ID", PropertyType.TEXT, required = true),
        EntityProperty(LABEL_PROPERTY, "Label", PropertyType.TEXT),
      ) +
        keys.map { key ->
          val name = uniqueName(propertyName(key), taken)
          EntityProperty(name, key, inferType(entities.mapNotNull { it.properties[key] }))
        }
    val dataset =
      EntityDataset(
        key = layer.id,
        kind = DatasetKind.MAP_LAYER,
        id = layer.datasetId ?: layer.id,
        displayName = layer.label,
        geometryType = geometryType,
        keyProperty = ID_PROPERTY,
        labelProperty = LABEL_PROPERTY,
        properties = properties,
        style =
          LayerStyle(
            colorHex = formatHexColorCss(layer.colorHex),
            visibleByDefault = layer.isVisible,
            iconName = layer.iconName,
          ),
      )
    val runtimeKeys = properties.drop(2).associate { it.name to it.label }
    return dataset.copy(
      rows =
        entities.map { entity ->
          EntityRow(
            key = entity.id,
            values = rowValues(dataset, entity, runtimeKeys),
            geometry = rowGeometry(entity),
          )
        }
    )
  }

  private fun deriveSamplePlotDataset(
    layer: MapLayerItem,
    entities: List<GeospatialEntityItem>,
    geometryType: GeometryType,
  ): EntityDataset {
    val taken = SamplePlotProperties.all.toMutableSet()
    val extraKeys =
      entities
        .flatMap { it.properties.keys }
        .distinct()
        .filterNot {
          it in NON_PROPERTY_KEYS || it in SamplePlotProperties.all || it == ID_PROPERTY
        }
    val extraProperties = extraKeys.map { key ->
      val name = uniqueName(propertyName(key), taken)
      EntityProperty(name, key, inferType(entities.mapNotNull { it.properties[key] }))
    }
    val dataset =
      EntityDataset(
        key = layer.id,
        kind = DatasetKind.MAP_LAYER,
        id = layer.datasetId ?: layer.id,
        displayName = layer.label,
        geometryType = geometryType,
        keyProperty = SamplePlotProperties.PLOT_ID,
        labelProperty = SamplePlotProperties.PLOT_ID,
        properties = SamplePlotProperties.schema + extraProperties,
        style =
          LayerStyle(
            colorHex = formatHexColorCss(layer.colorHex),
            visibleByDefault = layer.isVisible,
            iconName = layer.iconName,
          ),
      )
    val runtimeKeys = runtimeKeysFor(dataset, entities)
    return dataset.copy(
      rows =
        entities.map { entity ->
          val values = rowValues(dataset, entity, runtimeKeys)
          val plotId = values[SamplePlotProperties.PLOT_ID]?.takeIf { it.isNotBlank() }
          val rowKey =
            if (plotId != null && plotId != entity.id) "${layer.id}-$plotId" else entity.id
          EntityRow(
            key = rowKey,
            values = values,
            geometry = rowGeometry(entity),
          )
        }
    )
  }

  /** The runtime map layer a Map layer [dataset] was derived from or published to, if any. */
  fun runtimeLayerFor(dataset: EntityDataset, mapLayers: List<MapLayerItem>): MapLayerItem? =
    mapLayers.firstOrNull { it.id == dataset.key }
      ?: mapLayers.firstOrNull { it.datasetId == dataset.id }

  /**
   * Editor row key value of [row]: its [EntityDataset.keyProperty] value when non-blank, else
   * `row.key`.
   */
  fun rawKeyOf(dataset: EntityDataset, row: EntityRow): String =
    row.values[dataset.keyProperty]?.trim()?.takeIf { it.isNotEmpty() } ?: row.key

  /**
   * Runtime entity ID of [row]: a canonical UUID when [surveyId] is given for a
   * [DatasetKind.MAP_LAYER] (preserving [rawKeyOf] as-is when it is already a UUID, or hashing
   * `(surveyId, dataset.id, rawKey)` deterministically so human-readable plot codes like `P001`
   * satisfy Spanner's UUID key column).
   */
  fun entityIdOf(dataset: EntityDataset, row: EntityRow, surveyId: String = ""): String {
    val raw = rawKeyOf(dataset, row)
    if (dataset.kind != DatasetKind.MAP_LAYER || surveyId.isBlank() || RecordIds.isUuid(raw)) {
      return raw
    }
    return RecordIds.uuidOrDeterministic(raw, "$surveyId:${dataset.id}")
  }

  /**
   * Runtime property key of each dataset property (by name) other than the key and label
   * properties, as found on [entities]: the key itself, or the key whose [propertyName] matches.
   * Properties not yet on any feature map to their own name.
   */
  fun runtimeKeysFor(
    dataset: EntityDataset,
    entities: List<GeospatialEntityItem>,
  ): Map<String, String> {
    val names = dataset.properties.map { it.name }.toSet()
    val found = mutableMapOf<String, String>()
    entities
      .flatMap { it.properties.keys }
      .distinct()
      .filterNot { it in NON_PROPERTY_KEYS }
      .forEach { key ->
        when {
          key in names -> if (key !in found) found[key] = key
          propertyName(key) in names ->
            if (propertyName(key) !in found) found[propertyName(key)] = key
        }
      }
    return dataset.properties
      .filterNot { it.name == dataset.keyProperty || it.name == dataset.labelProperty }
      .associate { it.name to (found[it.name] ?: it.name) }
  }

  /** Row values of [entity] for [dataset]'s properties, reading runtime properties via [keys]. */
  fun rowValues(
    dataset: EntityDataset,
    entity: GeospatialEntityItem,
    keys: Map<String, String>,
  ): Map<String, String> = buildMap {
    val keyValue =
      entity.properties[keys[dataset.keyProperty] ?: dataset.keyProperty]
        ?: entity.properties[dataset.keyProperty]
        ?: entity.id
    dataset.properties.forEach { property ->
      when (property.name) {
        dataset.keyProperty -> put(property.name, keyValue)
        dataset.labelProperty -> put(property.name, entity.label)
        ID_PROPERTY ->
          put(
            property.name,
            entity.properties[keys[property.name] ?: property.name]
              ?: entity.properties[ID_PROPERTY]
              ?: keyValue,
          )
        else ->
          entity.properties[keys[property.name] ?: property.name]?.let { put(property.name, it) }
      }
    }
  }

  /**
   * The vertices the editor shows for [entity]: those of its geometry's first part
   * ([Geometry.outline]), or none for a record without geometry.
   */
  fun rowGeometry(entity: GeospatialEntityItem): List<LatLng> = entity.geometry?.outline.orEmpty()

  /** Dataset property name for a runtime entity property [key] (e.g. `Farmer / Owner`). */
  fun propertyName(key: String): String =
    if (FormEditorValidator.isValidName(key) && key != SaveToRules.GEOMETRY_PROPERTY) key
    else slugify(key, "property")

  /** Column type that accepts every one of [values]. */
  fun inferType(values: List<String>): PropertyType {
    val present = values.map { it.trim() }.filter { it.isNotEmpty() }
    if (present.isEmpty()) return PropertyType.TEXT
    return listOf(
        PropertyType.INTEGER,
        PropertyType.DECIMAL,
        PropertyType.BOOLEAN,
        PropertyType.DATE,
      )
      .firstOrNull { type -> present.all(type::accepts) } ?: PropertyType.TEXT
  }

  private fun uniqueName(base: String, taken: MutableSet<String>): String {
    var name = base
    var n = 2
    while (name in taken) name = "${base}_${n++}"
    taken += name
    return name
  }

  private fun placeholder(form: FormPreviewItem): ImportedForm =
    ImportedForm(
      form =
        EditorForm(
          formId = form.id,
          title = form.title,
          questions =
            listOf(
              EditorQuestion(
                key = "q1",
                name = "unavailable",
                type = EditorQuestionType.NOTE,
                label =
                  "This Form's XForms definition couldn't be read. Its questions aren't shown.",
              )
            ),
          availability = form.availability,
          ctaLabel = form.ctaLabel.takeIf { it != form.title }.orEmpty(),
          openInGoogleEarth = form.openInGoogleEarth && form.availability.includesWeb,
        ),
      notes = listOf("The XForms of \"${form.title}\" couldn't be parsed."),
    )

  /** Adds properties that [form]'s save-to mappings write but [dataset] does not declare. */
  fun withMappedProperties(dataset: EntityDataset, form: EditorForm): EntityDataset {
    val known = dataset.properties.map { it.name }.toSet()
    val questions = form.questions.associateBy { it.key }
    val missing =
      form.saveTo.mappings
        .mapNotNull { mapping ->
          val property = mapping.property ?: return@mapNotNull null
          if (property in known || property == "geometry") return@mapNotNull null
          val question = questions[mapping.questionKey]
          EntityProperty(
            name = property,
            label = question?.label?.ifBlank { null } ?: property,
            type =
              when (question?.type) {
                EditorQuestionType.NUMBER ->
                  if (question.decimalPlaces <= 0) PropertyType.INTEGER else PropertyType.DECIMAL
                EditorQuestionType.DATE -> PropertyType.DATE
                else -> PropertyType.TEXT
              },
          )
        }
        .distinctBy { it.name }
    return if (missing.isEmpty()) dataset
    else dataset.copy(properties = dataset.properties + missing)
  }

  /** Save-to logic of a Form that updates a feature of dataset [targetId]. */
  fun updateSaveTo(
    form: EditorForm,
    imported: ImportedForm,
    formKey: String,
    datasets: List<EntityDataset>,
    targetId: String,
  ): EditorSaveTo {
    val dataset = datasets.firstOrNull { it.id == targetId }
    val target = dataset?.toEditorDataset(formKey)
    var saveTo = form.saveTo.copy(mode = SaveToMode.UPDATE, targetDatasetId = targetId)
    if (saveTo.idSource == EntityIdSource.QUESTION) {
      saveTo =
        saveTo.copy(
          idMatchProperty =
            when (imported.idMatchColumn) {
              "name" -> dataset?.keyProperty
              "label" -> dataset?.labelProperty
              else -> imported.idMatchColumn
            }
        )
    }
    if (saveTo.mappings.none { it.property != null } && target != null) {
      saveTo =
        saveTo.copy(
          mappings =
            SaveToRules.autoMap(form, target, skipKey = saveTo.idQuestionKey).filter {
              it.property != null
            }
        )
    }
    if (saveTo.mappings.none { it.property != null } && !saveTo.status.enabled) {
      // Nothing to copy into the feature: the Form only marks it as surveyed.
      saveTo = saveTo.copy(status = saveTo.status.copy(enabled = true))
    }
    return saveTo
  }
}
