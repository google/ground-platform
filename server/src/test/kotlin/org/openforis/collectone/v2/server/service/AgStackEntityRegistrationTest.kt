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
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.GetEntityRequest
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.geo.geoid.AgStackGeoId
import org.openforis.collectone.v2.server.data.InMemoryUserDataStore
import org.openforis.collectone.v2.server.geo.AgStackRegistrationResult
import org.openforis.collectone.v2.server.geo.AgStackRegistryClient
import org.openforis.collectone.v2.server.messaging.SurveyTopicNotifier
import org.openforis.collectone.v2.server.service.ServiceTestSeed.DATASET
import org.openforis.collectone.v2.server.service.ServiceTestSeed.SURVEY

class AgStackEntityRegistrationTest {

  private class RecordingTopicNotifier : SurveyTopicNotifier {
    val entityChanges = mutableListOf<Pair<String, Instant>>()

    override fun notifySurveyDefChanged(surveyId: String, recordVersion: Long) = Unit

    override fun notifySurveyEntitiesChanged(surveyId: String, commitTs: Instant) {
      entityChanges += surveyId to commitTs
    }
  }

  private val plotA =
    GeometryProtos.polygon(
      listOf(
        listOf(
          GeometryProtos.position(latitude = 30.31080, longitude = 76.88850),
          GeometryProtos.position(latitude = 30.31080, longitude = 76.88880),
          GeometryProtos.position(latitude = 30.31100, longitude = 76.88880),
          GeometryProtos.position(latitude = 30.31100, longitude = 76.88850),
        )
      )
    )

  private val adjacentPlotB =
    GeometryProtos.polygon(
      listOf(
        listOf(
          GeometryProtos.position(latitude = 30.31110, longitude = 76.88900),
          GeometryProtos.position(latitude = 30.31110, longitude = 76.88930),
          GeometryProtos.position(latitude = 30.31130, longitude = 76.88930),
          GeometryProtos.position(latitude = 30.31130, longitude = 76.88900),
        )
      )
    )

  private val duplicatePlotA =
    GeometryProtos.polygon(
      listOf(
        listOf(
          GeometryProtos.position(latitude = 30.3108001, longitude = 76.8885001),
          GeometryProtos.position(latitude = 30.3108001, longitude = 76.8888001),
          GeometryProtos.position(latitude = 30.3110001, longitude = 76.8888001),
          GeometryProtos.position(latitude = 30.3110001, longitude = 76.8885001),
        )
      )
    )

  private val distantPlotC =
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

  @Test
  fun mutateData_validatesGeoId_replacesInvalidClientGeoId_andResolvesLevel13Collisions() =
    runTest {
      val store = InMemoryUserDataStore(ServiceTestSeed.seed)
      val backend = UserDataBackend(store = store)
      val idA = testId("plot-a")
      val idB = testId("plot-b")
      val idDup = testId("plot-dup")

      val candidatesA = AgStackGeoId.forGeometry(plotA)!!
      val candidatesB = AgStackGeoId.forGeometry(adjacentPlotB)!!
      assertEquals(candidatesA.primary, candidatesB.primary)
      assertNotEquals(candidatesA.fallback, candidatesB.fallback)

      // 1. Creating plotA with a bogus client geo_id ("P001") replaces it with the L13 primary.
      val respA =
        backend.MutateData(
          MutateDataRequest(
            survey_id = SURVEY,
            mutations =
              listOf(
                DataMutation(
                  mutation_id = "mut-a",
                  entity_mutation =
                    EntityMutation(
                      entity_id = idA,
                      dataset_id = DATASET,
                      upsert =
                        EntityRecord(
                          entity_id = idA,
                          dataset_id = DATASET,
                          label = "Plot A",
                          geo_id = "P001",
                          geometry = plotA,
                        ),
                    ),
                )
              ),
          )
        )
      val savedA = respA.results.single().entity!!
      assertEquals(candidatesA.primary, savedA.geo_id)
      assertEquals(AgStackGeoId.s2CellToken(plotA), savedA.s2_cell_token)

      // 2. Creating adjacentPlotB in the same L13 cell (<95% L20 overlap) promotes it to L20.
      val respB =
        backend.MutateData(
          MutateDataRequest(
            survey_id = SURVEY,
            mutations =
              listOf(
                DataMutation(
                  mutation_id = "mut-b",
                  entity_mutation =
                    EntityMutation(
                      entity_id = idB,
                      dataset_id = DATASET,
                      upsert =
                        EntityRecord(
                          entity_id = idB,
                          dataset_id = DATASET,
                          label = "Plot B",
                          geo_id = candidatesB.primary,
                          geometry = adjacentPlotB,
                        ),
                    ),
                )
              ),
          )
        )
      val savedB = respB.results.single().entity!!
      assertEquals(candidatesB.fallback, savedB.geo_id)

      // 3. Creating duplicatePlotA (>=95% L20 overlap with plotA) reuses plotA's GeoID.
      val respDup =
        backend.MutateData(
          MutateDataRequest(
            survey_id = SURVEY,
            mutations =
              listOf(
                DataMutation(
                  mutation_id = "mut-dup",
                  entity_mutation =
                    EntityMutation(
                      entity_id = idDup,
                      dataset_id = DATASET,
                      upsert =
                        EntityRecord(
                          entity_id = idDup,
                          dataset_id = DATASET,
                          label = "Plot A Duplicate",
                          geometry = duplicatePlotA,
                        ),
                    ),
                )
              ),
          )
        )
      val savedDup = respDup.results.single().entity!!
      assertEquals(candidatesA.primary, savedDup.geo_id)
    }

