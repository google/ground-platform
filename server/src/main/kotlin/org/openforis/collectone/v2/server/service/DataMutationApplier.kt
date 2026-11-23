/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.server.service

import openforis.collectone.v1.data.ConflictResolutionStatus
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.DataMutationResult
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.survey.QuotaLimits
import openforis.collectone.v1.survey.SurveyDef
import org.openforis.collectone.v2.core.access.SurveyRole
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.geo.geoid.AgStackGeoId
import org.openforis.collectone.v2.core.mutation.DataMutations
import org.openforis.collectone.v2.server.data.UserDataWriter

/**
 * Applies the mutations of one `MutateData` request to survey [surveyId] in its write transaction
 * [tx]. The caller ([access]) already holds [role] on the survey, at least data collector.
 *
 * For each new mutation it checks the caller may change the target record ([apply]), applies the
 * entity or submission change with its version and quota rules, and stamps the record's
 * server-owned audit info for [creator]. The transaction reads data as of its start, so the records
 * this request changed are kept here, and [writeChanges] writes them once at the end.
 */
internal class DataMutationApplier(
  private val tx: UserDataWriter,
  private val surveyId: String,
  private val access: Access,
  private val role: SurveyRole,
  /** The authenticated user changes are attributed to, or null for the default caller. */
  private val creator: String?,
) {
  private val entities = LinkedHashMap<String, EntityRecord>()
  private val initialEntities = LinkedHashMap<String, EntityRecord?>()
  private val submissions = LinkedHashMap<String, SubmissionRecord>()
  private val isOrganizer = role.atLeast(SurveyRole.SURVEY_ORGANIZER)
  private val ownRecordsOnly by lazy { access.seesOwnRecordsOnly(surveyId, role) }
  private val stamp = AuditStamp(creator.orEmpty(), tx.now)

  private fun storedEntity(entityId: String): EntityRecord? =
    entities[entityId] ?: tx.entity(surveyId, entityId)

  private fun storedSubmission(submissionId: String): SubmissionRecord? =
    submissions[submissionId] ?: tx.submission(surveyId, submissionId)

  /** Result for a replayed mutation: the target record's current state, unchanged. */
  fun duplicate(mutation: DataMutation): DataMutationResult {
    val entity = mutation.entity_mutation?.let { storedEntity(it.entity_id) }
    val submission = mutation.submission_mutation?.let { storedSubmission(it.submission_id) }
    return DataMutationResult(
      entity = entity,
      submission = if (entity == null) submission else null,
      conflict_status = ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_CLEAN,
      commit_timestamp = entity?.commit_timestamp ?: submission?.commit_timestamp,
      duplicate = true,
    )
  }

  /**
   * Applies [mutation] on top of the stored records and this request's earlier changes, after
   * checking the caller may change its target record, and stamps the record's server-owned audit
   * info. The store stamps the commit timestamp.
   */
  fun apply(mutation: DataMutation): DataMutationResult {
    checkMayChange(mutation)
    val em = mutation.entity_mutation
    val sm = mutation.submission_mutation
    return when {
      em != null -> {
        val prior = storedEntity(em.entity_id)
        stampEntity(prior, applyEntity(em))
      }
      sm != null -> {
        val prior = storedSubmission(sm.submission_id)
        stampSubmission(prior, applySubmission(sm))
      }
      else ->
        DataMutationResult(
          conflict_status = ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_CLEAN
        )
    }
  }

  /** Writes the records this request changed. Returns whether any entity changed. */
  fun writeChanges(): Boolean {
    entities.values.forEach(tx::putEntity)
    submissions.values.forEach(tx::putSubmission)
    return entities.isNotEmpty()
  }

  /**
   * Entities whose geometry was newly created or changed relative to the start of the transaction.
   */
  fun enrichmentCandidates(): List<EntityEnrichmentCandidate> =
    entities.values.mapNotNull { updated ->
      if (updated.is_deleted) return@mapNotNull null
      val newGeometry = GeometryProtos.of(updated) ?: return@mapNotNull null
      val priorGeometry =
        initialEntities[updated.entity_id]?.takeIf { !it.is_deleted }?.let(GeometryProtos::of)
      if (priorGeometry == newGeometry) return@mapNotNull null
      EntityEnrichmentCandidate(
        surveyId = surveyId,
        entityId = updated.entity_id,
        geometry = newGeometry,
      )
    }

  /**
   * Non-deleted entities whose geometry was created or changed (or whose GeoID was newly computed)
   * in this request, to be registered with the AgStack Asset Registry after commit.
   */
  fun geoIdCandidates(): List<GeoIdRegistrationCandidate> =
    entities.values.mapNotNull { updated ->
      if (updated.is_deleted) return@mapNotNull null
      val geometry = GeometryProtos.of(updated) ?: return@mapNotNull null
      val initial = initialEntities[updated.entity_id]
      val initialGeometry = initial?.let(GeometryProtos::of)
      val initialGeoId = initial?.geo_id.orEmpty()
      val needsRegistration =
        initial == null ||
          initial.is_deleted ||
          initialGeometry != geometry ||
          !AgStackGeoId.matchesGeometry(initialGeoId, geometry)
      if (!needsRegistration) return@mapNotNull null
      GeoIdRegistrationCandidate(
        surveyId = surveyId,
        datasetId = updated.dataset_id,
        entityId = updated.entity_id,
        geometry = geometry,
        provisionalGeoId = updated.geo_id,
      )
    }

  /** Fails with `PERMISSION_DENIED` unless the caller may change [mutation]'s target record. */
  private fun checkMayChange(mutation: DataMutation) {
    val em = mutation.entity_mutation
    val sm = mutation.submission_mutation
    val existingCreator =
      when {
        em != null -> existingEntityCreator(em)
        sm != null -> existingSubmissionCreator(sm)
        else -> null
      }
    if (ownRecordsOnly && existingCreator != null && existingCreator != access.userId) {
      throw Access.denied("records other collectors created in survey $surveyId")
    }
  }

  /**
   * Below organizer, collectors change only field-created entities (or patch properties on
   * predefined entities when submitting a form), and delete only their own.
   */
  private fun existingEntityCreator(em: EntityMutation): String? {
    val stored = storedEntity(em.entity_id) ?: return null
    if (!isOrganizer) {
      if (!stored.is_field_created) {
        if (!isPropertiesOnlyPatch(em)) {
          throw Access.denied("predefined entities in survey $surveyId")
        }
        return null
      }
      val isDelete = em.upsert?.is_deleted == true
      val entCreator = stored.audit_info?.created_by.orEmpty()
      if (isDelete && entCreator.isNotBlank() && entCreator != access.userId) {
        throw Access.denied("entities created by another collector in survey $surveyId")
      }
    }
    return stored.audit_info?.created_by.orEmpty()
  }

  /**
   * True when [em] only sets or clears `properties` (such as workflow `status`, `marker-symbol`,
   * `marker-color`, and `save_to` attributes updated when a collector submits a form on a
   * predefined entity), without replacing, deleting, relabeling, or moving the entity.
   */
  private fun isPropertiesOnlyPatch(em: EntityMutation): Boolean {
    val up = em.upsert
    val del = em.delete
    if (up == null && del == null) return false
    val emptyUp = EntityRecord(properties = up?.properties.orEmpty())
    val emptyDel = EntityRecord(properties = del?.properties.orEmpty())
    return (up == null || up == emptyUp) && (del == null || del == emptyDel)
  }

  /** Below organizer, collectors change only their own submissions. */
  private fun existingSubmissionCreator(sm: SubmissionMutation): String? {
    val stored = storedSubmission(sm.submission_id) ?: return null
    if (!isOrganizer) {
      val subCreator = stored.audit_info?.created_by.orEmpty()
      if (subCreator.isNotBlank() && subCreator != access.userId) {
        throw Access.denied("submissions created by another collector in survey $surveyId")
      }
    }
    return stored.audit_info?.created_by.orEmpty()
  }

  private fun applyEntity(em: EntityMutation): DataMutationResult {
    val targetId = em.entity_id.ifBlank { em.upsert?.entity_id.orEmpty() }
    if (targetId !in initialEntities) {
      initialEntities[targetId] = tx.entity(surveyId, targetId)
    }
    val stored = storedEntity(targetId)
    val existing =
      stored
        ?: EntityRecord(
          entity_id = em.entity_id,
          dataset_id = em.dataset_id,
          survey_id = surveyId,
          revision = 0L,
        )
    val emDelete = em.delete
    val emUpsert =
      em.upsert?.let {
        if (stored == null && !isOrganizer && !it.is_deleted && !it.is_field_created) {
          it.copy(is_field_created = true)
        } else {
          it
        }
      }
    val isEntityWholeUpsert =
      emUpsert != null &&
        emUpsert.entity_id.isNotEmpty() &&
        !emUpsert.is_deleted &&
        emDelete == null
    if (isEntityWholeUpsert) {
      checkBaseVersion(em.base_revision, stored?.revision, "entity ${em.entity_id}")
      if (existing.revision == 0L) checkNewEntityQuotas(em, emUpsert)
    }
    val mutated: EntityRecord =
      when {
        emUpsert?.is_deleted == true ->
          EntityRecord(
            entity_id = existing.entity_id,
            dataset_id = existing.dataset_id,
            survey_id = surveyId,
            audit_info = existing.audit_info,
            revision = existing.revision + 1L,
            is_deleted = true,
            sync_status_key = "SYNCED",
          )
        isEntityWholeUpsert ->
          emUpsert.copy(
            entity_id = em.entity_id.ifBlank { emUpsert.entity_id },
            dataset_id = em.dataset_id.ifBlank { emUpsert.dataset_id },
            survey_id = surveyId,
            revision = existing.revision + 1L,
            sync_status_key = "SYNCED",
          )
        else ->
          DataMutations.applyEntity(existing, emDelete, emUpsert)
            .copy(revision = existing.revision + 1L, sync_status_key = "SYNCED")
      }
    val updated = withValidatedSpatialIdentity(mutated, stored)
    entities[updated.entity_id] = updated
    return DataMutationResult(
      entity = updated,
      conflict_status = patchConflictStatus(em.base_revision, existing.revision),
    )
  }

  /**
   * Recomputes `s2_cell_token` and validates or resolves `geo_id` against [entity]'s geometry and
   * any existing entities in the survey that share a level-13 primary candidate.
   */
  private fun withValidatedSpatialIdentity(
    entity: EntityRecord,
    prior: EntityRecord?,
  ): EntityRecord {
    if (entity.is_deleted) return entity
    val geometry = GeometryProtos.of(entity)
    if (geometry == null) {
      return if (entity.geo_id.isEmpty() && entity.s2_cell_token.isEmpty()) {
        entity
      } else {
        entity.copy(geo_id = "", s2_cell_token = "")
      }
    }
    val s2CellToken = AgStackGeoId.s2CellToken(geometry)
    val priorGeometry = prior?.let(GeometryProtos::of)
    val resolvedGeoId = resolveGeoId(entity, geometry, priorGeometry)
    return entity.copy(geo_id = resolvedGeoId, s2_cell_token = s2CellToken)
  }

  private fun resolveGeoId(
    entity: EntityRecord,
    geometry: Geometry,
    priorGeometry: Geometry?,
  ): String {
    val candidates = AgStackGeoId.forGeometry(geometry) ?: return ""
    if (candidates.primary != candidates.fallback) {
      val colliding =
        allEntities().values.mapNotNull { other ->
          if (other.is_deleted || other.entity_id == entity.entity_id) return@mapNotNull null
          val otherGeom = GeometryProtos.of(other) ?: return@mapNotNull null
          val otherPrimary = AgStackGeoId.computeCandidate(otherGeom)
          if (otherPrimary == candidates.primary || other.geo_id == candidates.primary) {
            other to otherGeom
          } else {
            null
          }
        }
      if (colliding.isNotEmpty()) {
        val duplicateMatch = colliding.firstOrNull { (_, otherGeom) ->
          AgStackGeoId.level20OverlapFraction(geometry, otherGeom) >=
            AgStackGeoId.DUPLICATE_OVERLAP_THRESHOLD
        }
        return if (duplicateMatch != null) {
          val (other, otherGeom) = duplicateMatch
          other.geo_id.takeIf { AgStackGeoId.matchesGeometry(it, otherGeom) } ?: candidates.primary
        } else {
          candidates.fallback
        }
      }
    }
    if (priorGeometry == geometry && AgStackGeoId.matchesGeometry(entity.geo_id, geometry)) {
      return entity.geo_id
    }
    return candidates.primary
  }

  private fun applySubmission(sm: SubmissionMutation): DataMutationResult {
    val stored = storedSubmission(sm.submission_id)
    val existing =
      stored
        ?: SubmissionRecord(
          submission_id = sm.submission_id,
          form_id = sm.form_id,
          survey_id = surveyId,
          revision = 0L,
        )
    val smDelete = sm.delete
    val smUpsert = sm.upsert
    val isSubmissionWholeUpsert =
      smUpsert != null &&
        smUpsert.submission_id.isNotEmpty() &&
        !smUpsert.is_deleted &&
        smDelete == null
    if (isSubmissionWholeUpsert) {
      checkBaseVersion(sm.base_revision, stored?.revision, "submission ${sm.submission_id}")
      if (existing.revision == 0L) checkNewSubmissionQuota(smUpsert)
    }
    val updated: SubmissionRecord =
      when {
        smUpsert?.is_deleted == true ->
          SubmissionRecord(
            submission_id = existing.submission_id,
            form_id = existing.form_id,
            survey_id = surveyId,
            entity_id = existing.entity_id,
            audit_info = existing.audit_info,
            revision = existing.revision + 1L,
            is_deleted = true,
            sync_status_key = "SYNCED",
          )
        isSubmissionWholeUpsert ->
          smUpsert.copy(
            submission_id = sm.submission_id.ifBlank { smUpsert.submission_id },
            form_id = sm.form_id.ifBlank { smUpsert.form_id },
            survey_id = surveyId,
            revision = existing.revision + 1L,
            sync_status_key = "SYNCED",
          )
        else ->
          DataMutations.applySubmission(existing, smDelete, smUpsert)
            .copy(revision = existing.revision + 1L, sync_status_key = "SYNCED")
      }.copy(server_updated_at = tx.now)
    submissions[updated.submission_id] = updated
    return DataMutationResult(
      submission = updated,
      conflict_status = patchConflictStatus(sm.base_revision, existing.revision),
    )
  }

  /** Every entity of the survey, with this request's changes. */
  private fun allEntities(): Map<String, EntityRecord> =
    tx.entities(surveyId).associateBy { it.entity_id } + entities

  /** Quotas on creating entity [upsert]: polygon vertices, and per-kind entity counts. */
  private fun checkNewEntityQuotas(em: EntityMutation, upsert: EntityRecord) {
    val survey = tx.survey(surveyId)
    val limits = tx.resolveEffectiveQuota(survey, survey?.organization_id)
    val shapeVertices = GeometryProtos.of(upsert)?.let(GeometryProtos::polygonVertexCount) ?: 0
    if (limits.max_area_vertices > 0 && shapeVertices > limits.max_area_vertices) {
      throw quotaExceeded(
        "Polygon vertex quota exceeded: entity \"${upsert.entity_id}\" has $shapeVertices " +
          "vertices, exceeding maximum of ${limits.max_area_vertices}"
      )
    }
    if (upsert.is_field_created) {
      checkFieldCreatedEntityQuota(limits)
    } else {
      checkDatasetEntityQuota(em.dataset_id.ifBlank { upsert.dataset_id }, survey, limits)
    }
  }

  /** Field-created ("ad-hoc") entities are limited per user. */
  private fun checkFieldCreatedEntityQuota(limits: QuotaLimits) {
    val userId = creator.orEmpty()
    val userAdhoc =
      allEntities().values.count {
        !it.is_deleted &&
          it.is_field_created &&
          (userId.isBlank() || it.audit_info?.created_by == userId)
      }
    if (limits.max_adhoc_entities_per_user > 0 && userAdhoc >= limits.max_adhoc_entities_per_user) {
      throw quotaExceeded(
        "Ad-hoc entity quota exceeded: maximum ad-hoc entities per user reached " +
          "(${limits.max_adhoc_entities_per_user})"
      )
    }
  }

  /** Entities of generated (sampled) datasets and of predefined datasets have separate limits. */
  private fun checkDatasetEntityQuota(datasetId: String, survey: SurveyDef?, limits: QuotaLimits) {
    val generatedDatasetIds =
      survey?.entity_datasets.orEmpty().filter { it.sampling != null }.map { it.id }.toSet()
    val all = allEntities().values.filter { !it.is_deleted && !it.is_field_created }
    if (datasetId in generatedDatasetIds) {
      val generatedCount = all.count { it.dataset_id in generatedDatasetIds }
      if (limits.max_generated_entities > 0 && generatedCount >= limits.max_generated_entities) {
        throw quotaExceeded(
          "Generated entity quota exceeded: maximum generated entities reached " +
            "(${limits.max_generated_entities})"
        )
      }
    } else {
      val predefined = all.count { it.dataset_id !in generatedDatasetIds }
      if (limits.max_predefined_entities > 0 && predefined >= limits.max_predefined_entities) {
        throw quotaExceeded(
          "Predefined entity quota exceeded: maximum predefined entities reached " +
            "(${limits.max_predefined_entities})"
        )
      }
    }
  }

  /** Submissions about one entity are limited per user. */
  private fun checkNewSubmissionQuota(upsert: SubmissionRecord) {
    if (upsert.entity_id.isBlank()) return
    val survey = tx.survey(surveyId)
    val limits = tx.resolveEffectiveQuota(survey, survey?.organization_id)
    val userId = creator ?: upsert.collector_email
    val allSubmissions = tx.submissions(surveyId).associateBy { it.submission_id } + submissions
    val countForEntityAndUser =
      allSubmissions.values.count {
        !it.is_deleted &&
          it.entity_id == upsert.entity_id &&
          (userId.isBlank() || it.audit_info?.created_by == userId || it.collector_email == userId)
      }
    if (
      limits.max_submissions_per_entity_per_user > 0 &&
        countForEntityAndUser >= limits.max_submissions_per_entity_per_user
    ) {
      throw quotaExceeded(
        "Submission quota exceeded: maximum submissions per entity per user reached " +
          "(${limits.max_submissions_per_entity_per_user})"
      )
    }
  }

  /**
   * Stamps the audit info of the entity [result] wrote, in the result and buffers: created when
   * there was no [prior] record, otherwise modified. Attributed to [creator], or unknown for the
   * development fallback caller.
   */
  private fun stampEntity(prior: EntityRecord?, result: DataMutationResult): DataMutationResult {
    val entity = result.entity ?: return result
    val by = creator.orEmpty()
    val audit = if (prior == null) stamp.created(by) else stamp.modified(prior.audit_info, by)
    val stamped = entity.copy(audit_info = audit)
    entities[stamped.entity_id] = stamped
    return result.copy(entity = stamped)
  }

  /**
   * Stamps the audit info of the submission [result] wrote, like [stampEntity]. For the development
   * fallback caller, the submission's collector email stands in for the user.
   */
  private fun stampSubmission(
    prior: SubmissionRecord?,
    result: DataMutationResult,
  ): DataMutationResult {
    val submission = result.submission ?: return result
    val by = creator ?: submission.collector_email
    val audit = if (prior == null) stamp.created(by) else stamp.modified(prior.audit_info, by)
    val stamped = submission.copy(audit_info = audit)
    submissions[stamped.submission_id] = stamped
    return result.copy(submission = stamped)
  }

  /**
   * Conflict status for a field patch based on [baseRevision] against the stored [currentRevision]:
   * patches always apply (last writer wins), but report `AUTO_MERGED` when based on an older
   * revision.
   */
  private fun patchConflictStatus(baseRevision: Long, currentRevision: Long) =
    if (baseRevision in 1L until currentRevision) {
      ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_AUTO_MERGED
    } else {
      ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_CLEAN
    }
}
