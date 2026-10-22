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

import com.squareup.wire.ofEpochSecond
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ReviewStatus
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.ExportProfileDef
import openforis.collectone.v1.library.FormTemplateDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LibraryMutation
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.library.OrganizationLibrarySettings
import openforis.collectone.v1.library.PurposePackDef
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.EntityDatasetDef
import openforis.collectone.v1.survey.FormConceptLinks
import openforis.collectone.v1.survey.FormLaunchConfig
import openforis.collectone.v1.survey.ImagerySource
import openforis.collectone.v1.survey.LayerDef
import openforis.collectone.v1.survey.MapConfig
import openforis.collectone.v1.survey.OfflineTilePackageDef
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.OrganizationMutation
import openforis.collectone.v1.survey.OrganizationType
import openforis.collectone.v1.survey.PeerDataVisibility
import openforis.collectone.v1.survey.SharingPolicy
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyArea
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.survey.SurveyOutcome
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.core.mutation.MutationKeys

/** A sparse `(delete, upsert)` pair describing a change to a domain proto [T]. */
data class SparseDiff<T>(val delete: T? = null, val upsert: T? = null) {
  val isEmpty: Boolean
    get() = delete == null && upsert == null
}

/**
 * Turns a record's old and new proto values into the fine-grained sparse `delete` and `upsert`
 * masks that transform one into the other. Server bookkeeping (revisions, versions, commit
 * timestamps, sync status) is ignored. Keyed collection items are compared one by one, so editing
 * one form, member, or concept produces one sparse mutation.
 *
 * Applying the returned mutations to `old` with the shared functions in `RecordPatches` reproduces
 * `new` for every field the RPCs can mutate; `requiresUpsert` reports changes outside those fields.
 */
object MutationDiffs {
  /** Sparse diff for an entity, and whether other fields changed too. */
  data class EntityDiff(val diff: SparseDiff<EntityRecord>, val requiresUpsert: Boolean) {
    val isEmpty: Boolean
      get() = diff.isEmpty && !requiresUpsert
  }

  fun entity(old: EntityRecord, new: EntityRecord): EntityDiff {
    var up = EntityRecord()
    var del = EntityRecord()
    var hasUp = false
    var hasDel = false

    if (old.label != new.label) {
      if (new.label.isEmpty()) {
        del = del.copy(label = old.label)
        hasDel = true
      } else {
        up = up.copy(label = new.label)
        hasUp = true
      }
    }
    if (old.point != new.point || old.trace != new.trace || old.shape != new.shape) {
      when {
        new.point != null -> {
          up = up.copy(point = new.point)
          hasUp = true
        }
        new.trace != null -> {
          up = up.copy(trace = new.trace)
          hasUp = true
        }
        new.shape != null -> {
          up = up.copy(shape = new.shape)
          hasUp = true
        }
        else -> {
          del =
            del.copy(
              point = old.point ?: GeoPoint(),
              trace = old.trace?.takeIf { old.point == null },
              shape = old.shape?.takeIf { old.point == null && old.trace == null },
            )
          hasDel = true
        }
      }
    }
    val upProps = LinkedHashMap<String, TypedValue>()
    val delProps = LinkedHashMap<String, TypedValue>()
    for (key in (old.properties.keys + new.properties.keys).distinct()) {
      val value = new.properties[key]
      if (old.properties[key] != value) {
        if (value != null) upProps[key] = value else delProps[key] = TypedValue()
      }
    }
    if (upProps.isNotEmpty()) {
      up = up.copy(properties = upProps)
      hasUp = true
    }
    if (delProps.isNotEmpty()) {
      del = del.copy(properties = delProps)
      hasDel = true
    }

    val patchable = { r: EntityRecord ->
      normalize(r)
        .copy(label = "", point = null, trace = null, shape = null, properties = emptyMap())
    }
    return EntityDiff(
      SparseDiff(delete = del.takeIf { hasDel }, upsert = up.takeIf { hasUp }),
      patchable(old) != patchable(new),
    )
  }

