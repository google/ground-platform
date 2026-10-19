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
package org.openforis.collectone.v2.app.data.sync

import groundplatform.v2.data.EntityFieldPatch
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.SubmissionFieldPatch
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.LibraryFieldPatch
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.survey.AccessRulesPatch
import groundplatform.v2.survey.AclRolePatch
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationFieldPatch
import groundplatform.v2.survey.StringListValue
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyFieldPatch

/**
 * Turns a record's old and new proto values into the fine-grained field patches that transform one
 * into the other, using the key paths of the `Mutate*` RPCs. Server bookkeeping (revisions,
 * versions, commit timestamps, sync status) is ignored. Keyed collection items are compared one by
 * one, so editing one form, member, or concept produces one patch.
 *
 * Applying the returned patches to `old` with the shared patch functions reproduces `new` for every
 * field the RPCs can patch; `requiresUpsert` reports changes outside those fields.
 */
object MutationDiffs {
  /** Field patches for an entity, and whether other fields changed too. */
  data class EntityDiff(val patches: List<EntityFieldPatch>, val requiresUpsert: Boolean)

  fun entity(old: EntityRecord, new: EntityRecord): EntityDiff {
    val patches = mutableListOf<EntityFieldPatch>()
    if (old.label != new.label) patches += EntityFieldPatch(key = "label", label_value = new.label)
    if (old.point != new.point || old.trace != new.trace || old.shape != new.shape) {
      patches +=
        EntityFieldPatch(
          key = "geometry",
          point_value = new.point,
          trace_value = new.trace?.takeIf { new.point == null },
          shape_value = new.shape?.takeIf { new.point == null && new.trace == null },
        )
    }
    for (key in (old.properties.keys + new.properties.keys).distinct()) {
      val value = new.properties[key]
      if (old.properties[key] != value) {
        patches += EntityFieldPatch(key = "properties/$key", property_value = value)
      }
    }
    val patchable = { r: EntityRecord ->
      normalize(r)
        .copy(label = "", point = null, trace = null, shape = null, properties = emptyMap())
    }
    return EntityDiff(patches, patchable(old) != patchable(new))
  }

  /** Field patches for a submission, and whether other fields changed too. */
  data class SubmissionDiff(val patches: List<SubmissionFieldPatch>, val requiresUpsert: Boolean)

  fun submission(old: SubmissionRecord, new: SubmissionRecord): SubmissionDiff {
    val patches = mutableListOf<SubmissionFieldPatch>()
    if (old.entity_id != new.entity_id) {
      patches += SubmissionFieldPatch(key = "entity_id", string_value = new.entity_id)
    }
    if (old.form_version != new.form_version) {
      patches += SubmissionFieldPatch(key = "form_version", string_value = new.form_version)
    }
    if (old.is_quarantined != new.is_quarantined) {
      patches += SubmissionFieldPatch(key = "is_quarantined", bool_value = new.is_quarantined)
    }
    if (old.review_status != new.review_status) {
      patches +=
        SubmissionFieldPatch(key = "review_status", review_status_value = new.review_status)
    }
    val patchable = { r: SubmissionRecord ->
      normalize(r)
        .copy(
          entity_id = "",
          form_version = "",
          is_quarantined = false,
          review_status = SubmissionRecord().review_status,
        )
    }
    return SubmissionDiff(patches, patchable(old) != patchable(new))
  }

  /** Field patches for an organization, and whether other fields changed too. */
  data class OrganizationDiff(
    val patches: List<OrganizationFieldPatch>,
    val requiresUpsert: Boolean,
  )

