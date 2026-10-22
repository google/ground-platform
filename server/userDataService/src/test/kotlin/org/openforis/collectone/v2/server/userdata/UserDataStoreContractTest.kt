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
package org.openforis.collectone.v2.server.userdata

import com.squareup.wire.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LocalizedText
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.CachedProfile
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.SharingPolicy
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef

/**
 * Behavior every [UserDataStore] must have (see its KDoc). Subclasses provide an empty store.
 *
 * Survey-scoped records are written under an existing survey, as Spanner's interleaved tables
 * require.
 */
abstract class UserDataStoreContractTest {
  abstract fun newStore(): UserDataStore

  private val store by lazy { newStore() }

  private fun createSurveys(vararg surveyIds: String) {
    store.write { tx -> surveyIds.forEach { tx.putSurvey(SurveyDef(survey_id = it)) } }
  }

  @Test
  fun write_stampsEveryRecordWithOneCommitTimestamp_afterTheWatermark() {
    val before = store.watermark()

    val committed = store.write { tx ->
      tx.putOrganization(Organization(organization_id = ORG, name = "Org"))
      tx.putSurvey(SurveyDef(survey_id = SURVEY, title = "Survey"))
    }

    assertTrue(committed.commitTimestamp > before)
    assertTrue(store.watermark() >= committed.commitTimestamp)
    store.read { r ->
      assertEquals(committed.commitTimestamp, r.organization(ORG)?.commit_timestamp)
      assertEquals(committed.commitTimestamp, r.survey(SURVEY)?.commit_timestamp)
    }
  }

  @Test
  fun snapshots_includeEverythingCommittedBeforeThem() {
    val committed = store.write { it.putSurvey(SurveyDef(survey_id = SURVEY)) }

    store.read { r ->
      assertTrue(r.snapshotTimestamp >= committed.commitTimestamp)
      assertEquals(committed.commitTimestamp, r.survey(SURVEY)?.commit_timestamp)
    }
    val later = store.write { it.putSurvey(SurveyDef(survey_id = SURVEY, title = "Later")) }
    store.read { r -> assertTrue(later.commitTimestamp <= r.snapshotTimestamp) }
  }

  @Test
  fun laterWrites_commitStrictlyLater() {
    val first = store.write { it.putSurvey(SurveyDef(survey_id = SURVEY)) }
    val second = store.write { it.putSurvey(SurveyDef(survey_id = SURVEY, title = "Again")) }

    assertTrue(second.commitTimestamp > first.commitTimestamp)
  }

  @Test
  fun write_thatThrows_commitsNothing() {
    assertFailsWith<IllegalStateException> {
      store.write { tx ->
        tx.putSurvey(SurveyDef(survey_id = SURVEY))
        error("validation failed")
      }
    }

    assertNull(store.read { it.survey(SURVEY) })
  }

  @Test
  fun readsInsideAWrite_seeDataAsOfTheTransactionStart() {
    store.write { it.putSurvey(SurveyDef(survey_id = SURVEY, title = "Old")) }

    val seen = store.write { tx ->
      tx.putSurvey(SurveyDef(survey_id = SURVEY, title = "New"))
      tx.survey(SURVEY)?.title
    }

    assertEquals("Old", seen.value)
    assertEquals("New", store.read { it.survey(SURVEY)?.title })
  }

  @Test
  fun workingCopiesAndPlotLocks_canBeRemoved() {
    createSurveys(SURVEY)
    store.write { tx ->
      tx.putWorkingCopy(SURVEY, SurveyDef(survey_id = SURVEY, title = "Draft"))
      tx.putPlotLock(SURVEY, ENTITY, openforis.collectone.v1.data.PlotLock(entity_id = ENTITY))
    }
    store.write { tx ->
      tx.putWorkingCopy(SURVEY, null)
      tx.putPlotLock(SURVEY, ENTITY, null)
    }

    store.read { r ->
      assertNull(r.workingCopy(SURVEY))
      assertNull(r.plotLock(SURVEY, ENTITY))
    }
  }

