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
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.ReviewStatus
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewDef
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
import openforis.collectone.v1.survey.FormTemplateOrigin
import openforis.collectone.v1.survey.ImagerySource
import openforis.collectone.v1.survey.LayerDef
import openforis.collectone.v1.survey.MapConfig
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
 * timestamps, sync status) and server-owned fields (audit info, saved revision number) are ignored:
 * the client reads the server's values but never edits them. Keyed collection items are compared
 * one by one, so editing one form, member, or concept produces one sparse mutation.
 *
 * Applying the returned mutations to `old` with the shared core `*Mutations` objects reproduces
 * `new` for every field the RPCs can mutate; `requiresUpsert` reports changes outside those fields.
 *
 * proto3 can't tell an unset scalar from one upserted to its default, so clearing a field (empty
 * string, `false`, `*_UNSPECIFIED`, removed message) is sent as a `delete` mask that names the
 * field, never as an upsert of the default. Removed keyed items are deleted by key alone.
 */
object MutationDiffs {
  /** Sparse diff for an entity, and whether other fields changed too. */
  data class EntityDiff(val diff: SparseDiff<EntityRecord>, val requiresUpsert: Boolean) {
    val isEmpty: Boolean
      get() = diff.isEmpty && !requiresUpsert
  }

  /**
   * The label, geometry, and properties as sparse masks. Canonical and legacy geometry fields are
   * compared together: any change upserts the new one, and removing every geometry deletes it.
   */
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
    if (geometryFields(old) != geometryFields(new)) {
      when {
        new.geometry != null -> {
          up = up.copy(geometry = new.geometry)
          hasUp = true
        }
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
          del = del.copy(geometry = Geometry())
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

    if (old.dataset_name != new.dataset_name) {
      if (new.dataset_name.isEmpty()) {
        del = del.copy(dataset_name = old.dataset_name)
        hasDel = true
      } else {
        up = up.copy(dataset_name = new.dataset_name)
        hasUp = true
      }
    }
    if (old.layer_id != new.layer_id) {
      if (new.layer_id.isEmpty()) {
        del = del.copy(layer_id = old.layer_id)
        hasDel = true
      } else {
        up = up.copy(layer_id = new.layer_id)
        hasUp = true
      }
    }
    if (old.geo_id != new.geo_id) {
      if (new.geo_id.isEmpty()) {
        del = del.copy(geo_id = old.geo_id)
        hasDel = true
      } else {
        up = up.copy(geo_id = new.geo_id)
        hasUp = true
      }
    }
    if (old.area_hectares != new.area_hectares) {
      if (new.area_hectares == 0.0) {
        del = del.copy(area_hectares = old.area_hectares)
        hasDel = true
      } else {
        up = up.copy(area_hectares = new.area_hectares)
        hasUp = true
      }
    }
    if (old.perimeter_meters != new.perimeter_meters) {
      if (new.perimeter_meters == 0) {
        del = del.copy(perimeter_meters = old.perimeter_meters)
        hasDel = true
      } else {
        up = up.copy(perimeter_meters = new.perimeter_meters)
        hasUp = true
      }
    }
    if (old.color_hex != new.color_hex) {
      if (new.color_hex == 0L) {
        del = del.copy(color_hex = old.color_hex)
        hasDel = true
      } else {
        up = up.copy(color_hex = new.color_hex)
        hasUp = true
      }
    }
    if (old.singular_type_label != new.singular_type_label) {
      if (new.singular_type_label.isEmpty()) {
        del = del.copy(singular_type_label = old.singular_type_label)
        hasDel = true
      } else {
        up = up.copy(singular_type_label = new.singular_type_label)
        hasUp = true
      }
    }

    val patchable = { r: EntityRecord ->
      normalize(r)
        .copy(
          label = "",
          geometry = null,
          point = null,
          trace = null,
          shape = null,
          properties = emptyMap(),
          dataset_name = "",
          layer_id = "",
          geo_id = "",
          area_hectares = 0.0,
          perimeter_meters = 0,
          color_hex = 0L,
          singular_type_label = "",
        )
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

  /**
   * The map feature link, form version, quarantine flag, and review status as sparse masks; the
   * answers themselves (`record`) change only through a whole-record upsert ([upsertOf]).
   */
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

  /** One mutation per changed profile field, imagery source, or member (keyed by email or ID). */
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
    string(old.version, new.version) { SurveyDef(version = it) }
    string(old.location_label, new.location_label) { SurveyDef(location_label = it) }
    string(old.other_purpose, new.other_purpose) { SurveyDef(other_purpose = it) }
    string(old.primary_form_id, new.primary_form_id) { SurveyDef(primary_form_id = it) }
    string(old.thumbnail_theme, new.thumbnail_theme) { SurveyDef(thumbnail_theme = it) }
    if (old.entity_count != new.entity_count) {
      if (new.entity_count == 0) {
        emit(delete = SurveyDef(entity_count = old.entity_count))
      } else {
        emit(upsert = SurveyDef(entity_count = new.entity_count))
      }
    }
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
    keyed(old.form_template_origins, new.form_template_origins, { it.form_id }) { id, v ->
      if (v != null) emit(upsert = SurveyDef(form_template_origins = listOf(v)))
      else
        emit(delete = SurveyDef(form_template_origins = listOf(FormTemplateOrigin(form_id = id))))
    }
    val oldFormsById = old.forms.associateBy { it.form_id }
    keyed(old.forms, new.forms, { it.form_id }) { id, v ->
      if (v != null) {
        emit(
          delete = formUpdateDeleteMask(oldFormsById[id], v)?.let { SurveyDef(forms = listOf(it)) },
          upsert = SurveyDef(forms = listOf(v)),
        )
      } else {
        emit(delete = SurveyDef(forms = listOf(FormDef(form_id = id))))
      }
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
        // Clients no longer read or write `offline_tile_packages` (deprecated), so a survey's
        // packages never cause a change here.
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
    if (old.default_role != new.default_role) {
      if (new.default_role == openforis.collectone.v1.survey.Role.ROLE_UNSPECIFIED) {
        del = del.copy(default_role = old.default_role)
        hasDel = true
      } else {
        up = up.copy(default_role = new.default_role)
        hasUp = true
      }
    }
    if (hasUp || hasDel) {
      mutations +=
        SurveyMutation(acl_delete = del.takeIf { hasDel }, acl_upsert = up.takeIf { hasUp })
    }
    return mutations
  }

  /**
   * [def] as a whole-record upsert: without the saved revision number, which the server numbers
   * itself. The server also ignores the `audit_info` of an upsert and stamps its own.
   */
  fun upsertOf(def: SurveyDef): SurveyDef = def.copy(saved_revision_number = 0L)

  /**
   * [record] as a whole-record upsert: without `server_updated_at`, which the server sets when it
   * accepts the upload. The server also ignores the `audit_info` of an upsert and stamps its own.
   */
  fun upsertOf(record: SubmissionRecord): SubmissionRecord = record.copy(server_updated_at = null)

  /** The canonical and legacy geometry fields of [r]. */
  private fun geometryFields(r: EntityRecord) = listOf(r.geometry, r.point, r.trace, r.shape)

  private fun normalize(r: EntityRecord) =
    r.copy(
      revision = 0L,
      commit_timestamp = null,
      sync_status_key = "",
      is_deleted = false,
      audit_info = null,
    )

  private fun normalize(r: SubmissionRecord) =
    r.copy(
      revision = 0L,
      commit_timestamp = null,
      server_updated_at = null,
      sync_status_key = "",
      is_deleted = false,
      audit_info = null,
    )

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

  /**
   * Branch delete mask clearing replaced `model`, `view`, and cleared scalar fields before merging
   * an updated [FormDef], so inserted or reordered `ViewComponent`s keep their exact position
   * instead of being appended by `mergeKeyedUpsert` and removed components or bindings do not
   * linger.
   */
  private fun formUpdateDeleteMask(prev: FormDef?, next: FormDef): FormDef? {
    if (prev == null) return null
    val del =
      FormDef(
        form_id = next.form_id,
        title = if (next.title.isEmpty()) prev.title.ifEmpty { "_" } else "",
        version = if (next.version.isEmpty()) prev.version.ifEmpty { "_" } else "",
        default_language =
          if (next.default_language.isEmpty()) prev.default_language.ifEmpty { "_" } else "",
        model = if (prev.model != null || next.model != null) ModelDef() else null,
        view = if (prev.view != null || next.view != null) ViewDef() else null,
      )
    return del.takeIf { it != FormDef(form_id = next.form_id) }
  }
}
