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
package org.openforis.collectone.v2.mobile.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.ui.common.ClosedSurveyThumbnailScrim
import org.openforis.collectone.v2.app.ui.common.SurveyVisibilityIcon
import org.openforis.collectone.v2.app.ui.common.UserAvatarButton
import org.openforis.collectone.v2.app.ui.common.UserProfileSidePanelOverlay
import org.openforis.collectone.v2.app.ui.common.grayscale
import org.openforis.collectone.v2.app.ui.map.SurveyMapThumbnail
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingActions
import org.openforis.collectone.v2.app.ui.onboarding.OnboardingUiState
import org.openforis.collectone.v2.core.forms.ui.AlertDialogOverlay
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_available_offline
import org.openforis.collectone.v2.mobile.resources.mobile_back
import org.openforis.collectone.v2.mobile.resources.mobile_cancel
import org.openforis.collectone.v2.mobile.resources.mobile_clear_search_content_description
import org.openforis.collectone.v2.mobile.resources.mobile_download
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_clear_search
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_directory_header
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_empty
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_empty_hint
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_matching_header
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_no_match
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_no_match_hint
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_public_section
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_search_content_description
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_search_placeholder
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_shared_header
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_subtitle
import org.openforis.collectone.v2.mobile.resources.mobile_download_survey_title
import org.openforis.collectone.v2.mobile.resources.mobile_open
import org.openforis.collectone.v2.mobile.resources.mobile_remove
import org.openforis.collectone.v2.mobile.resources.mobile_remove_from_device
import org.openforis.collectone.v2.mobile.resources.mobile_remove_survey_dialog_body
import org.openforis.collectone.v2.mobile.resources.mobile_remove_survey_dialog_title
import org.openforis.collectone.v2.mobile.resources.mobile_sign_out
import org.openforis.collectone.v2.mobile.resources.mobile_sign_out_dialog_body
import org.openforis.collectone.v2.mobile.resources.mobile_sign_out_dialog_title
import org.openforis.collectone.v2.mobile.resources.mobile_survey_badge_private_submissions
import org.openforis.collectone.v2.mobile.resources.mobile_survey_badge_public_data
import org.openforis.collectone.v2.mobile.resources.mobile_survey_download_content_description
import org.openforis.collectone.v2.mobile.resources.mobile_survey_more_options_content_description
import org.openforis.collectone.v2.mobile.resources.mobile_survey_open_content_description
import org.openforis.collectone.v2.mobile.resources.mobile_unavailable
import org.openforis.collectone.v2.mobile.ui.common.SignOutConfirmationDialog
import org.openforis.collectone.v2.mobile.ui.common.SignOutConfirmationStep

