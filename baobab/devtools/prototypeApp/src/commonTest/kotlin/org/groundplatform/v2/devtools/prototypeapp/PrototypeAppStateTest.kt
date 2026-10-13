/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.domain.model.ListFilterTab
import org.groundplatform.v2.app.domain.model.PrototypeScreen
import org.groundplatform.v2.web.ui.WebPage

/**
 * Checks how the combined prototype routes pages and links its mobile app, web app, and workbench.
 */
class PrototypeAppStateTest {

  @Test
  fun showPage_switchesBetweenMobilePrototypeAndWebPagesAndNotifiesListener() {
    val state = PrototypeAppState()
    assertTrue(state.isMobilePrototypeShown)
    assertNull(state.activeWebPage)

    var notifiedPage: WebPage? = WebPage.ORGANIZATIONS
    state.onPageChanged = { notifiedPage = it }

    state.showPage(WebPage.WEB_DASHBOARD)
    assertEquals(WebPage.WEB_DASHBOARD, state.activeWebPage)
    assertEquals(WebPage.WEB_DASHBOARD, notifiedPage)

    // Pages the web app opens itself are shown too.
    state.web.selectPage(WebPage.SURVEY_EDITOR)
    assertEquals(WebPage.SURVEY_EDITOR, state.activeWebPage)
    assertEquals(WebPage.SURVEY_EDITOR, notifiedPage)

    state.showPage(null)
    assertTrue(state.isMobilePrototypeShown)
    assertNull(state.activeWebPage)
    assertNull(notifiedPage)

    // The web app's header button returns to the mobile prototype.
    state.showPage(WebPage.WEB_SURVEYS)
    state.web.onShowMobilePrototype?.invoke()
    assertTrue(state.isMobilePrototypeShown)
  }

  @Test
  fun hashFor_mapsMobilePrototypeAndWebPages() {
    assertEquals("prototype", PrototypeAppState.hashFor(null, "org-kfs"))
    assertEquals("organization/org-kfs", PrototypeAppState.hashFor(WebPage.ORGANIZATION, "org-kfs"))
    assertNull(WebPage.fromHash("#prototype"))
    assertNull(WebPage.fromHash(""))
  }

  @Test
  fun airplaneMode_onlyAffectsTheMobileApp() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    val mobile = state.mobile
    val web = state.web
    val entity =
      web.entities.first { e ->
        e.submissions.any { web.isSubmissionStoredOnDevice(it) } &&
          e.submissions.any { !web.isSubmissionStoredOnDevice(it) }
      }

    // Enable airplane mode in the mobile UI
    state.workbench.updateAirplaneMode(true)
    assertFalse(mobile.isOnline)
    assertFalse(mobile.isPlacesSearchAvailable)

    // The web app is always online
    state.showPage(WebPage.WEB_DASHBOARD)
    assertTrue(web.isOnline)
    assertTrue(web.isPlacesSearchAvailable)

    // Full submission history (including other collectors' submissions) is visible on web
    assertEquals(
      entity.submissions.size,
      web.availableGroupedSubmissionsForEntity(entity).sumOf { it.submissions.size },
    )

    // Places search and PLACES filter tab remain available on web
    web.dashboard.selectListFilterTab(ListFilterTab.PLACES)
    assertEquals(ListFilterTab.PLACES, web.listFilterTab)
    web.updateListSearchQuery("Othaya")
    assertTrue(web.filteredListPlaces.isNotEmpty())

    // Signing out and signing back in on web succeeds even when mobile airplane mode is set
    web.signOut()
    assertFalse(web.isSignedIn)
    web.signInWithGoogle()
    assertTrue(web.isSignedIn)

    // The mobile app stays offline
    state.showPage(null)
    assertFalse(mobile.isOnline)
    assertFalse(mobile.isPlacesSearchAvailable)
    assertEquals(ListFilterTab.ALL, mobile.listFilterTab)
    assertTrue(mobile.filteredListPlaces.isEmpty())
  }

  @Test
  fun signingOutOnWeb_returnsTheMobileAppToSignIn() {
    val state = PrototypeAppState()
    state.mobile.signInWithGoogle()
    state.mobile.acceptTermsOfService()
    state.mobile.openSurvey("survey-kenya-coffee")
    assertEquals(PrototypeScreen.MAIN_SURVEY, state.mobile.currentScreen)

    state.web.signOut()
    assertFalse(state.mobile.isSignedIn)
    assertEquals(PrototypeScreen.SIGN_IN, state.mobile.currentScreen)
  }

  @Test
  fun workbench_appliesFormDefinitionAndResetToTheWebAppToo() {
    val state = PrototypeAppState(initialScreen = PrototypeScreen.MAIN_SURVEY)
    assertEquals(state.mobile.customFormDef, state.web.customFormDef)

    state.workbench.updateCustomXFormsXml("")
    assertEquals(state.mobile.customFormDef, state.web.customFormDef)

    state.web.selectEntity(state.web.entities.first().id)
    state.workbench.resetPrototypeFlow()
    assertNull(state.web.selectedEntityId)
    assertEquals(PrototypeScreen.SIGN_IN, state.mobile.currentScreen)
  }

  @Test
  fun sharedTheme_followsBothApps() {
    val state = PrototypeAppState()
    assertFalse(state.isDarkTheme)
    state.mobile.toggleDarkTheme()
    assertTrue(state.web.isDarkTheme)
    state.web.toggleDarkTheme()
    assertFalse(state.mobile.isDarkTheme)
  }
}
