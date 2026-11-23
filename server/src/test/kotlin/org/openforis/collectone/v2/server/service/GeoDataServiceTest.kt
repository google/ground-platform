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

import google.earthengine.v1main.Expression
import google.earthengine.v1main.ValueNode
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.GeoTrace
import openforis.collectone.v1.survey.EarthEngineTileSource
import openforis.collectone.v1.survey.GetOverlayTileUrlRequest
import openforis.collectone.v1.survey.GetPlotStatisticsRequest
import openforis.collectone.v1.survey.GetPlotTimeSeriesRequest
import openforis.collectone.v1.survey.GetSceneTileUrlRequest
import openforis.collectone.v1.survey.ImagerySource
import openforis.collectone.v1.survey.MutateOrganizationRequest
import openforis.collectone.v1.survey.OrganizationMutation
import openforis.collectone.v1.survey.PlotRef
import openforis.collectone.v1.survey.SceneStretch
import openforis.collectone.v1.survey.TimeSeriesSource
import openforis.collectone.v1.survey.XyzTileSource
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.impact.ALL_USERS_ORGANIZATION_ID
import org.openforis.collectone.v2.server.app.loadSeed
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.auth.CallerElement
import org.openforis.collectone.v2.server.data.InMemoryUserDataStore
import org.openforis.collectone.v2.server.geo.EarthEngineTileClient
import org.openforis.collectone.v2.server.geo.ImageryAnalytics

class GeoDataServiceTest {
  private val testClock = AtomicReference(Instant.parse("2026-10-26T12:00:00Z"))
  private val fetchCount = AtomicInteger(0)

  private val testExpression =
    Expression(
      values = mapOf("testKey" to ValueNode(constant_value = "testVal")),
      result = "testKey",
    )

  private val testTileClient =
    EarthEngineTileClient.create(
      ttlSeconds = 7200L,
      clock = { testClock.get() },
      fetcher = { expr ->
        val count = fetchCount.incrementAndGet()
        "https://earthengine.googleapis.com/v1/projects/example-project/maps/map-$count/tiles/{z}/{x}/{y}"
      },
    )

  private val store = InMemoryUserDataStore(loadSeed(null))
  private val computedTags = mutableListOf<String>()
  private val computedExpressions = mutableListOf<Expression>()
  private val backend =
    UserDataBackend(
      store = store,
      earthEngineTileClient = testTileClient,
      imageryAnalytics =
        ImageryAnalytics(
          valueClient = { expression, tag ->
            computedTags += tag
            computedExpressions += expression
            if (tag == "c1-ts-stats") mapOf("elevation_mean" to 1_500.0) else emptyList<Any>()
          },
          tileClient = testTileClient,
          clock = { testClock.get() },
        ),
    )

  /** A map feature with a point, added to the Kenya survey. */
  private val kenyaPlot by lazy {
    EntityRecord(
        entity_id = "time-series-plot",
        survey_id = AuthorizationTest.KENYA,
        dataset_id = "plots",
        label = "Plot 1",
        point = GeoPoint(latitude = -0.42, longitude = 36.95),
      )
      .also { entity -> runBlocking { store.write { it.putEntity(entity) } } }
  }

  private suspend fun <T> asCaller(caller: Caller, block: suspend () -> T): T =
    withContext(CallerElement(caller)) {
      backend.registerCaller(caller)
      block()
    }

  private suspend fun assertStatus(
    expectedCode: Status.Code,
    caller: Caller,
    block: suspend () -> Unit,
  ) {
    val error =
      assertFailsWith<Exception> {
        asCaller(caller) { block() }
      }
    val actualCode =
      when (error) {
        is StatusException -> error.status.code
        is StatusRuntimeException -> error.status.code
        else -> throw AssertionError("Expected gRPC status exception, but got $error", error)
      }
    assertEquals(expectedCode, actualCode)
  }

  companion object {
    val KFS_MANAGER =
      Caller(userId = "uid-organizer", email = "organizer@example.org", emailVerified = true)
  }

