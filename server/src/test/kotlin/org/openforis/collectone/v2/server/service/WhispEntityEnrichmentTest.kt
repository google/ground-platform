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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.GetEntityRequest
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.server.data.InMemoryUserDataStore
import org.openforis.collectone.v2.server.geo.WhispClient
import org.openforis.collectone.v2.server.messaging.SurveyTopicNotifier
import org.openforis.collectone.v2.server.service.ServiceTestSeed.DATASET
import org.openforis.collectone.v2.server.service.ServiceTestSeed.SURVEY

class WhispEntityEnrichmentTest {

  private class RecordingTopicNotifier : SurveyTopicNotifier {
    val entityChanges = mutableListOf<Pair<String, Instant>>()

    override fun notifySurveyDefChanged(surveyId: String, recordVersion: Long) = Unit

    override fun notifySurveyEntitiesChanged(surveyId: String, commitTs: Instant) {
      entityChanges += surveyId to commitTs
    }
  }

  private val plotPolygon1 =
    GeometryProtos.polygon(
      listOf(
        listOf(
          GeometryProtos.position(latitude = -10.0, longitude = -55.0),
          GeometryProtos.position(latitude = -10.0, longitude = -54.99),
          GeometryProtos.position(latitude = -9.99, longitude = -54.99),
          GeometryProtos.position(latitude = -9.99, longitude = -55.0),
        )
      )
    )

  private val plotPolygon2 =
    GeometryProtos.polygon(
      listOf(
        listOf(
          GeometryProtos.position(latitude = -11.0, longitude = -56.0),
          GeometryProtos.position(latitude = -11.0, longitude = -55.98),
          GeometryProtos.position(latitude = -10.98, longitude = -55.98),
          GeometryProtos.position(latitude = -10.98, longitude = -56.0),
        )
      )
    )

  private fun testFixture(
    calledGeometries: MutableList<Geometry>
  ): Pair<UserDataBackend, RecordingTopicNotifier> {
    val store = InMemoryUserDataStore(ServiceTestSeed.seed)
    val topicNotifier = RecordingTopicNotifier()
    val whispClient = WhispClient { geometry ->
      calledGeometries += geometry
      if (geometry == plotPolygon1) {
        mapOf("Country" to "Brazil", "Plot_area_ha" to "1.25", "Ind_01_treecover" to "low")
      } else {
        mapOf("Country" to "Brazil", "Plot_area_ha" to "4.8", "Ind_01_treecover" to "high")
      }
    }
    val enricher =
      WhispEntityEnricher(
        store = store,
        whispClient = whispClient,
        topicNotifier = topicNotifier,
        prefix = "whisp_",
        async = false,
      )
    val backend =
      UserDataBackend(store = store, topicNotifier = topicNotifier, entityEnricher = enricher)
    return backend to topicNotifier
  }

  @Test
  fun mutateData_enrichesNewlyCreatedEntity_andNotifiesDeltaSync() = runTest {
    val calledGeometries = mutableListOf<Geometry>()
    val (backend, topicNotifier) = testFixture(calledGeometries)
    val initialSyncToken =
      backend.ListEntities(ListEntitiesRequest(survey_id = SURVEY)).next_sync_token
    val entityId = testId("whisp-plot-1")

    val createResp =
      backend.MutateData(
        MutateDataRequest(
          survey_id = SURVEY,
          mutations =
            listOf(
              DataMutation(
                mutation_id = "mut-create",
                entity_mutation =
                  EntityMutation(
                    entity_id = entityId,
                    dataset_id = DATASET,
                    upsert =
                      EntityRecord(
                        entity_id = entityId,
                        dataset_id = DATASET,
                        label = "Parcel 101",
                        geometry = plotPolygon1,
                        properties = mapOf("farmer_name" to TypedValue(string_value = "Ana Silva")),
                      ),
                  ),
              )
            ),
        )
      )

    assertEquals(1L, createResp.results.single().entity!!.revision)
    assertEquals(listOf(plotPolygon1), calledGeometries)
    assertEquals(2, topicNotifier.entityChanges.size, "Expected 2 entity topic notifications")

    val enriched =
      backend.GetEntity(GetEntityRequest(survey_id = SURVEY, entity_id = entityId)).entity!!
    assertEquals(2L, enriched.revision)
    assertTrue(enriched.commit_timestamp!! > createResp.commit_timestamp!!)
    assertEquals(
      mapOf(
        "farmer_name" to "Ana Silva",
        "whisp_Country" to "Brazil",
        "whisp_Plot_area_ha" to "1.25",
        "whisp_Ind_01_treecover" to "low",
      ),
      enriched.properties.mapValues { (_, v) -> v.string_value.orEmpty() },
    )

    val delta =
      backend.ListEntities(ListEntitiesRequest(survey_id = SURVEY, sync_token = initialSyncToken))
    val deltaEntity = delta.entities.single { it.entity_id == entityId }
    assertEquals(2L, deltaEntity.revision)
    assertEquals("low", deltaEntity.properties["whisp_Ind_01_treecover"]?.string_value)
  }

