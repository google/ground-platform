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

import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.ExportProfileDef
import openforis.collectone.v1.library.FormTemplateDef
import openforis.collectone.v1.library.GetLibraryRequest
import openforis.collectone.v1.library.GetLibraryResponse
import openforis.collectone.v1.library.GetResolvedLibraryRequest
import openforis.collectone.v1.library.GetResolvedLibraryResponse
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LibraryServiceServer
import openforis.collectone.v1.library.ListLibrariesRequest
import openforis.collectone.v1.library.ListLibrariesResponse
import openforis.collectone.v1.library.MutateLibraryRequest
import openforis.collectone.v1.library.MutateLibraryResponse
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.library.OrganizationLibrarySettings
import openforis.collectone.v1.library.PurposePackDef
import openforis.collectone.v1.library.SearchConceptsRequest
import openforis.collectone.v1.library.SearchConceptsResponse
import org.openforis.collectone.v2.core.impact.ALL_USERS_ORGANIZATION_ID
import org.openforis.collectone.v2.core.mutation.LibraryMutations
import org.openforis.collectone.v2.server.auth.currentCaller
import org.openforis.collectone.v2.server.data.ListParams
import org.openforis.collectone.v2.server.data.SyncLists
import org.openforis.collectone.v2.server.data.UserDataStore