  @Test
  fun getOverlayTileUrl_enforcesSurveyAcl() = runTest {
    // 1. Unauthenticated or outsider caller without survey access gets PERMISSION_DENIED on a
    // non-public survey
    assertStatus(Status.Code.PERMISSION_DENIED, AuthorizationTest.OUTSIDER) {
      backend.GetOverlayTileUrl(
        GetOverlayTileUrlRequest(
          survey_id = AuthorizationTest.KENYA,
          imagery_source_id = "imagery-all-users-opentopomap",
        )
      )
    }

    // 2. Unknown survey gets NOT_FOUND
    assertStatus(Status.Code.NOT_FOUND, AuthorizationTest.ADMIN) {
      backend.GetOverlayTileUrl(
        GetOverlayTileUrlRequest(
          survey_id = "00000000-0000-0000-0000-000000000000",
          imagery_source_id = "imagery-all-users-opentopomap",
        )
      )
    }

    // 3. Authorized collector on KENYA survey succeeds for standard XYZ layer
    val response =
      asCaller(AuthorizationTest.COLLECTOR) {
        backend.GetOverlayTileUrl(
          GetOverlayTileUrlRequest(
            survey_id = AuthorizationTest.KENYA,
            imagery_source_id = "imagery-all-users-opentopomap",
          )
        )
      }
    assertTrue(response.url_template.isNotBlank())
    assertEquals(7200L, response.ttl?.seconds)
  }

  @Test
  fun getOverlayTileUrl_cachesTileUrlInMemoryFor2Hours() = runTest {
    // Configure an Earth Engine source on "All users" organization as ADMIN
    val eeSource =
      ImagerySource(
        imagery_source_id = "imagery-ee-cached-test",
        name = "EE Test Layer",
        earth_engine = EarthEngineTileSource(expression = testExpression),
      )

    asCaller(AuthorizationTest.ADMIN) {
      val org = store.read { it.organization(ALL_USERS_ORGANIZATION_ID) }!!
      backend.MutateOrganization(
        MutateOrganizationRequest(
          organization_id = ALL_USERS_ORGANIZATION_ID,
          mutations =
            listOf(
              OrganizationMutation(
                upsert = org.copy(imagery_sources = org.imagery_sources + eeSource)
              )
            ),
        )
      )
    }

    // Call 1: fetchCount should increment to 1, ttl = 7200s
    val resp1 =
      asCaller(AuthorizationTest.COLLECTOR) {
        backend.GetOverlayTileUrl(
          GetOverlayTileUrlRequest(
            survey_id = AuthorizationTest.KENYA,
            imagery_source_id = "imagery-ee-cached-test",
          )
        )
      }
    assertEquals(1, fetchCount.get())
    assertEquals(
      "https://earthengine.googleapis.com/v1/projects/example-project/maps/map-1/tiles/{z}/{x}/{y}",
      resp1.url_template,
    )
    assertEquals(7200L, resp1.ttl?.seconds)

    // Advance clock by 30 minutes (1800s)
    testClock.set(testClock.get().plusSeconds(1800L))

    // Call 2: should return cached URL without calling fetcher again (fetchCount remains 1), ttl =
    // 5400s
    val resp2 =
      asCaller(AuthorizationTest.FIELD_LEAD) {
        backend.GetOverlayTileUrl(
          GetOverlayTileUrlRequest(
            survey_id = AuthorizationTest.KENYA,
            imagery_source_id = "imagery-ee-cached-test",
          )
        )
      }
    assertEquals(1, fetchCount.get())
    assertEquals(resp1.url_template, resp2.url_template)
    assertEquals(5400L, resp2.ttl?.seconds)

    // Advance clock past 2 hours (7201s from start, i.e., +5401s from current)
    testClock.set(testClock.get().plusSeconds(5401L))

    // Call 3: cache expired, fetchCount increments to 2, ttl refreshed to 7200s
    val resp3 =
      asCaller(AuthorizationTest.COLLECTOR) {
        backend.GetOverlayTileUrl(
          GetOverlayTileUrlRequest(
            survey_id = AuthorizationTest.KENYA,
            imagery_source_id = "imagery-ee-cached-test",
          )
        )
      }
    assertEquals(2, fetchCount.get())
    assertEquals(
      "https://earthengine.googleapis.com/v1/projects/example-project/maps/map-2/tiles/{z}/{x}/{y}",
      resp3.url_template,
    )
    assertEquals(7200L, resp3.ttl?.seconds)
  }

