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
package org.openforis.collectone.v2.app.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.StateFlow
import org.openforis.collectone.v2.app.di.AppData
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.SubmissionViewer
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAccess
import org.openforis.collectone.v2.app.domain.model.hasPlatformAdmin
import org.openforis.collectone.v2.app.ui.AppViewModels
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingUiState

/**
 * What the signed-in user ([email]) may do in the active survey of [data], as the app shells show
 * it: the survey and its organization, whether the user may collect data in it or manage it, and
 * what they see of other contributors' submissions.
 */
class ActiveSurveyAccess(
  private val data: AppData,
  private val email: String,
  private val isSignedIn: Boolean,
) {
  /**
   * The active survey: the first survey when none is active, or a blank placeholder while there are
   * no surveys yet (e.g. before the first sync).
   */
  val activeSurvey: SurveyPreviewItem =
    data.surveys.firstOrNull { it.id == data.activeSurveyId }
      ?: data.surveys.firstOrNull()
      ?: NO_SURVEY

  /** The organization the active survey belongs to, if any. */
  val organization: Organization? =
    activeSurvey.organizationId?.let { id -> data.organizations.firstOrNull { it.id == id } }

  /**
   * Whether the signed-in user is a platform administrator (active Manager of the synthetic `"All
   * users"` organization).
   */
  val isPlatformAdmin: Boolean = isSignedIn && data.organizations.hasPlatformAdmin(email)

  /**
   * Short affiliation line for profile cards: the user's organizations joined with " • ", or empty
   * if they belong to none.
   */
  val affiliation: String =
    data.organizations.filter { it.isMember(email) }.joinToString(" • ") { it.name }

  /** Whether the user can collect data in the active survey (never once it's closed). */
  val canCollectData: Boolean =
    !activeSurvey.isClosed &&
      SurveyAccess.canCollectData(
        email = email,
        sharing = data.editorDraft.sharing,
        organization = organization,
        isPlatformAdmin = isPlatformAdmin,
      )

  /**
   * The user as a viewer of the active survey's submissions, deciding what they see of each
   * contributor ([org.openforis.collectone.v2.app.domain.model.ContributorVisibility]).
   */
  val submissionViewer: SubmissionViewer =
    SubmissionViewer(email = email, isOrganizer = canManage(isPlatformAdmin))

  /**
   * Whether the signed-in user may open the Survey editor for the active survey: they own it, are
   * an accepted Survey organizer on its access list, manage its organization, or are a platform
   * administrator ([isPlatformAdmin], as the caller knows it) (see [SurveyAccess]).
   */
  fun canManageSurvey(isPlatformAdmin: Boolean): Boolean = isSignedIn && canManage(isPlatformAdmin)

  private fun canManage(isPlatformAdmin: Boolean): Boolean =
    SurveyAccess.canManage(
      email = email,
      sharing = data.editorDraft.sharing,
      organization = organization,
      isPlatformAdmin = isPlatformAdmin,
    )

  companion object {
    /** The access of the user [onboarding] signed in, in the active survey of [data]. */
    fun of(data: AppData, onboarding: OnboardingUiState): ActiveSurveyAccess =
      ActiveSurveyAccess(data, onboarding.profile.email, onboarding.isSignedIn)

    /** The [activeSurvey] shown while there are no surveys: blank, with no ID. */
    private val NO_SURVEY =
      SurveyPreviewItem(
        id = "",
        title = "",
        description = "",
        location = "",
        isDownloaded = false,
        entityCount = 0,
      )
  }
}

/** The [ActiveSurveyAccess] of the session's latest data and sign-in, for composition. */
@Composable
fun collectActiveSurveyAccess(
  viewModels: AppViewModels,
  data: StateFlow<AppData>,
): ActiveSurveyAccess {
  val appData by data.collectAsState()
  val onboarding by viewModels.onboarding.uiState.collectAsState()
  return ActiveSurveyAccess.of(appData, onboarding)
}
