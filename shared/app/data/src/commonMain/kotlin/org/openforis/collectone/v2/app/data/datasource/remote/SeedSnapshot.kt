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
package org.openforis.collectone.v2.app.data.datasource.remote

import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ListPlacesResponse
import openforis.collectone.v1.data.MutationLogRecord
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyOverview
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStoreTransaction
import org.openforis.collectone.v2.app.data.datasource.local.store.StoredPreferences
import org.openforis.collectone.v2.app.data.datasource.local.store.SurveyBase
import org.openforis.collectone.v2.app.data.mapper.ProtoOrganizationMapper
import org.openforis.collectone.v2.app.data.mapper.ProtoRecordMapper
import org.openforis.collectone.v2.app.data.mapper.ProtoSurveyMapper
import org.openforis.collectone.v2.app.data.sync.rebaseRecords
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.UserSettings

/** Everything [GrpcSyncDataSource.seedStoreFromGrpc] fetched, before it's written. */
internal class SeedSnapshot(
  val organizations: FullList<Organization>,
  val libraries: FullList<OrganizationLibraryRecord>,
  val overviews: FullList<SurveyOverview>,
  val fullSurveys: List<SurveyRevisions>,
  val entities: Map<String, FullList<EntityRecord>>,
  val submissions: Map<String, FullList<SubmissionRecord>>,
  val placesResponse: ListPlacesResponse,
  val mutationLogs: FullList<MutationLogRecord>,
)

/**
 * Writes an initial full sync ([GrpcSyncDataSource.seedStoreFromGrpc]) into the store inside the
 * caller's transaction: every record becomes its scope's server base, and the domain projections
 * the screens read (survey previews, forms, map layers, map features, submissions, survey configs,
 * and editor drafts) are rebuilt from those bases. The caller stores the sync tokens in the same
 * transaction, so a crash never leaves bases without the tokens to catch them up.
 *
 * Nothing is rebased onto the outbox here: the projections are written straight from the bases. A
 * survey whose saved revision is missing (`GetSurveyResponse.survey` unset) is skipped. The
 * preferences are reset to the seed's defaults, with [defaultActiveSurveyId] active.
 */
internal fun LocalStoreTransaction.applySeedSnapshot(
  seed: SeedSnapshot,
  defaultActiveSurveyId: String,
  seedVersion: Int,
  trackOutbox: Boolean = false,
) {
  // With the sync engine (`trackOutbox`), records fetched from the server are already committed:
  // normalize their presentation sync status to SYNCED before storing bases and projecting domain
  // items, then rebase any pending outbox mutations on top.
  val entityDeltas =
    seed.entities.values.flatMap { list ->
      if (trackOutbox) list.records.map { it.copy(sync_status_key = SyncStatus.SYNCED.name) }
      else list.records
    }
  val submissionDeltas =
    seed.submissions.values.flatMap { list ->
      if (trackOutbox) list.records.map { it.copy(sync_status_key = SyncStatus.SYNCED.name) }
      else list.records
    }
  seed.organizations.records.forEach {
    putOrganizationBase(it.organization_id, it)
    upsertOrganization(ProtoOrganizationMapper.organizationFromProto(it))
  }
  seed.libraries.records.forEach {
    putLibraryBase(it.organization_id, it)
    putLibrary(ProtoOrganizationMapper.libraryFromProto(it))
  }
  entityDeltas.forEach { putEntityBase(it.survey_id, it.entity_id, it) }
  submissionDeltas.forEach { putSubmissionBase(it.survey_id, it.submission_id, it) }

  val entitiesBySurvey = entityDeltas.groupBy { it.survey_id }
  val submissionsBySurvey = submissionDeltas.groupBy { it.survey_id }
  for (fullSurvey in seed.fullSurveys) {
    val surveyDef = fullSurvey.saved.survey ?: continue
    val surveyId = surveyDef.survey_id
    putSeededSurvey(
      surveyDef,
      fullSurvey,
      entitiesBySurvey[surveyId].orEmpty(),
      submissionsBySurvey[surveyId].orEmpty(),
    )
  }
  if (trackOutbox) {
    val pending = outbox()
    if (pending.isNotEmpty()) rebaseRecords(pending)
  }

  putMutations(seed.mutationLogs.records.map(ProtoRecordMapper::mutationLogFromProto))
  putPlaces(seed.placesResponse.places.map(ProtoRecordMapper::placeFromProto))
  putPreferences(
    StoredPreferences(
      activeSurveyId = defaultActiveSurveyId,
      userSettings =
        UserSettings(
          language = "en",
          measurementUnits = MeasurementUnitSystem.METRIC,
          shouldUploadPhotosOnWifiOnly = false,
        ),
      uploadedMediaCacheSizeLabel = "38.4 MB",
      uploadedMediaFileCount = 27,
      seedVersion = seedVersion,
    )
  )
}