  @Test
  fun mutateOrganization_adminGuardForEarthEngineSources() = runTest {
    val eeSource =
      ImagerySource(
        imagery_source_id = "imagery-kfs-ee",
        name = "KFS EE Layer",
        earth_engine = EarthEngineTileSource(expression = testExpression),
      )

    // 1. Non-admin manager of Kenya Forest Service tries to add Earth Engine layer ->
    // PERMISSION_DENIED
    val orgKfs = store.read { it.organization(AuthorizationTest.KENYA_FOREST_SERVICE) }!!
    assertStatus(Status.Code.PERMISSION_DENIED, KFS_MANAGER) {
      backend.MutateOrganization(
        MutateOrganizationRequest(
          organization_id = AuthorizationTest.KENYA_FOREST_SERVICE,
          mutations =
            listOf(
              OrganizationMutation(
                upsert = orgKfs.copy(imagery_sources = orgKfs.imagery_sources + eeSource)
              )
            ),
        )
      )
    }

    // 2. Non-admin manager adding an XYZ tiles source succeeds without error
    val xyzSource =
      ImagerySource(
        imagery_source_id = "imagery-kfs-xyz",
        name = "KFS XYZ Layer",
        xyz_tiles = XyzTileSource(url_template = "https://example.org/tiles/{z}/{x}/{y}"),
      )
    val successXyz =
      asCaller(KFS_MANAGER) {
        val org = store.read { it.organization(AuthorizationTest.KENYA_FOREST_SERVICE) }!!
        backend.MutateOrganization(
          MutateOrganizationRequest(
            organization_id = AuthorizationTest.KENYA_FOREST_SERVICE,
            mutations =
              listOf(
                OrganizationMutation(
                  upsert = org.copy(imagery_sources = org.imagery_sources + xyzSource)
                )
              ),
          )
        )
      }
    assertTrue(
      successXyz.organization!!.imagery_sources.any { it.imagery_source_id == "imagery-kfs-xyz" }
    )

    // 3. Platform admin adding Earth Engine layer succeeds
    val successEe =
      asCaller(AuthorizationTest.ADMIN) {
        val org = store.read { it.organization(AuthorizationTest.KENYA_FOREST_SERVICE) }!!
        backend.MutateOrganization(
          MutateOrganizationRequest(
            organization_id = AuthorizationTest.KENYA_FOREST_SERVICE,
            mutations =
              listOf(
                OrganizationMutation(
                  upsert = org.copy(imagery_sources = org.imagery_sources + eeSource)
                )
              ),
          )
        )
      }
    assertTrue(
      successEe.organization!!.imagery_sources.any { it.imagery_source_id == "imagery-kfs-ee" }
    )

    // 4. Earth Engine source with empty expression is rejected with INVALID_ARGUMENT
    val invalidEeSource =
      ImagerySource(
        imagery_source_id = "imagery-invalid-ee",
        name = "Invalid EE",
        earth_engine = EarthEngineTileSource(expression = Expression(result = "")),
      )
    val orgKfsForInvalid = store.read { it.organization(AuthorizationTest.KENYA_FOREST_SERVICE) }!!
    assertStatus(Status.Code.INVALID_ARGUMENT, AuthorizationTest.ADMIN) {
      backend.MutateOrganization(
        MutateOrganizationRequest(
          organization_id = AuthorizationTest.KENYA_FOREST_SERVICE,
          mutations =
            listOf(
              OrganizationMutation(
                upsert =
                  orgKfsForInvalid.copy(
                    imagery_sources = orgKfsForInvalid.imagery_sources + invalidEeSource
                  )
              )
            ),
        )
      )
    }
  }

  @Test
  fun getPlotTimeSeries_enforcesSurveyAclAndResolvesMapFeatures() = runTest {
    val request =
      GetPlotTimeSeriesRequest(
        survey_id = AuthorizationTest.KENYA,
        plot = PlotRef(entity_id = kenyaPlot.entity_id),
        source = TimeSeriesSource.LANDSAT,
      )
    assertStatus(Status.Code.PERMISSION_DENIED, AuthorizationTest.OUTSIDER) {
      backend.GetPlotTimeSeries(request)
    }
    assertStatus(Status.Code.NOT_FOUND, AuthorizationTest.COLLECTOR) {
      backend.GetPlotTimeSeries(request.copy(plot = PlotRef(entity_id = "no-such-feature")))
    }
    assertStatus(Status.Code.INVALID_ARGUMENT, AuthorizationTest.COLLECTOR) {
      backend.GetPlotTimeSeries(
        request.copy(source = TimeSeriesSource.TIME_SERIES_SOURCE_UNSPECIFIED)
      )
    }
    assertStatus(Status.Code.INVALID_ARGUMENT, AuthorizationTest.COLLECTOR) {
      backend.GetPlotTimeSeries(request.copy(plot = null))
    }

    val response = asCaller(AuthorizationTest.COLLECTOR) { backend.GetPlotTimeSeries(request) }
    assertTrue(response.time_start_ms.isEmpty())
    assertEquals(listOf("c1-ts-series"), computedTags)
  }

