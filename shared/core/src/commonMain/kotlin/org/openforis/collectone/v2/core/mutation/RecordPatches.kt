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

import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.ReviewStatus
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.forms.ActionDef
import groundplatform.v2.forms.ChoiceItem
import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.EntityDeclaration
import groundplatform.v2.forms.EntityPropertyMapping
import groundplatform.v2.forms.FieldBinding
import groundplatform.v2.forms.FieldDefinition
import groundplatform.v2.forms.ForeignAttribute
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.GroupDef
import groundplatform.v2.forms.ModelDef
import groundplatform.v2.forms.PreloadType
import groundplatform.v2.forms.PrimaryInstance
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.RecordNode
import groundplatform.v2.forms.RecordNodeList
import groundplatform.v2.forms.RecordSchema
import groundplatform.v2.forms.RepeatDef
import groundplatform.v2.forms.SecondaryInstance
import groundplatform.v2.forms.TranslationCatalog
import groundplatform.v2.forms.ViewComponent
import groundplatform.v2.forms.ViewDef
import groundplatform.v2.library.CodeList
import groundplatform.v2.library.CodeListItem
import groundplatform.v2.library.ConceptDef
import groundplatform.v2.library.ExportProfileDef
import groundplatform.v2.library.FormTemplateDef
import groundplatform.v2.library.LibraryBundle
import groundplatform.v2.library.LocalizedText
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.library.OrganizationLibrarySettings
import groundplatform.v2.library.PurposePackDef
import groundplatform.v2.survey.AclEntry
import groundplatform.v2.survey.EntityDatasetDef
import groundplatform.v2.survey.EntityPropertyDefinition
import groundplatform.v2.survey.EntityType
import groundplatform.v2.survey.FormLaunchConfig
import groundplatform.v2.survey.ImagerySource
import groundplatform.v2.survey.LayerDef
import groundplatform.v2.survey.MapConfig
import groundplatform.v2.survey.MembershipStatus
import groundplatform.v2.survey.OfflineTilePackageDef
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationMember
import groundplatform.v2.survey.OrganizationRole
import groundplatform.v2.survey.OrganizationType
import groundplatform.v2.survey.PeerDataVisibility
import groundplatform.v2.survey.QuotaLimits
import groundplatform.v2.survey.QuotaTier
import groundplatform.v2.survey.Role
import groundplatform.v2.survey.SharingPolicy
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyState

/*
 * Sparse-proto mutation semantics shared by the user data service and the clients. Every mutation
 * carries two optional sparse instances of the target domain proto `T` (`delete` and `upsert`),
 * applied in order as `merge(applyDelete(base, delete), upsert)`:
 *
 * 1. Structural deletion mask (`delete`):
 *    - Any non-default scalar field clears that scalar on `base` to its proto3 default.
 *    - Any non-empty repeated value list (e.g., `supported_languages`, `purpose_ids`,
 *      `program_ids`, `quarantined_user_ids`, `hidden_global_entry_ids`, or geometry vertices)
 *      clears that list to `emptyList()`.
 *    - Any map entry `k -> v` removes key `k` when `v` is primitive or an empty message, or
 *      recurses into `v` when `v` has child fields set.
 *    - Any sub-message or keyed repeated item (matched by its stable ID/key) is removed/cleared
 *      when it is a leaf (no child fields set beyond its ID/key), or retained and recursed into
 *      when it is a branch (child fields are populated).
 *
 * 2. Sparse `upsert` merge (`upsert`):
 *    - Non-default scalars overwrite `base`.
 *    - Non-empty repeated value lists replace `base`'s list.
 *    - Keyed repeated entity lists upsert by ID (recursively merging existing items and appending
 *      new ones) and sort by `sort_order` when `sort_order` is used.
 *
 * Server bookkeeping (`revision`, `version`, `record_version`, `commit_timestamp`, `sync_status_key`)
 * is left to the caller.
 */

private fun <T> List<T>.sortByOrderIfUsed(orderOf: (T) -> Int): List<T> =
  if (any { orderOf(it) != 0 }) sortedBy(orderOf) else this

private fun <T> List<T>.applyKeyedDelete(
  deleteItems: List<T>,
  keyOf: (T) -> String,
  isLeaf: (T) -> Boolean,
  applyBranchDelete: (T, T) -> T,
): List<T> {
  if (deleteItems.isEmpty()) return this
  val byKey = deleteItems.associateBy { keyOf(it) }
  return mapNotNull { item ->
    val del = byKey[keyOf(item)] ?: return@mapNotNull item
    if (isLeaf(del)) null else applyBranchDelete(item, del)
  }
}

private fun <T> List<T>.mergeKeyedUpsert(
  upsertItems: List<T>,
  keyOf: (T) -> String,
  mergeItem: (T, T) -> T,
  orderOf: ((T) -> Int)? = null,
): List<T> {
  if (upsertItems.isEmpty()) return this
  val result = LinkedHashMap<String, T>()
  for (item in this) result[keyOf(item)] = item
  for (up in upsertItems) {
    val k = keyOf(up)
    val existing = result[k]
    result[k] = if (existing != null) mergeItem(existing, up) else up
  }
  val merged = result.values.toList()
  return if (orderOf != null) merged.sortByOrderIfUsed(orderOf) else merged
}

/** Key extractors for keyed repeated messages. */
object MutationKeys {
  fun aclEntryKey(e: AclEntry): String =
    e.id.ifBlank { e.email.ifBlank { e.user_id } }.trim().lowercase()

  fun memberKey(m: OrganizationMember): String =
    m.id.ifBlank { m.email.ifBlank { m.user_id } }.trim().lowercase()

  fun fieldDefKey(f: FieldDefinition): String = f.id.ifBlank { f.name }

  fun bindingKey(b: FieldBinding): String = b.id.ifBlank { b.field_path }

  fun foreignAttrKey(a: ForeignAttribute): String = a.id.ifBlank { a.qualified_name }

  fun actionKey(a: ActionDef): String = a.id.ifBlank { "${a.type}:${a.target_field}" }

  fun entityDeclKey(e: EntityDeclaration): String = e.id.ifBlank { e.dataset }

  fun entityPropMapKey(m: EntityPropertyMapping): String = m.id.ifBlank { m.entity_property }

  fun choiceKey(c: ChoiceItem): String = c.id.ifBlank { c.value_ }

  fun componentKey(c: ViewComponent): String =
    c.id.ifBlank {
      c.control?.field_ref ?: c.group?.field_ref ?: c.repeat?.field_ref ?: ""
    }

  fun propDefKey(p: EntityPropertyDefinition): String = p.id.ifBlank { p.name }

  fun recordNodeKey(n: RecordNode, index: Int = 0): String = n.id.ifBlank { index.toString() }
}

/** Applies sparse `SurveyDef` and `SurveyAcl` `delete` and `upsert` masks. */
object SurveyPatches {
  /**
   * Roles allowed in `SurveyAcl.default_role`. Others leave it unchanged; the server rejects
   * `SURVEY_ORGANIZER` because organizers must be named on the ACL.
   */
  val SHARING_ROLES: Set<Role> = setOf(Role.VIEWER, Role.DATA_COLLECTOR)

  /** Applies [delete] then merges [upsert] onto [survey]. */
  fun apply(
    survey: SurveyDef,
    delete: SurveyDef? = null,
    upsert: SurveyDef? = null,
  ): SurveyDef {
    val afterDelete = if (delete != null) applyDelete(survey, delete) else survey
    return if (upsert != null) merge(afterDelete, upsert) else afterDelete
  }

  /** Applies [delete] then merges [upsert] onto [acl]. */
  fun applyAcl(
    acl: SurveyAcl,
    delete: SurveyAcl? = null,
    upsert: SurveyAcl? = null,
  ): SurveyAcl {
    val afterDelete = if (delete != null) applyAclDelete(acl, delete) else acl
    return if (upsert != null) mergeAcl(afterDelete, upsert) else afterDelete
  }

