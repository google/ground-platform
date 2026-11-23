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
package org.openforis.collectone.v2.web.ui.organization

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_tab_cost_controls
import org.openforis.collectone.v2.web.resources.org_tab_cost_tracking
import org.openforis.collectone.v2.web.resources.org_tab_details
import org.openforis.collectone.v2.web.resources.org_tab_dictionary
import org.openforis.collectone.v2.web.resources.org_tab_imagery
import org.openforis.collectone.v2.web.resources.org_tab_members
import org.openforis.collectone.v2.web.resources.org_tab_mobile_app
import org.openforis.collectone.v2.web.resources.org_tab_purposes
import org.openforis.collectone.v2.web.resources.org_tab_surveys
import org.openforis.collectone.v2.web.resources.org_tab_templates

/**
 * Sections of the web organization page, in the order they appear in its left panel. Details,
 * Imagery, and the library tabs (Purposes, Dictionary, and Templates) are read-only for anyone who
 * isn't a Manager. The organization's impact numbers are on the Impact page (`#impact/<id>`).
 */
enum class OrganizationTab(val label: StringResource) {
  DETAILS(Res.string.org_tab_details),
  SURVEYS(Res.string.org_tab_surveys),
  MEMBERS(Res.string.org_tab_members),
  IMAGERY_SOURCES(Res.string.org_tab_imagery),
  PURPOSES(Res.string.org_tab_purposes),
  DICTIONARY(Res.string.org_tab_dictionary),
  TEMPLATES(Res.string.org_tab_templates),
  COST_CONTROLS(Res.string.org_tab_cost_controls),
  COST_TRACKING(Res.string.org_tab_cost_tracking),
  MOBILE_APP(Res.string.org_tab_mobile_app),
}
