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

/**
 * Rules tying a Form to the Map layer or Data table its submissions add features to: which kind of
 * dataset a Form feeds, the schema it implies, and the Form implied by a dataset's schema.
 */
object FormDatasetLinks {

  /** Dataset kind matching [form]: a Map layer if it captures a geometry, else a Data table. */
  fun datasetKindFor(form: EditorForm): DatasetKind =
    if (form.hasGeometry) DatasetKind.MAP_LAYER else DatasetKind.DATA_TABLE

  /**
   * Geometry kind of a Map layer fed by [form]: that of its primary geometry question (Point, Line,
   * or Polygon), defaulting to points when the form has none.
   */
  fun geometryKindFor(form: EditorForm): GeometryKind =
    when (form.primaryGeometryQuestion?.type) {
      EditorQuestionType.LINE -> GeometryKind.LINE
      EditorQuestionType.POLYGON -> GeometryKind.POLYGON
      else -> GeometryKind.POINT
    }

  /** Dataset properties mirroring [form]'s questions (notes excluded). */
  fun formProperties(form: EditorForm): List<EntityProperty> =
    form.questions
      .filter { it.type != EditorQuestionType.NOTE }
      .map { q ->
        EntityProperty(
          name = q.name,
          label = q.label.ifBlank { q.name },
          type =
            when (q.type) {
              EditorQuestionType.INTEGER -> PropertyType.INTEGER
              EditorQuestionType.DECIMAL -> PropertyType.DECIMAL
              EditorQuestionType.DATE -> PropertyType.DATE
              else -> PropertyType.TEXT
            },
          required = q.required,
        )
      }

  /** The `id` property every linked dataset starts with. */
  val idProperty: EntityProperty
    get() = EntityProperty("id", "ID", PropertyType.TEXT, required = true)

  /** [formProperties] of [form], preceded by [idProperty] unless the form already has an `id`. */
  fun linkedProperties(form: EditorForm): List<EntityProperty> {
    val formProps = formProperties(form)
    return if (formProps.any { it.name == "id" }) formProps else listOf(idProperty) + formProps
  }

  /**
   * [dataset] with its schema synchronized to the questions of the Form it's linked to. The dataset
   * is a Map layer if the form has a geometry question (whose type sets the layer's point / line /
   * polygon kind, unless the layer's plots are generated), otherwise a Data table.
   */
  fun syncedWithForm(dataset: EntityDataset, form: EditorForm): EntityDataset {
    val formProps = formProperties(form)
    // Retain the existing key property if not in the form, or ensure at least one property exists.
    val idProp = dataset.properties.firstOrNull { it.name == dataset.keyProperty } ?: idProperty
    val combinedProps =
      if (formProps.any { it.name == idProp.name }) formProps else listOf(idProp) + formProps
    val keyProp =
      if (combinedProps.any { it.name == dataset.keyProperty }) dataset.keyProperty
      else combinedProps.first().name
    val labelProp =
      if (combinedProps.any { it.name == dataset.labelProperty }) dataset.labelProperty else keyProp
    return dataset.copy(
      kind = datasetKindFor(form),
      // Generated layers take their geometry from the sample design, not the form.
      geometryKind =
        if (form.hasGeometry && !dataset.isGenerated) geometryKindFor(form)
        else dataset.geometryKind,
      properties = combinedProps,
      keyProperty = keyProp,
      labelProperty = labelProp,
    )
  }