  fun applyDelete(survey: SurveyDef, delete: SurveyDef): SurveyDef {
    var s = survey
    if (delete.title.isNotEmpty()) s = s.copy(title = "")
    if (delete.description.isNotEmpty()) s = s.copy(description = "")
    if (delete.organization_id.isNotEmpty()) s = s.copy(organization_id = "")
    if (delete.owner_id.isNotEmpty()) s = s.copy(owner_id = "")
    if (delete.state != SurveyState.SURVEY_STATE_UNSPECIFIED) {
      s = s.copy(state = SurveyState.SURVEY_STATE_UNSPECIFIED)
    }
    if (delete.version.isNotEmpty()) s = s.copy(version = "")
    if (delete.default_language.isNotEmpty()) s = s.copy(default_language = "")
    if (delete.supported_languages.isNotEmpty()) s = s.copy(supported_languages = emptyList())
    if (delete.purpose_ids.isNotEmpty()) s = s.copy(purpose_ids = emptyList())
    if (delete.program_ids.isNotEmpty()) s = s.copy(program_ids = emptyList())
    if (delete.closed_at != null) s = s.copy(closed_at = null)
    if (delete.closed_at_iso.isNotEmpty()) s = s.copy(closed_at_iso = "")
    if (delete.outcome != null) s = s.copy(outcome = null)
    if (delete.location_label.isNotEmpty()) s = s.copy(location_label = "")
    if (delete.coordinates_label.isNotEmpty()) s = s.copy(coordinates_label = "")
    if (delete.offline_size_label.isNotEmpty()) s = s.copy(offline_size_label = "")
    if (delete.is_downloaded) s = s.copy(is_downloaded = false)
    if (delete.thumbnail_theme.isNotEmpty()) s = s.copy(thumbnail_theme = "")
    if (delete.entity_count != 0) s = s.copy(entity_count = 0)
    if (delete.other_purpose.isNotEmpty()) s = s.copy(other_purpose = "")
    if (delete.primary_form_id.isNotEmpty()) s = s.copy(primary_form_id = "")
    if (delete.trash_time != null) s = s.copy(trash_time = null)
    if (delete.quota_limits != null) {
      val q = delete.quota_limits
      s =
        if (q == QuotaLimits()) s.copy(quota_limits = null)
        else s.copy(quota_limits = s.quota_limits?.let { applyQuotaDelete(it, q) })
    }
    if (delete.xforms_xml_by_form_id.isNotEmpty()) {
      s =
        s.copy(xforms_xml_by_form_id = s.xforms_xml_by_form_id - delete.xforms_xml_by_form_id.keys)
    }
    if (delete.translations != null) {
      val dt = delete.translations
      s =
        if (dt.languages.isEmpty()) s.copy(translations = null)
        else s.copy(translations = s.translations?.let { applyTranslationCatalogDelete(it, dt) })
    }
    if (delete.map_config != null) {
      val dm = delete.map_config
      s =
        if (dm == MapConfig()) s.copy(map_config = null)
        else s.copy(map_config = s.map_config?.let { applyMapConfigDelete(it, dm) })
    }
    if (delete.forms.isNotEmpty()) {
      s =
        s.copy(
          forms =
            s.forms.applyKeyedDelete(
              deleteItems = delete.forms,
              keyOf = { it.form_id },
              isLeaf = { it == FormDef(form_id = it.form_id) },
              applyBranchDelete = ::applyFormDelete,
            )
        )
    }
    if (delete.entity_datasets.isNotEmpty()) {
      s =
        s.copy(
          entity_datasets =
            s.entity_datasets.applyKeyedDelete(
              deleteItems = delete.entity_datasets,
              keyOf = { it.id },
              isLeaf = { it == EntityDatasetDef(id = it.id) },
              applyBranchDelete = ::applyDatasetDelete,
            )
        )
    }
    if (delete.form_launch_configs.isNotEmpty()) {
      s =
        s.copy(
          form_launch_configs =
            s.form_launch_configs.applyKeyedDelete(
              deleteItems = delete.form_launch_configs,
              keyOf = { it.form_id },
              isLeaf = { it == FormLaunchConfig(form_id = it.form_id) },
              applyBranchDelete = { base, _ -> base },
            )
        )
    }
    if (delete.form_concept_links.isNotEmpty()) {
      s =
        s.copy(
          form_concept_links =
            s.form_concept_links.applyKeyedDelete(
              deleteItems = delete.form_concept_links,
              keyOf = { it.form_id },
              isLeaf = { it.field_concepts.isEmpty() },
              applyBranchDelete = { base, del ->
                base.copy(field_concepts = base.field_concepts - del.field_concepts.keys)
              },
            )
        )
    }
    return s
  }

  fun merge(base: SurveyDef, upsert: SurveyDef): SurveyDef {
    var s = base
    if (upsert.survey_id.isNotEmpty()) s = s.copy(survey_id = upsert.survey_id)
    if (upsert.title.isNotEmpty()) s = s.copy(title = upsert.title)
    if (upsert.description.isNotEmpty()) s = s.copy(description = upsert.description)
    if (upsert.organization_id.isNotEmpty()) s = s.copy(organization_id = upsert.organization_id)
    if (upsert.owner_id.isNotEmpty()) s = s.copy(owner_id = upsert.owner_id)
    if (upsert.state != SurveyState.SURVEY_STATE_UNSPECIFIED) s = s.copy(state = upsert.state)
    if (upsert.version.isNotEmpty()) s = s.copy(version = upsert.version)
    if (upsert.default_language.isNotEmpty()) {
      s = s.copy(default_language = upsert.default_language)
    }
    if (upsert.supported_languages.isNotEmpty()) {
      s = s.copy(supported_languages = upsert.supported_languages)
    }
    if (upsert.audit_info != null) s = s.copy(audit_info = upsert.audit_info)
    if (upsert.custom_extensions.isNotEmpty()) {
      s = s.copy(custom_extensions = upsert.custom_extensions)
    }
    if (upsert.purpose_ids.isNotEmpty()) s = s.copy(purpose_ids = upsert.purpose_ids)
    if (upsert.program_ids.isNotEmpty()) s = s.copy(program_ids = upsert.program_ids)
    if (upsert.outcome != null) s = s.copy(outcome = upsert.outcome)
    if (upsert.closed_at != null) s = s.copy(closed_at = upsert.closed_at)
    if (upsert.closed_at_iso.isNotEmpty()) s = s.copy(closed_at_iso = upsert.closed_at_iso)
    if (upsert.saved_revision_number != 0L) {
      s = s.copy(saved_revision_number = upsert.saved_revision_number)
    }
    if (upsert.location_label.isNotEmpty()) s = s.copy(location_label = upsert.location_label)
    if (upsert.coordinates_label.isNotEmpty()) {
      s = s.copy(coordinates_label = upsert.coordinates_label)
    }
    if (upsert.offline_size_label.isNotEmpty()) {
      s = s.copy(offline_size_label = upsert.offline_size_label)
    }
    if (upsert.is_downloaded) s = s.copy(is_downloaded = true)
    if (upsert.thumbnail_theme.isNotEmpty()) s = s.copy(thumbnail_theme = upsert.thumbnail_theme)
    if (upsert.entity_count != 0) s = s.copy(entity_count = upsert.entity_count)
    if (upsert.other_purpose.isNotEmpty()) s = s.copy(other_purpose = upsert.other_purpose)
    if (upsert.primary_form_id.isNotEmpty()) s = s.copy(primary_form_id = upsert.primary_form_id)
    if (upsert.xforms_xml_by_form_id.isNotEmpty()) {
      s = s.copy(xforms_xml_by_form_id = s.xforms_xml_by_form_id + upsert.xforms_xml_by_form_id)
    }
    if (upsert.quota_limits != null) {
      s =
        s.copy(
          quota_limits =
            s.quota_limits?.let { mergeQuotaLimits(it, upsert.quota_limits) } ?: upsert.quota_limits
        )
    }
    if (upsert.translations != null) {
      s =
        s.copy(
          translations =
            s.translations?.let { mergeTranslationCatalog(it, upsert.translations) }
              ?: upsert.translations
        )
    }
    if (upsert.map_config != null) {
      s =
        s.copy(
          map_config =
            s.map_config?.let { mergeMapConfig(it, upsert.map_config) } ?: upsert.map_config
        )
    }
    if (upsert.forms.isNotEmpty()) {
      s =
        s.copy(
          forms =
            s.forms.mergeKeyedUpsert(
              upsertItems = upsert.forms,
              keyOf = { it.form_id },
              mergeItem = ::mergeForm,
              orderOf = { it.sort_order },
            )
        )
    }
    if (upsert.entity_datasets.isNotEmpty()) {
      s =
        s.copy(
          entity_datasets =
            s.entity_datasets.mergeKeyedUpsert(
              upsertItems = upsert.entity_datasets,
              keyOf = { it.id },
              mergeItem = ::mergeDataset,
              orderOf = { it.sort_order },
            )
        )
    }
    if (upsert.form_launch_configs.isNotEmpty()) {
      s =
        s.copy(
          form_launch_configs =
            s.form_launch_configs.mergeKeyedUpsert(
              upsertItems = upsert.form_launch_configs,
              keyOf = { it.form_id },
              mergeItem = { _, up -> up },
              orderOf = { it.sort_order },
            )
        )
    }
    if (upsert.form_concept_links.isNotEmpty()) {
      s =
        s.copy(
          form_concept_links =
            s.form_concept_links.mergeKeyedUpsert(
              upsertItems = upsert.form_concept_links,
              keyOf = { it.form_id },
              mergeItem = { old, up ->
                old.copy(field_concepts = old.field_concepts + up.field_concepts)
              },
            )
        )
    }
    return s
  }

