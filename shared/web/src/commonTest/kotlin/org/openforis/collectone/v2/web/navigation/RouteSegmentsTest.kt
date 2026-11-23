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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.ui.organization.OrganizationTab
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorSection

class RouteSegmentsTest {

  @Test
  fun everyOrganizationTab_roundTrips_andTheDefaultHasNoSegment() {
    for (tab in OrganizationTab.entries) {
      val segment = RouteSegments.of(tab, defaultTab = OrganizationTab.DETAILS)
      if (tab == OrganizationTab.DETAILS) {
        assertNull(segment)
      } else {
        assertEquals(tab, RouteSegments.organizationTab(checkNotNull(segment)))
      }
    }
    assertEquals(
      "imagery-sources",
      RouteSegments.of(OrganizationTab.IMAGERY_SOURCES, OrganizationTab.DETAILS),
    )
    assertEquals("details", RouteSegments.of(OrganizationTab.DETAILS, OrganizationTab.MEMBERS))
    assertNull(RouteSegments.organizationTab("history"))
  }

  @Test
  fun everyWizardStep_roundTrips_andTheFirstHasNoSegment() {
    assertNull(RouteSegments.of(CreateSurveyWizardStep.OVERVIEW))
    for (step in CreateSurveyWizardStep.entries.drop(1)) {
      assertEquals(step, RouteSegments.wizardStep(checkNotNull(RouteSegments.of(step))))
    }
    assertEquals("mode-and-purpose", RouteSegments.of(CreateSurveyWizardStep.MODE_AND_PURPOSE))
    assertNull(RouteSegments.wizardStep("step-7"))
  }

  @Test
  fun editorSections_roundTrip_andTheDetailsHaveNoSegment() {
    assertNull(RouteSegments.of(SurveyEditorSection.Details))
    for (section in
      listOf(
        SurveyEditorSection.Sharing,
        SurveyEditorSection.Form("q1"),
        SurveyEditorSection.Dataset("d1"),
      )) {
      assertEquals(section, RouteSegments.editorSection(checkNotNull(RouteSegments.of(section))))
    }
    assertEquals("forms/q1", RouteSegments.of(SurveyEditorSection.Form("q1")))
    assertNull(RouteSegments.editorSection("forms"))
    assertNull(RouteSegments.editorSection("questions/q1"))
  }
}
