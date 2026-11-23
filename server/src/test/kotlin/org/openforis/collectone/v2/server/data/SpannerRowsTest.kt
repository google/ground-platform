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
package org.openforis.collectone.v2.server.data

import com.google.cloud.Timestamp
import com.google.cloud.spanner.Mutation
import com.google.cloud.spanner.Value
import com.squareup.wire.ProtoAdapter
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LocalizedText
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef

/**
 * The Spanner rows the store writes ([organizationRow], [entityRow], ...): what each record stores,
 * which commit timestamp it gets, and the keys of member and ACL entry rows. The Spanner store
 * tests (`./gradlew :server:spannerTest`) check the round trip through the emulator.
 */
class SpannerRowsTest {
  private val seedTime = Instant.parse("2026-01-02T03:04:05.000000006Z")

  private fun Mutation.column(name: String): Value =
    requireNotNull(asMap()[name]) { "No $name in $table: ${asMap().keys}" }

  private fun <M> Mutation.record(column: String, adapter: ProtoAdapter<M>): M =
    adapter.decode(column(column).bytes.toByteArray())

  @Test
  fun rowsWithoutATimestamp_useSpannersCommitTimestamp() {
    val row = entityRow(EntityRecord(survey_id = SURVEY, entity_id = ENTITY), commitTs = null)

    assertTrue(row.column("CommitTs").isCommitTimestamp)
  }

  @Test
  fun seedRows_keepTheSeedsCommitTimestamp() {
    val row = entityRow(EntityRecord(survey_id = SURVEY, entity_id = ENTITY), seedTime)

    assertEquals(Timestamp.ofTimeSecondsAndNanos(1767323045L, 6), row.column("CommitTs").timestamp)
  }

  @Test
  fun records_areStoredWithoutTheirCommitTimestamp() {
    val entity =
      EntityRecord(
        survey_id = SURVEY,
        entity_id = ENTITY,
        label = "Plot",
        commit_timestamp = seedTime,
      )

    val stored = entityRow(entity, null).record("Record", EntityRecord.ADAPTER)

    assertEquals(entity.copy(commit_timestamp = null), stored)
  }

  @Test
  fun deletedRecords_getADeletionTime_andLiveOnesNone() {
    val live = SubmissionRecord(survey_id = SURVEY, submission_id = SUBMISSION)

    assertTrue(submissionRow(live, null).column("DeletedAt").isNull)
    assertTrue(
      submissionRow(live.copy(is_deleted = true), null).column("DeletedAt").isCommitTimestamp
    )
    assertEquals(
      Timestamp.ofTimeSecondsAndNanos(1767323045L, 6),
      entityRow(EntityRecord(survey_id = SURVEY, entity_id = ENTITY, is_deleted = true), seedTime)
        .column("DeletedAt")
        .timestamp,
    )
  }

  @Test
  fun organizations_storeTheirMembersInTheirOwnRows() {
    val member = OrganizationMember(email = " Ana@Example.ORG ", user_id = "user-1")
    val org = Organization(organization_id = ORGANIZATION, members = listOf(member))

    val stored = organizationRow(org, null).record("Record", Organization.ADAPTER)
    val memberRow = memberRow(ORGANIZATION, member, null)

    assertEquals(emptyList(), stored.members)
    assertEquals("ana@example.org", memberRow.column("EmailNormalized").string)
    assertEquals(member, memberRow.record("Record", OrganizationMember.ADAPTER))
  }

  @Test
  fun entriesWithoutAnEmail_areKeyedByTheirUserId() {
    assertEquals("ana@example.org", emailKey(" Ana@Example.org", "user-1"))
    assertEquals("user-1", emailKey("  ", " user-1 "))
    assertEquals(
      "user-2",
      aclEntryRow(SURVEY, AclEntry(user_id = "user-2"), null).column("EmailNormalized").string,
    )
  }