  private fun applyMapConfigDelete(base: MapConfig, del: MapConfig): MapConfig {
    var mc = base
    if (del.initial_center != null) mc = mc.copy(initial_center = null)
    if (del.initial_zoom != 0.0) mc = mc.copy(initial_zoom = 0.0)
    if (del.survey_area != null) mc = mc.copy(survey_area = null)
    if (del.layers.isNotEmpty()) {
      mc =
        mc.copy(
          layers =
            mc.layers.applyKeyedDelete(
              deleteItems = del.layers,
              keyOf = { it.id },
              isLeaf = { it == LayerDef(id = it.id) },
              applyBranchDelete = { b, d ->
                var l = b
                if (d.label.isNotEmpty()) l = l.copy(label = "")
                if (d.visible_by_default) l = l.copy(visible_by_default = false)
                if (d.default_style != null) l = l.copy(default_style = null)
                if (d.entity_dataset_id != null) l = l.copy(entity_dataset_id = null)
                if (d.form_geometry != null) l = l.copy(form_geometry = null)
                l
              },
            )
        )
    }
    if (del.offline_tile_packages.isNotEmpty()) {
      mc =
        mc.copy(
          offline_tile_packages =
            mc.offline_tile_packages.applyKeyedDelete(
              deleteItems = del.offline_tile_packages,
              keyOf = { it.id },
              isLeaf = { it == OfflineTilePackageDef(id = it.id) },
              applyBranchDelete = { b, _ -> b },
            )
        )
    }
    return mc
  }

  private fun mergeMapConfig(base: MapConfig, up: MapConfig): MapConfig {
    var mc = base
    if (up.initial_center != null) mc = mc.copy(initial_center = up.initial_center)
    if (up.initial_zoom != 0.0) mc = mc.copy(initial_zoom = up.initial_zoom)
    if (up.survey_area != null) mc = mc.copy(survey_area = up.survey_area)
    if (up.layers.isNotEmpty()) {
      mc =
        mc.copy(
          layers =
            mc.layers.mergeKeyedUpsert(
              upsertItems = up.layers,
              keyOf = { it.id },
              mergeItem = { _, u -> u },
              orderOf = { it.sort_order },
            )
        )
    }
    if (up.offline_tile_packages.isNotEmpty()) {
      mc =
        mc.copy(
          offline_tile_packages =
            mc.offline_tile_packages.mergeKeyedUpsert(
              upsertItems = up.offline_tile_packages,
              keyOf = { it.id },
              mergeItem = { _, u -> u },
              orderOf = { it.sort_order },
            )
        )
    }
    return mc
  }

  private fun applyDatasetDelete(base: EntityDatasetDef, del: EntityDatasetDef): EntityDatasetDef {
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
    if (del.properties.isNotEmpty()) {
      d =
        d.copy(
          properties =
            d.properties.applyKeyedDelete(
              deleteItems = del.properties,
              keyOf = MutationKeys::propDefKey,
              isLeaf = {
                it == EntityPropertyDefinition(id = it.id, name = it.name)
              },
              applyBranchDelete = { b, p ->
                var prop = b
                if (p.label.isNotEmpty()) prop = prop.copy(label = "")
                if (p.required) prop = prop.copy(required = false)
                if (p.concept_ref != null) prop = prop.copy(concept_ref = null)
                prop
              },
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
              isLeaf = { it.values.isEmpty() && it.geometry.isEmpty() },
              applyBranchDelete = { b, r ->
                b.copy(
                  values = b.values - r.values.keys,
                  geometry = if (r.geometry.isNotEmpty()) emptyList() else b.geometry,
                )
              },
            )
        )
    }
    return d
  }

