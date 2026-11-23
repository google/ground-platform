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
package org.openforis.collectone.v2.core.proto

import com.squareup.wire.Message
import kotlin.test.Test
import kotlin.test.fail
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.library.OrganizationLibrarySettings
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import org.openforis.collectone.v2.core.forms.serialization.ProtoJsonSerializer
import org.openforis.collectone.v2.core.forms.serialization.TextProtoSerializer
import org.openforis.collectone.v2.core.library.LibraryTextProtoSerializer
import org.openforis.collectone.v2.core.mutation.DataMutations
import org.openforis.collectone.v2.core.mutation.LibraryMutations
import org.openforis.collectone.v2.core.mutation.OrganizationMutations
import org.openforis.collectone.v2.core.mutation.SurveyMutations

/**
 * Guards the codecs and mutation merges against silently dropping proto fields.
 *
 * Each test builds [FullyPopulatedProtos] fixtures by reflection, so a field added to the schema is
 * covered automatically: it fails here until the codec or merge handles it, or until it is added to
 * one of the documented exclusion sets below with the reason. The exclusion sets are shrink-only:
 * an entry that no longer applies fails the test too, so fixed gaps can't regress unnoticed.
 *
 * Fields are named `DeclaringMessage.field`, wherever the message is nested.
 */
class ProtoFieldCoverageTest {

  @Test
  fun formDef_survivesProtoJson() =
    assertRoundTrips(FormDef::class.java, FORM_CODEC_GAPS + JSON_RECORD_NODE_KEYS) {
      ProtoJsonSerializer.deserializeFormDef(ProtoJsonSerializer.serializeFormDef(it))
    }

  @Test
  fun formDef_survivesProtoJsonWithProtoFieldNames() =
    assertRoundTrips(FormDef::class.java, FORM_CODEC_GAPS + JSON_RECORD_NODE_KEYS) {
      ProtoJsonSerializer.deserializeFormDef(
        ProtoJsonSerializer.serializeFormDef(it, preserveProtoFieldNames = true)
      )
    }

  @Test
  fun recordInstance_survivesProtoJson() =
    assertRoundTrips(RecordInstance::class.java, JSON_RECORD_NODE_KEYS) {
      ProtoJsonSerializer.deserializeRecordInstance(ProtoJsonSerializer.serializeRecordInstance(it))
    }

  @Test
  fun formDef_survivesTextProto() =
    assertRoundTrips(FormDef::class.java, FORM_CODEC_GAPS) {
      TextProtoSerializer.deserializeFormDef(TextProtoSerializer.serializeFormDef(it))
    }

  @Test
  fun recordInstance_survivesTextProto() =
    assertRoundTrips(RecordInstance::class.java, emptySet()) {
      TextProtoSerializer.deserializeRecordInstance(TextProtoSerializer.serializeRecordInstance(it))
    }

  @Test
  fun libraryBundle_survivesLibraryTextProto() =
    assertRoundTrips(
      LibraryBundle::class.java,
      FORM_CODEC_GAPS + LIBRARY_CODEC_GAPS,
    ) {
      LibraryTextProtoSerializer.deserializeBundle(LibraryTextProtoSerializer.serializeBundle(it))
    }

  @Test
  fun librarySettings_survivesLibraryTextProto() =
    assertRoundTrips(OrganizationLibrarySettings::class.java, emptySet()) {
      LibraryTextProtoSerializer.deserializeSettings(
        LibraryTextProtoSerializer.serializeSettings(it)
      )
    }

  @Test
  fun surveyUpsert_setsEveryField() =
    assertRoundTrips(SurveyDef::class.java, SERVER_STAMPED) {
      SurveyMutations.merge(SurveyDef(), it)
    }

  @Test
  fun surveyAclUpsert_setsEveryField() =
    assertRoundTrips(SurveyAcl::class.java, SERVER_STAMPED) {
      SurveyMutations.mergeAcl(SurveyAcl(), it)
    }

  @Test
  fun organizationUpsert_setsEveryField() =
    assertRoundTrips(Organization::class.java, SERVER_STAMPED) {
      OrganizationMutations.merge(Organization(), it)
    }

  @Test
  fun libraryUpsert_setsEveryField() =
    assertRoundTrips(OrganizationLibraryRecord::class.java, SERVER_STAMPED) {
      LibraryMutations.merge(OrganizationLibraryRecord(), it)
    }