/**
 * Stores the base and projections of one seeded survey, [surveyDef] being its saved revision.
 * Submissions linked to a map feature (`entity_id`) are nested under it; the rest are standalone.
 */
private fun LocalStoreTransaction.putSeededSurvey(
  surveyDef: SurveyDef,
  fullSurvey: SurveyRevisions,
  surveyEntities: List<EntityRecord>,
  surveySubs: List<SubmissionRecord>,
) {
  val surveyId = surveyDef.survey_id
  putSurveyBase(
    surveyId,
    SurveyBase(
      saved = surveyDef,
      workingCopy = fullSurvey.workingCopy,
      acl = fullSurvey.saved.acl,
    ),
  )
  val forms = ProtoSurveyMapper.formsFromProto(surveyDef)
  val mapLayers = ProtoSurveyMapper.mapLayersFromProto(surveyDef)
  val config = ProtoSurveyMapper.surveyConfigFromProto(surveyDef, fullSurvey.saved.acl)
  val domainSubs = surveySubs.map(ProtoRecordMapper::submissionFromProto)
  val linkedByEntityId = domainSubs.filter { it.entityId.isNotBlank() }.groupBy { it.entityId }
  val standaloneSubs = domainSubs.filter { it.entityId.isBlank() }
  val geometries = surveySubs.flatMap { sub ->
    sub.geometry_polygons.mapNotNull {
      ProtoRecordMapper.geometryPolygonFromProto(it, sub.survey_id)
    }
  }
  val generatedDatasetIds = ProtoSurveyMapper.generatedDatasetIds(surveyDef)
  val entities = surveyEntities.map { entProto ->
    ProtoRecordMapper.entityFromProto(
      proto = entProto,
      submissions = linkedByEntityId[entProto.entity_id].orEmpty(),
      isGenerated = entProto.dataset_id in generatedDatasetIds,
    )
  }

  val preview =
    ProtoSurveyMapper.surveyPreviewFromProto(
      surveyDef = effectiveDefForPreview(surveyDef, fullSurvey.workingCopy),
      acl = fullSurvey.saved.acl,
      mapLayers = mapLayers,
      entities = entities,
    )
  upsertSurvey(preview)
  putForms(surveyId, forms)
  putMapLayers(surveyId, mapLayers)
  putEntities(surveyId, entities)
  putStandaloneSubmissions(surveyId, standaloneSubs)
  putSubmissionGeometries(surveyId, geometries)
  putSurveyConfig(surveyId, config)

  // The stored editor draft is the saved revision; unsaved working-copy edits are kept apart.
  if (fullSurvey.workingCopy != null || surveyDef.entity_datasets.isNotEmpty()) {
    putSurveyEditorDraft(
      surveyId,
      ProtoSurveyMapper.surveyEditorDraftFromProto(
        surveyDef = surveyDef,
        acl = fullSurvey.saved.acl,
        surveyPreview = preview,
        forms = forms,
        config = config,
        mapLayers = mapLayers,
        entities = entities,
      ),
    )
  }
  putSeededWorkingCopyDraft(surveyId, surveyDef, fullSurvey, entities)
}

private fun effectiveDefForPreview(surveyDef: SurveyDef, workingCopy: SurveyDef?): SurveyDef =
  if (
    surveyDef.entity_datasets.isEmpty() &&
      workingCopy != null &&
      workingCopy.entity_datasets.isNotEmpty()
  ) {
    surveyDef.copy(entity_datasets = workingCopy.entity_datasets)
  } else {
    surveyDef
  }

private fun LocalStoreTransaction.putSeededWorkingCopyDraft(
  surveyId: String,
  surveyDef: SurveyDef,
  fullSurvey: SurveyRevisions,
  entities: List<GeospatialEntityItem>,
) {
  putSurveyWorkingCopyDraft(
    surveyId,
    fullSurvey.workingCopy
      ?.takeIf { ProtoSurveyMapper.differsFromSaved(it, surveyDef) }
      ?.let {
        ProtoSurveyMapper.surveyEditorDraftFromWorkingCopyProto(
          workingCopy = it,
          acl = fullSurvey.saved.acl,
          entities = entities,
        )
      },
  )
}
