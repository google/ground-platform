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

import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

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
  fun geometryTypeFor(form: EditorForm): GeometryType =
    when (form.primaryGeometryQuestion?.type) {
      EditorQuestionType.LINE -> GeometryType.LINE_STRING
      EditorQuestionType.POLYGON -> GeometryType.POLYGON
      else -> GeometryType.POINT
    }

  /**
   * Dataset properties mirroring [form]'s questions (notes excluded). In a Map layer ([kind]) the
   * Form's primary geometry question is left out: its answer becomes each new feature's geometry
   * (see [XFormsEntityPlan]), so a property mirroring it would always stay empty.
   */
  fun formProperties(
    form: EditorForm,
    kind: DatasetKind = datasetKindFor(form),
  ): List<EntityProperty> {
    val geometryKey = if (kind == DatasetKind.MAP_LAYER) form.primaryGeometryQuestion?.key else null
    return form.questions
      .filter { it.type != EditorQuestionType.NOTE && it.key != geometryKey }
      .map(::questionToProperty)
  }

  private fun questionToProperty(
    q: EditorQuestion,
    required: Boolean = q.required,
  ): EntityProperty =
    EntityProperty(
      name = q.name,
      label = q.label.ifBlank { q.name },
      type =
        when (q.type) {
          EditorQuestionType.NUMBER ->
            if (q.decimalPlaces <= 0) PropertyType.INTEGER else PropertyType.DECIMAL
          EditorQuestionType.DATE -> PropertyType.DATE
          else -> PropertyType.TEXT
        },
      required = required,
      conceptLink = q.conceptLink,
    )

  /** The `id` property every linked dataset starts with. */
  val idProperty: EntityProperty
    get() = EntityProperty("id", "ID", PropertyType.TEXT, required = true)

  /**
   * [formProperties] of [form] for a [kind] dataset, preceded by [idProperty] unless the form
   * already has an `id`.
   */
  fun linkedProperties(
    form: EditorForm,
    kind: DatasetKind = datasetKindFor(form),
  ): List<EntityProperty> {
    val formProps = formProperties(form, kind)
    return if (formProps.any { it.name == "id" }) formProps else listOf(idProperty) + formProps
  }

  /**
   * The property of [properties] (a dataset fed by [form]) that best labels its features or rows in
   * choice lists, map callouts, and tables: the answer to a free-text question asking for a name
   * (`producer_name`), else for an identifier or code (`permit_id`), else the first single-choice
   * answer, else the first free-text one. Geometry, media, multiple-choice, number, and date
   * answers make poor labels and are skipped; with nothing better, it's [keyProperty].
   */
  fun labelPropertyFor(
    form: EditorForm,
    properties: List<EntityProperty>,
    keyProperty: String,
  ): String {
    val names = properties.map { it.name }.toSet() - keyProperty
    val candidates = form.questions.filter { it.name in names }
    fun EditorQuestion.isText() = type == EditorQuestionType.TEXT
    fun EditorQuestion.hasNameToken(vararg tokens: String) =
      name.lowercase().split('_').any { it in tokens }
    val ranked =
      candidates.firstOrNull { it.isText() && it.hasNameToken("name", "title") }
        ?: candidates.firstOrNull { it.isText() && it.hasNameToken("id", "code", "number") }
        ?: candidates.firstOrNull { it.type == EditorQuestionType.SELECT_ONE }
        ?: candidates.firstOrNull { it.isText() }
    return ranked?.name ?: keyProperty
  }

  /**
   * A new [kind] dataset [key] linked to Form [formKey] (so submissions of [form] add its features
   * or rows), named [displayName] with ID [id]. Its schema mirrors the Form's questions
   * ([linkedProperties]), keyed by `id` and labeled by [labelPropertyFor]; a Map layer's geometry
   * kind follows the Form's primary geometry question.
   */
  fun newLinkedDataset(
    form: EditorForm,
    key: String,
    formKey: String,
    displayName: String,
    id: String,
    kind: DatasetKind = datasetKindFor(form),
  ): EntityDataset {
    val properties = linkedProperties(form, kind)
    val keyProperty = properties.first().name
    return EntityDataset(
      key = key,
      kind = kind,
      id = id,
      displayName = displayName,
      geometryType = geometryTypeFor(form),
      keyProperty = keyProperty,
      labelProperty = labelPropertyFor(form, properties, keyProperty),
      linkedFormKey = formKey,
      properties = properties,
    )
  }

  /**
   * [dataset] with its schema synchronized to the questions of the Form it's linked to. The dataset
   * is a Map layer if the form has a geometry question (whose type sets the layer's point / line /
   * polygon kind, unless the layer's plots are generated), otherwise a Data table.
   */
  fun syncedWithForm(dataset: EntityDataset, form: EditorForm): EntityDataset {
    val kind = datasetKindFor(form)
    val formProps = formProperties(form, kind)
    // Retain the existing key property if not in the form, or ensure at least one property exists.
    val idProp = dataset.properties.firstOrNull { it.name == dataset.keyProperty } ?: idProperty
    val activeProps =
      if (formProps.any { it.name == idProp.name }) formProps else listOf(idProp) + formProps
    val activeNames = activeProps.map { it.name }.toSet()
    val existingByName = dataset.properties.associateBy { it.name }
    val preservedDeletedProps =
      form.deletedQuestions
        .filter {
          it.type != EditorQuestionType.NOTE &&
            !(kind == DatasetKind.MAP_LAYER && it.type.isGeometry) &&
            it.name !in activeNames
        }
        .distinctBy { it.name }
        .map { dq ->
          existingByName[dq.name]?.copy(required = false)
            ?: questionToProperty(dq, required = false)
        }
    val combinedProps = activeProps + preservedDeletedProps
    val keyProp =
      if (combinedProps.any { it.name == dataset.keyProperty }) dataset.keyProperty
      else combinedProps.first().name
    val labelProp =
      if (activeProps.any { it.name == dataset.labelProperty }) dataset.labelProperty
      else labelPropertyFor(form, activeProps, keyProp)
    return dataset.copy(
      kind = kind,
      // Generated layers take their geometry from the sample design, not the form.
      geometryType =
        if (form.hasGeometry && !dataset.isGenerated) geometryTypeFor(form)
        else dataset.geometryType,
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
        when (dataset.geometryType) {
          GeometryType.POINT -> EditorQuestionType.LOCATION
          GeometryType.LINE_STRING -> EditorQuestionType.LINE
          GeometryType.POLYGON -> EditorQuestionType.POLYGON
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
          PropertyType.INTEGER,
          PropertyType.DECIMAL -> EditorQuestionType.NUMBER
          PropertyType.DATE -> EditorQuestionType.DATE
          PropertyType.BOOLEAN -> EditorQuestionType.SELECT_ONE
          PropertyType.TEXT -> EditorQuestionType.TEXT
        }
      val decimalPlaces =
        if (prop.type == PropertyType.DECIMAL) EditorQuestion.DEFAULT_DECIMAL_PLACES else 0
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
          decimalPlaces = decimalPlaces,
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
            else LatLng(all.map { it.latitude }.average(), all.map { it.longitude }.average())
          // Offset successive features so they don't overlap.
          val step = 0.002 * ((dataset.rows.size % 5) - 2)
          LatLng.coerced(center.latitude + step, center.longitude - step)
        }
    val s = 0.0015
    return when (dataset.geometryType) {
      GeometryType.POINT -> listOf(c)
      GeometryType.LINE_STRING ->
        listOf(
          c,
          LatLng.coerced(c.latitude + s, c.longitude + s),
          LatLng.coerced(c.latitude + s, c.longitude + 2 * s),
        )
      GeometryType.POLYGON ->
        listOf(
          c,
          LatLng.coerced(c.latitude, c.longitude + s),
          LatLng.coerced(c.latitude - s, c.longitude + s),
          LatLng.coerced(c.latitude - s, c.longitude),
        )
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

/**
 * This Form's save-to logic and dataset-backed choice questions after dataset [oldId] was renamed
 * to [newId].
 */
fun EditorForm.withRenamedTargetDataset(oldId: String, newId: String): EditorForm =
  copy(
    saveTo = if (saveTo.targetDatasetId == oldId) saveTo.copy(targetDatasetId = newId) else saveTo,
    questions =
      questions.map { q -> if (q.choiceDatasetId == oldId) q.copy(choiceDatasetId = newId) else q },
  )

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