  /** Sparse diff for a submission, and whether other fields changed too. */
  data class SubmissionDiff(val diff: SparseDiff<SubmissionRecord>, val requiresUpsert: Boolean) {
    val isEmpty: Boolean
      get() = diff.isEmpty && !requiresUpsert
  }

  fun submission(old: SubmissionRecord, new: SubmissionRecord): SubmissionDiff {
    var up = SubmissionRecord()
    var del = SubmissionRecord()
    var hasUp = false
    var hasDel = false

    if (old.entity_id != new.entity_id) {
      if (new.entity_id.isEmpty()) {
        del = del.copy(entity_id = old.entity_id)
        hasDel = true
      } else {
        up = up.copy(entity_id = new.entity_id)
        hasUp = true
      }
    }
    if (old.form_version != new.form_version) {
      if (new.form_version.isEmpty()) {
        del = del.copy(form_version = old.form_version)
        hasDel = true
      } else {
        up = up.copy(form_version = new.form_version)
        hasUp = true
      }
    }
    if (old.is_quarantined != new.is_quarantined) {
      if (new.is_quarantined) {
        up = up.copy(is_quarantined = true)
        hasUp = true
      } else {
        del = del.copy(is_quarantined = true)
        hasDel = true
      }
    }
    if (old.review_status != new.review_status) {
      if (new.review_status == ReviewStatus.REVIEW_STATUS_UNSPECIFIED) {
        del = del.copy(review_status = old.review_status)
        hasDel = true
      } else {
        up = up.copy(review_status = new.review_status)
        hasUp = true
      }
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
    return SubmissionDiff(
      SparseDiff(delete = del.takeIf { hasDel }, upsert = up.takeIf { hasUp }),
      patchable(old) != patchable(new),
    )
  }

  /** Sparse mutations for an organization, and whether other fields changed too. */
  data class OrganizationDiff(
    val mutations: List<OrganizationMutation>,
    val requiresUpsert: Boolean,
  )

  fun organization(old: Organization, new: Organization): OrganizationDiff {
    val mutations = mutableListOf<OrganizationMutation>()
    fun emit(delete: Organization? = null, upsert: Organization? = null) {
      mutations += OrganizationMutation(delete = delete, upsert = upsert)
    }
    fun string(a: String, b: String, build: (String) -> Organization) {
      if (a != b) {
        if (b.isEmpty()) emit(delete = build(a)) else emit(upsert = build(b))
      }
    }
    fun bool(a: Boolean, b: Boolean, build: (Boolean) -> Organization) {
      if (a != b) {
        if (b) emit(upsert = build(true)) else emit(delete = build(true))
      }
    }
    string(old.name, new.name) { Organization(name = it) }
    string(old.description, new.description) { Organization(description = it) }
    string(old.website_url, new.website_url) { Organization(website_url = it) }
    string(old.logo_url, new.logo_url) { Organization(logo_url = it) }
    string(old.country_code, new.country_code) { Organization(country_code = it) }
    bool(old.listed, new.listed) { Organization(listed = it) }
    bool(old.exclude_from_platform_aggregates, new.exclude_from_platform_aggregates) {
      Organization(exclude_from_platform_aggregates = it)
    }
    if (old.organization_type != new.organization_type) {
      if (new.organization_type == OrganizationType.ORGANIZATION_TYPE_UNSPECIFIED) {
        emit(delete = Organization(organization_type = old.organization_type))
      } else {
        emit(upsert = Organization(organization_type = new.organization_type))
      }
    }
    keyed(old.imagery_sources, new.imagery_sources, { it.imagery_source_id }) { id, v ->
      if (v != null) emit(upsert = Organization(imagery_sources = listOf(v)))
      else
        emit(delete = Organization(imagery_sources = listOf(ImagerySource(imagery_source_id = id))))
    }
    keyed(old.members, new.members, { MutationKeys.memberKey(it) }) { email, v ->
      if (v != null) emit(upsert = Organization(members = listOf(v)))
      else emit(delete = Organization(members = listOf(OrganizationMember(email = email))))
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
    return OrganizationDiff(mutations, patchable(old) != patchable(new))
  }

  /** Sparse mutations for an organization library. */
  fun library(
    old: OrganizationLibraryRecord,
    new: OrganizationLibraryRecord,
  ): List<LibraryMutation> {
    val mutations = mutableListOf<LibraryMutation>()
    fun emit(delete: OrganizationLibraryRecord? = null, upsert: OrganizationLibraryRecord? = null) {
      mutations += LibraryMutation(delete = delete, upsert = upsert)
    }
    if (old.settings != new.settings) {
      val s = new.settings
      if (s == null || s == OrganizationLibrarySettings()) {
        emit(delete = OrganizationLibraryRecord(settings = OrganizationLibrarySettings()))
      } else {
        emit(upsert = OrganizationLibraryRecord(settings = s))
      }
    }
    val a = old.bundle
    val b = new.bundle
    keyed(a?.concepts.orEmpty(), b?.concepts.orEmpty(), { it.id }) { id, v ->
      if (v != null) {
        emit(upsert = OrganizationLibraryRecord(bundle = LibraryBundle(concepts = listOf(v))))
      } else {
        emit(
          delete =
            OrganizationLibraryRecord(
              bundle = LibraryBundle(concepts = listOf(ConceptDef(id = id)))
            )
        )
      }
    }
    keyed(a?.form_templates.orEmpty(), b?.form_templates.orEmpty(), { it.id }) { id, v ->
      if (v != null) {
        emit(upsert = OrganizationLibraryRecord(bundle = LibraryBundle(form_templates = listOf(v))))
      } else {
        emit(
          delete =
            OrganizationLibraryRecord(
              bundle = LibraryBundle(form_templates = listOf(FormTemplateDef(id = id)))
            )
        )
      }
    }
    keyed(a?.purpose_packs.orEmpty(), b?.purpose_packs.orEmpty(), { it.id }) { id, v ->
      if (v != null) {
        emit(upsert = OrganizationLibraryRecord(bundle = LibraryBundle(purpose_packs = listOf(v))))
      } else {
        emit(
          delete =
            OrganizationLibraryRecord(
              bundle = LibraryBundle(purpose_packs = listOf(PurposePackDef(id = id)))
            )
        )
      }
    }
    keyed(a?.export_profiles.orEmpty(), b?.export_profiles.orEmpty(), { it.id }) { id, v ->
      if (v != null) {
        emit(
          upsert = OrganizationLibraryRecord(bundle = LibraryBundle(export_profiles = listOf(v)))
        )
      } else {
        emit(
          delete =
            OrganizationLibraryRecord(
              bundle = LibraryBundle(export_profiles = listOf(ExportProfileDef(id = id)))
            )
        )
      }
    }
    return mutations
  }

  /** Sparse mutations for a survey definition (saved revision or working copy). */
  fun survey(old: SurveyDef, new: SurveyDef): List<SurveyMutation> {
    val mutations = mutableListOf<SurveyMutation>()
    fun emit(delete: SurveyDef? = null, upsert: SurveyDef? = null) {
      mutations += SurveyMutation(delete = delete, upsert = upsert)
    }
    fun string(a: String, b: String, build: (String) -> SurveyDef) {
      if (a != b) {
        if (b.isEmpty()) emit(delete = build(a)) else emit(upsert = build(b))
      }
    }
    fun strings(a: List<String>, b: List<String>, build: (List<String>) -> SurveyDef) {
      if (a != b) {
        if (b.isEmpty()) emit(delete = build(a)) else emit(upsert = build(b))
      }
    }
    string(old.title, new.title) { SurveyDef(title = it) }
    string(old.description, new.description) { SurveyDef(description = it) }
    string(old.organization_id, new.organization_id) { SurveyDef(organization_id = it) }
    string(old.owner_id, new.owner_id) { SurveyDef(owner_id = it) }
    string(old.default_language, new.default_language) { SurveyDef(default_language = it) }
    string(old.closed_at_iso, new.closed_at_iso) { SurveyDef(closed_at_iso = it) }
    if (old.state != new.state) {
      if (new.state == SurveyState.SURVEY_STATE_UNSPECIFIED) {
        emit(delete = SurveyDef(state = old.state))
      } else {
        emit(upsert = SurveyDef(state = new.state))
      }
    }
    if (old.closed_at != new.closed_at) {
      if (new.closed_at == null) {
        emit(delete = SurveyDef(closed_at = old.closed_at ?: ofEpochSecond(1L, 0L)))
      } else {
        emit(upsert = SurveyDef(closed_at = new.closed_at))
      }
    }
    if (old.outcome != new.outcome) {
      if (new.outcome == null || new.outcome == SurveyOutcome()) {
        emit(delete = SurveyDef(outcome = SurveyOutcome()))
      } else {
        emit(upsert = SurveyDef(outcome = new.outcome))
      }
    }
    strings(old.supported_languages, new.supported_languages) {
      SurveyDef(supported_languages = it)
    }
    strings(old.purpose_ids, new.purpose_ids) { SurveyDef(purpose_ids = it) }
    strings(old.program_ids, new.program_ids) { SurveyDef(program_ids = it) }
    keyed(old.forms, new.forms, { it.form_id }) { id, v ->
      if (v != null) emit(upsert = SurveyDef(forms = listOf(v)))
      else emit(delete = SurveyDef(forms = listOf(FormDef(form_id = id))))
    }
    keyed(old.entity_datasets, new.entity_datasets, { it.id }) { id, v ->
      if (v != null) emit(upsert = SurveyDef(entity_datasets = listOf(v)))
      else emit(delete = SurveyDef(entity_datasets = listOf(EntityDatasetDef(id = id))))
    }
    keyed(old.form_launch_configs, new.form_launch_configs, { it.form_id }) { id, v ->
      if (v != null) emit(upsert = SurveyDef(form_launch_configs = listOf(v)))
      else emit(delete = SurveyDef(form_launch_configs = listOf(FormLaunchConfig(form_id = id))))
    }
    keyed(old.form_concept_links, new.form_concept_links, { it.form_id }) { id, v ->
      if (v != null) emit(upsert = SurveyDef(form_concept_links = listOf(v)))
      else emit(delete = SurveyDef(form_concept_links = listOf(FormConceptLinks(form_id = id))))
    }
    val oldMap = old.map_config
    val newMap = new.map_config
    if (oldMap != newMap) {
      if (newMap == null) {
        emit(delete = SurveyDef(map_config = MapConfig()))
      } else {
        val withoutLayersAndArea = { m: MapConfig? ->
          m?.copy(layers = emptyList(), survey_area = null, offline_tile_packages = emptyList())
        }
        if (withoutLayersAndArea(oldMap) != withoutLayersAndArea(newMap)) {
          emit(upsert = SurveyDef(map_config = newMap))
        } else {
          if (oldMap?.survey_area != newMap.survey_area) {
            if (newMap.survey_area == null) {
              emit(delete = SurveyDef(map_config = MapConfig(survey_area = SurveyArea())))
            } else {
              emit(upsert = SurveyDef(map_config = MapConfig(survey_area = newMap.survey_area)))
            }
          }
          keyed(
            oldMap?.offline_tile_packages.orEmpty(),
            newMap.offline_tile_packages,
            { it.id },
          ) { id, v ->
            if (v != null) {
              emit(upsert = SurveyDef(map_config = MapConfig(offline_tile_packages = listOf(v))))
            } else {
              emit(
                delete =
                  SurveyDef(
                    map_config =
                      MapConfig(offline_tile_packages = listOf(OfflineTilePackageDef(id = id)))
                  )
              )
            }
          }
          keyed(oldMap?.layers.orEmpty(), newMap.layers, { it.id }) { id, v ->
            if (v != null) emit(upsert = SurveyDef(map_config = MapConfig(layers = listOf(v))))
            else {
              emit(delete = SurveyDef(map_config = MapConfig(layers = listOf(LayerDef(id = id)))))
            }
          }
        }
      }
    }
    return mutations
  }

  /** Sparse ACL mutations (one per changed collaborator, plus access rules if any changed). */
  fun acl(old: SurveyAcl, new: SurveyAcl): List<SurveyMutation> {
    val mutations = mutableListOf<SurveyMutation>()
    keyed(old.entries, new.entries, { MutationKeys.aclEntryKey(it) }) { key, v ->
      if (v != null) {
        mutations += SurveyMutation(acl_upsert = SurveyAcl(entries = listOf(v)))
      } else {
        val removed = old.entries.firstOrNull { MutationKeys.aclEntryKey(it) == key }
        mutations +=
          SurveyMutation(
            acl_delete =
              SurveyAcl(
                entries =
                  listOf(
                    AclEntry(
                      id = removed?.id.orEmpty(),
                      email = removed?.email?.ifBlank { key } ?: key,
                      user_id = removed?.user_id.orEmpty(),
                    )
                  )
              )
          )
      }
    }
    var up = SurveyAcl()
    var del = SurveyAcl()
    var hasUp = false
    var hasDel = false
    if (old.sharing_policy != new.sharing_policy) {
      if (new.sharing_policy == SharingPolicy.SHARING_POLICY_UNSPECIFIED) {
        del = del.copy(sharing_policy = old.sharing_policy)
        hasDel = true
      } else {
        up = up.copy(sharing_policy = new.sharing_policy)
        hasUp = true
      }
    }
    if (old.peer_data_visibility != new.peer_data_visibility) {
      if (new.peer_data_visibility == PeerDataVisibility.PEER_DATA_VISIBILITY_UNSPECIFIED) {
        del = del.copy(peer_data_visibility = old.peer_data_visibility)
        hasDel = true
      } else {
        up = up.copy(peer_data_visibility = new.peer_data_visibility)
        hasUp = true
      }
    }
    if (old.collector_consent_required != new.collector_consent_required) {
      if (new.collector_consent_required) {
        up = up.copy(collector_consent_required = true)
        hasUp = true
      } else {
        del = del.copy(collector_consent_required = true)
        hasDel = true
      }
    }
    if (old.collector_consent_text != new.collector_consent_text) {
      if (new.collector_consent_text.isEmpty()) {
        del = del.copy(collector_consent_text = old.collector_consent_text)
        hasDel = true
      } else {
        up = up.copy(collector_consent_text = new.collector_consent_text)
        hasUp = true
      }
    }
    if (old.care_sovereignty_applied != new.care_sovereignty_applied) {
      if (new.care_sovereignty_applied) {
        up = up.copy(care_sovereignty_applied = true)
        hasUp = true
      } else {
        del = del.copy(care_sovereignty_applied = true)
        hasDel = true
      }
    }
    if (old.owner_id != new.owner_id) {
      if (new.owner_id.isEmpty()) {
        del = del.copy(owner_id = old.owner_id)
        hasDel = true
      } else {
        up = up.copy(owner_id = new.owner_id)
        hasUp = true
      }
    }
    if (hasUp || hasDel) {
      mutations +=
        SurveyMutation(acl_delete = del.takeIf { hasDel }, acl_upsert = up.takeIf { hasUp })
    }
    return mutations
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