  @Test
  fun entityUpsert_setsEveryField() =
    assertRoundTrips(EntityRecord::class.java, SERVER_STAMPED + SERVER_DERIVED + SUPERSEDED) {
      DataMutations.mergeEntity(EntityRecord(), it)
    }

  @Test
  fun submissionUpsert_setsEveryField() =
    assertRoundTrips(SubmissionRecord::class.java, SERVER_STAMPED) {
      DataMutations.mergeSubmission(SubmissionRecord(), it)
    }

  @Test
  fun surveyDelete_clearsEveryClearableField() =
    assertClears(SurveyDef::class.java, NOT_CLEARED_BY_DELETE, SurveyMutations::applyDelete)

  @Test
  fun surveyAclDelete_clearsEveryClearableField() =
    assertClears(SurveyAcl::class.java, NOT_CLEARED_BY_DELETE, SurveyMutations::applyAclDelete)

  @Test
  fun organizationDelete_clearsEveryClearableField() =
    assertClears(
      Organization::class.java,
      NOT_CLEARED_BY_DELETE,
      OrganizationMutations::applyDelete,
    )

  @Test
  fun libraryDelete_clearsEveryClearableField() =
    assertClears(
      OrganizationLibraryRecord::class.java,
      NOT_CLEARED_BY_DELETE,
      LibraryMutations::applyDelete,
    )

  @Test
  fun entityDelete_clearsEveryClearableField() =
    assertClears(EntityRecord::class.java, NOT_CLEARED_BY_DELETE, DataMutations::applyEntityDelete)

  @Test
  fun submissionDelete_clearsEveryClearableField() =
    assertClears(
      SubmissionRecord::class.java,
      NOT_CLEARED_BY_DELETE,
      DataMutations::applySubmissionDelete,
    )

  /** Every fixture variant of [type] survives [transform] except for the [excluded] fields. */
  private fun <M : Message<M, *>> assertRoundTrips(
    type: Class<M>,
    excluded: Set<String>,
    transform: (M) -> M,
  ) {
    val dropped = sortedSetOf<String>()
    for (variant in 0 until FullyPopulatedProtos.variantCount(type)) {
      val original = FullyPopulatedProtos.populate(type, variant)
      dropped += ProtoDiff.diff(original, transform(original))
    }
    assertMatchesExclusions(type, "dropped", dropped, excluded)
  }

  /**
   * Deleting a fully populated message with itself as the delete mask leaves only the [excluded]
   * fields set.
   */
  private fun <M : Message<M, *>> assertClears(
    type: Class<M>,
    excluded: Set<String>,
    applyDelete: (M, M) -> M,
  ) {
    val remaining = sortedSetOf<String>()
    for (variant in 0 until FullyPopulatedProtos.variantCount(type)) {
      val full = FullyPopulatedProtos.populate(type, variant)
      remaining += ProtoDiff.setFields(applyDelete(full, full))
    }
    assertMatchesExclusions(type, "left set", remaining, excluded)
  }

  /**
   * Fails on fields in [actual] that aren't [excluded], and on stale exclusions: entries for a
   * message reachable from [root] that no longer show up. Entries for unrelated messages are
   * ignored, so one set (e.g. [SERVER_STAMPED]) can serve several checks.
   */
  private fun assertMatchesExclusions(
    root: Class<*>,
    what: String,
    actual: Set<String>,
    excluded: Set<String>,
  ) {
    val reachable = mutableSetOf<String>()
    FullyPopulatedProtos.walkMessageTypes(root) { reachable += it.simpleName }
    val unexpected = actual - excluded
    val stale = excluded.filter { it !in actual && it.substringBefore('.') in reachable }
    if (unexpected.isEmpty() && stale.isEmpty()) return
    fail(
      buildString {
        if (unexpected.isNotEmpty()) {
          appendLine("${root.simpleName}: fields $what that are not documented exclusions:")
          unexpected.forEach { appendLine("  \"$it\",") }
        }
        if (stale.isNotEmpty()) {
          appendLine("${root.simpleName}: documented exclusions that no longer apply:")
          stale.forEach { appendLine("  \"$it\",") }
        }
      }
    )
  }

