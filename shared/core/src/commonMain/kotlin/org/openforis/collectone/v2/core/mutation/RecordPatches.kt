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

import groundplatform.v2.data.EntityPatch
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.SubmissionPatch
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.LibraryBundle
import groundplatform.v2.library.LibraryFieldPatch
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.survey.AccessRulesPatch
import groundplatform.v2.survey.AclEntry
import groundplatform.v2.survey.AclRolePatch
import groundplatform.v2.survey.MapConfig
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationFieldPatch
import groundplatform.v2.survey.PeerDataVisibility
import groundplatform.v2.survey.Role
import groundplatform.v2.survey.SharingPolicy
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyFieldPatch

/*
 * Field-patch semantics shared by the user data service and the clients. The server applies
 * patches with these functions when it commits a `Mutate*` request, and clients apply the same
 * functions to preview pending outbox mutations on top of the last server copy of a record, so the
 * local preview matches what the server will store.
 *
 * Every patch sets or clears one key path, or upserts or removes one keyed collection item, so
 * applying a patch twice gives the same result as applying it once. Server bookkeeping
 * (`revision`, `version`, `commit_timestamp`, sync status) is left to the caller.
 */

/** Upserts ([value] non-null) or removes ([value] null) the item of [items] whose key is [key]. */
private fun <T> List<T>.upsertOrRemove(key: String, value: T?, keyOf: (T) -> String): List<T> {
  val without = filterNot { keyOf(it) == key }
  return if (value != null) without + value else without
}

/** Applies `SurveyMutation` field, ACL role, and access rule patches. */
object SurveyPatches {
  /** Applies one `SurveyFieldPatch` to [survey]. Unknown keys leave it unchanged. */
  fun applyField(survey: SurveyDef, patch: SurveyFieldPatch): SurveyDef =
    when {
      patch.key == "title" -> survey.copy(title = patch.string_value.orEmpty())
      patch.key == "description" -> survey.copy(description = patch.string_value.orEmpty())
      patch.key == "organization_id" -> survey.copy(organization_id = patch.string_value.orEmpty())
      patch.key == "owner_id" -> survey.copy(owner_id = patch.string_value.orEmpty())
      patch.key == "default_language" ->
        survey.copy(default_language = patch.string_value.orEmpty())
      patch.key == "closed_at_iso" -> survey.copy(closed_at_iso = patch.string_value.orEmpty())
      patch.key == "state" -> survey.copy(state = patch.state_value ?: survey.state)
      patch.key == "closed_at" -> survey.copy(closed_at = patch.timestamp_value)
      patch.key == "outcome" -> survey.copy(outcome = patch.outcome_value)
      patch.key == "map_config" -> survey.copy(map_config = patch.map_config_value)
      patch.key == "map_config/survey_area" -> {
        val mc = survey.map_config ?: MapConfig()
        survey.copy(map_config = mc.copy(survey_area = patch.survey_area_value))
      }
      patch.key == "supported_languages" ->
        survey.copy(supported_languages = patch.string_list_value?.values.orEmpty())
      patch.key == "purpose_ids" ->
        survey.copy(purpose_ids = patch.string_list_value?.values.orEmpty())
      patch.key == "program_ids" ->
        survey.copy(program_ids = patch.string_list_value?.values.orEmpty())
      patch.key.startsWith("forms/") ->
        survey.copy(
          forms =
            survey.forms.upsertOrRemove(patch.key.removePrefix("forms/"), patch.form_value) {
              it.form_id
            }
        )
      patch.key.startsWith("entity_datasets/") ->
        survey.copy(
          entity_datasets =
            survey.entity_datasets.upsertOrRemove(
              patch.key.removePrefix("entity_datasets/"),
              patch.entity_dataset_value,
            ) {
              it.id
            }
        )
      patch.key.startsWith("map_config/layers/") -> {
        val mc = survey.map_config ?: MapConfig()
        survey.copy(
          map_config =
            mc.copy(
              layers =
                mc.layers.upsertOrRemove(
                  patch.key.removePrefix("map_config/layers/"),
                  patch.layer_value,
                ) {
                  it.id
                }
            )
        )
      }
      patch.key.startsWith("form_launch_configs/") ->
        survey.copy(
          form_launch_configs =
            survey.form_launch_configs.upsertOrRemove(
              patch.key.removePrefix("form_launch_configs/"),
              patch.form_launch_config_value,
            ) {
              it.form_id
            }
        )
      patch.key.startsWith("form_concept_links/") ->
        survey.copy(
          form_concept_links =
            survey.form_concept_links.upsertOrRemove(
              patch.key.removePrefix("form_concept_links/"),
              patch.form_concept_links_value,
            ) {
              it.form_id
            }
        )
      else -> survey
    }