  @Test
  fun entities_arePerSurvey() {
    createSurveys(SURVEY, OTHER_SURVEY)
    store.write { tx ->
      tx.putEntity(EntityRecord(survey_id = SURVEY, entity_id = ENTITY, label = "A"))
      tx.putEntity(EntityRecord(survey_id = OTHER_SURVEY, entity_id = ENTITY, label = "B"))
    }

    store.read { r ->
      assertEquals("A", r.entity(SURVEY, ENTITY)?.label)
      assertEquals(listOf("B"), r.entities(OTHER_SURVEY).map { it.label })
    }
  }

  @Test
  fun entitiesChangedAfter_pagesInCommitOrder_fromAPosition() {
    createSurveys(SURVEY)
    val first = store.write { tx ->
      tx.putEntity(EntityRecord(survey_id = SURVEY, entity_id = ENTITY_B))
      tx.putEntity(EntityRecord(survey_id = SURVEY, entity_id = ENTITY))
    }
    val second = store.write {
      it.putEntity(EntityRecord(survey_id = SURVEY, entity_id = ENTITY_C))
    }
    val start = SyncPosition(0L, 0, null)

    store.read { r ->
      // Same commit timestamp: ordered by ID.
      assertEquals(
        listOf(ENTITY, ENTITY_B),
        r.entitiesChangedAfter(SURVEY, start, limit = 2).map { it.entity_id },
      )
      val afterFirst = SyncPosition.of(first.commitTimestamp, ENTITY)
      assertEquals(
        listOf(ENTITY_B, ENTITY_C),
        r.entitiesChangedAfter(SURVEY, afterFirst, limit = 10).map { it.entity_id },
      )
      assertEquals(
        listOf(ENTITY_C),
        r.entitiesChangedAfter(SURVEY, SyncPosition.after(first.commitTimestamp), limit = 10).map {
          it.entity_id
        },
      )
      assertTrue(
        r.entitiesChangedAfter(SURVEY, SyncPosition.after(second.commitTimestamp), 10).isEmpty()
      )
      assertTrue(r.entitiesChangedAfter(OTHER_SURVEY, start, 10).isEmpty())
    }
  }

  @Test
  fun entitiesAndSubmissions_canBeNarrowed() {
    createSurveys(SURVEY)
    store.write { tx ->
      tx.putEntity(EntityRecord(survey_id = SURVEY, entity_id = ENTITY, dataset_id = "plots"))
      tx.putEntity(
        EntityRecord(
          survey_id = SURVEY,
          entity_id = ENTITY_B,
          dataset_id = "plots",
          is_deleted = true,
        )
      )
      tx.putEntity(EntityRecord(survey_id = SURVEY, entity_id = ENTITY_C, dataset_id = "wells"))
      tx.putSubmission(SubmissionRecord(survey_id = SURVEY, submission_id = ENTITY, form_id = "a"))
      tx.putSubmission(
        SubmissionRecord(
          survey_id = SURVEY,
          submission_id = ENTITY_B,
          form_id = "b",
          is_deleted = true,
        )
      )
    }

    store.read { r ->
      assertEquals(3, r.entities(SURVEY).size)
      assertEquals(
        setOf(ENTITY, ENTITY_B),
        r.entities(SURVEY, "plots").map { it.entity_id }.toSet(),
      )
      assertEquals(
        listOf(ENTITY),
        r.entities(SURVEY, "plots", includeDeleted = false).map { it.entity_id },
      )
      assertEquals(listOf(ENTITY_B), r.submissions(SURVEY, "b").map { it.submission_id })
      assertEquals(
        listOf(ENTITY),
        r.submissions(SURVEY, includeDeleted = false).map { it.submission_id },
      )
      assertEquals(
        listOf(ENTITY, ENTITY_B),
        r.submissionsChangedAfter(SURVEY, SyncPosition(0L, 0, null), 10).map { it.submission_id },
      )
    }
  }