  private fun mergeDataset(base: EntityDatasetDef, up: EntityDatasetDef): EntityDatasetDef {
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
    if (up.sampling != null) d = d.copy(sampling = up.sampling, imported = null)
    if (up.imported != null) d = d.copy(imported = up.imported, sampling = null)
    if (up.editor_key.isNotEmpty()) d = d.copy(editor_key = up.editor_key)
    if (up.linked_form_key.isNotEmpty()) d = d.copy(linked_form_key = up.linked_form_key)
    if (up.default_style != null) d = d.copy(default_style = up.default_style)
    if (up.visible_by_default) d = d.copy(visible_by_default = true)
    if (up.sort_order != 0) d = d.copy(sort_order = up.sort_order)
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
                  geometry = if (u.geometry.isNotEmpty()) u.geometry else old.geometry,
                  sort_order = if (u.sort_order != 0) u.sort_order else old.sort_order,
                )
              },
              orderOf = { it.sort_order },
            )
        )
    }
    return d
  }

  internal fun applyFormDelete(base: FormDef, del: FormDef): FormDef {
    var f = base
    if (del.title.isNotEmpty()) f = f.copy(title = "")
    if (del.version.isNotEmpty()) f = f.copy(version = "")
    if (del.default_language.isNotEmpty()) f = f.copy(default_language = "")
    if (del.model != null) {
      f =
        if (del.model == ModelDef()) f.copy(model = null)
        else f.copy(model = f.model?.let { applyModelDelete(it, del.model) })
    }
    if (del.view != null) {
      f =
        if (del.view == ViewDef()) f.copy(view = null)
        else f.copy(view = f.view?.let { applyViewDelete(it, del.view) })
    }
    return f
  }

  internal fun mergeForm(base: FormDef, up: FormDef): FormDef {
    var f = base
    if (up.form_id.isNotEmpty()) f = f.copy(form_id = up.form_id)
    if (up.title.isNotEmpty()) f = f.copy(title = up.title)
    if (up.version.isNotEmpty()) f = f.copy(version = up.version)
    if (up.default_language.isNotEmpty()) f = f.copy(default_language = up.default_language)
    if (up.sort_order != 0) f = f.copy(sort_order = up.sort_order)
    if (up.custom_extensions.isNotEmpty()) f = f.copy(custom_extensions = up.custom_extensions)
    if (up.model != null) {
      f = f.copy(model = f.model?.let { mergeModel(it, up.model) } ?: up.model)
    }
    if (up.view != null) {
      f = f.copy(view = f.view?.let { mergeView(it, up.view) } ?: up.view)
    }
    return f
  }

  private fun applyModelDelete(base: ModelDef, del: ModelDef): ModelDef {
    var m = base
    if (del.primary_instance != null) {
      val dpi = del.primary_instance
      m =
        if (dpi == PrimaryInstance()) m.copy(primary_instance = null)
        else
          m.copy(
            primary_instance =
              m.primary_instance?.let { bpi ->
                var pi = bpi
                if (dpi.record_schema != null) {
                  val drs = dpi.record_schema
                  pi =
                    if (drs == RecordSchema()) pi.copy(record_schema = null)
                    else
                      pi.copy(
                        record_schema =
                          pi.record_schema?.let { brs ->
                            brs.copy(
                              fields =
                                brs.fields.applyKeyedDelete(
                                  deleteItems = drs.fields,
                                  keyOf = MutationKeys::fieldDefKey,
                                  isLeaf = { it.fields.isEmpty() },
                                  applyBranchDelete = ::applyFieldDefDelete,
                                )
                            )
                          }
                      )
                }
                if (dpi.default_values != null) pi = pi.copy(default_values = null)
                pi
              }
          )
    }
    if (del.secondary_instances.isNotEmpty()) {
      m =
        m.copy(
          secondary_instances =
            m.secondary_instances.applyKeyedDelete(
              deleteItems = del.secondary_instances,
              keyOf = { it.id },
              isLeaf = { it == SecondaryInstance(id = it.id) },
              applyBranchDelete = { b, _ -> b },
            )
        )
    }
    if (del.bindings.isNotEmpty()) {
      m =
        m.copy(
          bindings =
            m.bindings.applyKeyedDelete(
              deleteItems = del.bindings,
              keyOf = MutationKeys::bindingKey,
              isLeaf = { it == FieldBinding(id = it.id, field_path = it.field_path) },
              applyBranchDelete = ::applyBindingDelete,
            )
        )
    }
    if (del.translations != null) {
      m =
        if (del.translations.languages.isEmpty()) m.copy(translations = null)
        else
          m.copy(
            translations =
              m.translations?.let { applyTranslationCatalogDelete(it, del.translations) }
          )
    }
    if (del.actions.isNotEmpty()) {
      m =
        m.copy(
          actions =
            m.actions.applyKeyedDelete(
              deleteItems = del.actions,
              keyOf = MutationKeys::actionKey,
              isLeaf = { true },
              applyBranchDelete = { b, _ -> b },
            )
        )
    }
    if (del.entities.isNotEmpty()) {
      m =
        m.copy(
          entities =
            m.entities.applyKeyedDelete(
              deleteItems = del.entities,
              keyOf = MutationKeys::entityDeclKey,
              isLeaf = { it == EntityDeclaration(id = it.id, dataset = it.dataset) },
              applyBranchDelete = { b, d ->
                b.copy(
                  property_mappings =
                    b.property_mappings.applyKeyedDelete(
                      deleteItems = d.property_mappings,
                      keyOf = MutationKeys::entityPropMapKey,
                      isLeaf = { true },
                      applyBranchDelete = { bm, _ -> bm },
                    )
                )
              },
            )
        )
    }
    if (del.metadata != null) m = m.copy(metadata = null)
    if (del.submission != null) m = m.copy(submission = null)
    return m
  }

  private fun applyFieldDefDelete(base: FieldDefinition, del: FieldDefinition): FieldDefinition =
    base.copy(
      fields =
        base.fields.applyKeyedDelete(
          deleteItems = del.fields,
          keyOf = MutationKeys::fieldDefKey,
          isLeaf = { it.fields.isEmpty() },
          applyBranchDelete = ::applyFieldDefDelete,
        )
    )

  private fun applyBindingDelete(base: FieldBinding, del: FieldBinding): FieldBinding {
    var b = base
    if (del.read_only) b = b.copy(read_only = false)
    if (del.relevant_expression.isNotEmpty()) b = b.copy(relevant_expression = "")
    if (del.calculate_expression.isNotEmpty()) b = b.copy(calculate_expression = "")
    if (del.constraint_expression.isNotEmpty()) b = b.copy(constraint_expression = "")
    if (del.required_expression.isNotEmpty()) b = b.copy(required_expression = "")
    if (del.constraint_message.isNotEmpty()) b = b.copy(constraint_message = "")
    if (del.required_message.isNotEmpty()) b = b.copy(required_message = "")
    if (del.sms_tag.isNotEmpty()) b = b.copy(sms_tag = "")
    if (del.entity_saveto.isNotEmpty()) b = b.copy(entity_saveto = "")
    if (del.max_pixels != 0) b = b.copy(max_pixels = 0)
    if (del.preload != PreloadType.PRELOAD_UNSPECIFIED) {
      b = b.copy(preload = PreloadType.PRELOAD_UNSPECIFIED)
    }
    if (del.preload_param.isNotEmpty()) b = b.copy(preload_param = "")
    if (del.read_only_expression.isNotEmpty()) b = b.copy(read_only_expression = "")
    if (del.foreign_attributes.isNotEmpty()) {
      b =
        b.copy(
          foreign_attributes =
            b.foreign_attributes.applyKeyedDelete(
              deleteItems = del.foreign_attributes,
              keyOf = MutationKeys::foreignAttrKey,
              isLeaf = { true },
              applyBranchDelete = { ba, _ -> ba },
            )
        )
    }
    return b
  }

  private fun mergeModel(base: ModelDef, up: ModelDef): ModelDef {
    var m = base
    if (up.primary_instance != null) m = m.copy(primary_instance = up.primary_instance)
    if (up.secondary_instances.isNotEmpty()) {
      m =
        m.copy(
          secondary_instances =
            m.secondary_instances.mergeKeyedUpsert(
              upsertItems = up.secondary_instances,
              keyOf = { it.id },
              mergeItem = { _, u -> u },
              orderOf = { it.sort_order },
            )
        )
    }
    if (up.bindings.isNotEmpty()) {
      m =
        m.copy(
          bindings =
            m.bindings.mergeKeyedUpsert(
              upsertItems = up.bindings,
              keyOf = MutationKeys::bindingKey,
              mergeItem = { _, u -> u },
            )
        )
    }
    if (up.translations != null) {
      m =
        m.copy(
          translations =
            m.translations?.let { mergeTranslationCatalog(it, up.translations) } ?: up.translations
        )
    }
    if (up.actions.isNotEmpty()) {
      m =
        m.copy(
          actions =
            m.actions.mergeKeyedUpsert(
              upsertItems = up.actions,
              keyOf = MutationKeys::actionKey,
              mergeItem = { _, u -> u },
              orderOf = { it.sort_order },
            )
        )
    }
    if (up.entities.isNotEmpty()) {
      m =
        m.copy(
          entities =
            m.entities.mergeKeyedUpsert(
              upsertItems = up.entities,
              keyOf = MutationKeys::entityDeclKey,
              mergeItem = { _, u -> u },
            )
        )
    }
    if (up.metadata != null) m = m.copy(metadata = up.metadata)
    if (up.submission != null) m = m.copy(submission = up.submission)
    if (up.custom_extensions.isNotEmpty()) m = m.copy(custom_extensions = up.custom_extensions)
    return m
  }

  private fun applyViewDelete(base: ViewDef, del: ViewDef): ViewDef =
    base.copy(components = applyComponentsDelete(base.components, del.components))

  private fun applyComponentsDelete(
    base: List<ViewComponent>,
    del: List<ViewComponent>,
  ): List<ViewComponent> =
    base.applyKeyedDelete(
      deleteItems = del,
      keyOf = MutationKeys::componentKey,
      isLeaf = ::isComponentLeafDelete,
      applyBranchDelete = ::applyComponentBranchDelete,
    )

  private fun isComponentLeafDelete(c: ViewComponent): Boolean {
    val ctrl = c.control
    val grp = c.group
    val rep = c.repeat
    return when {
      ctrl != null -> ctrl == ControlDef(field_ref = ctrl.field_ref)
      grp != null -> grp == GroupDef(field_ref = grp.field_ref)
      rep != null -> rep == RepeatDef(field_ref = rep.field_ref)
      else -> true
    }
  }

  private fun applyComponentBranchDelete(base: ViewComponent, del: ViewComponent): ViewComponent {
    val bCtrl = base.control
    val dCtrl = del.control
    val bGrp = base.group
    val dGrp = del.group
    val bRep = base.repeat
    val dRep = del.repeat
    return when {
      bCtrl != null && dCtrl != null -> {
        var c = bCtrl
        if (dCtrl.label != null) c = c.copy(label = null)
        if (dCtrl.hint != null) c = c.copy(hint = null)
        if (dCtrl.appearance.isNotEmpty()) c = c.copy(appearance = "")
        if (dCtrl.choices.isNotEmpty()) {
          c =
            c.copy(
              choices =
                c.choices.applyKeyedDelete(
                  deleteItems = dCtrl.choices,
                  keyOf = MutationKeys::choiceKey,
                  isLeaf = { true },
                  applyBranchDelete = { bc, _ -> bc },
                )
            )
        }
        if (dCtrl.itemset != null) c = c.copy(itemset = null)
        if (dCtrl.range_config != null) c = c.copy(range_config = null)
        if (dCtrl.geo_config != null) c = c.copy(geo_config = null)
        base.copy(control = c)
      }
      bGrp != null && dGrp != null ->
        base.copy(
          group = bGrp.copy(components = applyComponentsDelete(bGrp.components, dGrp.components))
        )
      bRep != null && dRep != null ->
        base.copy(
          repeat = bRep.copy(components = applyComponentsDelete(bRep.components, dRep.components))
        )
      else -> base
    }
  }

  private fun mergeView(base: ViewDef, up: ViewDef): ViewDef =
    base.copy(components = mergeComponents(base.components, up.components))

  private fun mergeComponents(
    base: List<ViewComponent>,
    up: List<ViewComponent>,
  ): List<ViewComponent> =
    base.mergeKeyedUpsert(
      upsertItems = up,
      keyOf = MutationKeys::componentKey,
      mergeItem = ::mergeComponent,
      orderOf = { it.sort_order },
    )

  private fun mergeComponent(base: ViewComponent, up: ViewComponent): ViewComponent {
    val bCtrl = base.control
    val uCtrl = up.control
    val bGrp = base.group
    val uGrp = up.group
    val bRep = base.repeat
    val uRep = up.repeat
    val sortOrder = if (up.sort_order != 0) up.sort_order else base.sort_order
    val id = up.id.ifBlank { base.id }
    return when {
      bCtrl != null && uCtrl != null -> {
        var c = bCtrl
        if (uCtrl.field_ref.isNotEmpty()) c = c.copy(field_ref = uCtrl.field_ref)
        if (uCtrl.type != ControlType.CONTROL_TYPE_UNSPECIFIED) c = c.copy(type = uCtrl.type)
        if (uCtrl.label != null) c = c.copy(label = uCtrl.label)
        if (uCtrl.hint != null) c = c.copy(hint = uCtrl.hint)
        if (uCtrl.appearance.isNotEmpty()) c = c.copy(appearance = uCtrl.appearance)
        if (uCtrl.choices.isNotEmpty()) {
          c =
            c.copy(
              choices =
                c.choices.mergeKeyedUpsert(
                  upsertItems = uCtrl.choices,
                  keyOf = MutationKeys::choiceKey,
                  mergeItem = { _, uc -> uc },
                  orderOf = { it.sort_order },
                )
            )
        }
        if (uCtrl.itemset != null) c = c.copy(itemset = uCtrl.itemset)
        if (uCtrl.range_config != null) c = c.copy(range_config = uCtrl.range_config)
        if (uCtrl.media_type.isNotEmpty()) c = c.copy(media_type = uCtrl.media_type)
        if (uCtrl.intent != null) c = c.copy(intent = uCtrl.intent)
        if (uCtrl.actions.isNotEmpty()) c = c.copy(actions = uCtrl.actions)
        if (uCtrl.geo_config != null) c = c.copy(geo_config = uCtrl.geo_config)
        if (uCtrl.parent_question_key.isNotEmpty()) {
          c = c.copy(parent_question_key = uCtrl.parent_question_key)
        }
        base.copy(control = c, id = id, sort_order = sortOrder)
      }
      bGrp != null && uGrp != null -> {
        var g = bGrp
        if (uGrp.field_ref.isNotEmpty()) g = g.copy(field_ref = uGrp.field_ref)
        if (uGrp.label != null) g = g.copy(label = uGrp.label)
        if (uGrp.appearance.isNotEmpty()) g = g.copy(appearance = uGrp.appearance)
        if (uGrp.intent != null) g = g.copy(intent = uGrp.intent)
        if (uGrp.components.isNotEmpty()) {
          g = g.copy(components = mergeComponents(g.components, uGrp.components))
        }
        base.copy(group = g, id = id, sort_order = sortOrder)
      }
      bRep != null && uRep != null -> {
        var r = bRep
        if (uRep.field_ref.isNotEmpty()) r = r.copy(field_ref = uRep.field_ref)
        if (uRep.label != null) r = r.copy(label = uRep.label)
        if (uRep.appearance.isNotEmpty()) r = r.copy(appearance = uRep.appearance)
        if (uRep.count_expression.isNotEmpty()) r = r.copy(count_expression = uRep.count_expression)
        if (uRep.no_add_remove) r = r.copy(no_add_remove = true)
        if (uRep.components.isNotEmpty()) {
          r = r.copy(components = mergeComponents(r.components, uRep.components))
        }
        if (uRep.actions.isNotEmpty()) r = r.copy(actions = uRep.actions)
        base.copy(repeat = r, id = id, sort_order = sortOrder)
      }
      else -> up.copy(id = id, sort_order = sortOrder)
    }
  }

  private fun applyTranslationCatalogDelete(
    base: TranslationCatalog,
    del: TranslationCatalog,
  ): TranslationCatalog =
    base.copy(
      languages =
        base.languages.applyKeyedDelete(
          deleteItems = del.languages,
          keyOf = { it.language },
          isLeaf = { it.strings.isEmpty() && !it.is_default },
          applyBranchDelete = { b, d ->
            b.copy(
              is_default = if (d.is_default) false else b.is_default,
              strings = b.strings - d.strings.keys,
            )
          },
        )
    )

  private fun mergeTranslationCatalog(
    base: TranslationCatalog,
    up: TranslationCatalog,
  ): TranslationCatalog =
    base.copy(
      languages =
        base.languages.mergeKeyedUpsert(
          upsertItems = up.languages,
          keyOf = { it.language },
          mergeItem = { b, u ->
            b.copy(
              is_default = if (u.is_default) true else b.is_default,
              strings = b.strings + u.strings,
              sort_order = if (u.sort_order != 0) u.sort_order else b.sort_order,
            )
          },
          orderOf = { it.sort_order },
        )
    )

  fun applyAclDelete(acl: SurveyAcl, delete: SurveyAcl): SurveyAcl {
    var a = acl
    if (delete.sharing_policy != SharingPolicy.SHARING_POLICY_UNSPECIFIED) {
      a = a.copy(sharing_policy = SharingPolicy.SHARING_POLICY_UNSPECIFIED)
    }
    if (delete.peer_data_visibility != PeerDataVisibility.PEER_DATA_VISIBILITY_UNSPECIFIED) {
      a = a.copy(peer_data_visibility = PeerDataVisibility.PEER_DATA_VISIBILITY_UNSPECIFIED)
    }
    if (delete.owner_id.isNotEmpty()) a = a.copy(owner_id = "")
    if (delete.owner_profile != null) a = a.copy(owner_profile = null)
    if (delete.collector_consent_required) a = a.copy(collector_consent_required = false)
    if (delete.collector_consent_text.isNotEmpty()) a = a.copy(collector_consent_text = "")
    if (delete.care_sovereignty_applied) a = a.copy(care_sovereignty_applied = false)
    if (delete.quarantined_user_ids.isNotEmpty()) a = a.copy(quarantined_user_ids = emptyList())
    if (delete.default_role != Role.ROLE_UNSPECIFIED) {
      a = a.copy(default_role = Role.ROLE_UNSPECIFIED)
    }
    if (delete.entries.isNotEmpty()) {
      a =
        a.copy(
          entries =
            a.entries.applyKeyedDelete(
              deleteItems = delete.entries,
              keyOf = MutationKeys::aclEntryKey,
              isLeaf = {
                it.role == Role.ROLE_UNSPECIFIED &&
                  it.invitation_status == AclEntry.InvitationStatus.INVITATION_STATUS_UNSPECIFIED &&
                  it.invite_token.isEmpty() &&
                  it.profile == null
              },
              applyBranchDelete = { b, d ->
                var e = b
                if (d.role != Role.ROLE_UNSPECIFIED) e = e.copy(role = Role.ROLE_UNSPECIFIED)
                if (d.invite_token.isNotEmpty()) e = e.copy(invite_token = "")
                if (d.profile != null) e = e.copy(profile = null)
                e
              },
            )
        )
    }
    return a
  }

  fun mergeAcl(base: SurveyAcl, upsert: SurveyAcl): SurveyAcl {
    var a = base
    if (upsert.survey_id.isNotEmpty()) a = a.copy(survey_id = upsert.survey_id)
    if (upsert.sharing_policy != SharingPolicy.SHARING_POLICY_UNSPECIFIED) {
      a = a.copy(sharing_policy = upsert.sharing_policy)
    }
    if (upsert.peer_data_visibility != PeerDataVisibility.PEER_DATA_VISIBILITY_UNSPECIFIED) {
      a = a.copy(peer_data_visibility = upsert.peer_data_visibility)
    }
    if (upsert.owner_id.isNotEmpty()) a = a.copy(owner_id = upsert.owner_id)
    if (upsert.owner_profile != null) a = a.copy(owner_profile = upsert.owner_profile)
    if (upsert.collector_consent_required) a = a.copy(collector_consent_required = true)
    if (upsert.collector_consent_text.isNotEmpty()) {
      a = a.copy(collector_consent_text = upsert.collector_consent_text)
    }
    if (upsert.care_sovereignty_applied) a = a.copy(care_sovereignty_applied = true)
    if (upsert.quarantined_user_ids.isNotEmpty()) {
      a = a.copy(quarantined_user_ids = upsert.quarantined_user_ids)
    }
    if (upsert.default_role in SHARING_ROLES) {
      a = a.copy(default_role = upsert.default_role)
    }
    if (upsert.entries.isNotEmpty()) {
      a =
        a.copy(
          entries =
            a.entries.mergeKeyedUpsert(
              upsertItems = upsert.entries,
              keyOf = MutationKeys::aclEntryKey,
              mergeItem = { b, u ->
                b.copy(
                  user_id = u.user_id.ifBlank { b.user_id },
                  email = u.email.ifBlank { b.email },
                  role = if (u.role != Role.ROLE_UNSPECIFIED) u.role else b.role,
                  invitation_status =
                    if (
                      u.invitation_status != AclEntry.InvitationStatus.INVITATION_STATUS_UNSPECIFIED
                    ) {
                      u.invitation_status
                    } else {
                      b.invitation_status
                    },
                  invite_token = u.invite_token.ifBlank { b.invite_token },
                  profile = u.profile ?: b.profile,
                  id = u.id.ifBlank { b.id },
                  sort_order = if (u.sort_order != 0) u.sort_order else b.sort_order,
                )
              },
              orderOf = { it.sort_order },
            )
        )
    }
    return a
  }

  internal fun applyQuotaDelete(base: QuotaLimits, del: QuotaLimits): QuotaLimits {
    var q = base
    if (del.tier != QuotaTier.QUOTA_TIER_UNSPECIFIED) {
      q = q.copy(tier = QuotaTier.QUOTA_TIER_UNSPECIFIED)
    }
    if (del.max_surveys_per_owner != 0) q = q.copy(max_surveys_per_owner = 0)
    if (del.max_forms_per_survey != 0) q = q.copy(max_forms_per_survey = 0)
    if (del.max_questions_per_form != 0) q = q.copy(max_questions_per_form = 0)
    if (del.max_photo_questions_per_form != 0) q = q.copy(max_photo_questions_per_form = 0)
    if (del.max_predefined_entities != 0) q = q.copy(max_predefined_entities = 0)
    if (del.max_acl_users != 0) q = q.copy(max_acl_users = 0)
    if (del.max_surveys_per_user != 0) q = q.copy(max_surveys_per_user = 0)
    if (del.max_adhoc_entities_per_user != 0) q = q.copy(max_adhoc_entities_per_user = 0)
    if (del.max_submissions_per_entity_per_user != 0) {
      q = q.copy(max_submissions_per_entity_per_user = 0)
    }
    if (del.max_generated_entities != 0) q = q.copy(max_generated_entities = 0)
    if (del.max_area_vertices != 0) q = q.copy(max_area_vertices = 0)
    if (del.monthly_budget_usd != 0.0) q = q.copy(monthly_budget_usd = 0.0)
    return q
  }

  internal fun mergeQuotaLimits(base: QuotaLimits, up: QuotaLimits): QuotaLimits {
    var q = base
    if (up.tier != QuotaTier.QUOTA_TIER_UNSPECIFIED) q = q.copy(tier = up.tier)
    if (up.max_surveys_per_owner != 0) q = q.copy(max_surveys_per_owner = up.max_surveys_per_owner)
    if (up.max_forms_per_survey != 0) q = q.copy(max_forms_per_survey = up.max_forms_per_survey)
    if (up.max_questions_per_form != 0) {
      q = q.copy(max_questions_per_form = up.max_questions_per_form)
    }
    if (up.max_photo_questions_per_form != 0) {
      q = q.copy(max_photo_questions_per_form = up.max_photo_questions_per_form)
    }
    if (up.max_predefined_entities != 0) {
      q = q.copy(max_predefined_entities = up.max_predefined_entities)
    }
    if (up.max_acl_users != 0) q = q.copy(max_acl_users = up.max_acl_users)
    if (up.max_surveys_per_user != 0) q = q.copy(max_surveys_per_user = up.max_surveys_per_user)
    if (up.max_adhoc_entities_per_user != 0) {
      q = q.copy(max_adhoc_entities_per_user = up.max_adhoc_entities_per_user)
    }
    if (up.max_submissions_per_entity_per_user != 0) {
      q = q.copy(max_submissions_per_entity_per_user = up.max_submissions_per_entity_per_user)
    }
    if (up.max_generated_entities != 0) {
      q = q.copy(max_generated_entities = up.max_generated_entities)
    }
    if (up.max_area_vertices != 0) q = q.copy(max_area_vertices = up.max_area_vertices)
    if (up.monthly_budget_usd != 0.0) q = q.copy(monthly_budget_usd = up.monthly_budget_usd)
    return q
  }
}

