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

import openforis.collectone.v1.survey.OrganizationRole
import openforis.collectone.v1.survey.Role

/**
 * Renders HTML and plain-text invitation emails for survey collaborators and organization members,
 * with deep-link CTA buttons targeting the Collect One web console.
 */
object InviteEmailTemplates {
  const val DEFAULT_PUBLIC_WEB_URL = "https://collectone.example.org"

  fun buildSurveyInviteEmail(
    invite: SurveyInviteNotification,
    publicWebUrl: String = DEFAULT_PUBLIC_WEB_URL,
    fromEmail: String = OutboundEmail.DEFAULT_FROM_EMAIL,
    fromName: String = OutboundEmail.DEFAULT_FROM_NAME,
  ): OutboundEmail {
    val baseUrl = publicWebUrl.trim().trimEnd('/').ifBlank { DEFAULT_PUBLIC_WEB_URL }
    val ctaUrl = "$baseUrl/#join/${invite.surveyId}/${invite.inviteToken}"
    val surveyTitle = invite.surveyTitle.trim().ifBlank { "Untitled survey" }
    val roleLabel = surveyRoleLabel(invite.role)
    val inviterDisplay = formatInviter(invite.inviterDisplayName, invite.inviterEmail)
    val orgLine =
      invite.organizationName.trim().takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
    val subject = "You're invited to collaborate on \"$surveyTitle\" in Collect One"

    val textBody = buildString {
      appendLine("Hello,")
      appendLine()
      appendLine(
        "$inviterDisplay invited you to join the survey \"$surveyTitle\"$orgLine as a $roleLabel in Collect One."
      )
      appendLine()
      appendLine("Accept your invitation and open the survey:")
      appendLine(ctaUrl)
      appendLine()
      appendLine("What you can do as a $roleLabel:")
      appendLine("- ${surveyRoleDescription(invite.role)}")
      appendLine()
      appendLine("Sign in with ${invite.recipientEmail} when prompted to activate your access.")
      appendLine()
      appendLine("— The Collect One Team")
    }
      .trimEnd()

    val htmlBody =
      renderHtmlCard(
        badge = "Survey Invitation",
        headline = "Join \"${escapeHtml(surveyTitle)}\"",
        leadHtml =
          buildString {
            append(escapeHtml(inviterDisplay))
            append(" invited you to collaborate on ")
            append("<strong>${escapeHtml(surveyTitle)}</strong>")
            if (invite.organizationName.isNotBlank()) {
              append(" under <strong>${escapeHtml(invite.organizationName.trim())}</strong>")
            }
            append(" in Collect One.")
          },
        roleLabel = roleLabel,
        roleDescription = surveyRoleDescription(invite.role),
        ctaLabel = "Accept Invitation & Open Survey",
        ctaUrl = ctaUrl,
        recipientEmail = invite.recipientEmail,
      )

    val idempotencyKey =
      invite.idempotencyKey.ifBlank {
        "survey:${invite.surveyId}:${invite.recipientEmail.trim().lowercase()}:${invite.inviteToken}"
      }

    return OutboundEmail(
      idempotencyKey = idempotencyKey,
      kind = "survey_invite",
      recipientEmail = invite.recipientEmail.trim(),
      subject = subject,
      htmlBody = htmlBody,
      textBody = textBody,
      ctaUrl = ctaUrl,
      fromEmail = fromEmail,
      fromName = fromName,
      replyToEmail = invite.inviterEmail.trim().takeIf { it.isNotEmpty() },
    )
  }

  fun buildOrganizationInviteEmail(
    invite: OrganizationInviteNotification,
    publicWebUrl: String = DEFAULT_PUBLIC_WEB_URL,
    fromEmail: String = OutboundEmail.DEFAULT_FROM_EMAIL,
    fromName: String = OutboundEmail.DEFAULT_FROM_NAME,
  ): OutboundEmail {
    val baseUrl = publicWebUrl.trim().trimEnd('/').ifBlank { DEFAULT_PUBLIC_WEB_URL }
    val ctaUrl = "$baseUrl/#org-join/${invite.organizationId}/${invite.inviteToken}"
    val orgName = invite.organizationName.trim().ifBlank { "Organization" }
    val roleLabel = organizationRoleLabel(invite.role)
    val inviterDisplay = formatInviter(invite.inviterDisplayName, invite.inviterEmail)
    val subject = "You're invited to join $orgName on Collect One"

    val textBody = buildString {
      appendLine("Hello,")
      appendLine()
      appendLine(
        "$inviterDisplay invited you to join the organization \"$orgName\" as a $roleLabel on Collect One."
      )
      appendLine()
      appendLine("Accept your invitation:")
      appendLine(ctaUrl)
      appendLine()
      appendLine("What you can do as an organization $roleLabel:")
      appendLine("- ${organizationRoleDescription(invite.role)}")
      appendLine()
      appendLine("Sign in with ${invite.recipientEmail} when prompted to activate your membership.")
      appendLine()
      appendLine("— The Collect One Team")
    }
      .trimEnd()

    val htmlBody =
      renderHtmlCard(
        badge = "Organization Invitation",
        headline = "Join ${escapeHtml(orgName)}",
        leadHtml =
          "${escapeHtml(inviterDisplay)} invited you to join <strong>${escapeHtml(orgName)}</strong> on Collect One.",
        roleLabel = roleLabel,
        roleDescription = organizationRoleDescription(invite.role),
        ctaLabel = "Accept Invitation & Join Organization",
        ctaUrl = ctaUrl,
        recipientEmail = invite.recipientEmail,
      )

    val idempotencyKey =
      invite.idempotencyKey.ifBlank {
        "org:${invite.organizationId}:${invite.recipientEmail.trim().lowercase()}:${invite.inviteToken}"
      }

    return OutboundEmail(
      idempotencyKey = idempotencyKey,
      kind = "organization_invite",
      recipientEmail = invite.recipientEmail.trim(),
      subject = subject,
      htmlBody = htmlBody,
      textBody = textBody,
      ctaUrl = ctaUrl,
      fromEmail = fromEmail,
      fromName = fromName,
      replyToEmail = invite.inviterEmail.trim().takeIf { it.isNotEmpty() },
    )
  }

