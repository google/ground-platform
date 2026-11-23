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

import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.ui.organization.OrganizationTab
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorSection

/**
 * The URL segments that name a part of a page in a [WebRoute] argument: an organization tab, a
 * wizard step, or a Survey editor section. Each part has one segment; a page's default part has
 * none (`null`), so its URL stays short.
 */
object RouteSegments {
  private const val SHARING = "sharing"
  private const val FORMS = "forms"
  private const val DATASETS = "datasets"

  /** `MODE_AND_PURPOSE` -> `mode-and-purpose`. */
  private val Enum<*>.slug: String
    get() = name.lowercase().replace('_', '-')

  /** The segment of [tab] on a page whose default tab is [defaultTab]. */
  fun of(tab: OrganizationTab, defaultTab: OrganizationTab): String? =
    tab.slug.takeIf { tab != defaultTab }

  /** The organization tab named by [segment], or `null` if it names none. */
  fun organizationTab(segment: String): OrganizationTab? =
    OrganizationTab.entries.firstOrNull { it.slug == segment }

  /** The segment of [step]; the first step has none. */
  fun of(step: CreateSurveyWizardStep): String? =
    step.slug.takeIf { step != CreateSurveyWizardStep.entries.first() }

  /** The wizard step named by [segment], or `null` if it names none. */
  fun wizardStep(segment: String): CreateSurveyWizardStep? =
    CreateSurveyWizardStep.entries.firstOrNull { it.slug == segment }

  /** The segment of [section]; the survey details have none. */
  fun of(section: SurveyEditorSection): String? =
    when (section) {
      SurveyEditorSection.Details -> null
      SurveyEditorSection.Sharing -> SHARING
      is SurveyEditorSection.Form -> "$FORMS/${section.key}"
      is SurveyEditorSection.Dataset -> "$DATASETS/${section.key}"
    }

  /** The Survey editor section named by [segment], or `null` if it names none. */
  fun editorSection(segment: String): SurveyEditorSection? {
    if (segment == SHARING) return SurveyEditorSection.Sharing
    val kind = segment.substringBefore('/')
    val key = segment.substringAfter('/', missingDelimiterValue = "")
    if (key.isEmpty()) return null
    return when (kind) {
      FORMS -> SurveyEditorSection.Form(key)
      DATASETS -> SurveyEditorSection.Dataset(key)
      else -> null
    }
  }
}
