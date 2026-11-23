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
package org.openforis.collectone.v2.web.ui.surveyeditor.care

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules

class CareConformityTest {
  private val openDraft =
    SurveyEditorDraft.blank("s1").let {
      it.copy(sharing = it.sharing.copy(policy = SharingPolicy.ORGANIZATION))
    }

  @Test
  fun openSurvey_meetsOnlyEthics_andCareIsNotApplied() {
    val care = careConformity(openDraft, organization = null)

    assertEquals(listOf("E"), care.criteria.filter { it.isSatisfied }.map { it.code })
    assertEquals(1, care.metCount)
    assertFalse(care.isCareApplied)
    assertNull(care.excludedOrganizationName)
  }

  @Test
  fun withCareDefaults_meetsAuthorityResponsibilityAndFpic_andKeepsExistingConsentTerms() {
    val defaults = openDraft.sharing.withCareDefaults()
    assertEquals(SharingPolicy.RESTRICTED, defaults.policy)
    assertEquals(PeerDataVisibility.OWN_ONLY, defaults.peerDataVisibility)
    assertTrue(defaults.collectorConsentRequired)
    assertEquals(SurveyWizardRules.DEFAULT_FPIC_CONSENT_TEXT, defaults.collectorConsentText)

    val care = careConformity(openDraft.copy(sharing = defaults), organization = null)
    assertEquals(
      listOf("A", "R", "E", "FPIC"),
      care.criteria.filter { it.isSatisfied }.map { it.code },
    )
    assertTrue(care.isCareApplied)

    val custom = openDraft.sharing.copy(collectorConsentText = "Our terms").withCareDefaults()
    assertEquals("Our terms", custom.collectorConsentText)
  }

  @Test
  fun purposeChosen_meetsCollectiveBenefit() {
    val draft =
      openDraft.copy(details = openDraft.details.copy(purposeIds = listOf("eudr_due_diligence")))

    assertTrue(
      careConformity(draft, organization = null).criteria.first { it.code == "C" }.isSatisfied
    )
  }

  @Test
  fun iplcOrganizationExcludedFromTotals_isNamed_andRestrictedOwnOnlyCountsAsApplied() {
    val organization =
      Organization(
        id = "org",
        name = "Community Forest Council",
        organizationType = OrganizationType.IPLC_ORGANIZATION,
        excludeFromPlatformAggregates = true,
      )
    val draft =
      openDraft.copy(
        sharing =
          openDraft.sharing.copy(
            policy = SharingPolicy.RESTRICTED,
            peerDataVisibility = PeerDataVisibility.OWN_ONLY,
          )
      )

    val care = careConformity(draft, organization)
    assertTrue(care.isCareApplied)
    assertEquals("Community Forest Council", care.excludedOrganizationName)
  }
}
