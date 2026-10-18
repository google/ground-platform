/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.app.data.sync

import groundplatform.v2.data.DataMutation
import groundplatform.v2.data.EntityMutation
import groundplatform.v2.data.EntityPatch
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.SubmissionMutation
import groundplatform.v2.data.SubmissionPatch
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.library.LibraryMutation
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.survey.Organization as OrganizationProto
import groundplatform.v2.survey.OrganizationMutation
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyMutation
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.groundplatform.v2.app.data.datasource.local.store.LocalStore
import org.groundplatform.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.groundplatform.v2.app.data.datasource.local.store.MutationPayload
import org.groundplatform.v2.app.data.datasource.local.store.MutationTarget
import org.groundplatform.v2.app.data.datasource.remote.GrpcProtoMapper
import org.groundplatform.v2.app.domain.model.FormPreviewItem
import org.groundplatform.v2.app.domain.model.GeospatialEntityItem
import org.groundplatform.v2.app.domain.model.MapLayerItem
import org.groundplatform.v2.app.domain.model.Organization
import org.groundplatform.v2.app.domain.model.OrganizationLibrary
import org.groundplatform.v2.app.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.app.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.app.domain.model.SurveyConfig
import org.groundplatform.v2.app.domain.model.SurveyPreviewItem
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDraft

/**
 * [LocalStore] for local edits: every transaction records what it changed as fine-grained mutations
 * in the outbox, in the same transaction (`docs/technical/sync-engine.md`, "The Offline Flow").
 * Repositories keep writing domain models; at the end of each transaction, the records it touched
 * (surveys, map features, submissions, organizations, libraries) are mapped to protos and diffed
 * against their values when first touched ([MutationDiffs]), and the differences are recorded with
 * [OutboxRecorder].
 *
 * Only user edits go through this store. The sync engine writes server data to the underlying
 * [delegate] directly, so downloads aren't recorded as edits.
 */
class RecordingLocalStore(
  private val delegate: LocalStore,
  private val recorder: OutboxRecorder = OutboxRecorder(),
) : LocalStore by delegate {
  override suspend fun <R> transaction(block: suspend LocalStoreTransaction.() -> R): R {
    val open = currentCoroutineContext()[OpenRecording]
    if (open != null && open.store === this) return open.tx.block()
    return delegate.transaction {
      val tx = RecordingTransaction(this)
      val result = withContext(OpenRecording(this@RecordingLocalStore, tx)) { tx.block() }
      tx.recordChanges(recorder)
      result
    }
  }

  /** Marks the coroutine as running inside a recording transaction, so nested ones join it. */
  private class OpenRecording(val store: RecordingLocalStore, val tx: RecordingTransaction) :
    AbstractCoroutineContextElement(OpenRecording) {
    companion object Key : CoroutineContext.Key<OpenRecording>
  }
}

/** A survey's saved definition and ACL as the server would store them, plus its editor draft. */
private data class SurveySnapshot(
  val def: SurveyDef,
  val acl: SurveyAcl,
  val draft: SurveyEditorDraft?,
)

/** Map features and submissions of a survey as the server would store them. */
private data class DataSnapshot(
  val entities: Map<String, EntityRecord>,
  val submissions: Map<String, SubmissionRecord>,
)

/**
 * Transaction that remembers the value of each record before its first write, and records the
 * differences in the outbox when the transaction ends.
 */
