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
package org.openforis.collectone.v2.server.userdata

import groundplatform.v2.data.ConflictResolutionStatus
import groundplatform.v2.data.DataMutation
import groundplatform.v2.data.EntityMutation
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.GetAppliedMutationsRequest
import groundplatform.v2.data.GetEntityRequest
import groundplatform.v2.data.MutateDataRequest
import groundplatform.v2.library.LibraryMutation
import groundplatform.v2.library.MutateLibraryRequest
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.library.OrganizationLibrarySettings
import groundplatform.v2.survey.GetOrganizationRequest
import groundplatform.v2.survey.GetSurveyRequest
import groundplatform.v2.survey.MutateOrganizationRequest
import groundplatform.v2.survey.MutateSurveyRequest
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationMutation
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyMutation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Idempotency and conflict semantics of the `Mutate*` RPCs that the client outbox relies on: each
 * `client_seq` high-water marks per scope (at or below one is a replay), last-writer-wins field
 * patches, `base_version` guards on whole-record replacements, and atomic requests.
 */
open class MutationIdempotencyTest {
  /** A backend loaded with the bundled sample seed; overridden to test other stores. */
  protected open fun newBackend(): UserDataBackend = sampleBackend()

  private val backend by lazy { newBackend() }

  private fun entityTitle(id: String, value: String, seq: Long, baseRevision: Long = 0L) =
    DataMutation(
      mutation_id = "m-$seq",
      client_id = CLIENT,
      client_seq = seq,
      entity_mutation =
        EntityMutation(
          entity_id = id,
          dataset_id = DATASET,
          base_revision = baseRevision,
          upsert = EntityRecord(label = value),
        ),
    )

  private fun createEntity(id: String, seq: Long) =
    DataMutation(
      mutation_id = "m-$seq",
      client_id = CLIENT,
      client_seq = seq,
      entity_mutation =
        EntityMutation(
          entity_id = id,
          dataset_id = DATASET,
          upsert = EntityRecord(entity_id = id, dataset_id = DATASET, label = "Created"),
        ),
    )

  /** The stored entity, or `null` if it was never written (`GetEntity` returns a placeholder). */
  private suspend fun entity(id: String): EntityRecord? =
    backend.GetEntity(GetEntityRequest(survey_id = KENYA, entity_id = id)).entity?.takeIf {
      it.revision > 0L
    }

  @Test
  fun replayedDataMutation_isSkipped_andDoesNotOverwriteNewerWrites() = runBlocking {
    backend.MutateData(MutateDataRequest(KENYA, listOf(createEntity(ENTITY, seq = 1))))
    val first = backend.MutateData(MutateDataRequest(KENYA, listOf(entityTitle(ENTITY, "A", 2))))
    // Another writer changes the label afterwards.
    backend.MutateData(
      MutateDataRequest(
        KENYA,
        listOf(entityTitle(ENTITY, "B", seq = 1).copy(mutation_id = "other", client_id = "other")),
      )
    )

    // The first client retries after a lost response.
    val replay = backend.MutateData(MutateDataRequest(KENYA, listOf(entityTitle(ENTITY, "A", 2))))

    assertTrue(replay.results.single().duplicate)
    assertEquals("B", replay.results.single().entity?.label)
    assertEquals("B", entity(ENTITY)?.label)
    assertFalse(first.results.single().duplicate)
  }

  @Test
  fun clientSeqAtOrBelowTheWatermark_isAReplay_evenWithANewMutationId() = runBlocking {
    backend.MutateData(MutateDataRequest(KENYA, listOf(createEntity(ENTITY, seq = 1))))
    backend.MutateData(MutateDataRequest(KENYA, listOf(entityTitle(ENTITY, "B", seq = 3))))

    val older =
      backend.MutateData(
        MutateDataRequest(
          KENYA,
          listOf(entityTitle(ENTITY, "A", seq = 2).copy(mutation_id = "new")),
        )
      )

    assertTrue(older.results.single().duplicate)
    assertEquals("B", entity(ENTITY)?.label)
  }

  @Test
  fun repeatedClientSeqWithinOneRequest_appliesOnce() = runBlocking {
    backend.MutateData(MutateDataRequest(KENYA, listOf(createEntity(ENTITY, seq = 1))))
    val before = entity(ENTITY)!!.revision

    val response =
      backend.MutateData(
        MutateDataRequest(KENYA, listOf(entityTitle(ENTITY, "A", 2), entityTitle(ENTITY, "A", 2)))
      )

    assertEquals(listOf(false, true), response.results.map { it.duplicate })
    assertEquals(before + 1, entity(ENTITY)!!.revision)
  }

  @Test
  fun staleFieldPatch_appliesLastWriterWins_andReportsAutoMerged() = runBlocking {
    backend.MutateData(MutateDataRequest(KENYA, listOf(createEntity(ENTITY, seq = 1))))
    val base = entity(ENTITY)!!.revision
    backend.MutateData(MutateDataRequest(KENYA, listOf(entityTitle(ENTITY, "B", 2))))

    val stale =
      backend.MutateData(
        MutateDataRequest(KENYA, listOf(entityTitle(ENTITY, "C", 3, baseRevision = base)))
      )

    assertEquals(
      ConflictResolutionStatus.CONFLICT_RESOLUTION_STATUS_AUTO_MERGED,
      stale.results.single().conflict_status,
    )
    assertEquals("C", entity(ENTITY)?.label)
  }

