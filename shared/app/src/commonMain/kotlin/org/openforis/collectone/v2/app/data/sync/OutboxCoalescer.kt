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

import groundplatform.v2.data.DataMutation
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.ReviewStatus
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.forms.FormDef
import groundplatform.v2.library.LibraryMutation
import groundplatform.v2.survey.MapConfig
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationMutation
import groundplatform.v2.survey.OrganizationType
import groundplatform.v2.survey.PeerDataVisibility
import groundplatform.v2.survey.Role
import groundplatform.v2.survey.SharingPolicy
import groundplatform.v2.survey.SurveyMutation
import groundplatform.v2.survey.SurveyState
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.core.mutation.DataPatches
import org.openforis.collectone.v2.core.mutation.MutationKeys

/**
 * Coalesces a newly recorded mutation with the queued (not yet sent) mutations for the same record,
 * so the outbox holds at most one pending value per field or collection item (see
 * `docs/technical/sync-engine.md`, "The Offline Flow"):
 * - A sparse field/item mutation replaces queued mutations to the same path and to paths beneath it
 *   (for example a `FormDef` delete for `f1` drops queued component edits inside `f1`).
 * - A whole-record operation (creating or deleting an entity, submission, organization, library, or
 *   survey) replaces every queued sparse field mutation for the record.
 * - Deleting an entity or submission that was created locally and never sent cancels both.
 * - Discarding a survey's working copy drops queued (unsent) working-copy mutations.
 *
 * Mutations that are in flight, retrying, or rejected are never changed: the server may already
 * have applied them. Sparse mutations to different fields commute, so appending the new mutation
 * after the remaining queued ones preserves the result.
 */
object OutboxCoalescer {
  /** Returns [outbox] with [new] recorded: coalesced, appended, or cancelled. */
  fun coalesce(outbox: List<PendingMutation>, new: PendingMutation): List<PendingMutation> {
    val sameRecord = { m: PendingMutation ->
      m.target == new.target && m.recordKey == new.recordKey
    }
    val queued = { m: PendingMutation -> m.state == OutboxState.QUEUED && sameRecord(m) }

    if (isDataDelete(new.payload)) {
      val forRecord = outbox.filter(sameRecord)
      val createdLocally =
        forRecord.isNotEmpty() &&
          forRecord.all { it.state == OutboxState.QUEUED } &&
          isDataUpsert(forRecord.first().payload)
      if (createdLocally) return outbox.filterNot(sameRecord)
    }

    if (isDiscardWorkingCopy(new.payload)) {
      // Unsent working-copy edits would only be discarded again.
      return outbox.filterNot {
        queued(it) && it.target == new.target && isFieldPatch(it.payload)
      } + new
    }

    if (isWholeRecord(new.payload)) {
      return outbox.filterNot { queued(it) && isFieldPatch(it.payload) } + new
    }

    val newKeys = fieldKeys(new.payload)
    if (newKeys.isEmpty()) return outbox + new
    val coalesced = outbox.mapNotNull { existing ->
      if (!queued(existing)) existing else withoutKeys(existing, newKeys)
    }
    return coalesced + new
  }

  /** True when [old] is [new] or a path beneath it. */
  internal fun supersedes(new: String, old: String): Boolean = old == new || old.startsWith("$new/")

  /**
   * [mutation] without the sparse fields/items that any of [newKeys] supersedes, or `null` when
   * nothing is left of it.
   */
  private fun withoutKeys(mutation: PendingMutation, newKeys: Set<String>): PendingMutation? {
    fun superseded(key: String) = newKeys.any { supersedes(it, key) }
    return when (val payload = mutation.payload) {
      is MutationPayload.Survey -> {
        val keys = fieldKeys(payload)
        if (keys.isNotEmpty() && keys.all(::superseded)) null else mutation
      }
      is MutationPayload.Organization -> {
        val keys = fieldKeys(payload)
        if (keys.isNotEmpty() && keys.all(::superseded)) null else mutation
      }
      is MutationPayload.Library -> {
        val keys = fieldKeys(payload)
        if (keys.isNotEmpty() && keys.all(::superseded)) null else mutation
      }
      is MutationPayload.Data -> {
        val m = payload.mutation
        val em = m.entity_mutation
        val sm = m.submission_mutation
        when {
          em != null && !isDataUpsert(payload) && !isDataDelete(payload) -> {
            val up = em.upsert?.let { stripEntity(it, ::superseded) }
            val del = em.delete?.let { stripEntity(it, ::superseded) }
            if (up == null && del == null) null
            else
              mutation.copy(
                payload =
                  MutationPayload.Data(m.copy(entity_mutation = em.copy(upsert = up, delete = del)))
              )
          }
          sm != null && !isDataUpsert(payload) && !isDataDelete(payload) -> {
            val up = sm.upsert?.let { stripSubmission(it, ::superseded) }
            val del = sm.delete?.let { stripSubmission(it, ::superseded) }
            if (up == null && del == null) null
            else
              mutation.copy(
                payload =
                  MutationPayload.Data(
                    m.copy(submission_mutation = sm.copy(upsert = up, delete = del))
                  )
              )
          }
          else -> mutation
        }
      }
    }
  }

