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

import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.EntityDatasetDef
import openforis.collectone.v1.survey.FormLaunchConfig
import openforis.collectone.v1.survey.LayerDef
import openforis.collectone.v1.survey.MapConfig
import openforis.collectone.v1.survey.OfflineTilePackageDef
import openforis.collectone.v1.survey.PeerDataVisibility
import openforis.collectone.v1.survey.QuotaLimits
import openforis.collectone.v1.survey.QuotaTier
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SharingPolicy
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyState

/**
 * Applies sparse `SurveyDef` and `SurveyAcl` `delete` and `upsert` masks (semantics in
 * `KeyedListMasks.kt`). The user data service and the clients both apply mutations with it, so an
 * optimistic local edit and the server's result agree. Forms and datasets are delegated to
 * [FormDefMutations] and [EntityDatasetMutations].
 */
object SurveyMutations {
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

  /**
   * Clears what [delete] marks on [survey]. An empty sub-message (`QuotaLimits()`, `MapConfig()`, a
   * translation catalog without languages) clears the whole field; one with fields set recurses.
   */
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
    if (delete.outcome != null) s = s.copy(outcome = null)
    if (delete.location_label.isNotEmpty()) s = s.copy(location_label = "")
    if (delete.is_downloaded) s = s.copy(is_downloaded = false)
    if (delete.thumbnail_theme.isNotEmpty()) s = s.copy(thumbnail_theme = "")
    if (delete.entity_count != 0) s = s.copy(entity_count = 0)
    if (delete.other_purpose.isNotEmpty()) s = s.copy(other_purpose = "")
    if (delete.form_template_origins.isNotEmpty()) {
      val ids = delete.form_template_origins.map { it.form_id }.toSet()
      s = s.copy(form_template_origins = s.form_template_origins.filterNot { it.form_id in ids })
    }
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
        else
          s.copy(
            translations =
              s.translations?.let { FormDefMutations.applyTranslationCatalogDelete(it, dt) }
          )
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
              applyBranchDelete = FormDefMutations::applyFormDelete,
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
              applyBranchDelete = EntityDatasetMutations::applyDatasetDelete,
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

  /**
   * Merges the fields [upsert] sets onto [base]. Two fields are not plain overwrites:
   * `form_template_origins` replaces entries per Form, and `trash_time` keeps the first time a
   * survey was trashed (only a delete mask clears it).
   */
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
    if (upsert.saved_revision_number != 0L) {
      s = s.copy(saved_revision_number = upsert.saved_revision_number)
    }
    if (upsert.location_label.isNotEmpty()) s = s.copy(location_label = upsert.location_label)
    if (upsert.is_downloaded) s = s.copy(is_downloaded = true)
    if (upsert.thumbnail_theme.isNotEmpty()) s = s.copy(thumbnail_theme = upsert.thumbnail_theme)
    if (upsert.entity_count != 0) s = s.copy(entity_count = upsert.entity_count)
    if (upsert.other_purpose.isNotEmpty()) s = s.copy(other_purpose = upsert.other_purpose)
    if (upsert.form_template_origins.isNotEmpty()) {
      val ids = upsert.form_template_origins.map { it.form_id }.toSet()
      s =
        s.copy(
          form_template_origins =
            s.form_template_origins.filterNot { it.form_id in ids } + upsert.form_template_origins
        )
    }
    if (upsert.primary_form_id.isNotEmpty()) s = s.copy(primary_form_id = upsert.primary_form_id)
    if (upsert.trash_time != null && s.trash_time == null) {
      s = s.copy(trash_time = upsert.trash_time)
    }
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
            s.translations?.let {
              FormDefMutations.mergeTranslationCatalog(it, upsert.translations)
            } ?: upsert.translations
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
              mergeItem = FormDefMutations::mergeForm,
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
              mergeItem = EntityDatasetMutations::mergeDataset,
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

  /**
   * Clears what [delete] marks on [acl]. An entry mask with only its key removes the person; one
   * that also sets a role, invite token, or profile clears just those.
   */
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

  /**
   * Merges [upsert] onto [base]. Entries merge field by field, so an upsert that only changes a
   * role keeps the stored invite token and profile; `default_role` accepts only [SHARING_ROLES].
   */
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
    return q
  }
}