  /**
   * Questions of a Form that collects the properties of [dataset]: a geometry question matching a
   * Map layer's features, then one question per property.
   */
  fun questionsFor(dataset: EntityDataset): List<EditorQuestion> {
    var questionIdx = 1
    val questions = mutableListOf<EditorQuestion>()
    if (dataset.kind == DatasetKind.MAP_LAYER) {
      val geometryType =
        when (dataset.geometryKind) {
          GeometryKind.POINT -> EditorQuestionType.LOCATION
          GeometryKind.LINE -> EditorQuestionType.LINE
          GeometryKind.POLYGON -> EditorQuestionType.POLYGON
        }
      questions +=
        EditorQuestion(
          key = "q${questionIdx++}",
          name = "location",
          type = geometryType,
          label = "Location",
          required = true,
        )
    }
    dataset.properties.forEach { prop ->
      val qType =
        when (prop.type) {
          PropertyType.INTEGER -> EditorQuestionType.INTEGER
          PropertyType.DECIMAL -> EditorQuestionType.DECIMAL
          PropertyType.DATE -> EditorQuestionType.DATE
          PropertyType.BOOLEAN -> EditorQuestionType.SELECT_ONE
          PropertyType.TEXT -> EditorQuestionType.TEXT
        }
      val choices =
        if (prop.type == PropertyType.BOOLEAN) {
          listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No"))
        } else {
          emptyList()
        }
      questions +=
        EditorQuestion(
          key = "q${questionIdx++}",
          name = prop.name,
          type = qType,
          label = prop.label,
          required = prop.required,
          choices = choices,
        )
    }
    return questions
  }

  /**
   * A small default shape for a new feature of [dataset] at [at], or near its existing features.
   */
  fun defaultGeometry(dataset: EntityDataset, at: LatLng?): List<LatLng> {
    val all = dataset.rows.flatMap { it.geometry }
    val c =
      at
        ?: run {
          val center =
            if (all.isEmpty()) LatLng(-0.4180, 36.9530)
            else LatLng(all.map { it.lat }.average(), all.map { it.lng }.average())
          // Offset successive features so they don't overlap.
          val step = 0.002 * ((dataset.rows.size % 5) - 2)
          LatLng(center.lat + step, center.lng - step)
        }
    val s = 0.0015
    return when (dataset.geometryKind) {
      GeometryKind.POINT -> listOf(c)
      GeometryKind.LINE -> listOf(c, LatLng(c.lat + s, c.lng + s), LatLng(c.lat + s, c.lng + 2 * s))
      GeometryKind.POLYGON ->
        listOf(c, LatLng(c.lat, c.lng + s), LatLng(c.lat - s, c.lng + s), LatLng(c.lat - s, c.lng))
    }
  }

  /** [base] if not in [taken], else `"<base> 2"`, `"<base> 3"`, … */
  fun uniqueTitle(base: String, taken: List<String>): String {
    if (base !in taken) return base
    var n = 2
    while ("$base $n" in taken) n++
    return "$base $n"
  }

  /** [base] if not in [taken], else `"<base>_2"`, `"<base>_3"`, … */
  fun uniqueId(base: String, taken: List<String>): String {
    if (base !in taken) return base
    var n = 2
    while ("${base}_$n" in taken) n++
    return "${base}_$n"
  }
}

/** This Form's save-to logic after dataset [oldId] was renamed to [newId]. */
fun EditorForm.withRenamedTargetDataset(oldId: String, newId: String): EditorForm =
  if (saveTo.targetDatasetId != oldId) this else copy(saveTo = saveTo.copy(targetDatasetId = newId))

/**
 * This Form's save-to logic after property [oldName] of dataset [datasetId] was renamed to
 * [newName]: feature lookup, mappings, and status rules follow the rename. [saveTarget] is the
 * dataset this Form adds features to or updates (see [SaveToRules.saveTarget]), if any.
 */
fun EditorForm.withRenamedTargetProperty(
  datasetId: String,
  oldName: String,
  newName: String,
  saveTarget: EditorDataset?,
): EditorForm {
  if (saveTo.targetDatasetId != datasetId && saveTarget?.id != datasetId) return this
  return copy(
    saveTo =
      saveTo.copy(
        idMatchProperty =
          if (saveTo.idMatchProperty == oldName) newName else saveTo.idMatchProperty,
        mappings =
          saveTo.mappings.map { if (it.property == oldName) it.copy(property = newName) else it },
        status =
          saveTo.status.copy(
            rules =
              saveTo.status.rules.map { rule ->
                if (
                  rule.subject == StatusConditionSubject.ENTITY_PROPERTY && rule.property == oldName
                ) {
                  rule.copy(property = newName)
                } else {
                  rule
                }
              }
          ),
      )
  )
}
