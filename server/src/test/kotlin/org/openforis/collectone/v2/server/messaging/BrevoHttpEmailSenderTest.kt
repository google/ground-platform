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
package org.openforis.collectone.v2.server.messaging

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrevoHttpEmailSenderTest {

  @Test
  fun send_postsExpectedHeadersAndJsonBody_andParsesMessageId() {
    val receivedApiKey = AtomicReference<String>()
    val receivedBody = AtomicReference<String>()
    val statusCode = AtomicInteger(201)
    val responseBody = AtomicReference("""{"messageId":"<20261030.1234@smtp-relay.mailin.fr>"}""")

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/v3/smtp/email") { exchange ->
      receivedApiKey.set(exchange.requestHeaders.getFirst("api-key"))
      receivedBody.set(exchange.requestBody.readBytes().decodeToString())
      val bytes = responseBody.get().toByteArray()
      exchange.sendResponseHeaders(statusCode.get(), bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    try {
      val endpoint = URI.create("http://127.0.0.1:${server.address.port}/v3/smtp/email")
      val sender =
        BrevoHttpEmailSender(
          apiKey = "test-brevo-key",
          defaultFromEmail = "noreply@example.org",
          defaultFromName = "Collect One",
          endpoint = endpoint,
        )
      val email =
        OutboundEmail(
          idempotencyKey = "survey:s1:user@example.org:tok1",
          kind = "survey_invite",
          recipientEmail = "user@example.org",
          subject = "Invitation to Survey",
          htmlBody = "<p>Welcome</p>",
          textBody = "Welcome",
          ctaUrl = "https://collectone.example.org/#join/s1/tok1",
          replyToEmail = "inviter@example.org",
        )

      val outcome = sender.send(email)
      assertEquals(DeliveryStatus.SENT, outcome.status)
      assertEquals("<20261030.1234@smtp-relay.mailin.fr>", outcome.providerMessageId)
      assertEquals("test-brevo-key", receivedApiKey.get())
      val json = receivedBody.get()
      assertTrue(json.contains("\"email\":\"noreply@example.org\""))
      assertTrue(json.contains("\"email\":\"user@example.org\""))
      assertTrue(json.contains("\"email\":\"inviter@example.org\""))
      assertTrue(json.contains("\"subject\":\"Invitation to Survey\""))

      // Non-2xx response maps to FAILED with status details.
      statusCode.set(401)
      responseBody.set("""{"code":"unauthorized","message":"Key not found"}""")
      val failedOutcome = sender.send(email)
      assertEquals(DeliveryStatus.FAILED, failedOutcome.status)
      assertTrue(failedOutcome.errorMessage.contains("Brevo HTTP 401"))
    } finally {
      server.stop(0)
    }
  }
}