  private fun stripEntity(record: EntityRecord, superseded: (String) -> Boolean): EntityRecord? {
    var r = record
    if (r.label.isNotEmpty() && superseded("label")) r = r.copy(label = "")
    if ((r.point != null || r.trace != null || r.shape != null) && superseded("geometry")) {
      r = r.copy(point = null, trace = null, shape = null)
    }
    if (r.properties.isNotEmpty()) {
      r = r.copy(properties = r.properties.filterKeys { !superseded("properties/$it") })
    }
    if (r.is_field_created && superseded("is_field_created")) r = r.copy(is_field_created = false)
    if (r.active_lock != null && superseded("active_lock")) r = r.copy(active_lock = null)
    return r.takeIf { it != EntityRecord() }
  }

  private fun stripSubmission(
    record: SubmissionRecord,
    superseded: (String) -> Boolean,
  ): SubmissionRecord? {
    var r = record
    if (r.entity_id.isNotEmpty() && superseded("entity_id")) r = r.copy(entity_id = "")
    if (r.form_version.isNotEmpty() && superseded("form_version")) r = r.copy(form_version = "")
    if (r.is_quarantined && superseded("is_quarantined")) r = r.copy(is_quarantined = false)
    if (r.review_status != ReviewStatus.REVIEW_STATUS_UNSPECIFIED && superseded("review_status")) {
      r = r.copy(review_status = ReviewStatus.REVIEW_STATUS_UNSPECIFIED)
    }
    if (r.record != null && superseded("record")) r = r.copy(record = null)
    return r.takeIf { it != SubmissionRecord() }
  }

  /** Logical paths a sparse mutation touches; empty for whole-record/lifecycle mutations. */
  internal fun fieldKeys(payload: MutationPayload): Set<String> =
    when (payload) {
      is MutationPayload.Survey ->
        if (isWholeRecord(payload)) emptySet() else surveyKeys(payload.mutation)
      is MutationPayload.Organization ->
        if (isWholeRecord(payload)) emptySet() else organizationKeys(payload.mutation)
      is MutationPayload.Library ->
        if (isWholeRecord(payload)) emptySet() else libraryKeys(payload.mutation)
      is MutationPayload.Data ->
        if (isWholeRecord(payload)) emptySet() else dataKeys(payload.mutation)
    }

  private fun surveyKeys(m: SurveyMutation): Set<String> {
    val keys = mutableSetOf<String>()
    for (s in listOfNotNull(m.upsert, m.delete)) {
      if (s.title.isNotEmpty()) keys += "title"
      if (s.description.isNotEmpty()) keys += "description"
      if (s.organization_id.isNotEmpty()) keys += "organization_id"
      if (s.owner_id.isNotEmpty()) keys += "owner_id"
      if (s.default_language.isNotEmpty()) keys += "default_language"
      if (s.closed_at_iso.isNotEmpty()) keys += "closed_at_iso"
      if (s.state != SurveyState.SURVEY_STATE_UNSPECIFIED) keys += "state"
      if (s.closed_at != null) keys += "closed_at"
      if (s.outcome != null) keys += "outcome"
      if (s.supported_languages.isNotEmpty()) keys += "supported_languages"
      if (s.purpose_ids.isNotEmpty()) keys += "purpose_ids"
      if (s.program_ids.isNotEmpty()) keys += "program_ids"
      for (f in s.forms) {
        val onlyComponents =
          f.copy(view = null) == FormDef(form_id = f.form_id) &&
            f.view?.components?.isNotEmpty() == true
        if (onlyComponents) {
          for (c in f.view!!.components) {
            keys += "forms/${f.form_id}/components/${MutationKeys.componentKey(c)}"
          }
        } else {
          keys += "forms/${f.form_id}"
        }
      }
      for (d in s.entity_datasets) keys += "entity_datasets/${d.id}"
      for (c in s.form_launch_configs) keys += "form_launch_configs/${c.form_id}"
      for (c in s.form_concept_links) keys += "form_concept_links/${c.form_id}"
      val mc = s.map_config
      if (mc != null) {
        for (l in mc.layers) keys += "map_config/layers/${l.id}"
        for (p in mc.offline_tile_packages) keys += "map_config/offline_tile_packages/${p.id}"
        if (mc.survey_area != null) keys += "map_config/survey_area"
        if (
          mc == MapConfig() ||
            mc.copy(
              layers = emptyList(),
              offline_tile_packages = emptyList(),
              survey_area = null,
            ) != MapConfig()
        ) {
          keys += "map_config"
        }
      }
    }
    for (a in listOfNotNull(m.acl_upsert, m.acl_delete)) {
      for (e in a.entries) keys += aclKey(MutationKeys.aclEntryKey(e))
      if (
        a.sharing_policy != SharingPolicy.SHARING_POLICY_UNSPECIFIED ||
          a.peer_data_visibility != PeerDataVisibility.PEER_DATA_VISIBILITY_UNSPECIFIED ||
          a.collector_consent_required ||
          a.collector_consent_text.isNotEmpty() ||
          a.care_sovereignty_applied ||
          a.owner_id.isNotEmpty() ||
          a.default_role != Role.ROLE_UNSPECIFIED
      ) {
        keys += "acl/access_rules"
      }
    }
    return keys
  }

