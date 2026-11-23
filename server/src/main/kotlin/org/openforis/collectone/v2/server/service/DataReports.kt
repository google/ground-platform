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

import openforis.collectone.v1.data.AuditRecord
import openforis.collectone.v1.data.GetImpactAggregatesRequest
import openforis.collectone.v1.data.GetImpactAggregatesResponse
import openforis.collectone.v1.data.ImpactAggregate
import openforis.collectone.v1.data.ImpactEvent as ImpactEventProto
import openforis.collectone.v1.data.ListAuditRecordsRequest
import openforis.collectone.v1.data.ListAuditRecordsResponse
import openforis.collectone.v1.data.ListImpactEventsRequest
import openforis.collectone.v1.data.ListImpactEventsResponse
import openforis.collectone.v1.data.ListMutationLogsRequest
import openforis.collectone.v1.data.ListMutationLogsResponse
import openforis.collectone.v1.data.ListPlacesRequest
import openforis.collectone.v1.data.ListPlacesResponse
import openforis.collectone.v1.data.MutationLogRecord
import openforis.collectone.v1.data.RecordImpactEventsRequest
import openforis.collectone.v1.data.RecordImpactEventsResponse
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.core.access.SurveyRole
import org.openforis.collectone.v2.server.auth.currentCaller
import org.openforis.collectone.v2.server.data.ListParams
import org.openforis.collectone.v2.server.data.SyncLists
import org.openforis.collectone.v2.server.data.UserDataReader
import org.openforis.collectone.v2.server.data.UserDataStore

/**
 * The reporting RPCs of `DataService`: impact events, impact aggregates, audit records, mutation
 * logs, and places. Reports about a survey or organization are visible to administrators, its
 * organizers, and its organization's managers ([ReportAccess]); unlike entities and submissions,
 * they are listed from the full record set and filtered in memory.
 */
internal class DataReports(private val store: UserDataStore, private val syncLists: SyncLists) {
  /**
   * Whether the caller may see reports (impact events, audit records) about survey [surveyId] or
   * organization [organizationId]: as an administrator, a survey organizer, or an organization
   * manager. Memoized per ID for one request.
   */
  private class ReportAccess(private val reader: UserDataReader, private val access: Access) {
    private val surveys = HashMap<String, Boolean>()
    private val organizations = HashMap<String, Boolean>()

    fun allows(surveyId: String, organizationId: String = ""): Boolean =
      access.isAdmin ||
        (surveyId.isNotBlank() &&
          surveys.getOrPut(surveyId) {
            access.surveyRole(surveyId).atLeast(SurveyRole.SURVEY_ORGANIZER)
          }) ||
        (organizationId.isNotBlank() &&
          organizations.getOrPut(organizationId) {
            access.manages(reader.organization(organizationId))
          })
  }

  /**
   * Stores events the clients derived. Any viewer of an event's survey, or active member of its
   * organization, may record it; only administrators may record events with neither.
   */
  suspend fun recordImpactEvents(request: RecordImpactEventsRequest): RecordImpactEventsResponse {
    if (request.events.isEmpty()) {
      return RecordImpactEventsResponse(commit_timestamp = store.watermark())
    }
    val caller = currentCaller()
    store.read { r ->
      val access = Access(r, caller)
      for (event in request.events) {
        if (event.survey_id.isNotBlank()) {
          access.requireSurvey(event.survey_id, SurveyRole.VIEWER)
        }
        if (event.organization_id.isNotBlank()) {
          if (!access.isAdmin && !access.isActiveMember(event.organization_id)) {
            throw Access.denied("organization ${event.organization_id}")
          }
        }
        if (event.survey_id.isBlank() && event.organization_id.isBlank() && !access.isAdmin) {
          throw Access.denied("unscoped impact events")
        }
      }
    }
    val committed = store.write { tx -> request.events.forEach(tx::putImpactEvent) }
    val ts = committed.commitTimestamp
    return RecordImpactEventsResponse(
      events = request.events.map { it.copy(commit_timestamp = ts) },
      commit_timestamp = ts,
    )
  }

