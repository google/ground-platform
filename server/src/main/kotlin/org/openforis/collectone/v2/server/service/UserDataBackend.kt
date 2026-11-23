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

import com.squareup.wire.Instant
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.library.LibraryServiceServer
import openforis.collectone.v1.survey.CachedProfile
import openforis.collectone.v1.survey.GeoDataServiceServer
import openforis.collectone.v1.survey.OrganizationServiceServer
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyServiceServer
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.sync.SyncServiceServer
import openforis.collectone.v1.userdata.ClientVersionServiceServer
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.core.geo.io.KmlBalloonWriter
import org.openforis.collectone.v2.core.sync.ListBackedSyncService
import org.openforis.collectone.v2.core.version.MobileClientVersionPolicy
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.data.SpannerUserDataStore
import org.openforis.collectone.v2.server.data.SyncLists
import org.openforis.collectone.v2.server.data.UserAccount
import org.openforis.collectone.v2.server.data.UserDataStore
import org.openforis.collectone.v2.server.geo.EarthEngineTileClient
import org.openforis.collectone.v2.server.geo.EarthEngineValueClient
import org.openforis.collectone.v2.server.geo.ImageryAnalytics
import org.openforis.collectone.v2.server.messaging.InviteNotifier
import org.openforis.collectone.v2.server.messaging.NoopInviteNotifier
import org.openforis.collectone.v2.server.messaging.NoopSurveyTopicNotifier
import org.openforis.collectone.v2.server.messaging.SurveyTopicNotifier
import org.openforis.collectone.v2.server.web.StatusMapper

/**
 * The Collect One RPC services over a [UserDataStore]: one implementation per gRPC service
 * ([SurveyServiceImpl], [OrganizationServiceImpl], [LibraryServiceImpl], [DataServiceImpl], and
 * [GeoDataServiceImpl]) plus `SyncChanges` ([ListBackedSyncService]) over them, and the operations
 * that span services (caller registration, reset, the Trash purge, and the seed survey area
 * backfill). The services own the business rules; the store ([SpannerUserDataStore]) reads and
 * writes records atomically.
 *
 * Implements the server-side contract defined in `docs/technical/sync-engine.md`:
 * - Monotonically increasing authoritative `commit_timestamp` assigned to every initial record and
 *   every subsequent mutation.
 * - Record versions that strictly increase on every write to a record, which clients compare to
 *   tell which copy is newer: `revision` for entities and submissions (including deletes and plot
 *   lock changes), `version` for organizations and libraries (one per request), and the survey's
 *   shared `record_version` on its saved revision, working copy, ACL, and overview.
 * - Unary `List*` sync RPCs with query and delta modes selected by an opaque `sync_token`
 *   ([SyncLists]). Delta mode returns every record in scope committed after the token, ordered by
 *   `(commit_timestamp, id)` and including tombstones; query mode filters, sorts, and pages a
 *   snapshot and ends with a `next_sync_token` for the scope. Tokens are bound to the store's data
 *   epoch ([UserDataStore.dataEpoch]), so tokens from before a reset fail with
 *   `FAILED_PRECONDITION` and clients run a full sync.
 * - `SyncChanges`, which answers several sync scopes in one call with the same handlers and tokens
 *   as the `List*` RPCs ([ListBackedSyncService]).
 * - `SearchOrganizations` directory queries with offset-based paging.
 * - Soft-delete tombstones (`is_deleted = true` or `state = DELETED`), returned in delta mode and
 *   in query mode only when `include_deleted = true`.
 * - Idempotent mutations: the highest applied `client_seq` per `(client_id, scope)` is stored with
 *   the changes and reported by `GetAppliedMutations`. A mutation at or below it is a replay and is
 *   skipped, so a stale retry can't overwrite newer writes. Clients send a scope's mutations in
 *   `client_seq` order; mutations without a `client_id` aren't deduplicated.
 * - Last-writer-wins field patches. `base_revision` / `base_version` only guard whole-record
 *   replacements (`upsert_*`), which fail with `FAILED_PRECONDITION` when stale; stale field
 *   patches on data records report `AUTO_MERGED`.
 * - Atomic `Mutate*` requests: a request that fails leaves no partial changes.
 * - 5-minute cooperative plot locks.
 */
