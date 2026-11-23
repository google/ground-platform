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

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import okio.Buffer
import org.slf4j.LoggerFactory

/** Outbound transactional email message ready to be sent or logged. */
data class OutboundEmail(
  val idempotencyKey: String,
  val kind: String,
  val recipientEmail: String,
  val subject: String,
  val htmlBody: String,
  val textBody: String,
  val ctaUrl: String,
  val fromEmail: String = DEFAULT_FROM_EMAIL,
  val fromName: String = DEFAULT_FROM_NAME,
  val replyToEmail: String? = null,
) {
  companion object {
    const val DEFAULT_FROM_EMAIL = "noreply@example.org"
    const val DEFAULT_FROM_NAME = "Collect One"
  }
}

/** Result of attempting to send an [OutboundEmail] via an [EmailSender]. */
data class EmailDeliveryOutcome(
  val status: DeliveryStatus,
  val providerMessageId: String = "",
  val errorMessage: String = "",
)

/** Abstraction for sending transactional emails (e.g., via Brevo HTTP API or local log). */
fun interface EmailSender {
  fun send(email: OutboundEmail): EmailDeliveryOutcome
}

/**
 * Default local/test [EmailSender] that logs that an email was produced without calling an external
 * provider. The log leaves out the recipient and the invitation link; the debug Message Inspector
 * (`/debug/messages`) shows the whole email.
 */
object LoggingEmailSender : EmailSender {
  private val logger = LoggerFactory.getLogger(LoggingEmailSender::class.java)

  override fun send(email: OutboundEmail): EmailDeliveryOutcome {
    logger.info("Logged {} email instead of sending it: \"{}\"", email.kind, email.subject)
    return EmailDeliveryOutcome(
      status = DeliveryStatus.LOGGED,
      providerMessageId = "log-${System.currentTimeMillis()}",
    )
  }
}

/**
 * Sends transactional emails over the Brevo v3 HTTP API (`POST https://api.brevo.com/v3/smtp/email`
 * authenticated with the `api-key` header).
 */
class BrevoHttpEmailSender(
  private val apiKey: String,
  private val defaultFromEmail: String = OutboundEmail.DEFAULT_FROM_EMAIL,
  private val defaultFromName: String = OutboundEmail.DEFAULT_FROM_NAME,
  private val endpoint: URI = DEFAULT_ENDPOINT,
  private val httpClient: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : EmailSender {

  override fun send(email: OutboundEmail): EmailDeliveryOutcome {
    if (apiKey.isBlank()) {
      return EmailDeliveryOutcome(
        status = DeliveryStatus.FAILED,
        errorMessage = "BREVO_API_KEY is not configured",
      )
    }
    val payload =
      buildBrevoJsonPayload(
        email = email,
        fromEmail = email.fromEmail.ifBlank { defaultFromEmail },
        fromName = email.fromName.ifBlank { defaultFromName },
      )
    return try {
      val request =
        HttpRequest.newBuilder(endpoint)
          .timeout(Duration.ofSeconds(15))
          .header("accept", "application/json")
          .header("content-type", "application/json; charset=UTF-8")
          .header("api-key", apiKey)
          .POST(HttpRequest.BodyPublishers.ofString(payload))
          .build()
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() in 200..299) {
        val messageId =
          extractMessageId(response.body()).ifBlank { "brevo-${response.statusCode()}" }
        logger.info("Sent {} email via Brevo (messageId={})", email.kind, messageId)
        EmailDeliveryOutcome(status = DeliveryStatus.SENT, providerMessageId = messageId)
      } else {
        val errorBody = response.body().take(400)
        logger.warn("Brevo HTTP {} sending {} email", response.statusCode(), email.kind)
        EmailDeliveryOutcome(
          status = DeliveryStatus.FAILED,
          errorMessage = "Brevo HTTP ${response.statusCode()}: $errorBody",
        )
      }
    } catch (e: Exception) {
      logger.warn("Failed to send {} email via Brevo", email.kind, e)
      EmailDeliveryOutcome(
        status = DeliveryStatus.FAILED,
        errorMessage = e.message ?: e.javaClass.simpleName,
      )
    }
  }

  companion object {
    val DEFAULT_ENDPOINT: URI = URI.create("https://api.brevo.com/v3/smtp/email")

    private val logger = LoggerFactory.getLogger(BrevoHttpEmailSender::class.java)

    internal fun buildBrevoJsonPayload(
      email: OutboundEmail,
      fromEmail: String,
      fromName: String,
    ): String {
      val buffer = Buffer()
      JsonWriter.of(buffer).use { writer ->
        writer.beginObject()
        writer.name("sender").beginObject()
        writer.name("name").value(fromName)
        writer.name("email").value(fromEmail)
        writer.endObject()

        writer.name("to").beginArray()
        writer.beginObject()
        writer.name("email").value(email.recipientEmail)
        writer.endObject()
        writer.endArray()

        if (!email.replyToEmail.isNullOrBlank()) {
          writer.name("replyTo").beginObject()
          writer.name("email").value(email.replyToEmail)
          writer.endObject()
        }

        writer.name("subject").value(email.subject)
        writer.name("htmlContent").value(email.htmlBody)
        writer.name("textContent").value(email.textBody)
        writer.endObject()
      }
      return buffer.readUtf8()
    }

    internal fun extractMessageId(body: String): String = runCatching {
      val buffer = Buffer().writeUtf8(body)
      JsonReader.of(buffer).use { reader ->
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return ""
        reader.beginObject()
        while (reader.hasNext()) {
          when (reader.nextName()) {
            "messageId" -> return reader.nextString()
            "messageIds" -> {
              reader.beginArray()
              val first = if (reader.hasNext()) reader.nextString() else ""
              while (reader.hasNext()) reader.skipValue()
              reader.endArray()
              return first
            }
            else -> reader.skipValue()
          }
        }
        reader.endObject()
      }
      ""
    }
      .getOrDefault("")
  }
}
