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
package org.openforis.collectone.v2.server.web

import com.linecorp.armeria.server.Server
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.core.ids.RecordIds
import org.openforis.collectone.v2.server.app.UserDataServer
import org.openforis.collectone.v2.server.app.UserDataServerTest.Companion.KENYA
import org.openforis.collectone.v2.server.auth.Authenticator
import org.openforis.collectone.v2.server.service.UserDataBackend
import org.openforis.collectone.v2.server.service.sampleBackend

class KmlExportWebServiceTest {
  private lateinit var backend: UserDataBackend
  private lateinit var server: Server
  private val client = HttpClient.newHttpClient()
  private val baseUrl
    get() = "http://127.0.0.1:${server.activeLocalPort()}"

  @BeforeTest
  fun start() {
    backend = sampleBackend()
    server = UserDataServer.create(backend, port = 0)
    server.start().join()
  }

  @AfterTest
  fun stop() {
    server.stop().join()
  }

  @Test
  fun getSurveyKmlServesMultiFolderDocumentWithCorsHeaders() {
    val request =
      HttpRequest.newBuilder(
          URI(
            "$baseUrl/kml/surveys/$KENYA.kml?balloonUrl=https://example.org/balloon/balloon-form.html"
          )
        )
        .header("Origin", "https://earth.google.com")
        .GET()
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    assertEquals(200, response.statusCode())
    assertEquals("*", response.headers().firstValue("access-control-allow-origin").orElse(""))
    assertTrue(
      response
        .headers()
        .firstValue("content-type")
        .orElse("")
        .startsWith("application/vnd.google-earth.kml+xml")
    )

    val body = response.body()
    // Kenya sample survey has multiple map layers, each rendered as its own <Folder>.
    assertTrue("<Folder id=" in body, body)
    assertTrue("https://example.org/balloon/balloon-form.html" in body)
    assertTrue("<gx:displayMode>panel</gx:displayMode>" in body)
    assertTrue("<Placemark id=" in body)
    assertTrue("collectone:open-external-url" in body)
    // Exported KMLs never embed caller tokens, roles, or email addresses.
    assertFalse("maya.lin@" in body)
    assertFalse("token:" in body)
    assertFalse("devRole:" in body)

    val stylePayloads =
      Regex("""var sharedForms = "([^"]+)";""")
        .findAll(body)
        .map { match -> String(Base64.getUrlDecoder().decode(match.groupValues[1])) }
        .toList()
    assertTrue(
      stylePayloads.any {
        "\"id\":\"form-eudr-baseline\"" in it && "\"id\":\"form-household-interview\"" in it
      }
    )
    assertTrue(stylePayloads.none { "\"id\":\"form-pest-disease-sighting\"" in it })
  }

  @Test
  fun rejectsUnsafeBalloonUrlSchemes() {
    val request =
      HttpRequest.newBuilder(URI("$baseUrl/kml/surveys/$KENYA.kml?balloonUrl=javascript:alert(1)"))
        .GET()
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    assertEquals(400, response.statusCode())
  }

  @Test
  fun firebaseModeServesSurveyKmlWithoutCredentialsAndDefaultsBalloonUrl() {
    val fbServer =
      UserDataServer.create(
        sampleBackend(),
        port = 0,
        authenticator = Authenticator.firebase("test-project"),
        publicWebUrl = "https://collectone.example.org",
      )
    fbServer.start().join()
    try {
      val response =
        client.send(
          HttpRequest.newBuilder(
              URI("http://127.0.0.1:${fbServer.activeLocalPort()}/kml/surveys/$KENYA.kml")
            )
            .GET()
            .build(),
          HttpResponse.BodyHandlers.ofString(),
        )
      assertEquals(200, response.statusCode())
      val body = response.body()
      assertTrue("https://collectone.example.org/balloon/balloon-form.html" in body, body)
      assertFalse("maya.lin@" in body)
      assertFalse("token:" in body)
    } finally {
      fbServer.stop().join()
    }
  }

