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
package org.openforis.collectone.v2.web

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.AuthenticatedCollectorProfile
import org.openforis.collectone.v2.app.client.auth.FakeAuthClient
import org.openforis.collectone.v2.app.data.datasource.local.session.KeyValueSignedInSessionStore
import org.openforis.collectone.v2.app.data.datasource.local.session.KeyValueStorage
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.web.navigation.MemoryBrowserHistory
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.onboarding.WEB_SIGN_IN_PAGE_SCROLL_TAG
import org.openforis.collectone.v2.web.ui.onboarding.WEB_TOS_PAGE_SCROLL_TAG
import org.openforis.collectone.v2.web.ui.onboarding.WEB_TOS_TEXT_SCROLL_TAG

/**
 * The web console asks for the Terms of Service after every sign-in, in place of the page being
 * opened, and keeps the acceptance until the user signs out (so reloading doesn't ask again).
 */
@OptIn(ExperimentalTestApi::class)
class WebConsoleTermsOfServiceTest {
  private val title = "Terms of Service"
  private val continueButton = "Continue"
  private val decline = "Decline"
  private val checkbox = "I have read and agree to the Collect One Terms of Service."
  private val signIn = "Sign in with Google"

  private fun ComposeUiTest.showConsole(state: WebAppState, history: MemoryBrowserHistory) {
    setContent { WebConsoleApp(state = state, browserHistory = history) }
    waitForIdle()
  }

  private fun ComposeUiTest.scrollTermsToBottom() {
    onNodeWithTag(WEB_TOS_TEXT_SCROLL_TAG).performSemanticsAction(SemanticsActions.ScrollBy) {
      it(0f, 10_000f)
    }
    waitForIdle()
  }

  private fun ComposeUiTest.scrollTermsAndContinue() {
    scrollTermsToBottom()
    onNodeWithText(checkbox).performClick()
    waitForIdle()
    onNodeWithText(continueButton).performClick()
    waitForIdle()
  }

  private fun WebAppState.signIn() {
    viewModels.onboarding.signInWithGoogle(ignoreOffline = true)
  }

  private val WebAppState.hasAcceptedTerms: Boolean
    get() = viewModels.onboarding.uiState.value.hasAcceptedTerms

  @Test
  fun signingIn_showsTheTerms_inPlaceOfThePage_andKeepsItsUrl() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val history = MemoryBrowserHistory("/organizations")
      showConsole(state, history)

      state.signIn()
      waitForIdle()

