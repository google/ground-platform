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
package org.groundplatform.v2.devtools.prototypeapp.domain.model.editor

import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityShape
import org.groundplatform.v2.devtools.prototypeapp.domain.model.EntityShapeKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapLayerItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.formatHexColorCss

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
   * Derives the editor draft of [surveyId] from its runtime data. [formXml] returns the XForms of a
   * Form (or `null` when it has none, in which case a placeholder Form is created so the Form is
   * never dropped).
   */
  fun derive(
    surveyId: String,
    survey: SurveyPreviewItem?,
    forms: List<FormPreviewItem>,
    formXml: (FormPreviewItem) -> String?,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
  ): SurveyEditorDraft {
    val anchor = SurveyMapAnchor.forSurvey(surveyId)
    val datasets =
      mapLayers
        .map { layer -> deriveDataset(layer, entities.filter { it.layerId == layer.id }, anchor) }
        .toMutableList()
    val editorForms = forms.map { form ->
      val imported =
        formXml(form)?.let { FormImport.fromXml(it, form.id, form.title, form.availability) }
          ?: placeholder(form)
      val key = form.id
      // The Form list is the source of truth for the title; the XML may carry an older one.
      var editor = imported.form.copy(title = form.title.ifBlank { imported.form.title })
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
          datasets[linkIndex] = datasets[linkIndex].copy(linkedFormKey = key)
        }
      }
      SurveyEditorForm(key, editor)
    }
    return SurveyEditorDraft.forSurvey(
      surveyId = surveyId,
      stored =
        SurveyEditorDraft.blank(surveyId).copy(forms = editorForms, datasets = datasets.toList()),
      survey = survey,
    )
  }

  /**
   * Brings the Map layer rows of a stored [draft] up to date with the runtime map features: rows
   * are added for features created since the draft was saved (for example by submissions), removed
   * for features that no longer exist, and their property values refreshed. Row keys and the
   * geometry drawn in the editor are kept, since the runtime only stores a feature's center.
   */
  fun refresh(
    draft: SurveyEditorDraft,
    surveyId: String,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
  ): SurveyEditorDraft {
    val anchor = SurveyMapAnchor.forSurvey(surveyId)
    val datasets =
      draft.datasets.map { dataset ->
        if (dataset.kind != DatasetKind.MAP_LAYER) return@map dataset
        val layer = runtimeLayerFor(dataset, mapLayers) ?: return@map dataset
        val layerEntities = entities.filter { it.layerId == layer.id }
        val runtimeKeys = runtimeKeysFor(dataset, layerEntities)
        val discovered =
          layerEntities
            .flatMap { it.properties.keys }
            .distinct()
            .filterNot { it in PRESENTATION_KEYS || it in runtimeKeys.values }
        val taken = dataset.properties.map { it.name }.toMutableSet()
        val newProperties = discovered.map { key ->
          val name = uniqueName(propertyName(key), taken)
          EntityProperty(name, key, inferType(layerEntities.mapNotNull { it.properties[key] }))
        }
        val properties = dataset.properties + newProperties
        val keys = runtimeKeys + newProperties.associate { it.name to it.label }
        val previousByEntityId = dataset.rows.associateBy { entityIdOf(dataset, it) }
        val rows = layerEntities.map { entity ->
          val previous = previousByEntityId[entity.id]
          EntityRow(
            key = previous?.key ?: entity.id,
            values = rowValues(dataset.copy(properties = properties), entity, keys),
            geometry = previous?.geometry ?: rowGeometry(entity, anchor, dataset.geometryKind),
          )
        }
        dataset.copy(properties = properties, rows = rows)
      }
    return draft.copy(datasets = datasets)
  }

  /** The editor's Map layer for a runtime [layer] and its map features. */
  fun deriveDataset(
    layer: MapLayerItem,
    entities: List<GeospatialEntityItem>,
    anchor: SurveyMapAnchor,
  ): EntityDataset {
    val geometryKind =
      when (EntityShapeKind.of(layer.geometryTypeLabel)) {
        EntityShapeKind.LINE -> GeometryKind.LINE
        EntityShapeKind.POLYGON -> GeometryKind.POLYGON
        else -> GeometryKind.POINT
      }
    val keys =
      entities.flatMap { it.properties.keys }.distinct().filterNot { it in PRESENTATION_KEYS }
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
        geometryKind = geometryKind,
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
            geometry = rowGeometry(entity, anchor, geometryKind),
          )
        }
    )
  }

  /** The runtime map layer a Map layer [dataset] was derived from or published to, if any. */
  fun runtimeLayerFor(dataset: EntityDataset, mapLayers: List<MapLayerItem>): MapLayerItem? =
    mapLayers.firstOrNull { it.id == dataset.key }
      ?: mapLayers.firstOrNull { it.datasetId == dataset.id }

  /** Runtime entity ID of [row]: the feature it was derived from, else its key property value. */
  fun entityIdOf(dataset: EntityDataset, row: EntityRow): String =
    row.values[dataset.keyProperty]?.trim()?.takeIf { it.isNotEmpty() } ?: row.key

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
      .filterNot { it in PRESENTATION_KEYS }
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
    dataset.properties.forEach { property ->
      when (property.name) {
        dataset.keyProperty -> put(property.name, entity.id)
        dataset.labelProperty -> put(property.name, entity.label)
        else ->
          entity.properties[keys[property.name] ?: property.name]?.let { put(property.name, it) }
      }
    }
  }

  /** The vertices the editor shows for [entity], matching its drawn shape. */
  fun rowGeometry(
    entity: GeospatialEntityItem,
    anchor: SurveyMapAnchor,
    geometryKind: GeometryKind,
  ): List<LatLng> =
    EntityShape.vertices(
        kind =
          when (geometryKind) {
            GeometryKind.POINT -> EntityShapeKind.POINT
            GeometryKind.LINE -> EntityShapeKind.LINE
            GeometryKind.POLYGON -> EntityShapeKind.POLYGON
          },
        normalizedX = entity.normalizedX.toDouble(),
        normalizedY = entity.normalizedY.toDouble(),
        anchor = anchor,
        small = EntityShape.isGenerated(entity),
      )
      .map { LatLng(it.latitude, it.longitude) }

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
        ),
      notes = listOf("The XForms of \"${form.title}\" couldn't be parsed."),
    )

  /** Save-to logic of a Form that updates a feature of dataset [targetId]. */
  /** Adds properties that [form]'s save-to mappings write but [dataset] does not declare. */
  private fun withMappedProperties(dataset: EntityDataset, form: EditorForm): EntityDataset {
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
                EditorQuestionType.INTEGER -> PropertyType.INTEGER
                EditorQuestionType.DECIMAL -> PropertyType.DECIMAL
                EditorQuestionType.DATE -> PropertyType.DATE
                else -> PropertyType.TEXT
              },
          )
        }
        .distinctBy { it.name }
    return if (missing.isEmpty()) dataset
    else dataset.copy(properties = dataset.properties + missing)
  }

  private fun updateSaveTo(
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