  private fun organizationKeys(m: OrganizationMutation): Set<String> {
    val keys = mutableSetOf<String>()
    for (o in listOfNotNull(m.upsert, m.delete)) {
      if (o.name.isNotEmpty()) keys += "name"
      if (o.description.isNotEmpty()) keys += "description"
      if (o.website_url.isNotEmpty()) keys += "website_url"
      if (o.logo_url.isNotEmpty()) keys += "logo_url"
      if (o.country_code.isNotEmpty()) keys += "country_code"
      if (o.listed) keys += "listed"
      if (o.exclude_from_platform_aggregates) keys += "exclude_from_platform_aggregates"
      if (o.organization_type != OrganizationType.ORGANIZATION_TYPE_UNSPECIFIED) {
        keys += "organization_type"
      }
      for (src in o.imagery_sources) keys += "imagery_sources/${src.imagery_source_id}"
      for (mem in o.members) keys += "members/${MutationKeys.memberKey(mem)}"
    }
    return keys
  }

  private fun libraryKeys(m: LibraryMutation): Set<String> {
    val keys = mutableSetOf<String>()
    for (r in listOfNotNull(m.upsert, m.delete)) {
      if (r.settings != null) keys += "settings"
      val b = r.bundle
      if (b != null) {
        for (c in b.concepts) keys += "concepts/${c.id}"
        for (t in b.form_templates) keys += "form_templates/${t.id}"
        for (p in b.purpose_packs) keys += "purpose_packs/${p.id}"
        for (e in b.export_profiles) keys += "export_profiles/${e.id}"
      }
    }
    return keys
  }

  private fun dataKeys(m: DataMutation): Set<String> {
    val keys = mutableSetOf<String>()
    val em = m.entity_mutation
    if (em != null) {
      for (r in listOfNotNull(em.upsert, em.delete)) {
        if (r.label.isNotEmpty()) keys += "label"
        if (r.point != null || r.trace != null || r.shape != null) keys += "geometry"
        for (k in r.properties.keys) keys += "properties/$k"
        if (r.is_field_created) keys += "is_field_created"
        if (r.active_lock != null) keys += "active_lock"
      }
    }
    val sm = m.submission_mutation
    if (sm != null) {
      for (r in listOfNotNull(sm.upsert, sm.delete)) {
        if (r.entity_id.isNotEmpty()) keys += "entity_id"
        if (r.form_version.isNotEmpty()) keys += "form_version"
        if (r.is_quarantined) keys += "is_quarantined"
        if (r.review_status != ReviewStatus.REVIEW_STATUS_UNSPECIFIED) keys += "review_status"
        if (r.record != null) keys += "record"
      }
    }
    return keys
  }

  private fun isFieldPatch(payload: MutationPayload): Boolean = fieldKeys(payload).isNotEmpty()

  private fun isWholeRecord(payload: MutationPayload): Boolean =
    when (payload) {
      is MutationPayload.Survey ->
        payload.mutation.upsert?.survey_id?.isNotEmpty() == true ||
          payload.mutation.acl_upsert?.let { it.survey_id.isNotEmpty() && it.entries.isEmpty() } ==
            true
      is MutationPayload.Organization ->
        payload.mutation.upsert?.organization_id?.isNotEmpty() == true ||
          payload.mutation.delete_organization != null
      is MutationPayload.Library -> payload.mutation.upsert?.organization_id?.isNotEmpty() == true
      is MutationPayload.Data -> isDataUpsert(payload) || isDataDelete(payload)
    }

  private fun isDiscardWorkingCopy(payload: MutationPayload): Boolean =
    payload is MutationPayload.Survey && payload.mutation.discard_working_copy == true

  private fun isDataUpsert(payload: MutationPayload): Boolean =
    payload is MutationPayload.Data &&
      (payload.mutation.entity_mutation?.let {
        it.delete == null && it.upsert?.entity_id?.isNotEmpty() == true
      } == true ||
        payload.mutation.submission_mutation?.let {
          it.delete == null && it.upsert?.submission_id?.isNotEmpty() == true
        } == true)

  private fun isDataDelete(payload: MutationPayload): Boolean =
    payload is MutationPayload.Data && payload.mutation.isDelete()

  private fun DataMutation.isDelete(): Boolean =
    entity_mutation?.let { DataPatches.isEntityRootDelete(it.delete, it.upsert) } == true ||
      submission_mutation?.let { DataPatches.isSubmissionRootDelete(it.delete, it.upsert) } == true

  /** Pseudo key path for ACL role mutations, so later mutations for the same user coalesce. */
  private fun aclKey(userId: String): String = "acl/${userId.lowercase()}"
}
