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
package org.openforis.collectone.v2.server.geo

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.data.Geometry
import org.openforis.collectone.v2.core.geo.GeometryProtos

class HttpAgStackRegistryClientTest {

  @Test
  fun fieldBoundaryAndPointToWkt_formatCoordinatesInLonLatOrder() {
    val point = GeometryProtos.point(latitude = 30.90706, longitude = 74.78209)
    assertEquals("POINT (74.78209 30.90706)", HttpAgStackRegistryClient.pointToWkt(point.point))

    val polygon =
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
    assertEquals(
      "POLYGON ((-55 -10, -54.99 -10, -54.99 -9.99, -55 -9.99, -55 -10))",
      HttpAgStackRegistryClient.fieldBoundaryToWkt(polygon),
    )

    assertNull(HttpAgStackRegistryClient.fieldBoundaryToWkt(Geometry()))
  }

  @Test
  fun registerGeometry_postsFieldBoundaryAndPoint_andParsesRegistryResponses() {
    val receivedPath = AtomicReference<String>()
    val receivedApiKey = AtomicReference<String>()
    val receivedAuth = AtomicReference<String>()
    val receivedBody = AtomicReference<String>()
    val statusCode = AtomicInteger(200)
    val expectedPolygonGeoId = "0d8b4afb3b3332f75cf5b1889b0564a9e7a80f4bad239a9a593e1665210c3079"
    val expectedPointGeoId = "8c5c1d6ae4c50c574a05b44c0af6797c2001f518a35116c4642bd48edf769024"
    val responseBody =
      AtomicReference("""{"Geo Id": "$expectedPolygonGeoId", "matched fields": []}""")

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange ->
      receivedPath.set(exchange.requestURI.path)
      receivedApiKey.set(exchange.requestHeaders.getFirst("X-API-KEY"))
      receivedAuth.set(exchange.requestHeaders.getFirst("Authorization"))
      receivedBody.set(exchange.requestBody.readBytes().decodeToString())
      val bytes = responseBody.get().toByteArray()
      exchange.sendResponseHeaders(statusCode.get(), bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    try {
      val baseUrl = URI.create("http://127.0.0.1:${server.address.port}")
      val client =
        HttpAgStackRegistryClient(
          baseUrl = baseUrl,
          apiKey = "agstack-key",
          authToken = "secret-token",
        )
      val polygon =
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

      val polyResult = assertNotNull(client.registerGeometry(polygon, "ent-1"))
      assertEquals("/register-field-boundary", receivedPath.get())
      assertEquals("agstack-key", receivedApiKey.get())
      assertEquals("Bearer secret-token", receivedAuth.get())
      assertEquals(
        """{"wkt":"POLYGON ((-55 -10, -54.99 -10, -54.99 -9.99, -55 -9.99, -55 -10))","s2_index":"L13,L20","threshold":95}""",
        receivedBody.get(),
      )
      assertEquals(expectedPolygonGeoId, polyResult.geoId)
      assertFalse(polyResult.isDuplicate)

      // Duplicate overlap response returning matched fields.
      statusCode.set(400)
      responseBody.set("""{"matched fields": ["$expectedPolygonGeoId"]}""")
      val dupResult = assertNotNull(client.registerGeometry(polygon, "ent-2"))
      assertEquals(expectedPolygonGeoId, dupResult.geoId)
      assertTrue(dupResult.isDuplicate)
      assertEquals(listOf(expectedPolygonGeoId), dupResult.matchedGeoIds)

      // Point registration calls `/register-point`.
      statusCode.set(200)
      responseBody.set("""{"Geo Id": "$expectedPointGeoId"}""")
      val point = GeometryProtos.point(latitude = 30.90706, longitude = 74.78209)
      val pointResult = assertNotNull(client.registerGeometry(point, "ent-3"))
      assertEquals("/register-point", receivedPath.get())
      assertEquals("""{"wkt":"POINT (74.78209 30.90706)"}""", receivedBody.get())
      assertEquals(expectedPointGeoId, pointResult.geoId)
      assertFalse(pointResult.isDuplicate)
    } finally {
      server.stop(0)
    }
  }
}
