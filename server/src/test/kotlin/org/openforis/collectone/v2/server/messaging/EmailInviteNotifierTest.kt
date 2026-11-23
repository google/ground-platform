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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import openforis.collectone.v1.survey.OrganizationRole
import openforis.collectone.v1.survey.Role

class EmailInviteNotifierTest {

  @Test
  fun deliverSurveyAndOrganizationInvites_deduplicatesByIdempotencyKey_andResets() {
    val sentEmails = mutableListOf<OutboundEmail>()
    val sender = EmailSender { email ->
      sentEmails += email
      EmailDeliveryOutcome(
        status = DeliveryStatus.SENT,
        providerMessageId = "msg-${sentEmails.size}",
      )
    }
    val logRecorder = MessageLogRecorder()
    val notifier =
      EmailInviteNotifier(
        emailSender = sender,
        messageLogRecorder = logRecorder,
        publicWebUrl = "https://collectone.example.org",
        async = false,
      )

    try {
      val surveyInvite =
        SurveyInviteNotification(
          idempotencyKey = "survey:s-1:collector@example.org:tok-1",
          surveyId = "s-1",
          surveyTitle = "Forest Inventory",
          recipientEmail = "collector@example.org",
          role = Role.DATA_COLLECTOR,
          inviteToken = "tok-1",
          inviterEmail = "owner@example.org",
        )

      val firstResults = notifier.deliverSurveyInvites(listOf(surveyInvite))
      assertEquals(1, firstResults.size)
      assertEquals(DeliveryStatus.SENT, firstResults.single().status)
      assertEquals("msg-1", firstResults.single().providerMessageId)
      assertEquals(1, sentEmails.size)

      // Replaying the same idempotency key skips sending a duplicate email.
      val duplicateResults = notifier.deliverSurveyInvites(listOf(surveyInvite))
      assertEquals(DeliveryStatus.SKIPPED_DUPLICATE, duplicateResults.single().status)
      assertEquals(1, sentEmails.size)

      // Rotating the invite token changes the idempotency key and sends a fresh email.
      val rotatedInvite =
        surveyInvite.copy(
          idempotencyKey = "survey:s-1:collector@example.org:tok-2",
          inviteToken = "tok-2",
        )
      val rotatedResults = notifier.deliverSurveyInvites(listOf(rotatedInvite))
      assertEquals(DeliveryStatus.SENT, rotatedResults.single().status)
      assertEquals(2, sentEmails.size)

      // Organization invite
      val orgInvite =
        OrganizationInviteNotification(
          idempotencyKey = "org:o-1:member@example.org:org-tok-1",
          organizationId = "o-1",
          organizationName = "Geo Makers",
          recipientEmail = "member@example.org",
          role = OrganizationRole.MEMBER,
          inviteToken = "org-tok-1",
        )
      val orgResults = notifier.deliverOrganizationInvites(listOf(orgInvite))
      assertEquals(DeliveryStatus.SENT, orgResults.single().status)
      assertEquals(3, sentEmails.size)
      assertEquals(3, logRecorder.snapshot().size)

      // Reset clears both the deduplication cache and the message log.
      notifier.reset()
      assertTrue(logRecorder.snapshot().isEmpty())
      val afterResetResults = notifier.deliverSurveyInvites(listOf(surveyInvite))
      assertEquals(DeliveryStatus.SENT, afterResetResults.single().status)
      assertEquals(4, sentEmails.size)
    } finally {
      notifier.close()
    }
  }
}