  @Test
  fun organizationMembers_andAclEntries_canBeUpdatedAndRemoved() {
    val org =
      Organization(
        organization_id = ORG,
        members =
          listOf(
            OrganizationMember(email = "alice@example.org"),
            OrganizationMember(email = "bob@example.org"),
          ),
      )
    val acl =
      SurveyAcl(
        survey_id = SURVEY,
        entries =
          listOf(AclEntry(email = "alice@example.org"), AclEntry(email = "bob@example.org")),
      )
    store.write { tx ->
      tx.putOrganization(org)
      tx.putSurvey(SurveyDef(survey_id = SURVEY))
      tx.putAcl(acl)
    }
    store.write { tx ->
      tx.putOrganization(org.copy(members = listOf(OrganizationMember(email = "bob@example.org"))))
      tx.putAcl(acl.copy(entries = listOf(AclEntry(email = "alice@example.org"))))
    }

    store.read { r ->
      assertEquals(listOf("bob@example.org"), r.organization(ORG)?.members?.map { it.email })
      assertEquals(listOf("bob@example.org"), r.organizations().single().members.map { it.email })
      assertEquals(listOf("alice@example.org"), r.acl(SURVEY)?.entries?.map { it.email })
    }
  }

  @Test
  fun searchOrganizations_returnsMatches_amongListedLiveOrganizations() {
    store.write { tx ->
      tx.putOrganization(Organization(organization_id = ORG, name = "Kenya Forest", listed = true))
      tx.putOrganization(
        Organization(organization_id = OTHER_ORG, name = "Kenya Hidden", listed = false)
      )
    }

    store.read { r ->
      assertEquals(listOf(ORG), r.searchOrganizations("FOREST").map { it.organization_id })
      assertEquals(listOf(ORG), r.searchOrganizations("").map { it.organization_id })
    }
  }

  @Test
  fun searchPublicSurveys_returnsMatches_amongPublicSurveys() {
    store.write { tx ->
      tx.putSurvey(SurveyDef(survey_id = SURVEY, title = "Coffee plots"))
      tx.putAcl(SurveyAcl(survey_id = SURVEY, sharing_policy = SharingPolicy.PUBLIC))
      tx.putSurvey(SurveyDef(survey_id = OTHER_SURVEY, title = "Coffee secrets"))
    }

    store.read { r ->
      assertEquals(listOf(SURVEY), r.searchPublicSurveys("offee").map { it.survey_id })
    }
  }

  @Test
  fun searchConcepts_resolvesOverridesByConceptId() {
    fun concept(id: String, label: String) =
      ConceptDef(id = id, label = LocalizedText(values = mapOf("en" to label)))
    store.write { tx ->
      tx.putOrganization(Organization(organization_id = GLOBAL_ORG))
      tx.putOrganization(Organization(organization_id = ORG))
      tx.putLibrary(
        OrganizationLibraryRecord(
          organization_id = GLOBAL_ORG,
          bundle =
            LibraryBundle(concepts = listOf(concept("a", "Tree height"), concept("b", "Age"))),
        )
      )
      tx.putLibrary(
        OrganizationLibraryRecord(
          organization_id = ORG,
          bundle = LibraryBundle(concepts = listOf(concept("c", "Canopy"), concept("a", "Height"))),
        )
      )
    }

    store.read { r ->
      val all = r.searchConcepts(listOf(GLOBAL_ORG, ORG), "")
      assertEquals(listOf("a", "b", "c"), all.map { it.id })
      assertEquals("Height", all.first().label?.values?.get("en"))
      // The global label matches, but the organization's override replaces it.
      val tree = r.searchConcepts(listOf(GLOBAL_ORG, ORG), "tree")
      assertEquals("Height", tree.single { it.id == "a" }.label?.values?.get("en"))
      assertEquals(listOf("a", "b"), r.searchConcepts(listOf(GLOBAL_ORG), "").map { it.id })
    }
  }

  @Test
  fun users_canBeFoundByIdAndEmail() {
    val ana =
      UserAccount(
        userId = "uid-ana",
        email = "ana@example.org",
        emailVerified = true,
        profile = CachedProfile(display_name = "Ana"),
        accessEpoch = 3L,
        createTime = Instant.ofEpochSecond(1_700_000_000L, 0),
      )
    store.write { tx -> tx.putUser(ana) }
    store.write { tx -> tx.putUser(ana.copy(accessEpoch = 4L)) }

    store.read { r ->
      assertEquals(ana.copy(accessEpoch = 4L), r.user("uid-ana"))
      assertEquals(listOf("uid-ana"), r.usersByEmail("ana@example.org").map { it.userId })
      assertNull(r.user("uid-nobody"))
    }
  }

