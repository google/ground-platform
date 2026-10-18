/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.app.data.seed

import com.squareup.wire.Instant
import com.squareup.wire.ofEpochSecond
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.userdata.SurveySeed
import groundplatform.v2.userdata.UserDataSeed
import org.groundplatform.v2.app.data.datasource.remote.GrpcProtoMapper
import org.groundplatform.v2.app.data.seed.backend.BackendStaticEntitiesData
import org.groundplatform.v2.app.data.seed.backend.BackendStaticLibraryData
import org.groundplatform.v2.app.data.seed.backend.BackendStaticMapLayersData
import org.groundplatform.v2.app.data.seed.backend.BackendStaticMutationsData
import org.groundplatform.v2.app.data.seed.backend.BackendStaticOrganizationsData
import org.groundplatform.v2.app.data.seed.backend.BackendStaticPlacesData
import org.groundplatform.v2.app.data.seed.backend.BackendStaticSubmissionsData
import org.groundplatform.v2.app.data.seed.backend.BackendStaticSurveyEditorData
import org.groundplatform.v2.app.data.seed.backend.BackendStaticSurveysData
import org.groundplatform.v2.app.domain.model.SurveyConfig
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDerivation

/**
 * Builds the sample [UserDataSeed] loaded by the user data service (`server/userDataService`) from
 * the Kotlin sample data fixtures.
 *
 * Every record starts at version 1 (`revision`, `version`, or `record_version`) and gets a
 * distinct, increasing `commit_timestamp`, in the order organizations, libraries, then each survey
 * followed by its entities and submissions, then mutation logs. `UserDataSeedSnapshotTest` keeps
 * the checked-in snapshot identical to this output.
 */
object UserDataSeedGenerator {
  /** Server clock value before the first sample record. */
  const val INITIAL_EPOCH_SECOND: Long = 1_770_000_000L

