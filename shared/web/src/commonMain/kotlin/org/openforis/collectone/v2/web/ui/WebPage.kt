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
package org.openforis.collectone.v2.web.ui

import org.openforis.collectone.v2.app.domain.model.Organization

/** Top-level pages of the web app, addressable via the URL hash (e.g. `#dashboard`). */
enum class WebPage(val label: String, val hash: String) {
  /** Web landing page: the surveys the signed-in user can open, grouped by organization. */
  WEB_SURVEYS("Surveys", "surveys"),
  /** Full-page 6-step Survey Creation Wizard (`#create-survey`). */
  CREATE_SURVEY("Create survey", "create-survey"),
  WEB_DASHBOARD("Web dashboard", "dashboard"),
  SURVEY_EDITOR("Survey editor", "survey-editor"),
  /** The organizations the signed-in user belongs to, and listed ones they can ask to join. */
  ORGANIZATIONS("Organizations", "organizations"),
  /** One organization's surveys, members, and settings (`#organization/<id>`). */
  ORGANIZATION("Organization", "organization"),
  /**
   * Published impact numbers of the organizations the signed-in user follows and of all of Collect
   * One (`#impact`, or `#impact/<id>` to open one organization's numbers).
   */
  IMPACT("Impact", "impact"),
  /** Platform administration and global library (`#admin`). */
  ADMIN("Admin panel", "admin");

  /** Whether this page shows one survey, and so needs an active survey to open. */
  val needsActiveSurvey: Boolean
    get() = this == WEB_DASHBOARD || this == SURVEY_EDITOR

  companion object {
    /** Legacy hashes kept working after pages were renamed. */
    private val aliases = mapOf("form-editor" to SURVEY_EDITOR, "web" to WEB_DASHBOARD)

    /**
     * The page for a URL hash, ignoring any `/<id>` suffix (see [organizationIdFromHash]), or
     * `null` when the hash names no web page.
     */
    fun fromHash(hash: String?): WebPage? {
      val raw = hash?.removePrefix("#") ?: return null
      if (
        raw == ADMIN.hash ||
          raw == "organization/${Organization.ALL_USERS_ID}" ||
          raw.startsWith("organization/${Organization.ALL_USERS_ID}/")
      ) {
        return ADMIN
      }
      val h = raw.substringBefore('/')
      return entries.firstOrNull { it.hash == h } ?: aliases[h]
    }

    /** Pages whose hash may name an organization (`#<page>/<id>`). */
    private val organizationPages = setOf(ORGANIZATION, IMPACT)

    /**
     * The organization ID in an `#organization/<id>` or `#impact/<id>` hash, or `null` for any
     * other hash.
     */
    fun organizationIdFromHash(hash: String?): String? {
      val h = hash?.removePrefix("#") ?: return null
      val page = fromHash(h) ?: return null
      if (page == ADMIN) return Organization.ALL_USERS_ID
      if (page !in organizationPages) return null
      return h.substringAfter('/', "").takeIf { it.isNotBlank() }
    }

    /**
     * The URL hash for [page], including the organization ID for the organization and Impact pages.
     */
    fun hashFor(page: WebPage, organizationId: String?): String =
      if (page == ADMIN) {
        ADMIN.hash
      } else if (page in organizationPages && !organizationId.isNullOrBlank()) {
        "${page.hash}/$organizationId"
      } else {
        page.hash
      }
  }
}