  @Test
  fun staleUpsert_failsWithPrecondition_andLeavesNoPartialChanges() = runBlocking {
    backend.MutateData(MutateDataRequest(KENYA, listOf(createEntity(ENTITY, seq = 1))))
    val revision = entity(ENTITY)!!.revision
    backend.MutateData(MutateDataRequest(KENYA, listOf(entityTitle(ENTITY, "B", 2))))

    assertFailsWith<IllegalStateException> {
      backend.MutateData(
        MutateDataRequest(
          KENYA,
          listOf(
            createEntity(OTHER_ENTITY, seq = 3),
            createEntity(ENTITY, seq = 4).let {
              it.copy(entity_mutation = it.entity_mutation!!.copy(base_revision = revision))
            },
          ),
        )
      )
    }

    // Neither mutation was committed, and neither counts as applied, so a retry can succeed.
    assertNull(entity(OTHER_ENTITY))
    assertEquals("B", entity(ENTITY)?.label)
    val applied = backend.GetAppliedMutations(GetAppliedMutationsRequest(client_id = CLIENT))
    assertEquals(2L, applied.watermarks.single().client_seq)
    val retry = backend.MutateData(MutateDataRequest(KENYA, listOf(createEntity(OTHER_ENTITY, 3))))
    assertFalse(retry.results.single().duplicate)
  }

  @Test
  fun getAppliedMutations_reportsHighestClientSeqPerScope() = runBlocking {
    backend.MutateData(
      MutateDataRequest(KENYA, listOf(createEntity(ENTITY, seq = 1), entityTitle(ENTITY, "A", 3)))
    )
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        working_copy = true,
        mutations =
          listOf(
            SurveyMutation(
              mutation_id = "s-2",
              client_id = CLIENT,
              client_seq = 2,
              upsert = SurveyDef(title = "Draft"),
            )
          ),
      )
    )
    backend.MutateOrganization(
      MutateOrganizationRequest(
        organization_id = ORG,
        mutations =
          listOf(
            OrganizationMutation(
              mutation_id = "o-4",
              client_id = CLIENT,
              client_seq = 4,
              upsert = Organization(description = "D"),
            )
          ),
      )
    )
    backend.MutateLibrary(
      MutateLibraryRequest(
        organization_id = ORG,
        mutations =
          listOf(
            LibraryMutation(
              mutation_id = "l-5",
              client_id = CLIENT,
              client_seq = 5,
              upsert =
                OrganizationLibraryRecord(
                  settings = OrganizationLibrarySettings(organization_id = ORG)
                ),
            )
          ),
      )
    )

    val all = backend.GetAppliedMutations(GetAppliedMutationsRequest(client_id = CLIENT))
    assertEquals(
      mapOf(
        "surveys/$KENYA/data" to 3L,
        "surveys/$KENYA" to 2L,
        "organizations/$ORG" to 4L,
        "libraries/$ORG" to 5L,
      ),
      all.watermarks.associate { it.scope to it.client_seq },
    )
    val filtered =
      backend.GetAppliedMutations(
        GetAppliedMutationsRequest(client_id = CLIENT, scopes = listOf("surveys/$KENYA"))
      )
    assertEquals(listOf("surveys/$KENYA"), filtered.watermarks.map { it.scope })
    assertTrue(
      backend
        .GetAppliedMutations(GetAppliedMutationsRequest(client_id = "someone-else"))
        .watermarks
        .isEmpty()
    )
  }

  @Test
  fun replayedSurveyMutation_isSkipped_andDoesNotRestamp() = runBlocking {
    val patch =
      SurveyMutation(
        mutation_id = "s-1",
        client_id = CLIENT,
        client_seq = 1,
        upsert = SurveyDef(title = "Mine"),
      )
    val first =
      backend.MutateSurvey(
        MutateSurveyRequest(survey_id = KENYA, working_copy = true, mutations = listOf(patch))
      )
    backend.MutateSurvey(
      MutateSurveyRequest(
        survey_id = KENYA,
        working_copy = true,
        mutations = listOf(SurveyMutation(upsert = SurveyDef(title = "Theirs"))),
      )
    )

    val replay =
      backend.MutateSurvey(
        MutateSurveyRequest(survey_id = KENYA, working_copy = true, mutations = listOf(patch))
      )

    assertEquals("Theirs", replay.survey?.title)
    assertTrue(replay.commit_timestamp!! > first.commit_timestamp!!)
    val workingCopy =
      backend.GetSurvey(
        GetSurveyRequest(survey_id = KENYA, working_copy = GetSurveyRequest.WorkingCopy())
      )
    assertEquals("Theirs", workingCopy.survey?.title)
  }

  @Test
  fun organizationUpsert_checksBaseVersion_butFieldPatchesDoNot() = runBlocking {
    val staleVersion =
      backend.GetOrganization(GetOrganizationRequest(organization_id = ORG)).organization!!.version
    // Another writer bumps the version past the client's base (a base of 0 means "unchecked").
    backend.MutateOrganization(
      MutateOrganizationRequest(
        organization_id = ORG,
        mutations = listOf(OrganizationMutation(upsert = Organization(description = "Newer"))),
      )
    )
    assertTrue(staleVersion > 0L)

    assertFailsWith<IllegalStateException> {
      backend.MutateOrganization(
        MutateOrganizationRequest(
          organization_id = ORG,
          base_version = staleVersion,
          mutations = listOf(OrganizationMutation(upsert = Organization(organization_id = ORG))),
        )
      )
    }

    val patched =
      backend.MutateOrganization(
        MutateOrganizationRequest(
          organization_id = ORG,
          base_version = staleVersion,
          mutations = listOf(OrganizationMutation(upsert = Organization(name = "Renamed"))),
        )
      )
    assertEquals("Renamed", patched.organization?.name)
  }

  private companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val ORG = "00000013-31cc-5c95-90ea-379f624a5c72"
    const val DATASET = "coffee_parcels"
    val ENTITY = testId("entity-idempotency-1")
    val OTHER_ENTITY = testId("entity-idempotency-2")
    const val CLIENT = "client-a"
  }
}