  @Test
  fun mutateData_reEnrichesOnlyWhenGeometryChanges() = runTest {
    val calledGeometries = mutableListOf<Geometry>()
    val (backend, _) = testFixture(calledGeometries)
    val entityId = testId("whisp-plot-2")

    mutateEntity(
      backend = backend,
      mutationId = "mut-create",
      entityId = entityId,
      upsert =
        EntityRecord(
          entity_id = entityId,
          dataset_id = DATASET,
          label = "Parcel 102",
          geometry = plotPolygon1,
          properties = mapOf("farmer_name" to TypedValue(string_value = "Ana Silva")),
        ),
    )

    // Updating a non-geometry field does NOT re-call Whisp and preserves whisp_* properties.
    mutateEntity(
      backend = backend,
      mutationId = "mut-label",
      entityId = entityId,
      upsert =
        EntityRecord(
          label = "Parcel 102 Updated",
          properties = mapOf("crop" to TypedValue(string_value = "cocoa")),
        ),
    )
    assertEquals(1, calledGeometries.size, "Non-geometry edits must not re-invoke Whisp")
    val afterLabelEdit =
      backend.GetEntity(GetEntityRequest(survey_id = SURVEY, entity_id = entityId)).entity!!
    assertEquals(3L, afterLabelEdit.revision)
    assertEquals("Ana Silva", afterLabelEdit.properties["farmer_name"]?.string_value)
    assertEquals("cocoa", afterLabelEdit.properties["crop"]?.string_value)
    assertEquals("low", afterLabelEdit.properties["whisp_Ind_01_treecover"]?.string_value)

    // Updating the entity's geometry re-calls Whisp and replaces whisp_* properties.
    mutateEntity(
      backend = backend,
      mutationId = "mut-geo",
      entityId = entityId,
      upsert = EntityRecord(geometry = plotPolygon2),
    )
    assertEquals(listOf(plotPolygon1, plotPolygon2), calledGeometries)
    val afterGeoEdit =
      backend.GetEntity(GetEntityRequest(survey_id = SURVEY, entity_id = entityId)).entity!!
    assertEquals(5L, afterGeoEdit.revision)
    assertEquals("4.8", afterGeoEdit.properties["whisp_Plot_area_ha"]?.string_value)
    assertEquals("high", afterGeoEdit.properties["whisp_Ind_01_treecover"]?.string_value)
    assertEquals("cocoa", afterGeoEdit.properties["crop"]?.string_value)
  }

  private suspend fun mutateEntity(
    backend: UserDataBackend,
    mutationId: String,
    entityId: String,
    upsert: EntityRecord,
  ) {
    backend.MutateData(
      MutateDataRequest(
        survey_id = SURVEY,
        mutations =
          listOf(
            DataMutation(
              mutation_id = mutationId,
              entity_mutation =
                EntityMutation(entity_id = entityId, dataset_id = DATASET, upsert = upsert),
            )
          ),
      )
    )
  }

  @Test
  fun deliverEnrichments_skipsStaleGeometryOrResetEpoch() = runTest {
    val store = InMemoryUserDataStore(ServiceTestSeed.seed)
    val enricher =
      WhispEntityEnricher(
        store = store,
        whispClient = { mapOf("Ind_01_treecover" to "low") },
        async = false,
      )
    val backend = UserDataBackend(store = store)
    val entityId = testId("whisp-stale")

    backend.MutateData(
      MutateDataRequest(
        survey_id = SURVEY,
        mutations =
          listOf(
            DataMutation(
              mutation_id = "mut-1",
              entity_mutation =
                EntityMutation(
                  entity_id = entityId,
                  dataset_id = DATASET,
                  upsert =
                    EntityRecord(
                      entity_id = entityId,
                      dataset_id = DATASET,
                      label = "Parcel",
                      geometry = plotPolygon2,
                    ),
                ),
            )
          ),
      )
    )

    val staleGeoOutcome =
      enricher.deliverEnrichments(
        listOf(
          EntityEnrichmentCandidate(
            surveyId = SURVEY,
            entityId = entityId,
            geometry = plotPolygon1,
            dataEpoch = store.dataEpoch,
          )
        )
      )
    assertTrue(staleGeoOutcome.isEmpty())

    val staleEpochOutcome =
      enricher.deliverEnrichments(
        listOf(
          EntityEnrichmentCandidate(
            surveyId = SURVEY,
            entityId = entityId,
            geometry = plotPolygon2,
            dataEpoch = "old-epoch",
          )
        )
      )
    assertTrue(staleEpochOutcome.isEmpty())
  }
}