/**
 * Download survey: the surveys shared with the user, in one section per organization and then the
 * public ones ([downloadSurveySections]), filtered by the search query on name, description,
 * location, coordinates, or organization. A query also looks up matching surveys not shared with
 * the user (public surveys, or the survey a pasted share link points to). Offline, it shows its own
 * banner rather than the app's offline notice.
 *
 * [notice] (the outcome of downloading or removing a survey) shows in a snackbar; [onNoticeShown]
 * is called once it has gone, so the same notice isn't shown again.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadSurveyScreen(
  uiState: OnboardingUiState,
  actions: OnboardingActions,
  notice: String?,
  isDarkTheme: Boolean,
  onNoticeShown: () -> Unit = {},
) {
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  val filtered = uiState.filteredSurveys
  var isUserProfileOpen by remember { mutableStateOf(false) }
  val searchBarState = remember { CollapsingSearchBarState() }
  val listState = rememberLazyListState()
  // The top of the list always shows the search field, also when the list has just changed.
  LaunchedEffect(listState, searchBarState) {
    snapshotFlow { listState.canScrollBackward }
      .collect { canScrollBackward -> if (!canScrollBackward) searchBarState.expand() }
  }

  // Outcomes of downloading or removing a survey show briefly, then clear.
  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(notice) {
    if (!notice.isNullOrBlank()) {
      snackbarHostState.showSnackbar(message = notice, duration = SnackbarDuration.Short)
      onNoticeShown()
    }
  }

  var signOutStep by remember { mutableStateOf(SignOutConfirmationStep.NONE) }

  Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(
                text = stringResource(Res.string.mobile_download_survey_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text =
                  stringResource(
                    Res.string.mobile_download_survey_subtitle,
                    uiState.surveys.size,
                    uiState.downloadedSurveyCount,
                  ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
          navigationIcon = {
            IconButton(onClick = { actions.navigateBackFromDownloadSurvey() }) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.mobile_back),
              )
            }
          },
          actions = {
            UserAvatarButton(
              userName = uiState.profile.displayName,
              userInitials = uiState.profile.initials,
              onClick = { isUserProfileOpen = true },
              userPhotoUrl = uiState.profile.photoUrl,
              modifier = Modifier.padding(end = 12.dp),
            )
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface,
              titleContentColor = MaterialTheme.colorScheme.onSurface,
              navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
      containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .nestedScroll(searchBarState.nestedScrollConnection)
            .semantics { isTraversalGroup = true }
      ) {
        // Hides itself when the connection returns; the survey list then loads.
        if (!uiState.isOnline) {
          DownloadSurveyOfflineBanner(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
          )
        }
        // Search by survey name or location. Scrolling the list down hides the field and
        // scrolling back up returns it; a query keeps it in view.
        DownloadSurveySearchField(
          query = uiState.searchQuery,
          onQueryChange = actions::updateSearchQuery,
          onClear = actions::clearSearchQuery,
          modifier =
            Modifier.collapsedBy(searchBarState, isPinned = uiState.searchQuery.isNotEmpty())
              .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        // Section Summary Header
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (uiState.searchQuery.isBlank()) {
                stringResource(Res.string.mobile_download_survey_shared_header, filtered.size)
              } else {
                stringResource(
                  Res.string.mobile_download_survey_matching_header,
                  filtered.size,
                  uiState.surveys.size,
                )
              },
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.6.sp,
              ),
          )
          if (uiState.searchQuery.isNotBlank()) {
            TextButton(onClick = { actions.clearSearchQuery() }) {
              Text(
                text = stringResource(Res.string.mobile_download_survey_clear_search),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }

        // Survey Items List
        if (filtered.isEmpty() && uiState.directoryResults.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
          ) {
            if (
              uiState.isLoadingSurveys && uiState.surveys.isEmpty() && uiState.searchQuery.isBlank()
            ) {
              CircularProgressIndicator()
            } else {
              val trimmedQuery = uiState.searchQuery.trim()
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Map,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(36.dp),
                )
                Text(
                  text =
                    if (trimmedQuery.isEmpty()) {
                      stringResource(Res.string.mobile_download_survey_empty)
                    } else {
                      stringResource(Res.string.mobile_download_survey_no_match, trimmedQuery)
                    },
                  style = MaterialTheme.typography.titleSmall,
                  color = onSurfaceColor,
                  fontWeight = FontWeight.Bold,
                  textAlign = TextAlign.Center,
                )
                Text(
                  text =
                    stringResource(
                      if (trimmedQuery.isEmpty()) {
                        Res.string.mobile_download_survey_empty_hint
                      } else {
                        Res.string.mobile_download_survey_no_match_hint
                      }
                    ),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
                if (uiState.isSearchingDirectory) {
                  CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
              }
            }
          }
        } else {
          val publicSectionLabel = stringResource(Res.string.mobile_download_survey_public_section)
          val groupedSurveys =
            remember(filtered, uiState.organizationNames, publicSectionLabel) {
              downloadSurveySections(filtered, uiState.organizationNames, publicSectionLabel)
            }
          var collapsedGroups by remember { mutableStateOf(emptySet<String>()) }

          LazyColumn(
            state = listState,
            modifier =
              Modifier.weight(1f).fillMaxWidth().semantics {
                isTraversalGroup = true
                traversalIndex = 1f
              },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            groupedSurveys.forEach { (groupTitle, groupSurveys) ->
              val isExpanded = groupTitle !in collapsedGroups
              stickyHeader(key = "header-$groupTitle") {
                Surface(
                  modifier =
                    Modifier.fillMaxWidth().clickable {
                      collapsedGroups =
                        if (isExpanded) collapsedGroups + groupTitle
                        else collapsedGroups - groupTitle
                    },
                  color = MaterialTheme.colorScheme.surface,
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      modifier = Modifier.weight(1f),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                      )
                      Text(
                        text = "$groupTitle (${groupSurveys.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    Icon(
                      imageVector =
                        if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
              }
              if (isExpanded) {
                itemsIndexed(groupSurveys, key = { _, survey -> survey.id }) { index, survey ->
                  SurveyListItemCard(
                    survey = survey,
                    organizationName = null,
                    isDarkTheme = isDarkTheme,
                    onDownloadClick = { actions.downloadSurvey(survey.id) },
                    onRemoveClick = { actions.promptRemoveDownloadedSurvey(survey.id) },
                    onOpenSurveyClick = { actions.openSurvey(survey.id) },
                    traversalIndex = index.toFloat(),
                  )
                }
              }
            }
            if (uiState.directoryResults.isNotEmpty()) {
              item(key = "directory-header") {
                Text(
                  text =
                    stringResource(
                      Res.string.mobile_download_survey_directory_header,
                      uiState.directoryResults.size,
                    ),
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      letterSpacing = 0.6.sp,
                    ),
                  modifier = Modifier.padding(start = 2.dp, top = 8.dp),
                )
              }
              itemsIndexed(
                uiState.directoryResults,
                key = { _, survey -> "dir-${survey.id}" },
              ) { _, survey ->
                SurveyListItemCard(
                  survey = survey,
                  organizationName = uiState.organizationNames[survey.organizationId],
                  isDarkTheme = isDarkTheme,
                  onDownloadClick = { actions.downloadDirectorySurvey(survey.id) },
                  onRemoveClick = {},
                  onOpenSurveyClick = { actions.downloadDirectorySurvey(survey.id) },
                )
              }
            }
            item(key = "bottom-spacer") { Spacer(modifier = Modifier.height(12.dp)) }
          }
        }
      }
    }

    if (isUserProfileOpen) {
      UserProfileSidePanelOverlay(
        userName = uiState.profile.displayName,
        userEmail = uiState.profile.email,
        userInitials = uiState.profile.initials,
        organization = uiState.userOrganization,
        userPhotoUrl = uiState.profile.photoUrl,
        onDismissRequest = { isUserProfileOpen = false },
        onSignOut = { signOutStep = SignOutConfirmationStep.WARNING },
      )
    }

    SignOutConfirmationDialog(
      step = signOutStep,
      isOnline = uiState.isOnline,
      onStepChange = { signOutStep = it },
      onConfirmSignOut = actions::confirmSignOut,
    )

    if (uiState.isSignOutPromptOpen) {
      DownloadSurveySignOutPromptDialog(uiState, actions)
    }

    if (uiState.pendingRemovalSurveyId != null) {
      RemoveDownloadedSurveyConfirmationDialog(uiState, actions)
    }
  }
}

/**
 * Compact search field of the Download survey screen: a search icon, the [query] (or a
 * placeholder), and a clear button on the right while there is a query.
 */