  internal fun surveyRoleLabel(role: Role): String =
    when (role) {
      Role.SURVEY_ORGANIZER -> "Survey Organizer"
      Role.DATA_COLLECTOR -> "Data Collector"
      Role.VIEWER,
      Role.ROLE_UNSPECIFIED -> "Viewer"
    }

  private fun surveyRoleDescription(role: Role): String =
    when (role) {
      Role.SURVEY_ORGANIZER ->
        "Design forms, manage map layers and collaborators, review submissions, and export data."
      Role.DATA_COLLECTOR ->
        "Collect field and desk submissions, add map features, and work offline or online."
      Role.VIEWER,
      Role.ROLE_UNSPECIFIED ->
        "Explore the survey map, inspect geospatial features, and view collected submissions."
    }

  internal fun organizationRoleLabel(role: OrganizationRole): String =
    when (role) {
      OrganizationRole.MANAGER -> "Manager"
      OrganizationRole.MEMBER,
      OrganizationRole.ORGANIZATION_ROLE_UNSPECIFIED -> "Member"
    }

  private fun organizationRoleDescription(role: OrganizationRole): String =
    when (role) {
      OrganizationRole.MANAGER ->
        "Manage organization settings, member roster, shared libraries, and all organization surveys."
      OrganizationRole.MEMBER,
      OrganizationRole.ORGANIZATION_ROLE_UNSPECIFIED ->
        "Access surveys shared with the organization and use shared concept libraries and imagery."
    }

  private fun formatInviter(displayName: String, email: String): String {
    val name = displayName.trim()
    val mail = email.trim()
    return when {
      name.isNotEmpty() && mail.isNotEmpty() && !name.equals(mail, ignoreCase = true) ->
        "$name ($mail)"
      name.isNotEmpty() -> name
      mail.isNotEmpty() -> mail
      else -> "A Collect One organizer"
    }
  }

  private fun renderHtmlCard(
    badge: String,
    headline: String,
    leadHtml: String,
    roleLabel: String,
    roleDescription: String,
    ctaLabel: String,
    ctaUrl: String,
    recipientEmail: String,
  ): String =
    """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>$headline</title>
    </head>
    <body style="margin:0;padding:24px;background-color:#F7FBF2;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#181D18;">
      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:560px;margin:0 auto;background-color:#FFFFFF;border-radius:14px;border:1px solid #C1C9BE;overflow:hidden;">
        <tr>
          <td style="padding:24px 28px 16px 28px;background-color:#36693E;color:#FFFFFF;">
            <div style="font-size:12px;font-weight:600;letter-spacing:0.06em;text-transform:uppercase;opacity:0.9;">Collect One · ${escapeHtml(badge)}</div>
            <h1 style="margin:8px 0 0 0;font-size:22px;line-height:1.3;font-weight:700;color:#FFFFFF;">$headline</h1>
          </td>
        </tr>
        <tr>
          <td style="padding:24px 28px;">
            <p style="margin:0 0 16px 0;font-size:15px;line-height:1.55;color:#181D18;">$leadHtml</p>
            <div style="margin:0 0 22px 0;padding:14px 16px;background-color:#F1F5EC;border-radius:10px;border:1px solid #C1C9BE;">
              <div style="font-size:12px;font-weight:600;text-transform:uppercase;letter-spacing:0.04em;color:#1D5128;">Assigned Role: ${escapeHtml(roleLabel)}</div>
              <div style="margin-top:4px;font-size:14px;line-height:1.45;color:#424940;">${escapeHtml(roleDescription)}</div>
            </div>
            <div style="margin:24px 0;">
              <a href="${escapeHtml(ctaUrl)}" style="display:inline-block;padding:13px 22px;background-color:#36693E;color:#FFFFFF;text-decoration:none;font-size:15px;font-weight:600;border-radius:8px;">${escapeHtml(ctaLabel)}</a>
            </div>
            <p style="margin:18px 0 6px 0;font-size:13px;line-height:1.5;color:#424940;">
              Or copy and paste this link into your browser:
            </p>
            <p style="margin:0 0 18px 0;font-size:12px;line-height:1.45;font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace;word-break:break-all;color:#36693E;">
              <a href="${escapeHtml(ctaUrl)}" style="color:#36693E;">${escapeHtml(ctaUrl)}</a>
            </p>
            <hr style="border:none;border-top:1px solid #C1C9BE;margin:20px 0;">
            <p style="margin:0;font-size:12px;line-height:1.5;color:#424940;">
              This invitation was sent to <strong>${escapeHtml(recipientEmail.trim())}</strong>. Sign in with this email address when prompted in Collect One.
            </p>
          </td>
        </tr>
      </table>
    </body>
    </html>
    """
      .trimIndent()

  internal fun escapeHtml(text: String): String =
    buildString(text.length + 8) {
      for (ch in text) {
        when (ch) {
          '&' -> append("&amp;")
          '<' -> append("&lt;")
          '>' -> append("&gt;")
          '"' -> append("&quot;")
          '\'' -> append("&#39;")
          else -> append(ch)
        }
      }
    }
}
