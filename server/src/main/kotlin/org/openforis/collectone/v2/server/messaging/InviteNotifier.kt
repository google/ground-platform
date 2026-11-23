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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.slf4j.LoggerFactory

/**
 * Dispatches invitation notifications in-process whenever `MutateSurvey` or `MutateOrganization`
 * commits newly invited or re-invited collaborators/members.
 */
interface InviteNotifier : AutoCloseable {
  fun notifySurveyInvites(invites: List<SurveyInviteNotification>)

  fun notifyOrganizationInvites(invites: List<OrganizationInviteNotification>)

  /** Clears any deduplication cache and recorded message history (used by `POST /debug/reset`). */
  fun reset() {}

  override fun close() {}
}

/** Default no-op [InviteNotifier] used in tests that don't exercise invitation email delivery. */
object NoopInviteNotifier : InviteNotifier {
  override fun notifySurveyInvites(invites: List<SurveyInviteNotification>) {}

  override fun notifyOrganizationInvites(invites: List<OrganizationInviteNotification>) {}
}

/**
 * In-process [InviteNotifier] that renders HTML + plain-text invitation emails with
 * [InviteEmailTemplates], deduplicates deliveries by `idempotencyKey`, sends them via
 * [emailSender], and records every delivery in [messageLogRecorder] for `/debug/messages`.
 */
class EmailInviteNotifier(
  private val emailSender: EmailSender = LoggingEmailSender,
  private val messageLogRecorder: MessageLogRecorder = MessageLogRecorder(),
  private val publicWebUrl: String = InviteEmailTemplates.DEFAULT_PUBLIC_WEB_URL,
  private val fromEmail: String = OutboundEmail.DEFAULT_FROM_EMAIL,
  private val fromName: String = OutboundEmail.DEFAULT_FROM_NAME,
  private val async: Boolean = true,
  private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
    Thread(r, "invite-email-notifier").apply { isDaemon = true }
  },
  private val ownsExecutor: Boolean = true,
) : InviteNotifier {
  private val logger = LoggerFactory.getLogger(EmailInviteNotifier::class.java)

  private val processedIdempotencyKeys = ConcurrentHashMap<String, InviteDeliveryResult>()

  override fun notifySurveyInvites(invites: List<SurveyInviteNotification>) {
    if (invites.isEmpty()) return
    dispatch { deliverSurveyInvites(invites) }
  }

  override fun notifyOrganizationInvites(invites: List<OrganizationInviteNotification>) {
    if (invites.isEmpty()) return
    dispatch { deliverOrganizationInvites(invites) }
  }

  /** Synchronously renders and delivers [invites], returning per-recipient results. */
  fun deliverSurveyInvites(invites: List<SurveyInviteNotification>): List<InviteDeliveryResult> =
    invites.map { invite ->
      require(invite.recipientEmail.isNotBlank()) { "recipientEmail is required" }
      require(invite.surveyId.isNotBlank()) { "surveyId is required" }
      require(invite.inviteToken.isNotBlank()) { "inviteToken is required" }
      val email =
        InviteEmailTemplates.buildSurveyInviteEmail(
          invite = invite,
          publicWebUrl = publicWebUrl,
          fromEmail = fromEmail,
          fromName = fromName,
        )
      deliverOnce(email)
    }

  /** Synchronously renders and delivers [invites], returning per-recipient results. */
  fun deliverOrganizationInvites(
    invites: List<OrganizationInviteNotification>
  ): List<InviteDeliveryResult> = invites.map { invite ->
    require(invite.recipientEmail.isNotBlank()) { "recipientEmail is required" }
    require(invite.organizationId.isNotBlank()) { "organizationId is required" }
    require(invite.inviteToken.isNotBlank()) { "inviteToken is required" }
    val email =
      InviteEmailTemplates.buildOrganizationInviteEmail(
        invite = invite,
        publicWebUrl = publicWebUrl,
        fromEmail = fromEmail,
        fromName = fromName,
      )
    deliverOnce(email)
  }

  override fun reset() {
    processedIdempotencyKeys.clear()
    messageLogRecorder.clear()
  }

  override fun close() {
    if (ownsExecutor) {
      executor.shutdownNow()
    }
  }

  private fun dispatch(block: () -> Unit) {
    if (async) {
      executor.execute { deliverLoggingFailures(block) }
    } else {
      deliverLoggingFailures(block)
    }
  }

  private fun deliverLoggingFailures(block: () -> Unit) {
    runCatching(block).onFailure { e ->
      logger.error("Failed to deliver invite emails", e)
    }
  }

  private fun deliverOnce(email: OutboundEmail): InviteDeliveryResult {
    val key = email.idempotencyKey
    if (key.isNotBlank()) {
      val existing = processedIdempotencyKeys[key]
      if (existing != null && existing.status != DeliveryStatus.FAILED) {
        return existing.copy(status = DeliveryStatus.SKIPPED_DUPLICATE)
      }
    }
    val outcome = emailSender.send(email)
    messageLogRecorder.record(email, outcome)
    val result =
      InviteDeliveryResult(
        idempotencyKey = key,
        recipientEmail = email.recipientEmail,
        status = outcome.status,
        providerMessageId = outcome.providerMessageId,
        errorMessage = outcome.errorMessage,
      )
    if (key.isNotBlank() && outcome.status != DeliveryStatus.FAILED) {
      processedIdempotencyKeys[key] = result
    }
    return result
  }
}