  private companion object {
    /**
     * `google.protobuf.Any` extension payloads. Writing them needs a type registry to expand the
     * packed message (ProtoJSON `@type`, textproto `[type.googleapis.com/…] { … }`), which the
     * FormDef codecs don't have, so they are dropped (see `FormCodecRules`).
     */
    val FORM_CODEC_GAPS =
      setOf(
        "ControlDef.custom_extensions",
        "FieldBinding.custom_extensions",
        "FormDef.custom_extensions",
        "ModelDef.custom_extensions",
      )

    /**
     * ProtoJSON writes [openforis.collectone.v1.forms.RecordNode] as a flat object keyed by field
     * name (`{"species": "Baobab"}`), so its own `id` and `sort_order` would collide with record
     * fields of the same name. Intentional: repeat instance keys don't survive ProtoJSON.
     */
    val JSON_RECORD_NODE_KEYS = setOf("RecordNode.id", "RecordNode.sort_order")

    /** Library fields `LibraryTextProtoSerializer` doesn't write (see its KDoc). */
    val LIBRARY_CODEC_GAPS =
      setOf(
        "ConceptDef.audit_info",
        "FormTemplateDef.audit_info",
        "FormTemplateDef.default_dataset",
        "PurposePackDef.audit_info",
      )

    /**
     * Version and commit-time bookkeeping the server stamps on every write. Mutations never carry
     * it (or the server ignores what they carry), so merges ignore it.
     */
    val SERVER_STAMPED =
      setOf(
        "EntityRecord.commit_timestamp",
        "EntityRecord.revision",
        "Organization.commit_timestamp",
        "Organization.version",
        "OrganizationLibraryRecord.commit_timestamp",
        "OrganizationLibraryRecord.version",
        "SubmissionRecord.commit_timestamp",
        "SubmissionRecord.revision",
        "SubmissionRecord.server_updated_at",
        "SurveyAcl.commit_timestamp",
        "SurveyAcl.record_version",
        "SurveyDef.commit_timestamp",
        "SurveyDef.record_version",
      )

    /**
     * Legacy fields an upsert of their replacement in the same message clears: a canonical
     * `EntityRecord.geometry` replaces the legacy `point`, `trace`, or `shape`.
     */
    val SUPERSEDED = setOf("EntityRecord.point", "EntityRecord.shape", "EntityRecord.trace")

    /** Computed on the server from reviews and conflicting edits, never sent in mutations. */
    val SERVER_DERIVED = setOf("EntityRecord.quality_rollup", "EntityRecord.unresolved_conflicts")

    /** Keys that name the record a mutation applies to; a delete mask can't clear them. */
    val IDENTITY_KEYS =
      setOf(
        "EntityRecord.dataset_id",
        "EntityRecord.entity_id",
        "EntityRecord.survey_id",
        "Organization.organization_id",
        "OrganizationLibraryRecord.organization_id",
        "SubmissionRecord.form_id",
        "SubmissionRecord.submission_id",
        "SubmissionRecord.survey_id",
        "SurveyAcl.survey_id",
        "SurveyDef.survey_id",
      )

    /**
     * Nested delete masks, unlike `merge`: a populated nested message or keyed list item deletes
     * the fields set inside it and keeps the item (and its key); only an empty message or a bare
     * key removes it outright. A fully populated mask therefore leaves these fields set.
     */
    val NESTED_DELETE_MASKS =
      setOf(
        "Organization.imagery_sources",
        "Organization.members",
        "Organization.quota_limits",
        "OrganizationLibraryRecord.bundle",
        "OrganizationLibraryRecord.settings",
        "SubmissionRecord.record",
        "SurveyAcl.entries",
        "SurveyDef.entity_datasets",
        "SurveyDef.form_concept_links",
        "SurveyDef.form_launch_configs",
        "SurveyDef.forms",
        "SurveyDef.map_config",
        "SurveyDef.quota_limits",
        "SurveyDef.translations",
      )

    /**
     * Fields `merge` replaces but `applyDelete` has no way to clear, and nothing needs to:
     * - `audit_info` and `saved_revision_number` are server-owned. The server stamps them on every
     *   write and ignores client values, and clients don't send them, so a delete mask would never
     *   carry them. `merge` still copies them, which is harmless: the server overwrites them.
     * - `custom_extensions` isn't supported by the client codecs yet: `Any` payloads need a type
     *   registry, so ProtoJSON and textproto drop them (see [FORM_CODEC_GAPS]).
     */
    val UPSERT_ONLY =
      setOf(
        "EntityRecord.audit_info",
        "SubmissionRecord.audit_info",
        "SurveyDef.audit_info",
        "SurveyDef.custom_extensions",
        "SurveyDef.saved_revision_number",
      )

    val NOT_CLEARED_BY_DELETE =
      SERVER_STAMPED + SERVER_DERIVED + IDENTITY_KEYS + NESTED_DELETE_MASKS + UPSERT_ONLY
  }
}