  @Test
  fun surveyRows_carryTheirAcl_withoutEntries_onlyWhenGiven() {
    val survey = SurveyDef(survey_id = SURVEY, commit_timestamp = seedTime)
    val acl =
      SurveyAcl(
        survey_id = SURVEY,
        entries = listOf(AclEntry(email = "a@b.c")),
        commit_timestamp = seedTime,
      )

    val withoutAcl = surveyRow(survey, null)
    val withAcl = surveyRow(survey, null, acl)

    assertNull(withoutAcl.asMap()["AclRules"])
    assertEquals(emptyList(), withAcl.record("AclRules", SurveyAcl.ADAPTER).entries)
    // A write (no explicit time) stamps the ACL with the commit timestamp too, ignoring the ACL's.
    assertTrue(withAcl.column("AclCommitTs").isCommitTimestamp)
    assertEquals(
      Timestamp.ofTimeSecondsAndNanos(1767323045L, 6),
      surveyRow(survey, seedTime, acl).column("AclCommitTs").timestamp,
    )
  }

  @Test
  fun aclUpdates_onlyTouchTheAclColumns() {
    val update = aclUpdate(SurveyAcl(survey_id = SURVEY))

    assertEquals(Mutation.Op.UPDATE, update.operation)
    assertEquals(setOf("SurveyId", "AclRules", "AclCommitTs"), update.asMap().keys)
  }

  @Test
  fun revisionRows_startAtRevisionOne() {
    assertEquals(
      1L,
      revisionRow(SurveyDef(survey_id = SURVEY, saved_revision_number = 0L), null)
        .column("RevisionNumber")
        .int64,
    )
    assertEquals(
      7L,
      revisionRow(SurveyDef(survey_id = SURVEY, saved_revision_number = 7L), null)
        .column("RevisionNumber")
        .int64,
    )
  }

  @Test
  fun purgedSurveys_keepATombstone_withoutTheirAcl() {
    val row = purgedSurveyRow(SurveyDef(survey_id = SURVEY, commit_timestamp = seedTime))

    assertTrue(row.column("AclRules").isNull)
    assertTrue(row.column("AclCommitTs").isNull)
    assertTrue(row.column("PurgedAt").isCommitTimestamp)
    assertNull(row.record("SavedDef", SurveyDef.ADAPTER).commit_timestamp)
  }

  @Test
  fun conceptRows_keepTheBundleOrder_andIndexIdsAndLabels() {
    val library =
      OrganizationLibraryRecord(
        organization_id = ORGANIZATION,
        bundle =
          LibraryBundle(
            concepts =
              listOf(
                ConceptDef(id = "tree_height"),
                ConceptDef(
                  id = "species",
                  label = LocalizedText(values = mapOf("en" to "Species", "fr" to "Espèce")),
                ),
              )
          ),
      )

    val rows = conceptRows(library)

    assertEquals(listOf(0L, 1L), rows.map { it.column("Position").int64 })
    assertEquals("tree_height", rows[0].column("SearchText").string)
    assertEquals("species Species Espèce", rows[1].column("SearchText").string)
  }

  @Test
  fun usersWithoutAnEmailOrProfile_storeNulls() {
    val row =
      userRow(
        UserAccount(
          userId = "auth-user",
          email = "",
          emailVerified = false,
          profile = null,
          accessEpoch = 3L,
          createTime = seedTime,
        )
      )

    assertEquals("auth-user", row.column("UserId").string)
    assertTrue(row.column("EmailNormalized").isNull)
    assertTrue(row.column("Profile").isNull)
    assertEquals(3L, row.column("AccessEpoch").int64)
    assertEquals(
      Timestamp.ofTimeSecondsAndNanos(1767323045L, 6),
      row.column("CreateTime").timestamp,
    )
  }

  @Test
  fun recordIds_mustBeUuids() {
    assertNull(uuidOrNull("plot-1"))
    val error =
      assertFailsWith<IllegalArgumentException> {
        entityRow(EntityRecord(survey_id = SURVEY, entity_id = "plot-1"), null)
      }
    assertEquals("Record IDs must be UUIDs, got \"plot-1\"", error.message)
  }

  private companion object {
    const val SURVEY = "00000000-0000-4000-8000-000000000001"
    const val ENTITY = "00000000-0000-4000-8000-000000000002"
    const val SUBMISSION = "00000000-0000-4000-8000-000000000003"
    const val ORGANIZATION = "00000000-0000-4000-8000-000000000004"
  }
}