  /** Grants, updates, or revokes one user's entry in [acl]. */
  fun applyAclRole(acl: SurveyAcl, patch: AclRolePatch): SurveyAcl {
    val target = patch.user_id
    val without =
      acl.entries.filterNot { it.user_id == target || it.email.equals(target, ignoreCase = true) }
    val entryVal = patch.entry_value
    val roleVal = patch.role
    val updated =
      when {
        entryVal != null -> without + entryVal
        roleVal != null -> without + AclEntry(user_id = target, email = target, role = roleVal)
        else -> without
      }
    return acl.copy(entries = updated)
  }

  /** Updates the access rules set in [patch]; unset fields leave [acl] unchanged. */
  fun applyAccessRules(acl: SurveyAcl, patch: AccessRulesPatch): SurveyAcl {
    var updated = acl
    if (patch.sharing_policy != SharingPolicy.SHARING_POLICY_UNSPECIFIED) {
      updated = updated.copy(sharing_policy = patch.sharing_policy)
    }
    if (patch.peer_data_visibility != PeerDataVisibility.PEER_DATA_VISIBILITY_UNSPECIFIED) {
      updated = updated.copy(peer_data_visibility = patch.peer_data_visibility)
    }
    patch.collector_consent_required?.let {
      updated = updated.copy(collector_consent_required = it)
    }
    patch.collector_consent_text?.let { updated = updated.copy(collector_consent_text = it) }
    patch.care_sovereignty_applied?.let { updated = updated.copy(care_sovereignty_applied = it) }
    patch.owner_email?.let { updated = updated.copy(owner_email = it) }
    if (patch.sharing_role in SHARING_ROLES) {
      updated = updated.copy(sharing_role = patch.sharing_role)
    }
    return updated
  }

  /**
   * Roles allowed in `SurveyAcl.sharing_role`. Others leave it unchanged; the server rejects
   * `SURVEY_ORGANIZER` because organizers must be named on the ACL.
   */
  val SHARING_ROLES: Set<Role> = setOf(Role.VIEWER, Role.DATA_COLLECTOR)
}

/** Applies `OrganizationMutation.update_field` patches. */
object OrganizationPatches {
  /** Applies one `OrganizationFieldPatch` to [org]. Unknown keys leave it unchanged. */
  fun applyField(org: Organization, patch: OrganizationFieldPatch): Organization =
    when {
      patch.key == "name" -> org.copy(name = patch.string_value.orEmpty())
      patch.key == "description" -> org.copy(description = patch.string_value.orEmpty())
      patch.key == "website_url" -> org.copy(website_url = patch.string_value.orEmpty())
      patch.key == "logo_url" -> org.copy(logo_url = patch.string_value.orEmpty())
      patch.key == "country_code" -> org.copy(country_code = patch.string_value.orEmpty())
      patch.key == "listed" -> org.copy(listed = patch.bool_value ?: org.listed)
      patch.key == "exclude_from_platform_aggregates" ->
        org.copy(
          exclude_from_platform_aggregates =
            patch.bool_value ?: org.exclude_from_platform_aggregates
        )
      patch.key == "is_deleted" -> org.copy(is_deleted = patch.bool_value ?: org.is_deleted)
      patch.key == "organization_type" ->
        org.copy(organization_type = patch.organization_type_value ?: org.organization_type)
      patch.key.startsWith("imagery_sources/") ->
        org.copy(
          imagery_sources =
            org.imagery_sources.upsertOrRemove(
              patch.key.removePrefix("imagery_sources/"),
              patch.imagery_source_value,
            ) {
              it.imagery_source_id
            }
        )
      patch.key.startsWith("members/") -> {
        val email = patch.key.removePrefix("members/")
        val without = org.members.filterNot { it.email.equals(email, ignoreCase = true) }
        val memberVal = patch.member_value
        org.copy(members = if (memberVal != null) without + memberVal else without)
      }
      else -> org
    }
}