/** Applies sparse `Organization` `delete` and `upsert` masks. */
object OrganizationPatches {
  fun isRootDelete(delete: Organization?, upsert: Organization? = null): Boolean {
    if (delete == null) return false
    if (upsert != null && upsert != Organization(organization_id = upsert.organization_id)) {
      return false
    }
    return delete == Organization(organization_id = delete.organization_id) ||
      delete == Organization(organization_id = delete.organization_id, is_deleted = true)
  }

  fun apply(
    org: Organization,
    delete: Organization? = null,
    upsert: Organization? = null,
  ): Organization {
    val afterDelete = if (delete != null) applyDelete(org, delete) else org
    return if (upsert != null) merge(afterDelete, upsert) else afterDelete
  }

  fun applyDelete(org: Organization, delete: Organization): Organization {
    var o = org
    if (delete.name.isNotEmpty()) o = o.copy(name = "")
    if (delete.description.isNotEmpty()) o = o.copy(description = "")
    if (delete.website_url.isNotEmpty()) o = o.copy(website_url = "")
    if (delete.logo_url.isNotEmpty()) o = o.copy(logo_url = "")
    if (delete.listed) o = o.copy(listed = false)
    if (delete.created_at != null) o = o.copy(created_at = null)
    if (delete.organization_type != OrganizationType.ORGANIZATION_TYPE_UNSPECIFIED) {
      o = o.copy(organization_type = OrganizationType.ORGANIZATION_TYPE_UNSPECIFIED)
    }
    if (delete.country_code.isNotEmpty()) o = o.copy(country_code = "")
    if (delete.exclude_from_platform_aggregates) {
      o = o.copy(exclude_from_platform_aggregates = false)
    }
    if (delete.is_deleted) o = o.copy(is_deleted = false)
    if (delete.quota_limits != null) {
      val q = delete.quota_limits
      o =
        if (q == QuotaLimits()) o.copy(quota_limits = null)
        else o.copy(quota_limits = o.quota_limits?.let { SurveyPatches.applyQuotaDelete(it, q) })
    }
    if (delete.imagery_sources.isNotEmpty()) {
      o =
        o.copy(
          imagery_sources =
            o.imagery_sources.applyKeyedDelete(
              deleteItems = delete.imagery_sources,
              keyOf = { it.imagery_source_id },
              isLeaf = { it == ImagerySource(imagery_source_id = it.imagery_source_id) },
              applyBranchDelete = { b, d ->
                var s = b
                if (d.name.isNotEmpty()) s = s.copy(name = "")
                if (d.xyz_tiles != null) s = s.copy(xyz_tiles = null)
                s
              },
            )
        )
    }
    if (delete.members.isNotEmpty()) {
      o =
        o.copy(
          members =
            o.members.applyKeyedDelete(
              deleteItems = delete.members,
              keyOf = MutationKeys::memberKey,
              isLeaf = {
                it.role == OrganizationRole.ORGANIZATION_ROLE_UNSPECIFIED &&
                  it.status == MembershipStatus.MEMBERSHIP_STATUS_UNSPECIFIED &&
                  it.updated_at == null &&
                  it.invite_token.isEmpty() &&
                  it.profile == null
              },
              applyBranchDelete = { b, d ->
                var m = b
                if (d.role != OrganizationRole.ORGANIZATION_ROLE_UNSPECIFIED) {
                  m = m.copy(role = OrganizationRole.ORGANIZATION_ROLE_UNSPECIFIED)
                }
                if (d.status != MembershipStatus.MEMBERSHIP_STATUS_UNSPECIFIED) {
                  m = m.copy(status = MembershipStatus.MEMBERSHIP_STATUS_UNSPECIFIED)
                }
                if (d.invite_token.isNotEmpty()) m = m.copy(invite_token = "")
                if (d.profile != null) m = m.copy(profile = null)
                m
              },
            )
        )
    }
    return o
  }

