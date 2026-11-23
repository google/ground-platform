/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.navigation

import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.web.ui.InviteDeepLink

/** What a web console URL opens. */
sealed interface WebLink {
  /** A console page. */
  data class Page(val route: WebRoute) : WebLink

  /** An invitation to a survey or an organization, redeemed once the user is signed in. */
  data class Invite(val invite: InviteDeepLink) : WebLink

  /** A URL that names no console page. */
  data object Unknown : WebLink
}

/**
 * The URLs of the web console's pages, and the pages named by URLs:
 * - `/surveys` (or `/`): [SurveysRoute]
 * - `/surveys/new[/<step>]` and `/organizations/<id>/surveys/new[/<step>]`: [CreateSurveyRoute]
 * - `/surveys/<id>/dashboard` (or `/surveys/<id>`): [SurveyDashboardRoute]
 * - `/surveys/<id>/editor[/sharing | /forms/<key> | /datasets/<key>]`: [SurveyEditorRoute]
 * - `/organizations`: [OrganizationsRoute]
 * - `/organizations/<id>[/<tab>]`: [OrganizationRoute]
 * - `/impact[/<organizationId>]`: [ImpactRoute]
 * - `/admin[/<tab>]`: [AdminRoute]
 *
 * Invitation emails link to `/#join/<surveyId>/<token>` and `/#org-join/<organizationId>/<token>`.
 * The hashes of earlier versions of the console (`/#organizations`, `/#impact/<id>`, ...) still
 * open their pages; their survey pages named no survey, so they open the surveys list.
 */
object WebUrls {
  private const val SURVEYS = "surveys"
  private const val NEW = "new"
  private const val DASHBOARD = "dashboard"
  private const val EDITOR = "editor"
  private const val ORGANIZATIONS = "organizations"
  private const val IMPACT = "impact"
  private const val ADMIN = "admin"

  /** The path of [route]'s URL, e.g. `/surveys/abc/dashboard`. */
  fun pathOf(route: WebRoute): String =
    when (route) {
      SurveysRoute -> path(SURVEYS)
      is SurveyDashboardRoute -> path(SURVEYS, route.surveyId, DASHBOARD)
      is SurveyEditorRoute ->
        path(listOf(SURVEYS, route.surveyId, EDITOR) + route.section?.split('/').orEmpty())
      is CreateSurveyRoute ->
        route.organizationId?.let { path(ORGANIZATIONS, it) }.orEmpty() +
          path(listOfNotNull(SURVEYS, NEW, route.step))
      OrganizationsRoute -> path(ORGANIZATIONS)
      is OrganizationRoute -> path(listOfNotNull(ORGANIZATIONS, route.organizationId, route.tab))
      is ImpactRoute -> path(listOfNotNull(IMPACT, route.organizationId))
      is AdminRoute -> path(listOfNotNull(ADMIN, route.tab))
    }

  /**
   * What the URL with [path] and [hash] (including its `#`, or empty) opens. A hash is only read on
   * the root path, where invitation links and earlier versions of the console put it.
   */
  fun parse(path: String, hash: String = ""): WebLink {
    val segments = path.split('/').filter { it.isNotEmpty() }.map(::decodeSegment)
    if (segments.isEmpty()) {
      val fragment = hash.removePrefix("#")
      inviteFromHash(fragment)?.let {
        return WebLink.Invite(it)
      }
      return WebLink.Page(legacyRoute(fragment) ?: SurveysRoute)
    }
    return routeOf(segments)?.let(WebLink::Page) ?: WebLink.Unknown
  }

  private fun routeOf(segments: List<String>): WebRoute? {
    val rest = segments.drop(1)
    return when (segments.first()) {
      SURVEYS -> surveyRoute(rest)
      ORGANIZATIONS -> organizationRoute(rest)
      IMPACT -> if (rest.size > 1) null else ImpactRoute(rest.firstOrNull())
      ADMIN -> if (rest.size > 1) null else adminRoute(rest.firstOrNull())
      else -> null
    }
  }

  /** `/surveys/...`, without the `surveys` segment. */
  private fun surveyRoute(segments: List<String>): WebRoute? {
    if (segments.isEmpty()) return SurveysRoute
    if (segments.first() == NEW) return createSurveyRoute(organizationId = null, segments.drop(1))
    val surveyId = segments.first()
    val page = segments.getOrNull(1)
    return when {
      page == null || (page == DASHBOARD && segments.size == 2) -> SurveyDashboardRoute(surveyId)
      page == EDITOR && segments.size == 2 -> SurveyEditorRoute(surveyId)
      page == EDITOR -> {
        val section = segments.drop(2).joinToString("/")
        RouteSegments.editorSection(section)?.let { SurveyEditorRoute(surveyId, section) }
      }
      else -> null
    }
  }