/** Applies `LibraryMutation.update_field` patches. */
object LibraryPatches {
  /** Applies one `LibraryFieldPatch` to [record]. Unknown keys leave it unchanged. */
  fun applyField(
    record: OrganizationLibraryRecord,
    patch: LibraryFieldPatch,
  ): OrganizationLibraryRecord {
    val bundle = record.bundle ?: LibraryBundle()
    val key = patch.key
    return when {
      key == "settings" -> record.copy(settings = patch.settings_value ?: record.settings)
      key.startsWith("concepts/") ->
        record.copy(
          bundle =
            bundle.copy(
              concepts =
                bundle.concepts.upsertOrRemove(key.removePrefix("concepts/"), patch.concept_value) {
                  it.id
                }
            )
        )
      key.startsWith("form_templates/") ->
        record.copy(
          bundle =
            bundle.copy(
              form_templates =
                bundle.form_templates.upsertOrRemove(
                  key.removePrefix("form_templates/"),
                  patch.form_template_value,
                ) {
                  it.id
                }
            )
        )
      key.startsWith("purpose_packs/") ->
        record.copy(
          bundle =
            bundle.copy(
              purpose_packs =
                bundle.purpose_packs.upsertOrRemove(
                  key.removePrefix("purpose_packs/"),
                  patch.purpose_pack_value,
                ) {
                  it.id
                }
            )
        )
      key.startsWith("export_profiles/") ->
        record.copy(
          bundle =
            bundle.copy(
              export_profiles =
                bundle.export_profiles.upsertOrRemove(
                  key.removePrefix("export_profiles/"),
                  patch.export_profile_value,
                ) {
                  it.id
                }
            )
        )
      else -> record
    }
  }
}

/** Applies `EntityMutation.update` and `SubmissionMutation.update` patches. */
object DataPatches {
  /** Applies every field patch in [patch] to [record], in order. */
  fun applyEntity(record: EntityRecord, patch: EntityPatch): EntityRecord {
    var rec = record
    val props = LinkedHashMap(rec.properties)
    for (p in patch.patches) {
      val propVal = p.property_value
      when {
        p.key == "label" -> rec = rec.copy(label = p.label_value.orEmpty())
        p.key == "is_deleted" -> rec = rec.copy(is_deleted = p.bool_value ?: false)
        p.key == "geometry" ->
          rec = rec.copy(point = p.point_value, trace = p.trace_value, shape = p.shape_value)
        p.key.startsWith("properties/") -> {
          val propKey = p.key.removePrefix("properties/")
          if (propVal != null) props[propKey] = propVal else props.remove(propKey)
        }
        propVal != null -> props[p.key] = propVal
      }
    }
    return rec.copy(properties = props)
  }

  /** Applies every field patch in [patch] to [record], in order. */
  fun applySubmission(record: SubmissionRecord, patch: SubmissionPatch): SubmissionRecord {
    var rec = record
    for (p in patch.patches) {
      when (p.key) {
        "entity_id" -> rec = rec.copy(entity_id = p.string_value.orEmpty())
        "form_version" -> rec = rec.copy(form_version = p.string_value.orEmpty())
        "is_deleted" -> rec = rec.copy(is_deleted = p.bool_value ?: false)
        "is_quarantined" -> rec = rec.copy(is_quarantined = p.bool_value ?: false)
        "review_status" ->
          rec = rec.copy(review_status = p.review_status_value ?: rec.review_status)
      }
    }
    return rec
  }
}