  fun merge(base: Organization, upsert: Organization): Organization {
    var o = base
    if (upsert.organization_id.isNotEmpty()) o = o.copy(organization_id = upsert.organization_id)
    if (upsert.name.isNotEmpty()) o = o.copy(name = upsert.name)
    if (upsert.description.isNotEmpty()) o = o.copy(description = upsert.description)
    if (upsert.website_url.isNotEmpty()) o = o.copy(website_url = upsert.website_url)
    if (upsert.logo_url.isNotEmpty()) o = o.copy(logo_url = upsert.logo_url)
    if (upsert.listed) o = o.copy(listed = true)
    if (upsert.created_at != null) o = o.copy(created_at = upsert.created_at)
    if (upsert.organization_type != OrganizationType.ORGANIZATION_TYPE_UNSPECIFIED) {
      o = o.copy(organization_type = upsert.organization_type)
    }
    if (upsert.country_code.isNotEmpty()) o = o.copy(country_code = upsert.country_code)
    if (upsert.exclude_from_platform_aggregates) {
      o = o.copy(exclude_from_platform_aggregates = true)
    }
    if (upsert.is_deleted) o = o.copy(is_deleted = true)
    if (upsert.quota_limits != null) {
      o =
        o.copy(
          quota_limits =
            o.quota_limits?.let { SurveyPatches.mergeQuotaLimits(it, upsert.quota_limits) }
              ?: upsert.quota_limits
        )
    }
    if (upsert.imagery_sources.isNotEmpty()) {
      o =
        o.copy(
          imagery_sources =
            o.imagery_sources.mergeKeyedUpsert(
              upsertItems = upsert.imagery_sources,
              keyOf = { it.imagery_source_id },
              mergeItem = { _, u -> u },
              orderOf = { it.sort_order },
            )
        )
    }
    if (upsert.members.isNotEmpty()) {
      o =
        o.copy(
          members =
            o.members.mergeKeyedUpsert(
              upsertItems = upsert.members,
              keyOf = MutationKeys::memberKey,
              mergeItem = { b, u ->
                b.copy(
                  user_id = u.user_id.ifBlank { b.user_id },
                  email = u.email.ifBlank { b.email },
                  role =
                    if (u.role != OrganizationRole.ORGANIZATION_ROLE_UNSPECIFIED) u.role
                    else b.role,
                  status =
                    if (u.status != MembershipStatus.MEMBERSHIP_STATUS_UNSPECIFIED) u.status
                    else b.status,
                  updated_at = u.updated_at ?: b.updated_at,
                  invite_token = u.invite_token.ifBlank { b.invite_token },
                  profile = u.profile ?: b.profile,
                  id = u.id.ifBlank { b.id },
                  sort_order = if (u.sort_order != 0) u.sort_order else b.sort_order,
                )
              },
              orderOf = { it.sort_order },
            )
        )
    }
    return o
  }
}

