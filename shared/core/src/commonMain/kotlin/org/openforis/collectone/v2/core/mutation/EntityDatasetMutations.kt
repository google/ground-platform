/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.core.mutation

import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.survey.EntityDatasetDef
import openforis.collectone.v1.survey.EntityPropertyDefinition
import openforis.collectone.v1.survey.EntityType

/**
 * Delete masks and upserts of one `EntityDatasetDef` (a Data table or Map layer) inside a survey,
 * with the mask semantics described in `KeyedListMasks.kt`. Rows and property definitions are keyed
 * lists: rows by `row_key`, properties by ID or name.
 */
internal object EntityDatasetMutations {
  fun applyDatasetDelete(base: EntityDatasetDef, del: EntityDatasetDef): EntityDatasetDef {
    var d = base
    if (del.display_name.isNotEmpty()) d = d.copy(display_name = "")
    if (del.description.isNotEmpty()) d = d.copy(description = "")
    if (del.type != EntityType.ENTITY_TYPE_UNSPECIFIED) {
      d = d.copy(type = EntityType.ENTITY_TYPE_UNSPECIFIED)
    }
    if (del.key_property.isNotEmpty()) d = d.copy(key_property = "")
    if (del.label_property.isNotEmpty()) d = d.copy(label_property = "")
    if (del.field_creation_enabled) d = d.copy(field_creation_enabled = false)
    if (del.geometry_type != DataType.DATA_TYPE_UNSPECIFIED) {
      d = d.copy(geometry_type = DataType.DATA_TYPE_UNSPECIFIED)
    }
    if (del.sampling != null) d = d.copy(sampling = null)
    if (del.imported != null) d = d.copy(imported = null)
    if (del.default_style != null) d = d.copy(default_style = null)
    if (del.visible_by_default) d = d.copy(visible_by_default = false)
    return applyKeyedListDeletes(d, del)
  }

  private fun applyKeyedListDeletes(
    base: EntityDatasetDef,
    del: EntityDatasetDef,
  ): EntityDatasetDef {
    var d = base
    if (del.properties.isNotEmpty()) {
      d =
        d.copy(
          properties =
            d.properties.applyKeyedDelete(
              deleteItems = del.properties,
              keyOf = MutationKeys::propDefKey,
              isLeaf = { it == EntityPropertyDefinition(id = it.id, name = it.name) },
              applyBranchDelete = ::applyPropertyDelete,
            )
        )
    }
    if (del.rows.isNotEmpty()) {
      d =
        d.copy(
          rows =
            d.rows.applyKeyedDelete(
              deleteItems = del.rows,
              keyOf = { it.row_key },
              isLeaf = { it.values.isEmpty() && it.geometry == null },
              applyBranchDelete = { b, r ->
                b.copy(
                  values = b.values - r.values.keys,
                  geometry = if (r.geometry != null) null else b.geometry,
                )
              },
            )
        )
    }
    return d
  }

  private fun applyPropertyDelete(
    base: EntityPropertyDefinition,
    del: EntityPropertyDefinition,
  ): EntityPropertyDefinition {
    var prop = base
    if (del.label.isNotEmpty()) prop = prop.copy(label = "")
    if (del.required) prop = prop.copy(required = false)
    if (del.concept_ref != null) prop = prop.copy(concept_ref = null)
    return prop
  }

  fun mergeDataset(base: EntityDatasetDef, up: EntityDatasetDef): EntityDatasetDef {
    var d = base
    if (up.display_name.isNotEmpty()) d = d.copy(display_name = up.display_name)
    if (up.description.isNotEmpty()) d = d.copy(description = up.description)
    if (up.type != EntityType.ENTITY_TYPE_UNSPECIFIED) d = d.copy(type = up.type)
    if (up.key_property.isNotEmpty()) d = d.copy(key_property = up.key_property)
    if (up.label_property.isNotEmpty()) d = d.copy(label_property = up.label_property)
    if (up.field_creation_enabled) d = d.copy(field_creation_enabled = true)
    if (up.geometry_type != DataType.DATA_TYPE_UNSPECIFIED) {
      d = d.copy(geometry_type = up.geometry_type)
    }
    d = mergeOrigin(d, up)
    if (up.editor_key.isNotEmpty()) d = d.copy(editor_key = up.editor_key)
    if (up.linked_form_key.isNotEmpty()) d = d.copy(linked_form_key = up.linked_form_key)
    if (up.default_style != null) d = d.copy(default_style = up.default_style)
    if (up.visible_by_default) d = d.copy(visible_by_default = true)
    if (up.sort_order != 0) d = d.copy(sort_order = up.sort_order)
    return mergeKeyedLists(d, up)
  }

  /**
   * A dataset comes either from a sampling design or from an imported file, never both: setting one
   * clears the other. When an upsert sets both, the import wins.
   */
  private fun mergeOrigin(base: EntityDatasetDef, up: EntityDatasetDef): EntityDatasetDef {
    var d = base
    if (up.sampling != null) d = d.copy(sampling = up.sampling, imported = null)
    if (up.imported != null) d = d.copy(imported = up.imported, sampling = null)
    return d
  }

  private fun mergeKeyedLists(base: EntityDatasetDef, up: EntityDatasetDef): EntityDatasetDef {
    var d = base
    if (up.properties.isNotEmpty()) {
      d =
        d.copy(
          properties =
            d.properties.mergeKeyedUpsert(
              upsertItems = up.properties,
              keyOf = MutationKeys::propDefKey,
              mergeItem = { _, u -> u },
              orderOf = { it.sort_order },
            )
        )
    }
    if (up.rows.isNotEmpty()) {
      d =
        d.copy(
          rows =
            d.rows.mergeKeyedUpsert(
              upsertItems = up.rows,
              keyOf = { it.row_key },
              mergeItem = { old, u ->
                old.copy(
                  values = old.values + u.values,
                  geometry = u.geometry ?: old.geometry,
                  sort_order = if (u.sort_order != 0) u.sort_order else old.sort_order,
                )
              },
              orderOf = { it.sort_order },
            )
        )
    }
    return d
  }
}
