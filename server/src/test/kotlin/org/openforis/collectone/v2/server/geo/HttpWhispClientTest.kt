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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.data.Geometry
import org.openforis.collectone.v2.core.geo.GeometryProtos

class HttpWhispClientTest {

  @Test
  fun geometryToWkt_formatsPointLineAndPolygonInLonLatOrder() {
    val point = GeometryProtos.point(latitude = -1.2921, longitude = 36.8219)
    assertEquals("POINT (36.8219 -1.2921)", HttpWhispClient.geometryToWkt(point))

    val line =
      GeometryProtos.lineString(
        listOf(
          GeometryProtos.position(latitude = -1.0, longitude = 36.0),
          GeometryProtos.position(latitude = -1.5, longitude = 36.5),
        )
      )
    assertEquals("LINESTRING (36 -1, 36.5 -1.5)", HttpWhispClient.geometryToWkt(line))

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
      HttpWhispClient.geometryToWkt(polygon),
    )

    assertNull(HttpWhispClient.geometryToWkt(Geometry()))
  }

  @Test
  fun fetchProperties_postsWktWithApiKey_andParsesWhispFeatureProperties() {
    val receivedApiKey = AtomicReference<String>()
    val receivedBody = AtomicReference<String>()
    val statusCode = AtomicInteger(200)
    val responseBody =
      AtomicReference(
        """
        {
          "code": "analysis_completed",
          "data": {
            "type": "FeatureCollection",
            "features": [
              {
                "type": "Feature",
                "properties": {
                  "Country": "Brazil",
                  "Plot_area_ha": 1.25,
                  "GFC_loss_after_2020": 0,
                  "Ind_01_treecover": "low",
                  "In_waterbody": false,
                  "Nested_tags": ["cocoa", "agroforestry"],
                  "Null_field": null
                }
              }
            ]
          }
        }
        """
          .trimIndent()
      )

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/api/submit/wkt") { exchange ->
      receivedApiKey.set(exchange.requestHeaders.getFirst("X-API-KEY"))
      receivedBody.set(exchange.requestBody.readBytes().decodeToString())
      val bytes = responseBody.get().toByteArray()
      exchange.sendResponseHeaders(statusCode.get(), bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    try {
      val endpoint = URI.create("http://127.0.0.1:${server.address.port}/api/submit/wkt")
      val client = HttpWhispClient(apiKey = "whisp-test-key", endpoint = endpoint)
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

      val props = client.fetchProperties(polygon)
      assertEquals("whisp-test-key", receivedApiKey.get())
      assertEquals(
        """{"wkt":"POLYGON ((-55 -10, -54.99 -10, -54.99 -9.99, -55 -9.99, -55 -10))"}""",
        receivedBody.get(),
      )
      assertEquals(
        mapOf(
          "Country" to "Brazil",
          "Plot_area_ha" to "1.25",
          "GFC_loss_after_2020" to "0",
          "Ind_01_treecover" to "low",
          "In_waterbody" to "false",
          "Nested_tags" to """["cocoa","agroforestry"]""",
        ),
        props,
      )

      // Non-completed code returns emptyMap.
      responseBody.set("""{"code":"failed","message":"Invalid geometry"}""")
      assertTrue(client.fetchProperties(polygon).isEmpty())

      // HTTP error returns emptyMap without throwing.
      statusCode.set(401)
      responseBody.set("""{"code":"auth_missing_api_key","message":"API key is required"}""")
      assertTrue(client.fetchProperties(polygon).isEmpty())
    } finally {
      server.stop(0)
    }
  }
}
