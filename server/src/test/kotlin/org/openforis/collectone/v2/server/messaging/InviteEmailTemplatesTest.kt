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

class InviteEmailTemplatesTest {

  @Test
  fun buildSurveyInviteEmail_rendersSubjectDeepLinkAndEscapedHtml() {
    val invite =
      SurveyInviteNotification(
        idempotencyKey = "survey:s-1:alice@example.org:tok-123",
        surveyId = "s-1",
        surveyTitle = "Amazon <Canopy> & Plots",
        organizationId = "org-1",
        organizationName = "FAO Forestry",
        recipientEmail = "alice@example.org",
        role = Role.DATA_COLLECTOR,
        inviteToken = "tok-123",
        inviterEmail = "alex@example.org",
        inviterDisplayName = "Alex Rivera",
      )

    val email =
      InviteEmailTemplates.buildSurveyInviteEmail(
        invite = invite,
        publicWebUrl = "https://collectone.example.org/",
      )

    assertEquals("survey:s-1:alice@example.org:tok-123", email.idempotencyKey)
    assertEquals("survey_invite", email.kind)
    assertEquals("alice@example.org", email.recipientEmail)
    assertEquals("https://collectone.example.org/#join/s-1/tok-123", email.ctaUrl)
    assertEquals(
      "You're invited to collaborate on \"Amazon <Canopy> & Plots\" in Collect One",
      email.subject,
    )
    assertTrue(email.htmlBody.contains("Amazon &lt;Canopy&gt; &amp; Plots"))
    assertTrue(email.htmlBody.contains("FAO Forestry"))
    assertTrue(email.htmlBody.contains("Data Collector"))
    assertTrue(email.htmlBody.contains("https://collectone.example.org/#join/s-1/tok-123"))
    assertTrue(email.textBody.contains("Alex Rivera (alex@example.org)"))
    assertTrue(email.textBody.contains("https://collectone.example.org/#join/s-1/tok-123"))
  }

  @Test
  fun buildOrganizationInviteEmail_rendersSubjectAndOrgJoinDeepLink() {
    val invite =
      OrganizationInviteNotification(
        organizationId = "org-42",
        organizationName = "Kenya Forest Service",
        recipientEmail = "lead@kfs.go.ke",
        role = OrganizationRole.MANAGER,
        inviteToken = "org-tok-999",
        inviterEmail = "admin@kfs.go.ke",
        inviterDisplayName = "Wanjiku Mwangi",
      )

    val email =
      InviteEmailTemplates.buildOrganizationInviteEmail(
        invite = invite,
        publicWebUrl = "https://collectone.example.org",
      )

    assertEquals("org:org-42:lead@kfs.go.ke:org-tok-999", email.idempotencyKey)
    assertEquals("organization_invite", email.kind)
    assertEquals("lead@kfs.go.ke", email.recipientEmail)
    assertEquals("https://collectone.example.org/#org-join/org-42/org-tok-999", email.ctaUrl)
    assertEquals("You're invited to join Kenya Forest Service on Collect One", email.subject)
    assertTrue(email.htmlBody.contains("Kenya Forest Service"))
    assertTrue(email.htmlBody.contains("Assigned Role: Manager"))
    assertTrue(
      email.htmlBody.contains("https://collectone.example.org/#org-join/org-42/org-tok-999")
    )
  }

  @Test
  fun renderHtmlCard_usesAppLightColorSchemeTokens() {
    val invite =
      SurveyInviteNotification(
        surveyId = "s-1",
        surveyTitle = "Canopy Plots",
        recipientEmail = "alice@example.org",
        role = Role.SURVEY_ORGANIZER,
        inviteToken = "tok-123",
      )

    val html = InviteEmailTemplates.buildSurveyInviteEmail(invite).htmlBody

    // AppLightColorScheme tokens: background #F7FBF2, surfaceContainerLowest #FFFFFF,
    // surfaceContainerLow #F1F5EC, primary #36693E, onPrimaryContainer #1D5128,
    // onSurface #181D18, onSurfaceVariant #424940, outlineVariant #C1C9BE.
    for (hex in
      listOf(
        "#F7FBF2",
        "#FFFFFF",
        "#F1F5EC",
        "#36693E",
        "#1D5128",
        "#181D18",
        "#424940",
        "#C1C9BE",
      )) {
      assertTrue(html.contains(hex), "Expected HTML email to use token $hex")
    }
  }
}