  fun organization(old: Organization, new: Organization): OrganizationDiff {
    val patches = mutableListOf<OrganizationFieldPatch>()
    fun string(key: String, a: String, b: String) {
      if (a != b) patches += OrganizationFieldPatch(key = key, string_value = b)
    }
    fun bool(key: String, a: Boolean, b: Boolean) {
      if (a != b) patches += OrganizationFieldPatch(key = key, bool_value = b)
    }
    string("name", old.name, new.name)
    string("description", old.description, new.description)
    string("website_url", old.website_url, new.website_url)
    string("logo_url", old.logo_url, new.logo_url)
    string("country_code", old.country_code, new.country_code)
    bool("listed", old.listed, new.listed)
    bool(
      "exclude_from_platform_aggregates",
      old.exclude_from_platform_aggregates,
      new.exclude_from_platform_aggregates,
    )
    if (old.organization_type != new.organization_type) {
      patches +=
        OrganizationFieldPatch(
          key = "organization_type",
          organization_type_value = new.organization_type,
        )
    }
    keyed(old.imagery_sources, new.imagery_sources, { it.imagery_source_id }) { id, value ->
      patches += OrganizationFieldPatch(key = "imagery_sources/$id", imagery_source_value = value)
    }
    keyed(old.members, new.members, { it.email.lowercase() }) { email, value ->
      patches += OrganizationFieldPatch(key = "members/$email", member_value = value)
    }
    val patchable = { o: Organization ->
      o.copy(
        name = "",
        description = "",
        website_url = "",
        logo_url = "",
        country_code = "",
        listed = false,
        exclude_from_platform_aggregates = false,
        organization_type = Organization().organization_type,
        imagery_sources = emptyList(),
        members = emptyList(),
        version = 0L,
        is_deleted = false,
        commit_timestamp = null,
      )
    }
    return OrganizationDiff(patches, patchable(old) != patchable(new))
  }

  /** Field patches for an organization library. */
  fun library(
    old: OrganizationLibraryRecord,
    new: OrganizationLibraryRecord,
  ): List<LibraryFieldPatch> {
    val patches = mutableListOf<LibraryFieldPatch>()
    if (old.settings != new.settings) {
      patches += LibraryFieldPatch(key = "settings", settings_value = new.settings)
    }
    val a = old.bundle
    val b = new.bundle
    keyed(a?.concepts.orEmpty(), b?.concepts.orEmpty(), { it.id }) { id, v ->
      patches += LibraryFieldPatch(key = "concepts/$id", concept_value = v)
    }
    keyed(a?.form_templates.orEmpty(), b?.form_templates.orEmpty(), { it.id }) { id, v ->
      patches += LibraryFieldPatch(key = "form_templates/$id", form_template_value = v)
    }
    keyed(a?.purpose_packs.orEmpty(), b?.purpose_packs.orEmpty(), { it.id }) { id, v ->
      patches += LibraryFieldPatch(key = "purpose_packs/$id", purpose_pack_value = v)
    }
    keyed(a?.export_profiles.orEmpty(), b?.export_profiles.orEmpty(), { it.id }) { id, v ->
      patches += LibraryFieldPatch(key = "export_profiles/$id", export_profile_value = v)
    }
    return patches
  }

