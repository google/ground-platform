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

import openforis.collectone.v1.library.CodeList
import openforis.collectone.v1.library.CodeListItem
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.ExportProfileDef
import openforis.collectone.v1.library.FormTemplateDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LocalizedText
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.library.OrganizationLibrarySettings
import openforis.collectone.v1.library.PurposePackDef

/** Applies sparse `OrganizationLibraryRecord` `delete` and `upsert` masks. */
object LibraryMutations {
  /** Applies [delete] then merges [upsert]. */
  fun apply(
    record: OrganizationLibraryRecord,
    delete: OrganizationLibraryRecord? = null,
    upsert: OrganizationLibraryRecord? = null,
  ): OrganizationLibraryRecord {
    val afterDelete = if (delete != null) applyDelete(record, delete) else record
    return if (upsert != null) merge(afterDelete, upsert) else afterDelete
  }

  fun applyDelete(
    record: OrganizationLibraryRecord,
    delete: OrganizationLibraryRecord,
  ): OrganizationLibraryRecord {
    var r = record
    if (delete.is_deleted) r = r.copy(is_deleted = false)
    if (delete.settings != null) {
      val ds = delete.settings
      r =
        if (ds == OrganizationLibrarySettings(organization_id = ds.organization_id)) {
          r.copy(settings = OrganizationLibrarySettings(organization_id = r.organization_id))
        } else {
          r.copy(
            settings =
              r.settings?.copy(
                hidden_global_entry_ids =
                  if (ds.hidden_global_entry_ids.isNotEmpty()) emptyList()
                  else r.settings.hidden_global_entry_ids
              )
          )
        }
    }
    val db = delete.bundle
    if (db != null) {
      val b = r.bundle ?: LibraryBundle()
      r =
        r.copy(
          bundle =
            b.copy(
              concepts =
                b.concepts.applyKeyedDelete(
                  deleteItems = db.concepts,
                  keyOf = { it.id },
                  isLeaf = { it == ConceptDef(id = it.id) },
                  applyBranchDelete = { base, del ->
                    var c = base
                    if (del.label != null) {
                      c = c.copy(label = applyLocalizedTextDelete(c.label, del.label))
                    }
                    if (del.description != null) {
                      c =
                        c.copy(
                          description = applyLocalizedTextDelete(c.description, del.description)
                        )
                    }
                    if (del.unit.isNotEmpty()) c = c.copy(unit = "")
                    if (del.code_list != null) {
                      val items =
                        c.code_list
                          ?.items
                          .orEmpty()
                          .applyKeyedDelete(
                            deleteItems = del.code_list.items,
                            keyOf = { it.code },
                            isLeaf = { it == CodeListItem(code = it.code) },
                            applyBranchDelete = { bi, di ->
                              bi.copy(external_ids = bi.external_ids - di.external_ids.keys)
                            },
                          )
                      c = c.copy(code_list = if (items.isEmpty()) null else CodeList(items = items))
                    }
                    if (del.goals.isNotEmpty()) c = c.copy(goals = emptyList())
                    if (del.references.isNotEmpty()) c = c.copy(references = emptyList())
                    if (del.aliases.isNotEmpty()) c = c.copy(aliases = emptyList())
                    c
                  },
                ),
              form_templates =
                b.form_templates.applyKeyedDelete(
                  deleteItems = db.form_templates,
                  keyOf = { it.id },
                  isLeaf = { it == FormTemplateDef(id = it.id) },
                  applyBranchDelete = { base, _ -> base },
                ),
              purpose_packs =
                b.purpose_packs.applyKeyedDelete(
                  deleteItems = db.purpose_packs,
                  keyOf = { it.id },
                  isLeaf = { it == PurposePackDef(id = it.id) },
                  applyBranchDelete = { base, _ -> base },
                ),
              export_profiles =
                b.export_profiles.applyKeyedDelete(
                  deleteItems = db.export_profiles,
                  keyOf = { it.id },
                  isLeaf = { it == ExportProfileDef(id = it.id) },
                  applyBranchDelete = { base, del ->
                    base.copy(field_concepts = base.field_concepts - del.field_concepts.keys)
                  },
                ),
            )
        )
    }
    return r
  }

  private fun applyLocalizedTextDelete(base: LocalizedText?, del: LocalizedText): LocalizedText? {
    if (base == null) return null
    if (del.values.isEmpty()) return null
    val remaining = base.values - del.values.keys
    return if (remaining.isEmpty()) null else LocalizedText(values = remaining)
  }

  fun merge(
    base: OrganizationLibraryRecord,
    upsert: OrganizationLibraryRecord,
  ): OrganizationLibraryRecord {
    var r = base
    if (upsert.organization_id.isNotEmpty()) r = r.copy(organization_id = upsert.organization_id)
    if (upsert.is_deleted) r = r.copy(is_deleted = true)
    if (upsert.settings != null) r = r.copy(settings = upsert.settings)
    val ub = upsert.bundle
    if (ub != null) {
      val b = r.bundle ?: LibraryBundle()
      r =
        r.copy(
          bundle =
            b.copy(
              concepts =
                b.concepts.mergeKeyedUpsert(
                  upsertItems = ub.concepts,
                  keyOf = { it.id },
                  mergeItem = { _, u -> u },
                  orderOf = { it.sort_order },
                ),
              form_templates =
                b.form_templates.mergeKeyedUpsert(
                  upsertItems = ub.form_templates,
                  keyOf = { it.id },
                  mergeItem = { _, u -> u },
                  orderOf = { it.sort_order },
                ),
              purpose_packs =
                b.purpose_packs.mergeKeyedUpsert(
                  upsertItems = ub.purpose_packs,
                  keyOf = { it.id },
                  mergeItem = { _, u -> u },
                  orderOf = { it.sort_order },
                ),
              export_profiles =
                b.export_profiles.mergeKeyedUpsert(
                  upsertItems = ub.export_profiles,
                  keyOf = { it.id },
                  mergeItem = { _, u -> u },
                  orderOf = { it.sort_order },
                ),
            )
        )
    }
    return r
  }
}