  private fun createSurveyRoute(organizationId: String?, segments: List<String>): WebRoute? {
    val step = segments.singleOrNull()
    return when {
      segments.isEmpty() -> CreateSurveyRoute(organizationId)
      step != null && RouteSegments.wizardStep(step) != null ->
        CreateSurveyRoute(organizationId, step)
      else -> null
    }
  }

  /** `/organizations/...`, without the `organizations` segment. */
  private fun organizationRoute(segments: List<String>): WebRoute? {
    val organizationId = segments.firstOrNull() ?: return OrganizationsRoute
    val rest = segments.drop(1)
    val tab = rest.singleOrNull()
    return when {
      rest.size >= 2 && rest[0] == SURVEYS && rest[1] == NEW ->
        createSurveyRoute(organizationId, rest.drop(2))
      rest.size > 1 -> null
      organizationId == Organization.ALL_USERS_ID -> adminRoute(tab)
      tab != null && RouteSegments.organizationTab(tab) == null -> null
      else -> OrganizationRoute(organizationId, tab)
    }
  }

  private fun adminRoute(tab: String?): WebRoute? =
    if (tab == null || RouteSegments.organizationTab(tab) != null) AdminRoute(tab) else null

  /** The page named by a hash of an earlier version of the console, or `null`. */
  private fun legacyRoute(fragment: String): WebRoute? {
    val page = fragment.substringBefore('/')
    val id = fragment.substringAfter('/', missingDelimiterValue = "").ifBlank { null }
    return when (page) {
      SURVEYS,
      DASHBOARD,
      "web",
      "survey-editor",
      "form-editor" -> SurveysRoute
      "create-survey" -> CreateSurveyRoute()
      ORGANIZATIONS -> OrganizationsRoute
      "organization" ->
        when (id) {
          null -> OrganizationsRoute
          Organization.ALL_USERS_ID -> AdminRoute()
          else -> OrganizationRoute(id)
        }
      IMPACT -> ImpactRoute(id)
      ADMIN -> AdminRoute()
      else -> null
    }
  }

  /**
   * Parses a survey (`join/<surveyId>/<token>`) or organization
   * (`org-join/<organizationId>/<token>`) invitation from a URL [fragment] (without `#`), or
   * returns `null` if it isn't one.
   */
  private fun inviteFromHash(fragment: String): InviteDeepLink? {
    val parts = fragment.trim('/').split('/')
    val kind =
      when (parts.first()) {
        "join" -> InviteDeepLink.Kind.SURVEY
        "org-join" -> InviteDeepLink.Kind.ORGANIZATION
        else -> null
      }
    val targetId = parts.getOrNull(1)?.trim().orEmpty()
    val token = parts.drop(2).joinToString("/").trim()
    return if (kind == null || targetId.isEmpty() || token.isEmpty()) {
      null
    } else {
      InviteDeepLink(kind = kind, targetId = targetId, token = token)
    }
  }

  private fun path(vararg segments: String): String = path(segments.asList())

  private fun path(segments: List<String>): String =
    segments.joinToString(separator = "/", prefix = "/", transform = ::encodeSegment)

  /** RFC 3986 section 2.3: unreserved characters stay as they are; others are percent-encoded. */
  private fun encodeSegment(segment: String): String = buildString {
    for (byte in segment.encodeToByteArray()) {
      val char = (byte.toInt() and 0xFF).toChar()
      if ((char.isLetterOrDigit() && char.code < 0x80) || char in "-._~") {
        append(char)
      } else {
        append('%').append(HEX[byte.toInt() shr 4 and 0xF]).append(HEX[byte.toInt() and 0xF])
      }
    }
  }

  /** Reverses [encodeSegment]; malformed escapes are kept as they are. */
  private fun decodeSegment(segment: String): String {
    if ('%' !in segment) return segment
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < segment.length) {
      val value =
        if (segment[i] == '%' && i + 2 <= segment.lastIndex) {
          segment.substring(i + 1, i + 3).toIntOrNull(radix = 16)
        } else {
          null
        }
      if (value != null) {
        bytes += value.toByte()
        i += 3
      } else {
        bytes += segment[i].toString().encodeToByteArray().toList()
        i++
      }
    }
    return bytes.toByteArray().decodeToString()
  }

  private const val HEX = "0123456789ABCDEF"
}
