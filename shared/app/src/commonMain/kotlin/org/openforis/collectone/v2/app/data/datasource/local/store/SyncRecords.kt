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
package org.openforis.collectone.v2.app.data.datasource.local.store

import com.squareup.wire.Instant
import groundplatform.v2.data.DataMutation
import groundplatform.v2.library.LibraryMutation
import groundplatform.v2.survey.OrganizationMutation
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyMutation

/*
 * Sync bookkeeping kept in the [LocalStore] next to the data it describes (see
 * `docs/technical/sync-engine.md`, "Rebase on Remote Changes"): the outbox of pending mutations,
 * the last server copy (base) of each synced record, and the sync state (sync tokens and the
 * install's mutation identity).
 */

/** The `Mutate*` RPC (and the record scope within it) that a [PendingMutation] is sent to. */
sealed interface MutationTarget {
  /** Scope reported by `GetAppliedMutations` for mutations sent to this target. */
  val scope: String

  /** `MutateSurvey` for [surveyId], on the working copy when [workingCopy] is true. */
  data class Survey(val surveyId: String, val workingCopy: Boolean) : MutationTarget {
    override val scope: String
      get() = "surveys/$surveyId"
  }

  /** `MutateData` (entities and submissions) for [surveyId]. */
  data class Data(val surveyId: String) : MutationTarget {
    override val scope: String
      get() = "surveys/$surveyId/data"
  }

  /** `MutateOrganization` for [organizationId]. */
  data class Organization(val organizationId: String) : MutationTarget {
    override val scope: String
      get() = "organizations/$organizationId"
  }

  /** `MutateLibrary` for the library of [organizationId]. */
  data class Library(val organizationId: String) : MutationTarget {
    override val scope: String
      get() = "libraries/$organizationId"
  }
}

/**
 * The wire mutation of a [PendingMutation], without its identity fields (`mutation_id`,
 * `client_id`, `client_seq`, `client_timestamp`), which are stamped when the mutation is sent.
 */
sealed interface MutationPayload {
  data class Survey(val mutation: SurveyMutation) : MutationPayload

  data class Data(val mutation: DataMutation) : MutationPayload

  data class Organization(val mutation: OrganizationMutation) : MutationPayload

  data class Library(val mutation: LibraryMutation) : MutationPayload
}

/** Lifecycle of a [PendingMutation] in the outbox. */
enum class OutboxState {
  /** Waiting to be sent. Only queued mutations can be coalesced. */
  QUEUED,

  /** Sent and awaiting the server's response. */
  IN_FLIGHT,

  /** The last attempt failed with a transient error; retried after [PendingMutation.notBefore]. */
  RETRYING,

  /** The server rejected the mutation permanently. Kept until the user retries or discards it. */
  REJECTED,
}

/**
 * One fine-grained mutation in the outbox. Mutations are sent in [clientSeq] order per [target],
 * and are removed once the server confirms them.
 */
data class PendingMutation(
  /** Idempotency key (`mutation_id`), a random UUID. */
  val mutationId: String,
  /** Position in the install's mutation sequence (`client_seq`), strictly increasing. */
  val clientSeq: Long,
  val target: MutationTarget,
  /**
   * The record the mutation changes, e.g. `survey/<id>`, `entity/<id>`, `submission/<id>`,
   * `organization/<id>`, or `library/<id>`. Rebase re-applies pending mutations per record.
   */
  val recordKey: String,
  val payload: MutationPayload,
  /**
   * Revision of the record's base when the mutation was recorded (`base_revision` for data,
   * `base_version` otherwise), or `0` when unknown.
   */
  val baseRevision: Long = 0L,
  /** Client time when the mutation was recorded (`client_timestamp`). */
  val clientTimestamp: Instant? = null,
  val state: OutboxState = OutboxState.QUEUED,
  /** Number of send attempts so far. */
  val attempts: Int = 0,
  /** Message of the last failure, for the Uploads view. */
  val lastError: String? = null,
  /** Earliest time the mutation may be (re)sent: debounce and backoff. `null` sends at once. */
  val notBefore: Instant? = null,
)

/** Last server copies of a survey's saved revision, working copy, and ACL. */
data class SurveyBase(
  val saved: SurveyDef? = null,
  val workingCopy: SurveyDef? = null,
  val acl: SurveyAcl? = null,
)

/**
 * Opaque `next_sync_token` of each synced `List*` scope (`docs/technical/sync-engine.md`, "Sync
 * Tokens"). Clients store tokens and send them back unchanged; they never parse or construct one.
 * An empty token means the scope hasn't been synced, so its next sync is a full sync.
 */
data class SyncTokens(
  /** `ListOrganizations`. */
  val organizations: String = "",
  /** `ListLibraries` for every organization. */
  val libraries: String = "",
  /** `ListSurveyOverviews` for every organization. */
  val surveys: String = "",
  /** `ListEntities`, keyed by survey ID. */
  val entitiesBySurvey: Map<String, String> = emptyMap(),
  /** `ListSubmissions`, keyed by survey ID. */
  val submissionsBySurvey: Map<String, String> = emptyMap(),
  /** `ListImpactEvents` for every survey. */
  val impactEvents: String = "",
)

/** Sync state stored with the data, so it's updated atomically with what it describes. */
data class SyncState(
  /** Stable identifier of this install (`client_id`), or blank until first assigned. */
  val clientId: String = "",
  /** The `client_seq` the next recorded mutation gets. */
  val nextClientSeq: Long = 1L,
  val syncTokens: SyncTokens = SyncTokens(),
  /**
   * Surveys the user downloaded to this device. Clients that sync only downloaded surveys (mobile)
   * keep the definitions and data of these surveys; it also drives
   * [org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem.isDownloaded].
   */
  val downloadedSurveyIds: Set<String> = emptySet(),
)