  suspend fun listImpactEvents(request: ListImpactEventsRequest): ListImpactEventsResponse {
    val caller = currentCaller()
    val page = store.read { snapshot ->
      val reports = ReportAccess(snapshot, Access(snapshot, caller))
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey =
          "impact_events|survey_id=${request.survey_id}|organization_id=${request.organization_id}",
        records =
          snapshot.impactEvents().filter { ev ->
            (request.survey_id.isBlank() || ev.survey_id == request.survey_id) &&
              (request.organization_id.isBlank() || ev.organization_id == request.organization_id)
          },
        commitTimestamp = ImpactEventProto::commit_timestamp,
        id = ImpactEventProto::event_id,
        query = commitOrderQuery(ImpactEventProto::commit_timestamp),
        queryFieldsSet = emptyList(),
        watermark = snapshot.snapshotTimestamp,
        accessEpoch = snapshot.accessEpochOf(caller),
        visible = { reports.allows(it.survey_id, it.organization_id) },
      )
    }
    return ListImpactEventsResponse(
      events = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  /**
   * Live map features and their area in the surveys the caller may report on, as one `FEATURES`
   * aggregate for the requested survey, organization, or everything. Computed on request until the
   * aggregation pipeline (`docs/technical/backend/impact-aggregation.md`) exists, hence the fixed
   * run ID.
   */
  suspend fun impactAggregates(request: GetImpactAggregatesRequest): GetImpactAggregatesResponse {
    val caller = currentCaller()
    val activeSurveys =
      store
        .read { r ->
          val reports = ReportAccess(r, Access(r, caller))
          r.surveys().filter { reports.allows(it.survey_id, it.organization_id) }
        }
        .filter { s ->
          s.state != SurveyState.DELETED &&
            s.trash_time == null &&
            (request.survey_id.isBlank() || s.survey_id == request.survey_id) &&
            (request.organization_id.isBlank() || s.organization_id == request.organization_id)
        }
    var totalEntities = 0L
    var totalAreaHa = 0.0
    for (s in activeSurveys) {
      val ents = store.read { it.entities(s.survey_id, includeDeleted = false) }
      totalEntities += ents.size
      totalAreaHa += ents.sumOf { it.area_hectares }
    }
    return GetImpactAggregatesResponse(
      aggregates =
        listOf(
          ImpactAggregate(
            run_id = "run-static-1",
            pipeline_version = 1,
            computed_at = store.watermark(),
            scope_type =
              if (request.survey_id.isNotBlank()) ImpactAggregate.ScopeType.SURVEY
              else if (request.organization_id.isNotBlank()) ImpactAggregate.ScopeType.ORGANIZATION
              else ImpactAggregate.ScopeType.GLOBAL,
            scope_id = request.survey_id.ifBlank { request.organization_id },
            metric = ImpactAggregate.Metric.FEATURES,
            feature_count = totalEntities,
            area_ha = totalAreaHa,
          )
        )
    )
  }

  suspend fun listAuditRecords(request: ListAuditRecordsRequest): ListAuditRecordsResponse {
    val caller = currentCaller()
    val page = store.read { snapshot ->
      val reports = ReportAccess(snapshot, Access(snapshot, caller))
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "audit_records|survey_id=${request.survey_id}|target_id=${request.target_id}",
        records =
          snapshot.auditRecords().filter { a ->
            (request.survey_id.isBlank() || a.survey_id == request.survey_id) &&
              (request.target_id.isBlank() || a.target_id == request.target_id)
          },
        commitTimestamp = AuditRecord::commit_timestamp,
        id = AuditRecord::audit_id,
        query = commitOrderQuery(AuditRecord::commit_timestamp),
        queryFieldsSet = emptyList(),
        watermark = snapshot.snapshotTimestamp,
        accessEpoch = snapshot.accessEpochOf(caller),
        visible = { reports.allows(it.survey_id) },
      )
    }
    return ListAuditRecordsResponse(
      audit_records = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }

  suspend fun listPlaces(request: ListPlacesRequest): ListPlacesResponse {
    val q = request.query.trim().lowercase()
    val filtered =
      if (q.isEmpty()) {
        store.read { it.places() }
      } else {
        store
          .read { it.places() }
          .filter { it.name.lowercase().contains(q) || it.region_subtitle.lowercase().contains(q) }
      }
    return ListPlacesResponse(places = filtered)
  }

  suspend fun listMutationLogs(request: ListMutationLogsRequest): ListMutationLogsResponse {
    val caller = currentCaller()
    val page = store.read { snapshot ->
      val access = Access(snapshot, caller)
      if (request.survey_id.isNotBlank()) {
        access.requireSurvey(request.survey_id, SurveyRole.VIEWER)
      }
      syncLists.list(
        params = ListParams(request.page_size, request.page_token, request.sync_token),
        scopeKey = "mutation_logs|survey_id=${request.survey_id}",
        records =
          snapshot.mutationLogs().filter {
            request.survey_id.isBlank() || it.survey_id == request.survey_id
          },
        commitTimestamp = MutationLogRecord::commit_timestamp,
        id = MutationLogRecord::id,
        query = commitOrderQuery(MutationLogRecord::commit_timestamp),
        queryFieldsSet = emptyList(),
        watermark = snapshot.snapshotTimestamp,
        accessEpoch = snapshot.accessEpochOf(caller),
        visible = {
          access.isAdmin ||
            (it.survey_id.isNotBlank() &&
              access.surveyRole(it.survey_id).atLeast(SurveyRole.VIEWER))
        },
      )
    }
    return ListMutationLogsResponse(
      mutation_logs = page.items,
      next_page_token = page.nextPageToken,
      next_sync_token = page.nextSyncToken,
    )
  }
}