private class RecordingTransaction(private val raw: LocalStoreTransaction) :
  LocalStoreTransaction by raw {
  private val surveysBefore = LinkedHashMap<String, SurveySnapshot?>()
  private val dataBefore = LinkedHashMap<String, DataSnapshot>()
  private val organizationsBefore = LinkedHashMap<String, OrganizationProto?>()
  private val librariesBefore = LinkedHashMap<String, OrganizationLibraryRecord?>()

  // Surveys ---------------------------------------------------------------------------------------

  override fun upsertSurvey(survey: SurveyPreviewItem) {
    touchSurvey(survey.id)
    raw.upsertSurvey(survey)
  }

  override fun updateSurvey(
    surveyId: String,
    transform: (SurveyPreviewItem) -> SurveyPreviewItem,
  ): SurveyPreviewItem? {
    val existing = survey(surveyId) ?: return null
    val updated = transform(existing)
    upsertSurvey(updated)
    return updated
  }

  override fun deleteSurvey(surveyId: String) {
    touchSurvey(surveyId)
    raw.deleteSurvey(surveyId)
  }

  override fun putSurveyConfig(surveyId: String, config: SurveyConfig) {
    touchSurvey(surveyId)
    raw.putSurveyConfig(surveyId, config)
  }

  override fun putSurveyEditorDraft(surveyId: String, draft: SurveyEditorDraft) {
    touchSurvey(surveyId)
    raw.putSurveyEditorDraft(surveyId, draft)
  }

  override fun putForms(surveyId: String, forms: List<FormPreviewItem>) {
    touchSurvey(surveyId)
    raw.putForms(surveyId, forms)
  }

  override fun putMapLayers(surveyId: String, layers: List<MapLayerItem>) {
    touchSurvey(surveyId)
    raw.putMapLayers(surveyId, layers)
  }

  // Organizations and libraries -------------------------------------------------------------------

  override fun upsertOrganization(organization: Organization) {
    touchOrganization(organization.id)
    raw.upsertOrganization(organization)
  }

  override fun updateOrganization(
    organizationId: String,
    transform: (Organization) -> Organization,
  ): Organization? {
    val existing = organization(organizationId) ?: return null
    val updated = transform(existing)
    upsertOrganization(updated)
    return updated
  }

  override fun deleteOrganization(organizationId: String) {
    touchOrganization(organizationId)
    raw.deleteOrganization(organizationId)
  }

  override fun putLibrary(library: OrganizationLibrary) {
    touchLibrary(library.organizationId)
    raw.putLibrary(library)
  }

  // `MutateLibrary` can't delete a library, so deleting one locally records no mutation.
  override fun deleteLibrary(organizationId: String) {
    touchLibrary(organizationId)
    raw.deleteLibrary(organizationId)
  }

  // Map features and submissions ------------------------------------------------------------------

  override fun upsertEntities(surveyId: String, entities: List<GeospatialEntityItem>) {
    touchData(surveyId)
    raw.upsertEntities(surveyId, entities)
  }

  override fun upsertEntity(surveyId: String, entity: GeospatialEntityItem) =
    upsertEntities(surveyId, listOf(entity))

  override fun putEntities(surveyId: String, entities: List<GeospatialEntityItem>) {
    touchData(surveyId)
    raw.putEntities(surveyId, entities)
  }

  override fun deleteEntity(surveyId: String, entityId: String) {
    touchData(surveyId)
    raw.deleteEntity(surveyId, entityId)
  }

  override fun putStandaloneSubmissions(
    surveyId: String,
    submissions: List<SubmissionPreviewItem>,
  ) {
    touchData(surveyId)
    raw.putStandaloneSubmissions(surveyId, submissions)
  }

  override fun putSubmissionGeometries(
    surveyId: String,
    geometries: List<SubmissionGeometryPolygon>,
  ) {
    touchData(surveyId)
    raw.putSubmissionGeometries(surveyId, geometries)
  }

  // Snapshots -------------------------------------------------------------------------------------

  private fun touchSurvey(surveyId: String) {
    if (surveyId !in surveysBefore) surveysBefore[surveyId] = surveySnapshot(surveyId)
  }

  private fun touchData(surveyId: String) {
    if (surveyId !in dataBefore) dataBefore[surveyId] = dataSnapshot(surveyId)
  }

  private fun touchOrganization(organizationId: String) {
    if (organizationId !in organizationsBefore) {
      organizationsBefore[organizationId] =
        raw.organization(organizationId)?.let { GrpcProtoMapper.organizationToProto(it) }
    }
  }

  private fun touchLibrary(organizationId: String) {
    if (organizationId !in librariesBefore) {
      librariesBefore[organizationId] =
        raw.library(organizationId)?.let { GrpcProtoMapper.libraryToProto(it) }
    }
  }

  private fun surveySnapshot(surveyId: String): SurveySnapshot? {
    val survey = raw.survey(surveyId) ?: return null
    val draft = raw.surveyEditorDraft(surveyId)
    val (def, acl) =
      GrpcProtoMapper.surveyToProto(
        survey = survey,
        forms = raw.forms(surveyId),
        mapLayers = raw.mapLayers(surveyId),
        config = raw.surveyConfig(surveyId),
        editorDraft = draft,
      )
    return SurveySnapshot(def, acl, draft)
  }

  private fun dataSnapshot(surveyId: String): DataSnapshot {
    val geometriesBySubmission = raw.submissionGeometries(surveyId).groupBy { it.submissionId }
    val entities = raw.entities(surveyId)
    val submissions = entities.flatMap { it.submissions } + raw.standaloneSubmissions(surveyId)
    return DataSnapshot(
      entities =
        entities.associate { e ->
          e.id to GrpcProtoMapper.entityToProto(surveyId, e.copy(submissions = emptyList()))
        },
      submissions =
        submissions.associate { s ->
          s.id to
            GrpcProtoMapper.submissionToProto(
              surveyId,
              s,
              geometriesBySubmission[s.id].orEmpty(),
            )
        },
    )
  }

  // Recording -------------------------------------------------------------------------------------

  fun recordChanges(recorder: OutboxRecorder) {
    for ((id, before) in organizationsBefore) recordOrganization(recorder, id, before)
    for ((id, before) in librariesBefore) recordLibrary(recorder, id, before)
    for ((id, before) in surveysBefore) recordSurvey(recorder, id, before)
    for ((id, before) in dataBefore) recordData(recorder, id, before)
  }

  private fun recordOrganization(recorder: OutboxRecorder, id: String, before: OrganizationProto?) {
    val after = raw.organization(id)?.let { GrpcProtoMapper.organizationToProto(it) }
    val target = MutationTarget.Organization(id)
    val key = Rebaser.organizationKey(id)
    val base = raw.organizationBase(id)?.version ?: 0L
    fun record(m: OrganizationMutation) =
      recorder.record(raw, target, key, MutationPayload.Organization(m), baseRevision = base)
    when {
      before == null && after == null -> Unit
      before == null -> record(OrganizationMutation(upsert_organization = after))
      after == null -> record(OrganizationMutation(delete_organization = true))
      else -> {
        val diff = MutationDiffs.organization(before, after)
        if (diff.requiresUpsert) {
          record(OrganizationMutation(upsert_organization = after))
        } else {
          diff.patches.forEach { record(OrganizationMutation(update_field = it)) }
        }
      }
    }
  }

  private fun recordLibrary(
    recorder: OutboxRecorder,
    id: String,
    before: OrganizationLibraryRecord?,
  ) {
    val after = raw.library(id)?.let { GrpcProtoMapper.libraryToProto(it) } ?: return
    val target = MutationTarget.Library(id)
    val key = Rebaser.libraryKey(id)
    val base = raw.libraryBase(id)?.version ?: 0L
    if (before == null) {
      recorder.record(
        raw,
        target,
        key,
        MutationPayload.Library(LibraryMutation(upsert_library = after)),
        baseRevision = base,
      )
      return
    }
    MutationDiffs.library(before, after).forEach {
      recorder.record(
        raw,
        target,
        key,
        MutationPayload.Library(LibraryMutation(update_field = it)),
        baseRevision = base,
      )
    }
  }

  private fun recordSurvey(recorder: OutboxRecorder, surveyId: String, before: SurveySnapshot?) {
    val after = surveySnapshot(surveyId)
    val saved = MutationTarget.Survey(surveyId, workingCopy = false)
    val key = Rebaser.surveyKey(surveyId)
    fun record(m: SurveyMutation, target: MutationTarget = saved) =
      recorder.record(raw, target, key, MutationPayload.Survey(m))
    when {
      before == null && after == null -> Unit
      before == null -> {
        record(SurveyMutation(upsert_survey = after!!.def))
        record(SurveyMutation(upsert_acl = after.acl))
      }
      after == null -> record(SurveyMutation(trash_survey = true))
      else -> {
        MutationDiffs.survey(before.def, after.def.withLayerVisibilityFrom(before.def)).forEach {
          record(SurveyMutation(update_field = it))
        }
        val acl = MutationDiffs.acl(before.acl, after.acl)
        acl.roles.forEach { record(SurveyMutation(update_acl_role = it)) }
        acl.accessRules?.let { record(SurveyMutation(update_access_rules = it)) }
      }
    }
  }

  /**
   * [this] with each existing map layer's `visible_by_default` taken from [other]: showing and
   * hiding layers on the map is a local view preference, not a survey edit.
   */
  private fun SurveyDef.withLayerVisibilityFrom(other: SurveyDef): SurveyDef {
    val mapConfig = map_config ?: return this
    val visibility = other.map_config?.layers.orEmpty().associate { it.id to it.visible_by_default }
    return copy(
      map_config =
        mapConfig.copy(
          layers =
            mapConfig.layers.map { layer ->
              visibility[layer.id]?.let { layer.copy(visible_by_default = it) } ?: layer
            }
        )
    )
  }

  private fun recordData(recorder: OutboxRecorder, surveyId: String, before: DataSnapshot) {
    val after = dataSnapshot(surveyId)
    val target = MutationTarget.Data(surveyId)
    fun record(key: String, m: DataMutation, baseRevision: Long) =
      recorder.record(raw, target, key, MutationPayload.Data(m), baseRevision = baseRevision)

    for (id in (before.entities.keys + after.entities.keys).distinct()) {
      val old = before.entities[id]
      val new = after.entities[id]
      val key = Rebaser.entityKey(id)
      val base = raw.entityBase(surveyId, id)?.revision ?: 0L
      fun upsert() =
        record(
          key,
          DataMutation(
            entity_mutation =
              EntityMutation(entity_id = id, dataset_id = new!!.dataset_id, upsert = new)
          ),
          base,
        )
      when {
        old == null && new == null -> Unit
        old == null -> upsert()
        new == null ->
          record(
            key,
            DataMutation(entity_mutation = EntityMutation(entity_id = id, delete = true)),
            base,
          )
        else -> {
          val diff = MutationDiffs.entity(old, new)
          when {
            diff.requiresUpsert -> upsert()
            diff.patches.isNotEmpty() ->
              record(
                key,
                DataMutation(
                  entity_mutation =
                    EntityMutation(
                      entity_id = id,
                      dataset_id = new.dataset_id,
                      update = EntityPatch(patches = diff.patches),
                    )
                ),
                base,
              )
          }
        }
      }
    }

    for (id in (before.submissions.keys + after.submissions.keys).distinct()) {
      val old = before.submissions[id]
      val new = after.submissions[id]
      val key = Rebaser.submissionKey(id)
      val base = raw.submissionBase(surveyId, id)?.revision ?: 0L
      fun upsert() =
        record(
          key,
          DataMutation(
            submission_mutation =
              SubmissionMutation(submission_id = id, form_id = new!!.form_id, upsert = new)
          ),
          base,
        )
      when {
        old == null && new == null -> Unit
        old == null -> upsert()
        new == null ->
          record(
            key,
            DataMutation(
              submission_mutation = SubmissionMutation(submission_id = id, delete = true)
            ),
            base,
          )
        else -> {
          val diff = MutationDiffs.submission(old, new)
          when {
            diff.requiresUpsert -> upsert()
            diff.patches.isNotEmpty() ->
              record(
                key,
                DataMutation(
                  submission_mutation =
                    SubmissionMutation(
                      submission_id = id,
                      form_id = new.form_id,
                      update = SubmissionPatch(patches = diff.patches),
                    )
                ),
                base,
              )
          }
        }
      }
    }
  }
}