@Composable
private fun DownloadSurveySearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val searchSurveysDescription =
    stringResource(Res.string.mobile_download_survey_search_content_description)
  Box(modifier = modifier) {
    Surface(
      modifier = Modifier.fillMaxWidth().height(40.dp),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
      Row(
        modifier = Modifier.fillMaxSize().padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
        Box(
          modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (query.isEmpty()) {
            Text(
              text = stringResource(Res.string.mobile_download_survey_search_placeholder),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          @Suppress("DEPRECATION")
          BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle =
              MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier =
              Modifier.fillMaxWidth().semantics {
                contentDescription = searchSurveysDescription
                traversalIndex = 0f
              },
          )
        }
        if (query.isNotEmpty()) {
          IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription =
                stringResource(Res.string.mobile_clear_search_content_description),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

/** Confirmation prompt dialog shown before removing a downloaded survey from the device. */
@Composable
private fun RemoveDownloadedSurveyConfirmationDialog(
  uiState: OnboardingUiState,
  actions: OnboardingActions,
) {
  val surveyId = uiState.pendingRemovalSurveyId ?: return
  val survey = uiState.surveys.firstOrNull { it.id == surveyId } ?: return

  AlertDialogOverlay(
    onDismissRequest = { actions.dismissRemoveDownloadedSurvey() },
    icon = {
      Icon(
        imageVector = Icons.Outlined.CloudOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
      )
    },
    title = {
      Text(
        text = stringResource(Res.string.mobile_remove_survey_dialog_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Text(
        text =
          stringResource(
            Res.string.mobile_remove_survey_dialog_body,
            survey.title,
          ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    confirmButton = {
      Button(
        onClick = { actions.confirmRemoveDownloadedSurvey() },
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text(stringResource(Res.string.mobile_remove), fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = { actions.dismissRemoveDownloadedSurvey() }) {
        Text(stringResource(Res.string.mobile_cancel))
      }
    },
  )
}

/**
 * Confirmation prompt dialog shown when the user taps Back on the Download surveys screen after
 * accepting the Terms of Service, confirming before signing the user out.
 */
@Composable
private fun DownloadSurveySignOutPromptDialog(
  uiState: OnboardingUiState,
  actions: OnboardingActions,
) {
  AlertDialogOverlay(
    onDismissRequest = { actions.dismissSignOutPrompt() },
    icon = {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.Logout,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
      )
    },
    title = {
      Text(
        text = stringResource(Res.string.mobile_sign_out_dialog_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Text(
        text = stringResource(Res.string.mobile_sign_out_dialog_body, uiState.profile.email),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    confirmButton = {
      Button(
        onClick = { actions.confirmSignOut() },
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text(stringResource(Res.string.mobile_sign_out), fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = { actions.dismissSignOutPrompt() }) {
        Text(stringResource(Res.string.mobile_cancel))
      }
    },
  )
}

/**
 * Individual survey card showing:
 * - Map thumbnail placeholder (`SurveyMapThumbnail`), with an overflow menu on downloaded surveys
 *   (`Remove from device`, [onRemoveClick])
 * - Survey title and visibility icon
 * - Survey location & coordinates
 * - Organization the survey belongs to, if any
 * - Survey description
 * - `Available offline` next to the action to open the survey in the Main Survey UI (`Open`) on
 *   downloaded surveys, or a Download CTA
 *
 * Downloaded and other surveys share one outline; the action row tells them apart.
 */
@Composable
private fun SurveyListItemCard(
  survey: SurveyPreviewItem,
  organizationName: String?,
  isDarkTheme: Boolean,
  onDownloadClick: () -> Unit,
  onRemoveClick: () -> Unit,
  onOpenSurveyClick: () -> Unit,
  traversalIndex: Float = 0f,
) {
  val cardBg =
    if (survey.isDownloaded) {
      MaterialTheme.colorScheme.surfaceContainerLow
    } else {
      MaterialTheme.colorScheme.surface
    }

  OutlinedCard(
    onClick = if (survey.isDownloaded) onOpenSurveyClick else onDownloadClick,
    modifier =
      Modifier.fillMaxWidth().height(308.dp).semantics(mergeDescendants = true) {
        role = Role.Button
        contentDescription = survey.title
        this.traversalIndex = traversalIndex
      },
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.outlinedCardColors(containerColor = cardBg),
    border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Box(modifier = Modifier.fillMaxWidth().height(136.dp)) {
        // The action row shows the downloaded status, so the thumbnail carries no badge.
        SurveyMapThumbnail(
          thumbnail = survey.thumbnail,
          isDownloaded = false,
          modifier = Modifier.fillMaxSize().grayscale(survey.isClosed),
        )
        if (survey.isClosed) {
          ClosedSurveyThumbnailScrim()
        }
        if (survey.isUnavailable) {
          Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            TonalBadge(
              text = stringResource(Res.string.mobile_unavailable),
              tone = BadgeTone.ERROR,
              outlined = true,
            )
          }
        }
        if (survey.isDownloaded) {
          SurveyCardOverflowMenu(
            surveyTitle = survey.title,
            onRemoveClick = onRemoveClick,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
          )
        }
      }
      HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
      Column(
        modifier = Modifier.fillMaxWidth().weight(1f).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().grayscale(survey.isClosed),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Text(
              text = survey.title,
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            SurveyVisibilityIcon(
              survey = survey,
              publicDataLabel = stringResource(Res.string.mobile_survey_badge_public_data),
              privateSubmissionsLabel =
                stringResource(Res.string.mobile_survey_badge_private_submissions),
              showTooltip = false,
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.LocationOn,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(12.dp),
            )
            Text(
              text =
                if (organizationName != null) {
                  "${survey.location} · $organizationName"
                } else {
                  survey.location
                },
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Text(
            text = survey.description.ifBlank { " " },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        SurveyCardActionRow(
          survey = survey,
          onDownloadClick = onDownloadClick,
          onOpenSurveyClick = onOpenSurveyClick,
        )
      }
    }
  }
}

/**
 * Bottom row of a survey card: `Available offline` and the `Open` button on a downloaded survey,
 * the `Download` button otherwise.
 */
@Composable
private fun SurveyCardActionRow(
  survey: SurveyPreviewItem,
  onDownloadClick: () -> Unit,
  onOpenSurveyClick: () -> Unit,
) {
  val openDescription =
    stringResource(Res.string.mobile_survey_open_content_description, survey.title)
  val downloadDescription =
    stringResource(Res.string.mobile_survey_download_content_description, survey.title)
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (survey.isDownloaded) {
      Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Outlined.OfflinePin,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = stringResource(Res.string.mobile_available_offline),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      FilledTonalButton(
        onClick = onOpenSurveyClick,
        modifier =
          Modifier.height(40.dp).semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = openDescription
          },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      ) {
        Text(
          text = stringResource(Res.string.mobile_open),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          softWrap = false,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
      }
    } else {
      Spacer(modifier = Modifier.weight(1f))
      Button(
        onClick = onDownloadClick,
        modifier =
          Modifier.height(40.dp).semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = downloadDescription
          },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Download,
          contentDescription = null,
          modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(Res.string.mobile_download),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          softWrap = false,
        )
      }
    }
  }
}

/**
 * Overflow menu of a downloaded survey's card, over the top-right corner of its thumbnail. `Remove
 * from device` calls [onRemoveClick], which asks for confirmation before anything is removed.
 */
@Composable
private fun SurveyCardOverflowMenu(
  surveyTitle: String,
  onRemoveClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var isExpanded by remember { mutableStateOf(false) }
  Box(modifier = modifier) {
    // No fill, so the map shows through. The thumbnail is imagery, not a themed surface: the dots
    // are light with a dark edge, like the thumbnail's place labels, to read on any of it.
    IconButton(onClick = { isExpanded = true }) {
      Icon(
        imageVector = Icons.Outlined.MoreVert,
        contentDescription = null,
        tint = Color.Black.copy(alpha = 0.55f),
        modifier = Modifier.offset(x = 1.dp, y = 1.dp),
      )
      Icon(
        imageVector = Icons.Outlined.MoreVert,
        contentDescription =
          stringResource(Res.string.mobile_survey_more_options_content_description, surveyTitle),
        tint = Color.White,
      )
    }
    DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.mobile_remove_from_device)) },
        leadingIcon = { Icon(imageVector = Icons.Outlined.Delete, contentDescription = null) },
        onClick = {
          isExpanded = false
          onRemoveClick()
        },
      )
    }
  }
}