  fun generate(): UserDataSeed {
    var epochSecond = INITIAL_EPOCH_SECOND
    fun nextCommitTimestamp(): Instant {
      epochSecond += 1L
      return ofEpochSecond(epochSecond, 0L)
    }

    val organizations =
      BackendStaticOrganizationsData.defaultOrganizations().map { org ->
        GrpcProtoMapper.organizationToProto(org, commitTimestamp = nextCommitTimestamp())
      }
    val libraries =
      (listOf(GlobalLibrarySeedData.library()) +
          BackendStaticLibraryData.defaultOrganizationLibraries())
        .map { lib -> GrpcProtoMapper.libraryToProto(lib, commitTimestamp = nextCommitTimestamp()) }

    val defaultTilePackages = BackendStaticMapLayersData.defaultOfflineTilePackages()
    val surveys =
      BackendStaticSurveysData.defaultSampleSurveys().map { survey ->
        val surveyId = survey.id
        val forms = BackendStaticSurveysData.formsForSurvey(surveyId)
        val mapLayers = BackendStaticMapLayersData.mapLayersForSurvey(surveyId)
        val entities = BackendStaticEntitiesData.entitiesForSurvey(surveyId)
        val standaloneSubs = BackendStaticSubmissionsData.standaloneSubmissionsForSurvey(surveyId)
        val geometries = BackendStaticSubmissionsData.submissionGeometriesForSurvey(surveyId)
        val formsById = forms.associate {
          it.id to BackendStaticSurveysData.builtInFallbackFormForForm(it)
        }
        val config =
          SurveyConfig(
            primaryForm = BackendStaticSurveysData.exampleFormForSurveyId(surveyId).formDefinition,
            formsById = formsById,
          )
        val baseDerivedDraft =
          SurveyEditorDerivation.derive(
            surveyId = surveyId,
            survey = survey,
            forms = forms,
            formDefinition = { formsById[it.id] },
            mapLayers = mapLayers,
            entities = entities,
          )
        // Only these two surveys have editor extras that can't be derived (sharing people, survey
        // area, data tables); they ship with a working copy.
        val customDraft =
          when (surveyId) {
            SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID ->
              BackendStaticSurveyEditorData.kenyaCoffeeDraft(baseDerivedDraft)
            BackendStaticSurveyEditorData.ORGANIZATION_SHARED_SURVEY_ID ->
              BackendStaticSurveyEditorData.organizationSharedDraft(baseDerivedDraft)
            else -> null
          }

        val (unversionedDef, unversionedAcl) =
          GrpcProtoMapper.surveyToProto(
            survey = survey,
            forms = forms,
            mapLayers = mapLayers,
            config = config,
            editorDraft = customDraft,
            offlineTilePackages =
              if (surveyId == SampleDataSeeder.DEFAULT_ACTIVE_SURVEY_ID) defaultTilePackages
              else emptyList(),
            commitTimestamp = nextCommitTimestamp(),
          )
        // Every record starts at version 1 (entity and submission `revision`, organization and
        // library `version`); the saved revision, working copy, and ACL share the survey's.
        val surveyDef = unversionedDef.copy(record_version = 1L)
        val acl = unversionedAcl.copy(record_version = 1L)

        val entityRecords = mutableListOf<groundplatform.v2.data.EntityRecord>()
        val submissions = linkedMapOf<String, SubmissionRecord>()
        val geometriesBySubId = geometries.groupBy { it.submissionId }
        val consumedGeometryIds = mutableSetOf<String>()

        for (entity in entities) {
          entityRecords +=
            GrpcProtoMapper.entityToProto(
              surveyId = surveyId,
              entity = entity,
              commitTimestamp = nextCommitTimestamp(),
            )
          for (sub in entity.submissions) {
            val subPolys = geometriesBySubId[sub.id].orEmpty()
            subPolys.forEach { consumedGeometryIds += it.id }
            submissions[sub.id] =
              GrpcProtoMapper.submissionToProto(
                surveyId = surveyId,
                submission = sub,
                geometryPolygons = subPolys,
                commitTimestamp = nextCommitTimestamp(),
              )
          }
        }

        // The first standalone submission also carries any geometries not tied to a submission.
        standaloneSubs.forEachIndexed { index, sub ->
          val subPolys =
            if (index == 0) {
              geometriesBySubId[sub.id].orEmpty() +
                geometries.filter { it.id !in consumedGeometryIds && it.submissionId != sub.id }
            } else {
              geometriesBySubId[sub.id].orEmpty()
            }
          subPolys.forEach { consumedGeometryIds += it.id }
          submissions[sub.id] =
            GrpcProtoMapper.submissionToProto(
              surveyId = surveyId,
              submission = sub,
              geometryPolygons = subPolys,
              commitTimestamp = nextCommitTimestamp(),
            )
        }

        val remainingGeoms = geometries.filter { it.id !in consumedGeometryIds }
        if (remainingGeoms.isNotEmpty() && submissions.isNotEmpty()) {
          val firstKey = submissions.keys.first()
          val firstSub = submissions.getValue(firstKey)
          submissions[firstKey] =
            firstSub.copy(
              geometry_polygons =
                firstSub.geometry_polygons +
                  remainingGeoms.map(GrpcProtoMapper::geometryPolygonToProto)
            )
        }

        SurveySeed(
          survey = surveyDef,
          acl = acl,
          working_copy = if (customDraft != null) surveyDef else null,
          entities = entityRecords,
          submissions = submissions.values.toList(),
        )
      }

    val places = BackendStaticPlacesData.defaultSurveyPlaces().map(GrpcProtoMapper::placeToProto)
    val mutationLogs =
      BackendStaticMutationsData.defaultMutations().map { mut ->
        GrpcProtoMapper.mutationLogToProto(mut, commitTimestamp = nextCommitTimestamp())
      }

    return UserDataSeed(
      organizations = organizations,
      libraries = libraries,
      surveys = surveys,
      places = places,
      mutation_logs = mutationLogs,
    )
  }
}