/** `LibraryService`: organization libraries, merged over the global library shared by all users. */
internal class LibraryServiceImpl(
  private val store: UserDataStore,
  private val syncLists: SyncLists,
) : LibraryServiceServer {

  /** Fails with `PERMISSION_DENIED` unless the caller can read [organizationId]'s library. */
  private suspend fun requireLibraryReader(organizationId: String) {
    val caller = currentCaller()
    store.read { r ->
      if (!Access(r, caller).readsLibrary(organizationId)) {
        throw Access.denied("library $organizationId")
      }
    }
  }

  override suspend fun GetLibrary(request: GetLibraryRequest): GetLibraryResponse {
    requireLibraryReader(request.organization_id)
    val record =
      store.read { it.library(request.organization_id) }
        ?: OrganizationLibraryRecord(
          organization_id = request.organization_id,
          bundle = LibraryBundle(),
          settings = OrganizationLibrarySettings(organization_id = request.organization_id),
          version = 1L,
          commit_timestamp = store.watermark(),
        )
    return GetLibraryResponse(library = record)
  }

  override suspend fun GetResolvedLibrary(
    request: GetResolvedLibraryRequest
  ): GetResolvedLibraryResponse {
    requireLibraryReader(request.organization_id)
    val (globalRecord, orgRecord) =
      store.read { r ->
        r.library(ALL_USERS_ORGANIZATION_ID) to
          if (request.organization_id.isNotBlank()) r.library(request.organization_id) else null
      }
    val mergedConcepts = LinkedHashMap<String, ConceptDef>()
    val mergedTemplates = LinkedHashMap<String, FormTemplateDef>()
    val mergedPacks = LinkedHashMap<String, PurposePackDef>()
    val mergedProfiles = LinkedHashMap<String, ExportProfileDef>()

    globalRecord?.bundle?.let { b ->
      b.concepts.forEach { mergedConcepts[it.id] = it }
      b.form_templates.forEach { mergedTemplates[it.id] = it }
      b.purpose_packs.forEach { mergedPacks[it.id] = it }
      b.export_profiles.forEach { mergedProfiles[it.id] = it }
    }
    orgRecord?.bundle?.let { b ->
      b.concepts.forEach { mergedConcepts[it.id] = it }
      b.form_templates.forEach { mergedTemplates[it.id] = it }
      b.purpose_packs.forEach { mergedPacks[it.id] = it }
      b.export_profiles.forEach { mergedProfiles[it.id] = it }
    }
    return GetResolvedLibraryResponse(
      resolved_library =
        OrganizationLibraryRecord(
          organization_id = request.organization_id,
          bundle =
            LibraryBundle(
              concepts = mergedConcepts.values.toList(),
              form_templates = mergedTemplates.values.toList(),
              purpose_packs = mergedPacks.values.toList(),
              export_profiles = mergedProfiles.values.toList(),
            ),
          settings = orgRecord?.settings ?: globalRecord?.settings,
          version = orgRecord?.version ?: 1L,
          commit_timestamp = store.watermark(),
        )
    )
  }

  override suspend fun ListLibraries(request: ListLibrariesRequest): ListLibrariesResponse {
    val orgId = request.organization_id
    val caller = currentCaller()
    val page = store.read { r ->
      val access = Access(r, caller)
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "libraries|organization_id=$orgId",
        records = r.libraries().filter { orgId.isBlank() || it.organization_id == orgId },
        commitTimestamp = OrganizationLibraryRecord::commit_timestamp,
        id = OrganizationLibraryRecord::organization_id,
        query = commitOrderQuery(OrganizationLibraryRecord::commit_timestamp) { !it.is_deleted },
        queryFieldsSet = emptyList(),
        watermark = r.snapshotTimestamp,
        accessEpoch = r.accessEpochOf(caller),
        visible = { access.readsLibrary(it.organization_id) },
      )
    }
    return ListLibrariesResponse(
      libraries = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  override suspend fun SearchConcepts(request: SearchConceptsRequest): SearchConceptsResponse {
    requireLibraryReader(request.organization_id)
    val q = request.query.trim().lowercase()
    // The global library, overridden by the organization's (as in GetResolvedLibrary).
    val libraries =
      listOf(ALL_USERS_ORGANIZATION_ID) +
        listOfNotNull(request.organization_id.takeIf { it.isNotBlank() })
    val matches =
      store
        .read { it.searchConcepts(libraries, q) }
        .filter { c ->
          (request.data_type == DataType.DATA_TYPE_UNSPECIFIED ||
            c.data_type == request.data_type) &&
            (q.isEmpty() ||
              c.id.lowercase().contains(q) ||
              c.label?.values?.values?.any { it.lowercase().contains(q) } == true)
        }
    return SearchConceptsResponse(concepts = matches)
  }

  override suspend fun MutateLibrary(request: MutateLibraryRequest): MutateLibraryResponse {
    val orgId = request.organization_id
    val caller = currentCaller()
    val committed = store.write { tx ->
      if (!Access(tx, caller).manages(tx.organization(orgId))) {
        throw Access.denied("library $orgId")
      }
      // Replays are recognized in the transaction that records new mutations as applied, so
      // concurrent attempts of one mutation apply it once.
      val toApply =
        tx.newMutations("libraries/$orgId", request.mutations) {
          MutationIdentity(it.client_id, it.client_seq)
        }
      if (toApply.isEmpty()) return@write null
      val stored = tx.library(orgId)
      if (toApply.any { it.upsert?.organization_id?.isNotEmpty() == true && it.delete == null }) {
        checkBaseVersion(request.base_version, stored?.version, "library $orgId")
      }
      var current =
        stored
          ?: OrganizationLibraryRecord(
            organization_id = orgId,
            bundle = LibraryBundle(),
            settings = OrganizationLibrarySettings(organization_id = orgId),
            version = 0L,
          )
      for (mutation in toApply) {
        val delLib = mutation.delete
        val upLib = mutation.upsert
        if (upLib != null && upLib.organization_id.isNotEmpty() && delLib == null) {
          // The server owns the version; it's bumped once for the whole request below.
          current = upLib.copy(organization_id = orgId, version = current.version)
        } else {
          current = LibraryMutations.apply(current, delLib, upLib)
        }
      }
      // Every write bumps the version by exactly one, however many mutations the request has.
      current = current.copy(version = (stored?.version ?: 0L) + 1L)
      tx.putLibrary(current)
      tx.recordApplied(
        scope = "libraries/$orgId",
        identities = toApply.map { MutationIdentity(it.client_id, it.client_seq) },
      )
      current
    }
    // Every mutation is a replay: report the current state.
    val library = committed.value ?: return currentLibrary(orgId)
    return MutateLibraryResponse(
      organization_id = orgId,
      library = library.copy(commit_timestamp = committed.commitTimestamp),
      commit_timestamp = committed.commitTimestamp,
    )
  }

  /** The library's current state, as `MutateLibrary` reports it when it changes nothing. */
  private suspend fun currentLibrary(orgId: String): MutateLibraryResponse {
    val existing = store.read { it.library(orgId) }
    return MutateLibraryResponse(
      organization_id = orgId,
      library = existing,
      commit_timestamp = existing?.commit_timestamp ?: store.watermark(),
    )
  }
}