      onNodeWithText(continueButton).assertIsDisplayed()
      onNodeWithText(continueButton).assertIsNotEnabled()
      onNodeWithText(decline).assertIsDisplayed()
      onNodeWithText(checkbox).assertIsDisplayed()
      onNodeWithText(checkbox).assertIsOff()
      onNodeWithText(checkbox).assertIsNotEnabled()
      onNodeWithText(
          "Signed in as ${FakeAuthClient.DEFAULT_COLLECTOR_PROFILE.email}",
          substring = true,
        )
        .assertIsDisplayed()
      onNodeWithText(signIn).assertDoesNotExist()
      assertEquals("/organizations", history.path)
      assertFalse(state.hasAcceptedTerms)
    }

  @Test
  fun agreeing_opensThePageThatWasBeingOpened() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val history = MemoryBrowserHistory("/surveys")
      showConsole(state, history)
      state.signIn()
      waitForIdle()

      scrollTermsAndContinue()

      onNodeWithText(continueButton).assertDoesNotExist()
      onNodeWithText("Create survey").assertIsDisplayed()
      assertEquals("/surveys", history.path)
      assertTrue(state.hasAcceptedTerms)
    }

  @Test
  fun aSurveyDashboardLink_showsTheTermsBeforeTheDashboard() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val surveyId = state.viewModels.dashboard.uiState.value.surveys.first().id
      val history = MemoryBrowserHistory("/surveys/$surveyId/dashboard")
      showConsole(state, history)

      state.signIn()
      waitForIdle()

      onNodeWithText(continueButton).assertIsDisplayed()
      assertEquals("/surveys/$surveyId/dashboard", history.path)

      scrollTermsAndContinue()

      onNodeWithText(continueButton).assertDoesNotExist()
      assertEquals(surveyId, state.viewModels.dashboard.uiState.value.activeSurveyId)
    }

  @Test
  fun checkbox_isUncheckedAndDisabledUntilScrolledToBottom_andContinueFollowsCheckbox() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      showConsole(state, MemoryBrowserHistory("/surveys"))
      state.signIn()
      waitForIdle()

      // Before scrolling: checkbox is unchecked and disabled; Continue is disabled.
      onNodeWithText(checkbox).assertIsOff().assertIsNotEnabled()
      onNodeWithText(continueButton).assertIsNotEnabled()

      // Clicking the disabled checkbox before scrolling has no effect.
      onNodeWithText(checkbox).performClick()
      waitForIdle()
      onNodeWithText(checkbox).assertIsOff().assertIsNotEnabled()
      onNodeWithText(continueButton).assertIsNotEnabled()

      // Scrolling partway down keeps the checkbox disabled.
      onNodeWithTag(WEB_TOS_TEXT_SCROLL_TAG).performSemanticsAction(SemanticsActions.ScrollBy) {
        it(0f, 100f)
      }
      waitForIdle()
      onNodeWithText(checkbox).assertIsNotEnabled()
      onNodeWithText(continueButton).assertIsNotEnabled()

      // Scrolling to the bottom enables the checkbox (still unchecked, Continue still disabled).
      scrollTermsToBottom()
      onNodeWithText(checkbox).assertIsOff().assertIsEnabled()
      onNodeWithText(continueButton).assertIsNotEnabled()

      // Scrolling back up keeps the checkbox enabled.
      onNodeWithTag(WEB_TOS_TEXT_SCROLL_TAG).performSemanticsAction(SemanticsActions.ScrollBy) {
        it(0f, -10_000f)
      }
      waitForIdle()
      onNodeWithText(checkbox).assertIsEnabled()

      // Checking enables Continue; unchecking disables Continue again.
      onNodeWithText(checkbox).performClick()
      waitForIdle()
      onNodeWithText(checkbox).assertIsOn()
      onNodeWithText(continueButton).assertIsEnabled()

      onNodeWithText(checkbox).performClick()
      waitForIdle()
      onNodeWithText(checkbox).assertIsOff()
      onNodeWithText(continueButton).assertIsNotEnabled()
    }

  @Test
  fun declining_signsOut_andReturnsToTheSignInPage_keepingTheUrl() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      val history = MemoryBrowserHistory("/organizations")
      showConsole(state, history)
      state.signIn()
      waitForIdle()

      onNodeWithText(decline).performClick()
      waitForIdle()

      onNodeWithText(signIn).assertIsDisplayed()
      onNodeWithText(continueButton).assertDoesNotExist()
      assertFalse(state.viewModels.onboarding.uiState.value.isSignedIn)
      assertEquals("/organizations", history.path)
    }

  @Test
  fun signingOutAndBackIn_asksForTheTermsAgain() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      showConsole(state, MemoryBrowserHistory("/surveys"))
      state.signIn()
      waitForIdle()
      scrollTermsAndContinue()

      state.signOut()
      waitForIdle()
      onNodeWithText(signIn).assertIsDisplayed()
      state.signIn()
      waitForIdle()

      onNodeWithText(continueButton).assertIsDisplayed().assertIsNotEnabled()
      onNodeWithText(checkbox).assertIsOff().assertIsNotEnabled()
      assertFalse(state.hasAcceptedTerms)
    }

  @Test
  fun reloading_afterAgreeing_doesNotAskAgain_untilSigningOut() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val browser = Browser()
      val first = browser.open()
      showConsole(first, MemoryBrowserHistory("/surveys"))
      first.signIn()
      waitForIdle()
      scrollTermsAndContinue()

      // Reloading: a new session over the same browser, whose auth client restores the account.
      val reloaded = browser.open()
      assertTrue(reloaded.viewModels.onboarding.uiState.value.isSignedIn)
      assertTrue(reloaded.hasAcceptedTerms)

      reloaded.signOut()
      waitForIdle()
      val afterSignOut = browser.open()
      assertFalse(afterSignOut.viewModels.onboarding.uiState.value.isSignedIn)
      afterSignOut.signIn()
      assertFalse(afterSignOut.hasAcceptedTerms)
    }

  @Test
  fun reloading_beforeAgreeing_asksForTheTerms() =
    runDesktopComposeUiTest(width = 1440, height = 900) {
      val browser = Browser()
      browser.open().signIn()

      val reloaded = browser.open()
      showConsole(reloaded, MemoryBrowserHistory("/surveys"))

      onNodeWithText(continueButton).assertIsDisplayed().assertIsNotEnabled()
      onNodeWithText(checkbox).assertIsOff().assertIsNotEnabled()
      // In the header and as the card's title.
      onAllNodesWithText(title).assertCountEquals(2)
      assertFalse(reloaded.hasAcceptedTerms)
    }

  @Test
  fun smallViewport_signInPageScrollsToRevealSignInButton() =
    runDesktopComposeUiTest(width = 400, height = 450) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      showConsole(state, MemoryBrowserHistory("/surveys"))

      onNodeWithTag(WEB_SIGN_IN_PAGE_SCROLL_TAG).performSemanticsAction(SemanticsActions.ScrollBy) {
        it(0f, 10_000f)
      }
      waitForIdle()

      onNodeWithText(signIn).assertIsDisplayed().performClick()
      waitForIdle()

      assertTrue(state.viewModels.onboarding.uiState.value.isSignedIn)
    }

  @Test
  fun smallViewport_termsPageScrollsToRevealCheckboxAndContinueButton() =
    runDesktopComposeUiTest(width = 400, height = 450) {
      val state = WebAppState(dataHolder = sampleDataHolder())
      showConsole(state, MemoryBrowserHistory("/surveys"))
      state.signIn()
      waitForIdle()

      scrollTermsToBottom()
      onNodeWithTag(WEB_TOS_PAGE_SCROLL_TAG).performSemanticsAction(SemanticsActions.ScrollBy) {
        it(0f, 10_000f)
      }
      waitForIdle()

      onNodeWithText(checkbox)
        .performScrollTo()
        .assertIsDisplayed()
        .assertIsEnabled()
        .performClick()
      waitForIdle()
      onNodeWithText(continueButton)
        .performScrollTo()
        .assertIsDisplayed()
        .assertIsEnabled()
        .performClick()
      waitForIdle()

      assertTrue(state.hasAcceptedTerms)
    }

  /**
   * A browser that keeps the auth client's session (like Firebase) and the web console's
   * `localStorage` across page loads: each [open] is a page load with a new session.
   */
  private class Browser {
    private val localStorage = mutableMapOf<String, String>()
    private val authSession = MutableStateFlow<AuthenticatedCollectorProfile?>(null)

    fun open(): WebAppState =
      WebAppState(
        dataHolder =
          AppDataHolder(
            localStore = seededStore(),
            authClient = PersistentAuthClient(authSession),
            signedInSessionStore = {
              KeyValueSignedInSessionStore(
                storage =
                  object : KeyValueStorage {
                    override fun get(key: String) = localStorage[key]

                    override fun set(key: String, value: String) {
                      localStorage[key] = value
                    }

                    override fun remove(key: String) {
                      localStorage.remove(key)
                    }
                  },
                restoresAccount = false,
              )
            },
          )
      )
  }

  /** An [AuthClient] whose session outlives the page, as Firebase keeps it in the browser. */
  private class PersistentAuthClient(
    override val currentUser: MutableStateFlow<AuthenticatedCollectorProfile?>
  ) : AuthClient {
    override suspend fun signInWithGoogle(): AuthenticatedCollectorProfile =
      FakeAuthClient.DEFAULT_COLLECTOR_PROFILE.also { currentUser.value = it }

    override suspend fun signOut() {
      currentUser.value = null
    }

    override suspend fun idToken(forceRefresh: Boolean): String? = null
  }
}