  /** Field patches for a survey definition (saved revision or working copy). */
  fun survey(old: SurveyDef, new: SurveyDef): List<SurveyFieldPatch> {
    val patches = mutableListOf<SurveyFieldPatch>()
    fun string(key: String, a: String, b: String) {
      if (a != b) patches += SurveyFieldPatch(key = key, string_value = b)
    }
    fun strings(key: String, a: List<String>, b: List<String>) {
      if (a != b) {
        patches += SurveyFieldPatch(key = key, string_list_value = StringListValue(values = b))
      }
    }
    string("title", old.title, new.title)
    string("description", old.description, new.description)
    string("organization_id", old.organization_id, new.organization_id)
    string("owner_id", old.owner_id, new.owner_id)
    string("default_language", old.default_language, new.default_language)
    string("closed_at_iso", old.closed_at_iso, new.closed_at_iso)
    if (old.state != new.state) patches += SurveyFieldPatch(key = "state", state_value = new.state)
    if (old.closed_at != new.closed_at) {
      patches += SurveyFieldPatch(key = "closed_at", timestamp_value = new.closed_at)
    }
    if (old.outcome != new.outcome) {
      patches += SurveyFieldPatch(key = "outcome", outcome_value = new.outcome)
    }
    strings("supported_languages", old.supported_languages, new.supported_languages)
    strings("purpose_ids", old.purpose_ids, new.purpose_ids)
    strings("program_ids", old.program_ids, new.program_ids)
    keyed(old.forms, new.forms, { it.form_id }) { id, v ->
      patches += SurveyFieldPatch(key = "forms/$id", form_value = v)
    }
    keyed(old.entity_datasets, new.entity_datasets, { it.id }) { id, v ->
      patches += SurveyFieldPatch(key = "entity_datasets/$id", entity_dataset_value = v)
    }
    keyed(old.form_launch_configs, new.form_launch_configs, { it.form_id }) { id, v ->
      patches += SurveyFieldPatch(key = "form_launch_configs/$id", form_launch_config_value = v)
    }
    keyed(old.form_concept_links, new.form_concept_links, { it.form_id }) { id, v ->
      patches += SurveyFieldPatch(key = "form_concept_links/$id", form_concept_links_value = v)
    }
    val oldMap = old.map_config
    val newMap = new.map_config
    if (oldMap != newMap) {
      val withoutLayersAndArea = { m: groundplatform.v2.survey.MapConfig? ->
        m?.copy(layers = emptyList(), survey_area = null)
      }
      if (withoutLayersAndArea(oldMap) != withoutLayersAndArea(newMap)) {
        patches += SurveyFieldPatch(key = "map_config", map_config_value = newMap)
      } else {
        if (oldMap?.survey_area != newMap?.survey_area) {
          patches +=
            SurveyFieldPatch(
              key = "map_config/survey_area",
              survey_area_value = newMap?.survey_area,
            )
        }
        keyed(oldMap?.layers.orEmpty(), newMap?.layers.orEmpty(), { it.id }) { id, v ->
          patches += SurveyFieldPatch(key = "map_config/layers/$id", layer_value = v)
        }
      }
    }
    return patches
  }

  /** Role patches (one per changed collaborator) and an access rules patch, if any changed. */
  data class AclDiff(val roles: List<AclRolePatch>, val accessRules: AccessRulesPatch?)

  fun acl(old: SurveyAcl, new: SurveyAcl): AclDiff {
    val roles = mutableListOf<AclRolePatch>()
    keyed(old.entries, new.entries, { it.email.ifBlank { it.user_id }.lowercase() }) { key, v ->
      roles += AclRolePatch(user_id = key, entry_value = v)
    }
    val access =
      AccessRulesPatch(
        sharing_policy =
          new.sharing_policy.takeIf { it != old.sharing_policy }
            ?: AccessRulesPatch().sharing_policy,
        peer_data_visibility =
          new.peer_data_visibility.takeIf { it != old.peer_data_visibility }
            ?: AccessRulesPatch().peer_data_visibility,
        collector_consent_required =
          new.collector_consent_required.takeIf { it != old.collector_consent_required },
        collector_consent_text =
          new.collector_consent_text.takeIf { it != old.collector_consent_text },
        care_sovereignty_applied =
          new.care_sovereignty_applied.takeIf { it != old.care_sovereignty_applied },
        owner_email = new.owner_email.takeIf { it != old.owner_email },
      )
    return AclDiff(roles, access.takeIf { it != AccessRulesPatch() })
  }

  private fun normalize(r: EntityRecord) =
    r.copy(revision = 0L, commit_timestamp = null, sync_status_key = "", is_deleted = false)

  private fun normalize(r: SubmissionRecord) =
    r.copy(revision = 0L, commit_timestamp = null, sync_status_key = "", is_deleted = false)

  /** Calls [emit] for every key whose item was added, changed (new value), or removed (`null`). */
  private inline fun <T> keyed(
    old: List<T>,
    new: List<T>,
    keyOf: (T) -> String,
    emit: (String, T?) -> Unit,
  ) {
    val before = old.associateBy(keyOf)
    val after = new.associateBy(keyOf)
    for ((key, value) in after) if (before[key] != value) emit(key, value)
    for (key in before.keys) if (key !in after) emit(key, null)
  }
}