  @Test
  fun getPlotStatistics_acceptsUnsavedPoints() = runTest {
    val response =
      asCaller(AuthorizationTest.COLLECTOR) {
        backend.GetPlotStatistics(
          GetPlotStatisticsRequest(
            survey_id = AuthorizationTest.KENYA,
            plot = PlotRef(geometry = GeometryProtos.point(-0.42, 36.95)),
          )
        )
      }
    assertEquals(1_500.0, response.elevation_mean_meters)
    assertEquals(null, response.population)
  }

  @Test
  fun plots_preferTheStoredGeometryAndFallBackToTheDrawnOne() = runTest {
    suspend fun analyze(plot: PlotRef) =
      backend.GetPlotStatistics(
        GetPlotStatisticsRequest(survey_id = AuthorizationTest.KENYA, plot = plot)
      )
    suspend fun analyzedGeometry(plot: PlotRef): String {
      asCaller(AuthorizationTest.COLLECTOR) { analyze(plot) }
      return computedExpressions.last().toString()
    }
    val drawn = GeometryProtos.point(-0.4189, 36.9503)

    // A stored point wins over the geometry the client drew.
    assertTrue(
      "[36.95, -0.42]" in
        analyzedGeometry(PlotRef(entity_id = kenyaPlot.entity_id, geometry = drawn))
    )

    // Sample map features have no stored geometry, so the drawn one is analyzed, as it is for
    // features that aren't on the server yet.
    store.write { it.putEntity(kenyaPlot.copy(entity_id = "drawn-only", point = null)) }
    assertTrue(
      "[36.9503, -0.4189]" in analyzedGeometry(PlotRef(entity_id = "drawn-only", geometry = drawn))
    )
    val ring =
      listOf(
        GeoPoint(latitude = -0.418, longitude = 36.949),
        GeoPoint(latitude = -0.418, longitude = 36.951),
        GeoPoint(latitude = -0.420, longitude = 36.951),
      )
    assertTrue(
      "[36.949, -0.418]" in
        analyzedGeometry(
          PlotRef(
            entity_id = "not-uploaded",
            geometry =
              GeometryProtos.polygon(
                listOf(ring.map { GeometryProtos.position(it.latitude, it.longitude) })
              ),
          )
        )
    )

    // Without a drawn point or polygon, a feature without a stored one can't be analyzed.
    assertStatus(Status.Code.INVALID_ARGUMENT, AuthorizationTest.COLLECTOR) {
      analyze(PlotRef(entity_id = "drawn-only"))
    }
    assertStatus(Status.Code.INVALID_ARGUMENT, AuthorizationTest.COLLECTOR) {
      analyze(
        PlotRef(
          entity_id = "drawn-only",
          geometry =
            GeometryProtos.lineString(
              ring.map { GeometryProtos.position(it.latitude, it.longitude) }
            ),
        )
      )
    }
    store.write {
      it.putEntity(
        kenyaPlot.copy(entity_id = "line", point = null, trace = GeoTrace(points = ring))
      )
    }
    assertStatus(Status.Code.INVALID_ARGUMENT, AuthorizationTest.COLLECTOR) {
      analyze(PlotRef(entity_id = "line"))
    }
  }

  @Test
  fun getSceneTileUrl_enforcesSurveyAclAndSceneAllowlist() = runTest {
    val request =
      GetSceneTileUrlRequest(
        survey_id = AuthorizationTest.KENYA,
        scene_id = "LANDSAT/LC08/C02/T1_TOA/LC08_168060_20230814",
        stretch = SceneStretch.SWIR_NIR_RED,
      )
    assertStatus(Status.Code.PERMISSION_DENIED, AuthorizationTest.OUTSIDER) {
      backend.GetSceneTileUrl(request)
    }
    assertStatus(Status.Code.INVALID_ARGUMENT, AuthorizationTest.COLLECTOR) {
      backend.GetSceneTileUrl(request.copy(scene_id = "projects/someone/assets/private"))
    }
    val response = asCaller(AuthorizationTest.COLLECTOR) { backend.GetSceneTileUrl(request) }
    assertTrue(response.url_template.endsWith("/tiles/{z}/{x}/{y}"))
    assertEquals(SceneStretch.SWIR_NIR_RED, response.stretch)
  }
}
