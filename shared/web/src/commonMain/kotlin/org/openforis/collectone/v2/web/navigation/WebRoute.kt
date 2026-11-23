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

import kotlinx.serialization.Serializable

/**
 * A page of the web console: a destination of its `NavHost` and a URL of its own (see
 * [WebUrls.pathOf]).
 *
 * Optional arguments that name a part of a page (a tab, a wizard step, an editor section) are URL
 * segments (see [WebUrls]); `null` is the page's default part.
 */
sealed interface WebRoute

/** The landing page: the surveys the signed-in user can open, grouped by organization. */
@Serializable data object SurveysRoute : WebRoute

/** The map and data tables of the survey [surveyId]. */
@Serializable data class SurveyDashboardRoute(val surveyId: String) : WebRoute

/**
 * The Survey editor of [surveyId], showing [section]: `sharing`, `forms/<key>`, `datasets/<key>`,
 * or `null` for the survey details.
 */
@Serializable
data class SurveyEditorRoute(val surveyId: String, val section: String? = null) : WebRoute

/**
 * The 6-step Survey Creation Wizard, pre-selecting [organizationId] when it was opened from an
 * organization, on [step] (`null` for the first step).
 */
@Serializable
data class CreateSurveyRoute(val organizationId: String? = null, val step: String? = null) :
  WebRoute

/** The organizations the signed-in user belongs to, and listed ones they can ask to join. */
@Serializable data object OrganizationsRoute : WebRoute

/** One organization's page, on [tab] (`null` for its default tab). */
@Serializable
data class OrganizationRoute(val organizationId: String, val tab: String? = null) : WebRoute

/**
 * The Impact page: published numbers of the organizations the signed-in user follows, focused on
 * [organizationId] (`null` for the overview).
 */
@Serializable data class ImpactRoute(val organizationId: String? = null) : WebRoute

/** Platform administration and the global library, on [tab] (`null` for its default tab). */
@Serializable data class AdminRoute(val tab: String? = null) : WebRoute
