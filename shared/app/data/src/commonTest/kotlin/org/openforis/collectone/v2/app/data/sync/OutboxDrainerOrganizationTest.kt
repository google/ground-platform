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
package org.openforis.collectone.v2.app.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LibraryMutation
import openforis.collectone.v1.library.LibraryServiceServer
import openforis.collectone.v1.library.MutateLibraryRequest
import openforis.collectone.v1.library.MutateLibraryResponse
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.MutateOrganizationRequest
import openforis.collectone.v1.survey.MutateOrganizationResponse
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMutation
import openforis.collectone.v1.survey.OrganizationServiceServer
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService

/**
 * Organization and library edits drain through `MutateOrganization` and `MutateLibrary` with the
 * client's identity, and the records the server returns become the new bases.
 */
class OutboxDrainerOrganizationTest {
  private val store = InMemoryLocalStore()
  private val seed = SeedUserDataService()
  private val organizationRequests = mutableListOf<MutateOrganizationRequest>()
  private val libraryRequests = mutableListOf<MutateLibraryRequest>()

  private val organizationService =
    object : OrganizationServiceServer by seed {
      override suspend fun MutateOrganization(request: MutateOrganizationRequest) =
        MutateOrganizationResponse(
            organization =
              Organization(organization_id = request.organization_id, name = "Server", version = 7L)
          )
          .also { organizationRequests += request }
    }

  private val libraryService =
    object : LibraryServiceServer by seed {
      override suspend fun MutateLibrary(request: MutateLibraryRequest) =
        MutateLibraryResponse(
            library =
              OrganizationLibraryRecord(
                organization_id = request.organization_id,
                bundle = LibraryBundle(concepts = listOf(ConceptDef(id = "server"))),
                version = 3L,
              )
          )
          .also { libraryRequests += request }
    }

  private val drainer =
    OutboxDrainer(store, CollectOneGrpcClient(seed, organizationService, libraryService, seed))

  private fun record(target: MutationTarget, key: String, payload: MutationPayload) = runNow {
    store.transaction {
      updateSyncState { it.copy(clientId = "client-1") }
      OutboxRecorder().record(this, target, key, payload)
    }
  }

  @Test
  fun organizationEdit_isSentWithItsIdentityAndTheResponseBecomesTheBase() {
    record(
      MutationTarget.Organization("org"),
      Rebaser.organizationKey("org"),
      MutationPayload.Organization(OrganizationMutation(upsert = Organization(name = "Local"))),
    )

    val result = runNow { drainer.drain() }

    assertEquals(1, result.sent)
    val sent = organizationRequests.single().mutations.single()
    assertEquals("client-1", sent.client_id)
    assertEquals(Organization(name = "Local"), sent.upsert)
    runNow {
      store.transaction {
        assertEquals(emptyList(), outbox())
        assertEquals(7L, organizationBase("org")?.version)
      }
    }
  }

  @Test
  fun libraryEdit_isSentAndTheResponseBecomesTheBase() {
    record(
      MutationTarget.Library("org"),
      Rebaser.libraryKey("org"),
      MutationPayload.Library(
        LibraryMutation(
          upsert =
            OrganizationLibraryRecord(
              bundle = LibraryBundle(concepts = listOf(ConceptDef(id = "c1")))
            )
        )
      ),
    )

    val result = runNow { drainer.drain() }

    assertEquals(1, result.sent)
    assertEquals("org", libraryRequests.single().organization_id)
    runNow {
      store.transaction {
        assertEquals(emptyList(), outbox())
        assertEquals(listOf("server"), libraryBase("org")?.bundle?.concepts?.map { it.id })
      }
    }
  }
}