/** Applies sparse `OrganizationLibraryRecord` `delete` and `upsert` masks. */
object LibraryPatches {
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

/** Applies sparse `EntityRecord` and `SubmissionRecord` `delete` and `upsert` masks. */
object DataPatches {
  /**
   * True when [delete] is a root-level soft-delete mask (either `delete.is_deleted == true`, or
   * [delete] has no child fields set beyond `entity_id` / `dataset_id` / `survey_id`).
   */
  fun isEntityRootDelete(delete: EntityRecord?, upsert: EntityRecord? = null): Boolean {
    if (delete == null) return upsert?.is_deleted == true
    val stripped = delete.copy(entity_id = "", dataset_id = "", survey_id = "", is_deleted = false)
    if (stripped == EntityRecord()) {
      // A root leaf delete (or delete.is_deleted = true) soft-deletes unless upsert is restoring
      // fields while clearing is_deleted.
      val upsertHasContent =
        upsert != null &&
          upsert.copy(entity_id = "", dataset_id = "", survey_id = "") != EntityRecord()
      return !upsertHasContent || upsert.is_deleted
    }
    return false
  }

  /**
   * True when [delete] is a root-level soft-delete mask (either `delete.is_deleted == true`, or
   * [delete] has no child fields set beyond `submission_id` / `form_id` / `survey_id`).
   */
  fun isSubmissionRootDelete(
    delete: SubmissionRecord?,
    upsert: SubmissionRecord? = null,
  ): Boolean {
    if (delete == null) return upsert?.is_deleted == true
    val stripped = delete.copy(submission_id = "", form_id = "", survey_id = "", is_deleted = false)
    if (stripped == SubmissionRecord()) {
      val upsertHasContent =
        upsert != null &&
          upsert.copy(submission_id = "", form_id = "", survey_id = "") != SubmissionRecord()
      return !upsertHasContent || upsert.is_deleted
    }
    return false
  }

  /** Applies [delete] then merges [upsert] onto [record]. */
  fun applyEntity(
    record: EntityRecord,
    delete: EntityRecord? = null,
    upsert: EntityRecord? = null,
  ): EntityRecord {
    val afterDelete = if (delete != null) applyEntityDelete(record, delete) else record
    return if (upsert != null) mergeEntity(afterDelete, upsert) else afterDelete
  }

  fun applyEntityDelete(record: EntityRecord, delete: EntityRecord): EntityRecord {
    if (isEntityRootDelete(delete)) {
      return EntityRecord(
        entity_id = record.entity_id.ifBlank { delete.entity_id },
        dataset_id = record.dataset_id.ifBlank { delete.dataset_id },
        survey_id = record.survey_id.ifBlank { delete.survey_id },
        audit_info = record.audit_info,
        revision = record.revision,
        commit_timestamp = record.commit_timestamp,
        is_deleted = true,
      )
    }
    var r = record
    if (delete.label.isNotEmpty()) r = r.copy(label = "")
    if (delete.point != null || delete.trace != null || delete.shape != null) {
      r = r.copy(point = null, trace = null, shape = null)
    }
    if (delete.properties.isNotEmpty()) {
      r = r.copy(properties = r.properties - delete.properties.keys)
    }
    if (delete.is_field_created) r = r.copy(is_field_created = false)
    if (delete.is_deleted) r = r.copy(is_deleted = false)
    if (delete.s2_cell_token.isNotEmpty()) r = r.copy(s2_cell_token = "")
    if (delete.active_lock != null) r = r.copy(active_lock = null)
    if (delete.dataset_name.isNotEmpty()) r = r.copy(dataset_name = "")
    if (delete.layer_id.isNotEmpty()) r = r.copy(layer_id = "")
    if (delete.geo_id.isNotEmpty()) r = r.copy(geo_id = "")
    if (delete.geometry_type_label.isNotEmpty()) r = r.copy(geometry_type_label = "")
    if (delete.area_hectares != 0.0) r = r.copy(area_hectares = 0.0)
    if (delete.perimeter_meters != 0) r = r.copy(perimeter_meters = 0)
    if (delete.coordinates_label.isNotEmpty()) r = r.copy(coordinates_label = "")
    if (delete.normalized_x != 0f) r = r.copy(normalized_x = 0f)
    if (delete.normalized_y != 0f) r = r.copy(normalized_y = 0f)
    if (delete.color_hex != 0L) r = r.copy(color_hex = 0L)
    if (delete.singular_type_label.isNotEmpty()) r = r.copy(singular_type_label = "")
    if (delete.sync_status_key.isNotEmpty()) r = r.copy(sync_status_key = "")
    return r
  }

  fun mergeEntity(base: EntityRecord, upsert: EntityRecord): EntityRecord {
    var r = base
    if (upsert.entity_id.isNotEmpty()) r = r.copy(entity_id = upsert.entity_id)
    if (upsert.dataset_id.isNotEmpty()) r = r.copy(dataset_id = upsert.dataset_id)
    if (upsert.survey_id.isNotEmpty()) r = r.copy(survey_id = upsert.survey_id)
    if (upsert.label.isNotEmpty()) r = r.copy(label = upsert.label)
    when {
      upsert.point != null -> r = r.copy(point = upsert.point, trace = null, shape = null)
      upsert.trace != null -> r = r.copy(point = null, trace = upsert.trace, shape = null)
      upsert.shape != null -> r = r.copy(point = null, trace = null, shape = upsert.shape)
    }
    if (upsert.properties.isNotEmpty()) {
      r = r.copy(properties = r.properties + upsert.properties)
    }
    if (upsert.is_field_created) r = r.copy(is_field_created = true)
    if (upsert.audit_info != null) r = r.copy(audit_info = upsert.audit_info)
    if (upsert.is_deleted) r = r.copy(is_deleted = true)
    if (upsert.s2_cell_token.isNotEmpty()) r = r.copy(s2_cell_token = upsert.s2_cell_token)
    if (upsert.active_lock != null) r = r.copy(active_lock = upsert.active_lock)
    if (upsert.dataset_name.isNotEmpty()) r = r.copy(dataset_name = upsert.dataset_name)
    if (upsert.layer_id.isNotEmpty()) r = r.copy(layer_id = upsert.layer_id)
    if (upsert.geo_id.isNotEmpty()) r = r.copy(geo_id = upsert.geo_id)
    if (upsert.geometry_type_label.isNotEmpty()) {
      r = r.copy(geometry_type_label = upsert.geometry_type_label)
    }
    if (upsert.area_hectares != 0.0) r = r.copy(area_hectares = upsert.area_hectares)
    if (upsert.perimeter_meters != 0) r = r.copy(perimeter_meters = upsert.perimeter_meters)
    if (upsert.coordinates_label.isNotEmpty()) {
      r = r.copy(coordinates_label = upsert.coordinates_label)
    }
    if (upsert.normalized_x != 0f) r = r.copy(normalized_x = upsert.normalized_x)
    if (upsert.normalized_y != 0f) r = r.copy(normalized_y = upsert.normalized_y)
    if (upsert.color_hex != 0L) r = r.copy(color_hex = upsert.color_hex)
    if (upsert.singular_type_label.isNotEmpty()) {
      r = r.copy(singular_type_label = upsert.singular_type_label)
    }
    if (upsert.sync_status_key.isNotEmpty()) r = r.copy(sync_status_key = upsert.sync_status_key)
    return r
  }

  /** Applies [delete] then merges [upsert] onto [record]. */
  fun applySubmission(
    record: SubmissionRecord,
    delete: SubmissionRecord? = null,
    upsert: SubmissionRecord? = null,
  ): SubmissionRecord {
    val afterDelete = if (delete != null) applySubmissionDelete(record, delete) else record
    return if (upsert != null) mergeSubmission(afterDelete, upsert) else afterDelete
  }

