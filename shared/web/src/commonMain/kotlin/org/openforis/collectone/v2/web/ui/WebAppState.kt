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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.flow.first
import org.openforis.collectone.v2.app.di.AppData
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.domain.error.getOrNull
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.repository.InviteRedemption
import org.openforis.collectone.v2.app.domain.repository.Invitee
import org.openforis.collectone.v2.app.ui.AppViewModels
import org.openforis.collectone.v2.app.ui.session.ActiveSurveyAccess
import org.openforis.collectone.v2.app.ui.session.NoticeController
import org.openforis.collectone.v2.app.ui.session.SessionCoordinator
import org.openforis.collectone.v2.app.ui.session.SessionEntityDetailsActions
import org.openforis.collectone.v2.app.ui.session.SessionSearchableListActions
import org.openforis.collectone.v2.app.ui.session.SessionShell
import org.openforis.collectone.v2.web.ui.organization.OrganizationActions
import org.openforis.collectone.v2.web.ui.surveys.CreateSurveyRequest

/**
 * Composition root of a web console session: the feature ViewModels both clients share
 * ([viewModels], wired together by a [SessionCoordinator]), the session's notice, what only the web
 * console keeps (recently opened surveys and invitation links), and the store of the ViewModels its
 * pages share ([sessionViewModels]). Pages read their ViewModels' `uiState` and send intents to
 * them (see [sessionViewModel]); which page shows is up to the console's `NavHost` (see
 * `navigation/WebNavigator`).
 *
 * The web app is always online: airplane mode is only simulated for the mobile app.
 */
class WebAppState(
  val dataHolder: AppDataHolder = AppDataHolder(),
  /**
   * The feature ViewModels of the session. Hosts pass the ones `koinAppViewModels()` returns; by
   * default the session gets its own.
   */
  val viewModels: AppViewModels = AppViewModels.create(dataHolder, isAlwaysOnline = true),
  /**
   * Keeps the ViewModels that several pages share for as long as the session lasts: the browser
   * window's store, or by default a store of the session's own.
   */
  val sessionViewModels: ViewModelStoreOwner = standaloneSessionViewModels(dataHolder),
) : AutoCloseable {
  /** The session's notice, which the outcomes of every feature replace or clear. */
  val notices = NoticeController()

  // The console's pages follow the ViewModels' state themselves, so its shell has no reactions.
  private val coordinator =
    SessionCoordinator(dataHolder, viewModels, notices, shell = object : SessionShell {})

  /** Intents of the dashboard's searchable list of map features and places. */
  val searchableListActions = SessionSearchableListActions(viewModels)

  /** Intents of the dashboard's map feature details card. */
  val entityDetailsActions = SessionEntityDetailsActions(viewModels)

  /** What the signed-in user may do in the active survey, from the latest data and sign-in. */
  val activeSurveyAccess: ActiveSurveyAccess
    get() = ActiveSurveyAccess.of(dataHolder.appData.value, viewModels.onboarding.uiState.value)

  /** Recently opened survey IDs (most recent first). */
  private var recentSurveyIdHistory by mutableStateOf(listOf<String>())

  /** Up to 5 recently viewed surveys of [data], starting with the currently active survey. */
  fun recentSurveys(data: AppData = dataHolder.appData.value): List<SurveyPreviewItem> {
    val byId = data.surveys.associateBy { it.id }
    val orderedIds =
      (listOf(data.activeSurveyId) + recentSurveyIdHistory + data.surveys.map { it.id }).distinct()
    return orderedIds.mapNotNull { byId[it] }.take(5)
  }

  /** Stops routing the ViewModels' events. Hosts call it when the session leaves composition. */
  override fun close() = coordinator.close()

  /**
   * Resets every ViewModel of the session and what the console keeps, as when the store is reset to
   * its sample data.
   */
  fun reset() {
    coordinator.reset()
    recentSurveyIdHistory = emptyList()
  }

  /** Signs the user out. */
  fun signOut() = viewModels.onboarding.signOut()

  /** Turns off imagery source [sourceId] on the survey map after its organization removed it. */
  fun onImagerySourceRemoved(sourceId: String) {
    val surveyMap = viewModels.surveyMap
    if (surveyMap.uiState.value.isImagerySourceEnabled(sourceId)) {
      surveyMap.toggleImagerySource(sourceId)
    }
  }

  /**
   * Opens a survey in the web app (dashboard and Survey editor). Unlike the mobile app's
   * `openSurvey`, this doesn't download the survey for offline use.
   */
  fun openSurveyOnWeb(surveyId: String) {
    val activeSurveyId = dataHolder.appData.value.activeSurveyId
    recentSurveyIdHistory =
      (listOf(surveyId, activeSurveyId) + recentSurveyIdHistory).distinct().take(5)
    viewModels.dashboard.openSurveyOnWeb(surveyId)
  }

  /**
   * Creates a new, empty survey owned by the signed-in user, optionally in [organizationId], makes
   * it active, and returns its ID. The caller normally opens the Survey editor next.
   */
  fun createSurvey(title: String, organizationId: String? = null): String {
    val id = viewModels.dashboard.createSurvey(title, organizationId)
    recentSurveyIdHistory = (listOf(id) + recentSurveyIdHistory).distinct().take(5)
    return id
  }

  /** Creates a survey from the Create survey dialog, seeded with its Purpose Packs' forms. */
  fun createSurvey(request: CreateSurveyRequest): String {
    val id =
      viewModels.dashboard.createSurvey(
        title = request.title,
        organizationId = request.organizationId,
        purposeIds = request.purposeIds,
        programIds = request.programIds,
      )
    recentSurveyIdHistory = (listOf(id) + recentSurveyIdHistory).distinct().take(5)
    return id
  }

  /**
   * Redeems an invitation deep link (`#join/<surveyId>/<token>` or
   * `#org-join/<organizationId>/<token>`) once the user is signed in and has accepted the Terms of
   * Service, and the initial sync has finished; opens the survey or the [organization], then calls
   * [onRedeemed] to show its page. A user who declines the terms joins nothing.
   */
  fun redeemInviteDeepLink(
    invite: InviteDeepLink,
    organization: OrganizationActions,
    onRedeemed: () -> Unit,
  ) {
    dataHolder.launch {
      dataHolder.authRepository.observeSession().first { it.isSignedIn && it.hasAcceptedTerms }
      dataHolder.hasCompletedInitialSync.first { it }
      val profile = viewModels.onboarding.uiState.value.profile
      val invitee =
        Invitee(
          userId = "uid-${profile.email.substringBefore('@').replace('.', '-')}",
          email = profile.email,
          displayName = profile.displayName,
          photoUrl = profile.photoUrl,
        )
      val invites = dataHolder.inviteRepository
      when (invite.kind) {
        InviteDeepLink.Kind.SURVEY -> {
          // Opens even when redeeming failed, with whatever the device has.
          invites.acceptSurveyInvite(invite.targetId, invite.token, invitee)
          openSurveyOnWeb(invite.targetId)
        }
        InviteDeepLink.Kind.ORGANIZATION -> {
          val redemption = invites.joinOrganization(invite.targetId, invite.token, invitee)
          if (redemption.getOrNull() == InviteRedemption.NO_SERVICE) {
            organization.acceptInvite(
              organizationId = invite.targetId,
              email = invitee.email,
              displayName = invitee.displayName,
              photoUrl = invitee.photoUrl,
            )
          }
          organization.openOrganization(invite.targetId)
        }
      }
      onRedeemed()
    }
  }
}
