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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.GetAppliedMutationsRequest
import openforis.collectone.v1.data.GetEntityRequest
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.library.GetLibraryRequest
import openforis.collectone.v1.library.LibraryMutation
import openforis.collectone.v1.library.MutateLibraryRequest
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.library.OrganizationLibrarySettings
import openforis.collectone.v1.survey.GetOrganizationRequest
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.MutateOrganizationRequest
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMutation
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import org.openforis.collectone.v2.server.app.DEFAULT_RPC_PARALLELISM
import org.openforis.collectone.v2.server.app.loadSeed
import org.openforis.collectone.v2.server.data.InMemoryUserDataStore
import org.openforis.collectone.v2.server.data.RendezvousUserDataStore
import org.openforis.collectone.v2.server.data.UserDataStore

/**
 * A client that retries a `Mutate*` request before the first attempt's response arrives sends the
 * same `client_seq` again, and the attempts can run concurrently. Exactly one of them may apply it,
 * however the server schedules requests: the replay check must be part of the write transaction,
 * not a read before it. Each test races as many attempts as the server runs RPC handlers at once
 * ([DEFAULT_RPC_PARALLELISM]).
 */
open class ConcurrentMutationTest {
  /** A store loaded with the bundled sample seed; overridden to test other stores. */
  protected open fun newStore(): UserDataStore = InMemoryUserDataStore(loadSeed(null))

  private val store by lazy { newStore() }

  /** A backend over the store, for reading state before and after the concurrent requests. */
  private val backend by lazy { UserDataBackend(store) }

  /**
   * Runs [ATTEMPTS] attempts of [request] at once, overlapping their store calls. Each attempt gets
   * its own thread, since the rendezvous blocks until all of them reach the store.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun <R> concurrentAttempts(request: suspend UserDataBackend.() -> R): List<R> =
    coroutineScope {
      val racing = UserDataBackend(RendezvousUserDataStore(store, parties = ATTEMPTS))
      val lanes = Dispatchers.IO.limitedParallelism(ATTEMPTS)
      List(ATTEMPTS) { async(lanes) { racing.request() } }.awaitAll()
    }

  private suspend fun appliedSeq(scope: String): Long? =
    backend
      .GetAppliedMutations(GetAppliedMutationsRequest(client_id = CLIENT))
      .watermarks
      .firstOrNull { it.scope == scope }
      ?.client_seq

  @Test
  fun duplicateSurveyMutations_applyOnce() = runTest {
    val before = surveyRecordVersion()
    val retitle =
      SurveyMutation(client_id = CLIENT, client_seq = 1, upsert = SurveyDef(title = "Retitled"))

    concurrentAttempts {
      MutateSurvey(
        MutateSurveyRequest(survey_id = KENYA, working_copy = true, mutations = listOf(retitle))
      )
    }

    assertEquals(before + 1, surveyRecordVersion())
    assertEquals(1L, appliedSeq("surveys/$KENYA"))
  }

  @Test
  fun duplicateOrganizationMutations_applyOnce() = runTest {
    val before = organizationVersion()
    val describe =
      OrganizationMutation(
        client_id = CLIENT,
        client_seq = 1,
        upsert = Organization(description = "Described"),
      )

    concurrentAttempts {
      MutateOrganization(
        MutateOrganizationRequest(organization_id = ORG, mutations = listOf(describe))
      )
    }

    assertEquals(before + 1, organizationVersion())
    assertEquals(1L, appliedSeq("organizations/$ORG"))
  }

  @Test
  fun duplicateLibraryMutations_applyOnce() = runTest {
    val before = libraryVersion()
    val configure =
      LibraryMutation(
        client_id = CLIENT,
        client_seq = 1,
        upsert =
          OrganizationLibraryRecord(settings = OrganizationLibrarySettings(organization_id = ORG)),
      )

    concurrentAttempts {
      MutateLibrary(MutateLibraryRequest(organization_id = ORG, mutations = listOf(configure)))
    }

    assertEquals(before + 1, libraryVersion())
    assertEquals(1L, appliedSeq("libraries/$ORG"))
  }

  @Test
  fun duplicateDataMutations_applyOnce_andOneAttemptReportsADuplicate() = runTest {
    val create =
      DataMutation(
        mutation_id = "m-1",
        client_id = CLIENT,
        client_seq = 1,
        entity_mutation =
          EntityMutation(
            entity_id = ENTITY,
            dataset_id = DATASET,
            upsert = EntityRecord(entity_id = ENTITY, dataset_id = DATASET, label = "Created"),
          ),
      )

    val responses = concurrentAttempts {
      MutateData(MutateDataRequest(KENYA, listOf(create)))
    }

    assertEquals(
      listOf(false) + List(ATTEMPTS - 1) { true },
      responses.map { it.results.single().duplicate }.sorted(),
    )
    assertEquals(1L, entityRevision())
    assertEquals(1L, appliedSeq("surveys/$KENYA/data"))
  }

  /** The survey's shared record version, which every write to any of its parts bumps. */
  private suspend fun surveyRecordVersion(): Long =
    backend.GetSurvey(GetSurveyRequest(survey_id = KENYA)).survey!!.record_version

  private suspend fun organizationVersion(): Long =
    backend.GetOrganization(GetOrganizationRequest(organization_id = ORG)).organization!!.version

  private suspend fun libraryVersion(): Long =
    backend.GetLibrary(GetLibraryRequest(organization_id = ORG)).library?.version ?: 0L

  private suspend fun entityRevision(): Long =
    backend.GetEntity(GetEntityRequest(survey_id = KENYA, entity_id = ENTITY)).entity!!.revision

  private companion object {
    /** As many attempts as the server runs RPC handlers at once. */
    const val ATTEMPTS = DEFAULT_RPC_PARALLELISM
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"
    const val ORG = "00000013-31cc-5c95-90ea-379f624a5c72"
    const val DATASET = "coffee_parcels"
    val ENTITY = testId("entity-concurrent-1")
    const val CLIENT = "client-concurrent"
  }
}