  fun applySubmissionDelete(record: SubmissionRecord, delete: SubmissionRecord): SubmissionRecord {
    if (isSubmissionRootDelete(delete)) {
      return SubmissionRecord(
        submission_id = record.submission_id.ifBlank { delete.submission_id },
        form_id = record.form_id.ifBlank { delete.form_id },
        survey_id = record.survey_id.ifBlank { delete.survey_id },
        entity_id = record.entity_id,
        audit_info = record.audit_info,
        revision = record.revision,
        commit_timestamp = record.commit_timestamp,
        is_deleted = true,
      )
    }
    var r = record
    if (delete.entity_id.isNotEmpty()) r = r.copy(entity_id = "")
    if (delete.form_version.isNotEmpty()) r = r.copy(form_version = "")
    if (delete.is_deleted) r = r.copy(is_deleted = false)
    if (delete.is_quarantined) r = r.copy(is_quarantined = false)
    if (delete.review_status != ReviewStatus.REVIEW_STATUS_UNSPECIFIED) {
      r = r.copy(review_status = ReviewStatus.REVIEW_STATUS_UNSPECIFIED)
    }
    if (delete.entity_label.isNotEmpty()) r = r.copy(entity_label = "")
    if (delete.form_title.isNotEmpty()) r = r.copy(form_title = "")
    if (delete.collector_name.isNotEmpty()) r = r.copy(collector_name = "")
    if (delete.collector_email.isNotEmpty()) r = r.copy(collector_email = "")
    if (delete.timestamp_label.isNotEmpty()) r = r.copy(timestamp_label = "")
    if (delete.target_type_label.isNotEmpty()) r = r.copy(target_type_label = "")
    if (delete.sync_status_key.isNotEmpty()) r = r.copy(sync_status_key = "")
    if (delete.coordinates_label.isNotEmpty()) r = r.copy(coordinates_label = "")
    if (delete.normalized_x != null) r = r.copy(normalized_x = null)
    if (delete.normalized_y != null) r = r.copy(normalized_y = null)
    if (delete.field_entries.isNotEmpty()) r = r.copy(field_entries = emptyList())
    if (delete.geometry_polygons.isNotEmpty()) r = r.copy(geometry_polygons = emptyList())
    if (delete.record != null) {
      r =
        if (delete.record == RecordInstance()) r.copy(record = null)
        else r.copy(record = r.record?.let { applyRecordInstanceDelete(it, delete.record) })
    }
    return r
  }

  private fun applyRecordInstanceDelete(
    base: RecordInstance,
    del: RecordInstance,
  ): RecordInstance {
    var ri = base
    if (del.metadata != null) ri = ri.copy(metadata = null)
    if (del.audit_log != null) ri = ri.copy(audit_log = null)
    if (del.data_ != null) {
      ri =
        if (del.data_.fields.isEmpty()) ri.copy(data_ = null)
        else ri.copy(data_ = ri.data_?.let { applyRecordNodeDelete(it, del.data_) })
    }
    return ri
  }

  private fun applyRecordNodeDelete(base: RecordNode, del: RecordNode): RecordNode {
    if (del.fields.isEmpty()) return base
    val updated = LinkedHashMap(base.fields)
    for ((k, dVal) in del.fields) {
      val bVal = updated[k] ?: continue
      val bNode = bVal.node_value
      val dNode = dVal.node_value
      val bRep = bVal.repeat_value
      val dRep = dVal.repeat_value
      when {
        bNode != null && dNode != null && dNode.fields.isNotEmpty() ->
          updated[k] = bVal.copy(node_value = applyRecordNodeDelete(bNode, dNode))
        bRep != null && dRep != null && dRep.nodes.isNotEmpty() -> {
          val nodes =
            bRep.nodes.applyKeyedDelete(
              deleteItems = dRep.nodes,
              keyOf = { it.id },
              isLeaf = { it.fields.isEmpty() },
              applyBranchDelete = ::applyRecordNodeDelete,
            )
          updated[k] = bVal.copy(repeat_value = RecordNodeList(nodes = nodes))
        }
        else -> updated.remove(k)
      }
    }
    return base.copy(fields = updated)
  }

  fun mergeSubmission(base: SubmissionRecord, upsert: SubmissionRecord): SubmissionRecord {
    var r = base
    if (upsert.submission_id.isNotEmpty()) r = r.copy(submission_id = upsert.submission_id)
    if (upsert.form_id.isNotEmpty()) r = r.copy(form_id = upsert.form_id)
    if (upsert.survey_id.isNotEmpty()) r = r.copy(survey_id = upsert.survey_id)
    if (upsert.entity_id.isNotEmpty()) r = r.copy(entity_id = upsert.entity_id)
    if (upsert.form_version.isNotEmpty()) r = r.copy(form_version = upsert.form_version)
    if (upsert.audit_info != null) r = r.copy(audit_info = upsert.audit_info)
    if (upsert.is_deleted) r = r.copy(is_deleted = true)
    if (upsert.is_quarantined) r = r.copy(is_quarantined = true)
    if (upsert.review_status != ReviewStatus.REVIEW_STATUS_UNSPECIFIED) {
      r = r.copy(review_status = upsert.review_status)
    }
    if (upsert.entity_label.isNotEmpty()) r = r.copy(entity_label = upsert.entity_label)
    if (upsert.form_title.isNotEmpty()) r = r.copy(form_title = upsert.form_title)
    if (upsert.collector_name.isNotEmpty()) r = r.copy(collector_name = upsert.collector_name)
    if (upsert.collector_email.isNotEmpty()) r = r.copy(collector_email = upsert.collector_email)
    if (upsert.timestamp_label.isNotEmpty()) r = r.copy(timestamp_label = upsert.timestamp_label)
    if (upsert.target_type_label.isNotEmpty()) {
      r = r.copy(target_type_label = upsert.target_type_label)
    }
    if (upsert.sync_status_key.isNotEmpty()) r = r.copy(sync_status_key = upsert.sync_status_key)
    if (upsert.coordinates_label.isNotEmpty()) {
      r = r.copy(coordinates_label = upsert.coordinates_label)
    }
    if (upsert.normalized_x != null) r = r.copy(normalized_x = upsert.normalized_x)
    if (upsert.normalized_y != null) r = r.copy(normalized_y = upsert.normalized_y)
    if (upsert.field_entries.isNotEmpty()) r = r.copy(field_entries = upsert.field_entries)
    if (upsert.geometry_polygons.isNotEmpty()) {
      r = r.copy(geometry_polygons = upsert.geometry_polygons)
    }
    if (upsert.record != null) {
      r = r.copy(record = r.record?.let { mergeRecordInstance(it, upsert.record) } ?: upsert.record)
    }
    return r
  }

  private fun mergeRecordInstance(base: RecordInstance, up: RecordInstance): RecordInstance {
    var ri = base
    if (up.form_id.isNotEmpty()) ri = ri.copy(form_id = up.form_id)
    if (up.form_version.isNotEmpty()) ri = ri.copy(form_version = up.form_version)
    if (up.metadata != null) ri = ri.copy(metadata = up.metadata)
    if (up.audit_log != null) ri = ri.copy(audit_log = up.audit_log)
    if (up.data_ != null) {
      ri = ri.copy(data_ = ri.data_?.let { mergeRecordNode(it, up.data_) } ?: up.data_)
    }
    return ri
  }

  private fun mergeRecordNode(base: RecordNode, up: RecordNode): RecordNode {
    val updated = LinkedHashMap(base.fields)
    for ((k, uVal) in up.fields) {
      val bVal = updated[k]
      val bNode = bVal?.node_value
      val uNode = uVal.node_value
      val bRep = bVal?.repeat_value
      val uRep = uVal.repeat_value
      updated[k] =
        when {
          bNode != null && uNode != null -> uVal.copy(node_value = mergeRecordNode(bNode, uNode))
          bRep != null && uRep != null -> {
            val canKeyById =
              bRep.nodes.all { it.id.isNotEmpty() } && uRep.nodes.all { it.id.isNotEmpty() }
            val mergedNodes =
              if (canKeyById) {
                bRep.nodes.mergeKeyedUpsert(
                  upsertItems = uRep.nodes,
                  keyOf = { it.id },
                  mergeItem = ::mergeRecordNode,
                  orderOf = { it.sort_order },
                )
              } else {
                uRep.nodes
              }
            uVal.copy(repeat_value = RecordNodeList(nodes = mergedNodes))
          }
          else -> uVal
        }
    }
    return base.copy(
      fields = updated,
      id = up.id.ifBlank { base.id },
      sort_order = if (up.sort_order != 0) up.sort_order else base.sort_order,
    )
  }
}
