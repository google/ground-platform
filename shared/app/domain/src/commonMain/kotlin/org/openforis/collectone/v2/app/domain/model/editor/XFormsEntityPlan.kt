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

import openforis.collectone.v1.forms.ActionDef
import openforis.collectone.v1.forms.ActionType
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.EntityDeclaration
import openforis.collectone.v1.forms.EntityPropertyMapping
import openforis.collectone.v1.forms.EntitySyncMetadata
import openforis.collectone.v1.forms.EventType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.ItemsetDef
import openforis.collectone.v1.forms.LabelDef

/**
 * Entity declaration derived from a Form's save-to logic and its target dataset, following the ODK
 * XForms Entities specification (version 2024.1.0).
 *
 * The ODK spec leaves `id`, `baseVersion`, and the label empty in the primary instance and fills
 * them with `<bind calculate>` / `<setvalue>`. Collect One's form engine evaluates those attributes
 * (and the label text) directly as XPath, so the generator writes the expression in both places:
 * ODK clients overwrite the instance default via the binds, and Collect One evaluates it in place.
 */
internal class XFormsEntityPlan(val form: EditorForm, val target: EditorDataset) {
  val isUpdate = form.saveTo.mode == SaveToMode.UPDATE
  val hasStatus = form.saveTo.status.enabled
  val selectsTarget = isUpdate && form.saveTo.idSource == EntityIdSource.SELECTED_FEATURE
  val idExpression: String =
    if (isUpdate) SaveToRules.entityIdExpression(form, target).orEmpty() else "uuid()"
  val baseVersionExpression: String =
    if (isUpdate && idExpression.isNotEmpty()) {
      "instance('${target.id}')/root/item[name = $idExpression]/__version"
    } else {
      ""
    }
  val labelExpression: String? =
    if (isUpdate) {
      null
    } else {
      val labelQuestion =
        form.questions.firstOrNull { it.name == target.labelProperty }
          ?: form.questions.firstOrNull { it.type == EditorQuestionType.TEXT }
      labelQuestion?.let { form.instancePath(it) } ?: xpathStringLiteral(form.title)
    }

  /** `entities:saveto` property per question key. */
  val saveTo: Map<String, String> =
    if (isUpdate) {
      form.saveTo.mappings
        .mapNotNull { m -> m.property?.let { m.questionKey to it } }
        .filter { (key, prop) ->
          form.find(key)?.type?.let { it != EditorQuestionType.NOTE } == true &&
            (!hasStatus || prop !in SaveToRules.STATUS_PROPERTIES)
        }
        .toMap()
    } else {
      val geometryKey = form.primaryGeometryQuestion?.key
      SaveToRules.savableQuestions(form)
        .mapNotNull { q ->
          when {
            q.key == geometryKey && target.isMapLayer -> q.key to SaveToRules.GEOMETRY_PROPERTY
            SaveToRules.isReservedProperty(q.name) -> null
            hasStatus && q.name in SaveToRules.STATUS_PROPERTIES -> null
            else -> q.key to q.name
          }
        }
        .toMap()
    }

  /**
   * Binds for the entity's metadata (`orx:meta/entities:entity`) and, with status marker rules, the
   * status fields whose `calculate` writes the feature's status properties.
   */
  fun bindings(): List<FieldBinding> = buildList {
    if (hasStatus) {
      val statusCalc = SaveToRules.statusCalculateExpression(form, target) { it.label }
      val symbolCalc = SaveToRules.statusCalculateExpression(form, target) { it.symbol.symbol }
      val colorCalc = SaveToRules.statusCalculateExpression(form, target) { it.colorHex }
      add(
        FieldBinding(
          field_path = SaveToRules.STATUS_FIELD,
          type = DataType.TYPE_STRING,
          read_only = true,
          calculate_expression = statusCalc,
          entity_saveto = SaveToRules.STATUS_PROPERTY,
        )
      )
      add(
        FieldBinding(
          field_path = SaveToRules.MARKER_SYMBOL_FIELD,
          type = DataType.TYPE_STRING,
          read_only = true,
          calculate_expression = symbolCalc,
          entity_saveto = SaveToRules.MARKER_SYMBOL_PROPERTY,
        )
      )
      add(
        FieldBinding(
          field_path = SaveToRules.MARKER_COLOR_FIELD,
          type = DataType.TYPE_STRING,
          read_only = true,
          calculate_expression = colorCalc,
          entity_saveto = SaveToRules.MARKER_COLOR_PROPERTY,
        )
      )
    }
    val path = "orx:meta/entities:entity"
    if (isUpdate) {
      add(
        FieldBinding(
          field_path = "$path/@id",
          type = DataType.TYPE_STRING,
          read_only = true,
          calculate_expression = idExpression,
        )
      )
      add(
        FieldBinding(
          field_path = "$path/@baseVersion",
          type = DataType.TYPE_STRING,
          read_only = true,
          calculate_expression = baseVersionExpression,
        )
      )
    } else {
      add(FieldBinding(field_path = "$path/@id", type = DataType.TYPE_STRING, read_only = true))
      labelExpression?.let { labelExpr ->
        add(
          FieldBinding(
            field_path = "$path/entities:label",
            type = DataType.TYPE_STRING,
            read_only = true,
            calculate_expression = labelExpr,
          )
        )
      }
    }
  }

  /**
   * New entities get their ID once, when the record is first loaded (ODK XForms Entities: the
   * `odk-instance-first-load` `setvalue` of `uuid()`), so it is stable across edits of the draft.
   */
  fun firstLoadActions(): List<ActionDef> =
    if (isUpdate) {
      emptyList()
    } else {
      listOf(
        ActionDef(
          events = listOf(EventType.EVENT_INSTANCE_FIRST_LOAD),
          type = ActionType.ACTION_SET_VALUE,
          target_field = "orx:meta/entities:entity/@id",
          value_expression = "uuid()",
        )
      )
    }

  /** The `entities:entity` declaration, mapping every bind with an `entities:saveto` property. */
  fun declaration(bindings: List<FieldBinding>): EntityDeclaration {
    val mappings =
      bindings
        .filter { it.entity_saveto.isNotEmpty() }
        .map {
          EntityPropertyMapping(
            entity_property = it.entity_saveto,
            source_field_path = it.field_path,
          )
        }
    val syncMeta =
      if (isUpdate && baseVersionExpression.isNotEmpty()) {
        EntitySyncMetadata(base_version_expression = baseVersionExpression)
      } else {
        null
      }
    return EntityDeclaration(
      dataset = target.id,
      entity_id_expression = idExpression,
      label_expression = labelExpression.orEmpty(),
      create_condition = if (isUpdate) "" else "1",
      update_condition = if (isUpdate) "1" else "",
      sync_metadata = syncMeta,
      property_mappings = mappings,
    )
  }

  /** Picker for the feature to update; the app pre-fills it when opened from a map feature. */
  fun targetPickerControl(): ControlDef {
    val noun = if (target.isMapLayer) "Map feature" else "Row"
    return ControlDef(
      field_ref = SaveToRules.TARGET_ENTITY_FIELD,
      type = ControlType.CONTROL_SELECT_ONE,
      label = LabelDef(text = "$noun to update"),
      hint =
        if (target.isMapLayer) {
          LabelDef(text = "Filled in automatically when the form is opened from a map feature.")
        } else {
          null
        },
      itemset =
        ItemsetDef(
          instance_id = target.id,
          nodeset_path = "/root/item",
          value_ref = "name",
          label_ref = "label",
        ),
    )
  }
}