class UserDataBackend
private constructor(
  private val store: UserDataStore,
  private val collaborators: Collaborators,
  surveys: SurveyServiceImpl,
  organizations: OrganizationServiceImpl,
  libraries: LibraryServiceImpl,
  data: DataServiceImpl,
  geoData: GeoDataServiceImpl,
  private val clientVersions: ClientVersionServiceImpl,
) :
  SurveyServiceServer by surveys,
  OrganizationServiceServer by organizations,
  LibraryServiceServer by libraries,
  DataServiceServer by data,
  GeoDataServiceServer by geoData,
  ClientVersionServiceServer by clientVersions,
  SyncServiceServer by ListBackedSyncService(
    surveyService = surveys,
    organizationService = organizations,
    libraryService = libraries,
    dataService = data,
    scopeErrorCode = StatusMapper::scopeErrorCode,
  ) {

  private class Collaborators(
    val topicNotifier: SurveyTopicNotifier,
    val inviteNotifier: InviteNotifier,
    val entityEnricher: EntityEnricher,
    val geoIdRegistrar: GeoIdRegistrar,
  )

  constructor(
    store: UserDataStore,
    topicNotifier: SurveyTopicNotifier = NoopSurveyTopicNotifier,
    inviteNotifier: InviteNotifier = NoopInviteNotifier,
    earthEngineTileClient: EarthEngineTileClient = EarthEngineTileClient.fake(),
    imageryAnalytics: ImageryAnalytics =
      ImageryAnalytics(EarthEngineValueClient.unavailable(), earthEngineTileClient),
    entityEnricher: EntityEnricher = NoopEntityEnricher,
    geoIdRegistrar: GeoIdRegistrar = NoopGeoIdRegistrar,
    defaultMobileClientVersionPolicy: MobileClientVersionPolicy = MobileClientVersionPolicy.EMPTY,
  ) : this(
    store,
    Collaborators(topicNotifier, inviteNotifier, entityEnricher, geoIdRegistrar),
    earthEngineTileClient,
    imageryAnalytics,
    SyncLists { store.dataEpoch },
    defaultMobileClientVersionPolicy,
  )

  private constructor(
    store: UserDataStore,
    collaborators: Collaborators,
    earthEngineTileClient: EarthEngineTileClient,
    imageryAnalytics: ImageryAnalytics,
    syncLists: SyncLists,
    defaultMobileClientVersionPolicy: MobileClientVersionPolicy,
  ) : this(
    store,
    collaborators,
    SurveyServiceImpl(store, syncLists, collaborators.topicNotifier, collaborators.inviteNotifier),
    OrganizationServiceImpl(store, syncLists, collaborators.inviteNotifier),
    LibraryServiceImpl(store, syncLists),
    DataServiceImpl(
      store,
      syncLists,
      collaborators.topicNotifier,
      collaborators.entityEnricher,
      collaborators.geoIdRegistrar,
    ),
    GeoDataServiceImpl(store, earthEngineTileClient, imageryAnalytics),
    ClientVersionServiceImpl(store, defaultMobileClientVersionPolicy),
  )

  /** Current effective mobile client version policy (cached for fast interceptor access). */
  fun currentMobileClientVersionPolicy(): MobileClientVersionPolicy = clientVersions.currentPolicy()

  /** Loads the persisted mobile client version policy into the in-memory cache. */
  suspend fun refreshMobileClientVersionPolicy(): MobileClientVersionPolicy =
    clientVersions.refreshPolicy()

  /** Current highest committed server timestamp watermark. */
  suspend fun currentWatermark(): Instant = store.watermark()

  /**
   * Exports [surveyId] as a zero-credential multi-layer OGC KML 2.2 document (one `<Folder>` per
   * map layer).
   */
  suspend fun exportSurveyKml(
    surveyId: String,
    balloonAppUrl: String = "",
    serviceUrl: String = "",
    forNetworkLink: Boolean = false,
  ): ExportedSurveyKml =
    SurveyKmlExporter(store)
      .export(
        surveyId = surveyId,
        balloonAppUrl = balloonAppUrl,
        serviceUrl = serviceUrl,
        forNetworkLink = forNetworkLink,
      )

  /**
   * Exports a lightweight self-refreshing `<NetworkLink>` wrapper KML document for [surveyId] that
   * polls [targetKmlUrl] every [refreshIntervalSeconds].
   */
  suspend fun exportSurveyNetworkLinkKml(
    surveyId: String,
    targetKmlUrl: String,
    refreshIntervalSeconds: Int = KmlBalloonWriter.DEFAULT_REFRESH_INTERVAL_SECONDS,
  ): ExportedSurveyKml =
    SurveyKmlExporter(store)
      .exportNetworkLink(
        surveyId = surveyId,
        targetKmlUrl = targetKmlUrl,
        refreshIntervalSeconds = refreshIntervalSeconds,
      )

  /** Reads a live (non-deleted) [EntityRecord] for KML balloon status sync, or `null` if absent. */
  suspend fun readEntityForBalloon(surveyId: String, entityId: String): EntityRecord? =
    store.read { r ->
      r.entity(surveyId, entityId)?.takeUnless { it.is_deleted }
    }

  /** Resets all state to the store's initial data and starts a new data epoch. */
  suspend fun reset() {
    store.reset()
    clientVersions.clearCache()
    collaborators.inviteNotifier.reset()
    collaborators.entityEnricher.reset()
    collaborators.geoIdRegistrar.reset()
  }

  /**
   * Sets the survey areas of [seed]'s sample surveys on stored surveys that have none yet
   * ([SeedSurveyAreaBackfill]); returns the IDs of the surveys it changed. Run at startup: it's
   * idempotent and never replaces an existing area.
   */
  suspend fun backfillSeedSurveyAreas(seed: UserDataSeed): List<String> =
    SeedSurveyAreaBackfill(store, collaborators.topicNotifier).run(seed)

  /**
   * Registers [caller] before its RPC: on its first call, or when its email, verification, or
   * profile changed, upserts its [UserAccount]. For a verified email, it also binds ACL entries and
   * organization invites addressed to that email to the caller's user ID, which changes the
   * caller's access (so its access epoch increases).
   */
  suspend fun registerCaller(caller: Caller) {
    val email = caller.normalizedEmail
    val profile = CachedProfile(display_name = caller.displayName, avatar_url = caller.photoUrl)
    val existing = store.read { it.user(caller.userId) }
    if (
      existing != null &&
        existing.email == email &&
        existing.emailVerified == caller.emailVerified &&
        existing.profile == profile
    ) {
      return
    }
    store.write { tx ->
      val access = AccessChanges()
      if (caller.emailVerified && email.isNotEmpty()) tx.bindInvites(caller.userId, email, access)
      val user =
        (tx.user(caller.userId) ?: UserAccount(caller.userId, createTime = tx.now)).copy(
          email = email,
          emailVerified = caller.emailVerified,
          profile = profile,
        )
      tx.applyAccessChanges(access, pending = user)
    }
  }

  /**
   * Purges surveys whose `trash_time` is strictly before [olderThan] (30 days ago in the daily
   * job): removes their data and replaces each with a permanent `DELETED` tombstone so downloaded
   * copies receive a tombstone on their next delta. Returns the IDs of the purged surveys.
   */
  suspend fun purgeTrashedSurveys(olderThan: Instant): List<String> {
    val purged =
      store
        .write { tx ->
          tx
            .surveys()
            .filter { s ->
              val trashedAt = s.trash_time
              s.state != SurveyState.DELETED && trashedAt != null && trashedAt < olderThan
            }
            .map { s ->
              val recordVersion = tx.nextSurveyRecordVersion(s.survey_id)
              tx.purgeSurvey(
                SurveyDef(
                  survey_id = s.survey_id,
                  organization_id = s.organization_id,
                  owner_id = s.owner_id,
                  state = SurveyState.DELETED,
                  record_version = recordVersion,
                )
              )
              s.survey_id to recordVersion
            }
        }
        .value
    for ((surveyId, recordVersion) in purged) {
      collaborators.topicNotifier.notifySurveyDefChanged(surveyId, recordVersion)
    }
    return purged.map { it.first }
  }
}