  @Test
  fun invitesCanBeFoundByEmail() {
    store.write { tx ->
      tx.putOrganization(
        Organization(
          organization_id = ORG,
          members = listOf(OrganizationMember(email = "Ana@Example.org")),
        )
      )
      tx.putSurvey(SurveyDef(survey_id = SURVEY))
      tx.putAcl(
        SurveyAcl(survey_id = SURVEY, entries = listOf(AclEntry(email = "ana@example.org")))
      )
    }

    store.read { r ->
      assertEquals(listOf(SURVEY), r.surveyIdsWithAclEmail("ana@example.org"))
      assertEquals(listOf(ORG), r.organizationIdsWithMemberEmail("ana@example.org"))
      assertTrue(r.surveyIdsWithAclEmail("bob@example.org").isEmpty())
    }
  }

  @Test
  fun surveyListCandidates_areFoundByOwnerAclUserMembershipAndOrganization() {
    store.write { tx ->
      tx.putOrganization(
        Organization(
          organization_id = ORG,
          members = listOf(OrganizationMember(email = "ana@example.org", user_id = "uid-ana")),
        )
      )
      tx.putSurvey(SurveyDef(survey_id = SURVEY, owner_id = "uid-ana"))
      tx.putSurvey(SurveyDef(survey_id = OTHER_SURVEY, organization_id = ORG))
      tx.putAcl(
        SurveyAcl(
          survey_id = OTHER_SURVEY,
          owner_id = "Bob@Example.org",
          entries = listOf(AclEntry(email = "ana@example.org", user_id = "uid-ana")),
        )
      )
    }

    store.read { r ->
      assertEquals(listOf(SURVEY), r.surveysOwnedBy("uid-ana", "").map { it.survey_id })
      // The ACL's owner email counts as ownership, compared in lowercase.
      assertEquals(
        listOf(OTHER_SURVEY),
        r.surveysOwnedBy("", "bob@example.org").map { it.survey_id },
      )
      assertTrue(r.surveysOwnedBy("", "").isEmpty())
      assertEquals(listOf(OTHER_SURVEY), r.surveyIdsWithAclUser("uid-ana"))
      assertEquals(listOf(ORG), r.organizationIdsWithMemberUser("uid-ana"))
      assertTrue(r.organizationIdsWithMemberUser("uid-bob").isEmpty())
      assertEquals(listOf(OTHER_SURVEY), r.surveysInOrganization(ORG).map { it.survey_id })
      assertTrue(r.surveysInOrganization(OTHER_ORG).isEmpty())
    }
  }

  @Test
  fun recordApplied_onlyRaisesWatermarks() {
    store.write { it.recordApplied(SCOPE, CLIENT, clientSeq = 5L) }
    store.write { it.recordApplied(SCOPE, CLIENT, clientSeq = 3L) }

    store.read { r ->
      val watermark = r.appliedWatermarks(CLIENT).single()
      assertEquals(SCOPE, watermark.scope)
      assertEquals(5L, watermark.client_seq)
      assertTrue(r.appliedWatermarks("someone-else").isEmpty())
    }
  }

  @Test
  fun reset_startsANewDataEpoch() {
    val epoch = store.dataEpoch
    store.write { it.putSurvey(SurveyDef(survey_id = SURVEY)) }

    store.reset()

    assertNotEquals(epoch, store.dataEpoch)
    assertNull(store.read { it.survey(SURVEY) })
  }

  companion object {
    const val ORG = "00000000-0000-4000-8000-0000000000a1"
    const val OTHER_ORG = "00000000-0000-4000-8000-0000000000a2"
    const val GLOBAL_ORG = "00000000-0000-4000-8000-000000000001"
    const val SURVEY = "00000000-0000-4000-8000-0000000000b1"
    const val OTHER_SURVEY = "00000000-0000-4000-8000-0000000000b2"
    const val ENTITY = "00000000-0000-4000-8000-0000000000c1"
    const val ENTITY_B = "00000000-0000-4000-8000-0000000000c2"
    const val ENTITY_C = "00000000-0000-4000-8000-0000000000c3"
    const val CLIENT = "00000000-0000-4000-8000-0000000000d1"
    const val SCOPE = "surveys/$SURVEY/data"
  }
}

class InMemoryUserDataStoreTest : UserDataStoreContractTest() {
  override fun newStore(): UserDataStore = InMemoryUserDataStore()
}