  @Test
  fun pkceBalloonAuthRelayCompletesClaimsOnceAndRejectsWrongVerifier() {
    val sessionId = RecordIds.newId()
    val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    val challenge = BalloonAuthStore.sha256Base64Url(verifier)

    val beforeComplete =
      client.send(
        HttpRequest.newBuilder(URI("$baseUrl/balloon/auth/claim"))
          .header("Content-Type", "application/json")
          .POST(
            HttpRequest.BodyPublishers.ofString(
              """{"sessionId":"$sessionId","codeVerifier":"$verifier"}"""
            )
          )
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(202, beforeComplete.statusCode())

    val completeResp =
      client.send(
        HttpRequest.newBuilder(URI("$baseUrl/balloon/auth/complete"))
          .header("Content-Type", "application/json")
          .header(Authenticator.DEV_USER_ID_HEADER, "uid-viewer-1")
          .header(Authenticator.DEV_USER_EMAIL_HEADER, "viewer@example.org")
          .header(Authenticator.DEV_USER_NAME_HEADER, "Viewer One")
          .header(Authenticator.DEV_ROLE_HEADER, "admin")
          .POST(
            HttpRequest.BodyPublishers.ofString(
              """{"sessionId":"$sessionId","codeChallenge":"$challenge"}"""
            )
          )
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, completeResp.statusCode(), completeResp.body())

    val wrongVerifier =
      client.send(
        HttpRequest.newBuilder(URI("$baseUrl/balloon/auth/claim"))
          .header("Content-Type", "application/json")
          .POST(
            HttpRequest.BodyPublishers.ofString(
              """{"sessionId":"$sessionId","codeVerifier":"0000000000000000000000000000000000000000000"}"""
            )
          )
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(403, wrongVerifier.statusCode())

    val validClaim =
      client.send(
        HttpRequest.newBuilder(URI("$baseUrl/balloon/auth/claim"))
          .header("Content-Type", "application/json")
          .POST(
            HttpRequest.BodyPublishers.ofString(
              """{"sessionId":"$sessionId","codeVerifier":"$verifier"}"""
            )
          )
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, validClaim.statusCode())
    assertTrue("\"collectorEmail\":\"viewer@example.org\"" in validClaim.body(), validClaim.body())

    // Single-use: a replay of the same sessionId + codeVerifier cannot claim credentials again.
    val replayClaim =
      client.send(
        HttpRequest.newBuilder(URI("$baseUrl/balloon/auth/claim"))
          .header("Content-Type", "application/json")
          .POST(
            HttpRequest.BodyPublishers.ofString(
              """{"sessionId":"$sessionId","codeVerifier":"$verifier"}"""
            )
          )
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(202, replayClaim.statusCode())
  }

  @Test
  fun networkLinkModeAndEtagConditionalGetReturn304WhenUnchanged() {
    val balloonParam = "balloonUrl=https://example.org/balloon/balloon-form.html"
    val networkLinkResp =
      get("/kml/surveys/$KENYA.kml?mode=networklink&refreshInterval=15&$balloonParam")
    assertEquals(200, networkLinkResp.statusCode())
    val nlBody = networkLinkResp.body()
    assertTrue("<NetworkLink id=\"collectone-networklink-$KENYA\">" in nlBody, nlBody)
    assertTrue("<name>Live Layers</name>" in nlBody, nlBody)
    assertFalse("— Live Layers" in nlBody, nlBody)
    assertTrue("<refreshVisibility>0</refreshVisibility>" in nlBody, nlBody)
    assertTrue("<flyToView>0</flyToView>" in nlBody, nlBody)
    assertTrue("<refreshMode>onInterval</refreshMode>" in nlBody, nlBody)
    assertTrue("<refreshInterval>15</refreshInterval>" in nlBody, nlBody)
    assertTrue("/kml/surveys/$KENYA.kml?balloonUrl=" in nlBody, nlBody)
    assertTrue("&amp;source=networklink" in nlBody, nlBody)

    val networkLinkTargetPoll = get("/kml/surveys/$KENYA.kml?$balloonParam&source=networklink")
    assertEquals(200, networkLinkTargetPoll.statusCode())
    val nlTargetBody = networkLinkTargetPoll.body()
    assertTrue("    <name>Map Layers</name>" in nlTargetBody, nlTargetBody)
    assertTrue("<Folder id=" in nlTargetBody, nlTargetBody)
    assertFalse("All Form Field Types Showcase (Kenya Coffee)</name>" in nlTargetBody, nlTargetBody)

    val firstPoll = get("/kml/surveys/$KENYA.kml?$balloonParam")
    assertEquals(200, firstPoll.statusCode())
    val etag = firstPoll.headers().firstValue("etag").orElse("")
    assertTrue(etag.startsWith("\"") && etag.endsWith("\""), "Expected strong ETag, got: $etag")

    val kmlBody = firstPoll.body()
    assertTrue("<Style id=\"collectone-layer-1-in-progress\">" in kmlBody)
    assertTrue("<Style id=\"collectone-layer-1-completed\">" in kmlBody)
    assertTrue("<name>✓ Plot NYR-104 • Kamau Family Parcel</name>" in kmlBody, kmlBody)
    assertTrue("<name>◐ Plot NYR-108 • Njeri Cooperative Block B</name>" in kmlBody, kmlBody)
    assertTrue("<name>○ Plot NYR-112 • Kariuki Hillside Parcel</name>" in kmlBody, kmlBody)
    assertTrue(
      "<Data name=\"c1_entity_title\"><value>Plot NYR-112 • Kariuki Hillside Parcel</value></Data>" in
        kmlBody
    )
    assertFalse("\$[c1_status]" in kmlBody)
    assertFalse("\$[c1_marker_symbol]" in kmlBody)

    val conditionalPoll =
      get("/kml/surveys/$KENYA.kml?$balloonParam", mapOf("If-None-Match" to etag))
    assertEquals(304, conditionalPoll.statusCode())
    assertEquals("", conditionalPoll.body())
  }

  @Test
  fun unsubmittedBalloonDraftDoesNotAlterPlacemarkStatusOrEtag() {
    val pendingEntityId = "00000009-1699-51bb-b7a3-f90a60bedd4d"
    val beforeDraft =
      client.send(
        HttpRequest.newBuilder(URI("$baseUrl/kml/surveys/$KENYA.kml")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    val etagBefore = beforeDraft.headers().firstValue("etag").orElse("")
    assertTrue("<name>○ Plot NYR-112 • Kariuki Hillside Parcel</name>" in beforeDraft.body())

    val saveResp =
      client.send(
        HttpRequest.newBuilder(URI("$baseUrl/balloon/state/$KENYA/$pendingEntityId"))
          .header("Content-Type", "application/json")
          .POST(
            HttpRequest.BodyPublishers.ofString(
              """{"formId":"form-eudr-baseline","recordBase64":"dGVzdC1kcmFmdA"}"""
            )
          )
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, saveResp.statusCode(), saveResp.body())

    val withDraft =
      client.send(
        HttpRequest.newBuilder(URI("$baseUrl/kml/surveys/$KENYA.kml")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(etagBefore, withDraft.headers().firstValue("etag").orElse(""))
    assertTrue(
      "<name>○ Plot NYR-112 • Kariuki Hillside Parcel</name>" in withDraft.body(),
      withDraft.body(),
    )
    assertFalse(
      "<name>◐ Plot NYR-112 • Kariuki Hillside Parcel</name>" in withDraft.body(),
      withDraft.body(),
    )
    assertTrue(
      "<Data name=\"c1_entity_title\"><value>Plot NYR-112 • Kariuki Hillside Parcel</value></Data>" in
        withDraft.body()
    )
  }

  @Test
  fun balloonStateEndpointReturnsLiveStatusAndPersistsInProgressDrafts() {
    val saveResp =
      client.send(
        HttpRequest.newBuilder(URI("$baseUrl/balloon/state/$KENYA/parcel-1"))
          .header("Content-Type", "application/json")
          .POST(
            HttpRequest.BodyPublishers.ofString(
              """{"formId":"form-eudr-baseline","recordBase64":"dGVzdC1kcmFmdA"}"""
            )
          )
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, saveResp.statusCode(), saveResp.body())

    val getResp =
      client.send(
        HttpRequest.newBuilder(URI("$baseUrl/balloon/state/$KENYA/parcel-1")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, getResp.statusCode())
    val stateBody = getResp.body()
    assertTrue("\"draftFormId\":\"form-eudr-baseline\"" in stateBody, stateBody)
    assertTrue("\"draftRecordBase64\":\"dGVzdC1kcmFmdA\"" in stateBody, stateBody)

    val deleteResp =
      client.send(
        HttpRequest.newBuilder(URI("$baseUrl/balloon/state/$KENYA/parcel-1")).DELETE().build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, deleteResp.statusCode())

    val afterDelete =
      client.send(
        HttpRequest.newBuilder(URI("$baseUrl/balloon/state/$KENYA/parcel-1")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, afterDelete.statusCode())
    assertTrue("\"draftRecordBase64\":\"\"" in afterDelete.body(), afterDelete.body())
  }

  private fun get(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
    val builder = HttpRequest.newBuilder(URI("$baseUrl$path")).GET()
    headers.forEach { (name, value) -> builder.header(name, value) }
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
  }
}