  @Test
  fun mutateData_registersWithAgStackRegistry_reconcilesFallbackGeoId_andReRegistersOnGeometryEdit() =
    runTest {
      val store = InMemoryUserDataStore(ServiceTestSeed.seed)
      val topicNotifier = RecordingTopicNotifier()
      val registeredGeometries = mutableListOf<Geometry>()
      val candidatesC = AgStackGeoId.forGeometry(distantPlotC)!!

      // Simulate an external AgStack Asset Registry where distantPlotC's L13 primary is already
      // taken by another farm outside Collect One, so the registry returns the L20 fallback.
      val registryClient = AgStackRegistryClient { geometry, _ ->
        registeredGeometries += geometry
        val c = AgStackGeoId.forGeometry(geometry) ?: return@AgStackRegistryClient null
        if (geometry == distantPlotC) {
          AgStackRegistrationResult(geoId = c.fallback)
        } else {
          AgStackRegistrationResult(geoId = c.primary)
        }
      }
      val registrar =
        AgStackEntityRegistrar(
          store = store,
          registryClient = registryClient,
          topicNotifier = topicNotifier,
          async = false,
        )
      val backend =
        UserDataBackend(
          store = store,
          topicNotifier = topicNotifier,
          geoIdRegistrar = registrar,
        )
      val entityId = testId("plot-c")

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
                          label = "Parcel C",
                          geometry = distantPlotC,
                        ),
                    ),
                )
              ),
          )
        )
      // Synchronously inside MutateData it received the provisional L13 primary:
      assertEquals(candidatesC.primary, createResp.results.single().entity!!.geo_id)
      // Immediately after commit, AgStackEntityRegistrar reconciled it to the L20 fallback:
      assertEquals(listOf(distantPlotC), registeredGeometries)
      assertEquals(2, topicNotifier.entityChanges.size)
      val reconciled =
        backend.GetEntity(GetEntityRequest(survey_id = SURVEY, entity_id = entityId)).entity!!
      assertEquals(2L, reconciled.revision)
      assertEquals(candidatesC.fallback, reconciled.geo_id)

      // Non-geometry property edits preserve the registered L20 fallback without calling AgStack:
      backend.MutateData(
        MutateDataRequest(
          survey_id = SURVEY,
          mutations =
            listOf(
              DataMutation(
                mutation_id = "mut-prop",
                entity_mutation =
                  EntityMutation(
                    entity_id = entityId,
                    dataset_id = DATASET,
                    upsert =
                      EntityRecord(
                        properties = mapOf("status" to TypedValue(string_value = "Completed"))
                      ),
                  ),
              )
            ),
        )
      )
      assertEquals(1, registeredGeometries.size)
      val afterPropEdit =
        backend.GetEntity(GetEntityRequest(survey_id = SURVEY, entity_id = entityId)).entity!!
      assertEquals(candidatesC.fallback, afterPropEdit.geo_id)

      // Editing the geometry invalidates the old GeoID and registers the new boundary with AgStack:
      val point = GeometryProtos.point(latitude = 30.90706, longitude = 74.78209)
      backend.MutateData(
        MutateDataRequest(
          survey_id = SURVEY,
          mutations =
            listOf(
              DataMutation(
                mutation_id = "mut-move",
                entity_mutation =
                  EntityMutation(
                    entity_id = entityId,
                    dataset_id = DATASET,
                    upsert = EntityRecord(geometry = point),
                  ),
              )
            ),
        )
      )
      assertEquals(listOf(distantPlotC, point), registeredGeometries)
      val afterMove =
        backend.GetEntity(GetEntityRequest(survey_id = SURVEY, entity_id = entityId)).entity!!
      assertEquals(AgStackGeoId.computeCandidate(point), afterMove.geo_id)
    }
}
