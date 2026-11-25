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
package org.openforis.collectone.v2.mobile.ui.surveys

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.ui.common.ClosedSurveyThumbnailScrim
import org.openforis.collectone.v2.app.ui.common.SurveyVisibilityIcon
import org.openforis.collectone.v2.app.ui.common.grayscale
import org.openforis.collectone.v2.app.ui.map.SurveyMapThumbnail
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.mobile.resources.Res
import org.openforis.collectone.v2.mobile.resources.mobile_back
import org.openforis.collectone.v2.mobile.resources.mobile_browse_more_surveys
import org.openforis.collectone.v2.mobile.resources.mobile_downloaded_surveys_count
import org.openforis.collectone.v2.mobile.resources.mobile_downloaded_surveys_title
import org.openforis.collectone.v2.mobile.resources.mobile_open
import org.openforis.collectone.v2.mobile.resources.mobile_survey_badge_active
import org.openforis.collectone.v2.mobile.resources.mobile_survey_badge_private_submissions
import org.openforis.collectone.v2.mobile.resources.mobile_survey_badge_public_data
import org.openforis.collectone.v2.mobile.resources.mobile_survey_location_entity_count
import org.openforis.collectone.v2.mobile.resources.mobile_switch
import org.openforis.collectone.v2.mobile.resources.mobile_unavailable

/**
 * Separate screen accessible from the `"Surveys"` navigation drawer option showing ONLY the surveys
 * which have already been downloaded to the device, plus a primary action button (`"Browse &
 * download more surveys"`) which navigates to the full `Download surveys` screen.
 *
 * @param downloadedSurveys the surveys downloaded to the device; [activeSurveyId] is highlighted.
 * @param onOpenSurvey opens a survey in the Main Survey UI.
 * @param onBrowseMoreSurveys opens the full `Download surveys` screen.
 * @param onBack closes the sub-screen from the Back arrow at the top left; null hides the arrow
 *   (when there is nothing to return to).
 */
@Composable
internal fun SwitchDownloadedSurveysSubScreen(
  downloadedSurveys: List<SurveyPreviewItem>,
  activeSurveyId: String,
  onOpenSurvey: (surveyId: String) -> Unit,
  onBrowseMoreSurveys: () -> Unit,
  onBack: (() -> Unit)?,
) {
  val downloadedList = downloadedSurveys

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (onBack != null) {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = stringResource(Res.string.mobile_back),
            )
          }
        } else {
          Icon(
            imageVector = Icons.Outlined.SwapHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
          )
        }
        Column {
          Text(
            text = stringResource(Res.string.mobile_downloaded_surveys_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              pluralStringResource(
                Res.plurals.mobile_downloaded_surveys_count,
                downloadedList.size,
                downloadedList.size,
              ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // List of Downloaded Surveys only
    downloadedList.forEach { survey ->
      val isActive = survey.id == activeSurveyId
      OutlinedCard(
        onClick = { onOpenSurvey(survey.id) },
        modifier = Modifier.fillMaxWidth().height(260.dp),
        shape = MaterialTheme.shapes.medium,
        // Every card has the same outline; the active survey is marked by its badge and its tint.
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
        colors =
          CardDefaults.outlinedCardColors(
            containerColor =
              if (isActive) {
                MaterialTheme.colorScheme.secondaryContainer
              } else {
                MaterialTheme.colorScheme.surface
              },
            contentColor =
              if (isActive) {
                MaterialTheme.colorScheme.onSecondaryContainer
              } else {
                MaterialTheme.colorScheme.onSurface
              },
          ),
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            // Every survey here is on the device, so the thumbnail carries no downloaded badge.
            SurveyMapThumbnail(
              thumbnail = survey.thumbnail,
              isDownloaded = false,
              modifier = Modifier.fillMaxSize().grayscale(survey.isClosed),
            )
            if (survey.isClosed) {
              ClosedSurveyThumbnailScrim()
            }
            if (isActive || survey.isUnavailable) {
              Row(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (isActive) {
                  TonalBadge(
                    text = stringResource(Res.string.mobile_survey_badge_active),
                    tone = BadgeTone.PRIMARY,
                    outlined = true,
                  )
                }
                if (survey.isUnavailable) {
                  TonalBadge(
                    text = stringResource(Res.string.mobile_unavailable),
                    tone = BadgeTone.ERROR,
                    outlined = true,
                  )
                }
              }
            }
          }
          HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
          Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxSize(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier.weight(1f).grayscale(survey.isClosed),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = survey.title,
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color =
                    if (isActive) {
                      MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                      MaterialTheme.colorScheme.onSurface
                    },
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    pluralStringResource(
                      Res.plurals.mobile_survey_location_entity_count,
                      survey.entityCount,
                      survey.location,
                      survey.entityCount,
                    ),
                  style =
                    MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  color =
                    if (isActive) {
                      MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                      MaterialTheme.colorScheme.primary
                    },
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = survey.description.ifBlank { " " },
                  style = MaterialTheme.typography.bodySmall,
                  color =
                    if (isActive) {
                      MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
                  lineHeight = 16.sp,
                  minLines = 2,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              if (isActive) {
                FilledTonalButton(
                  onClick = { onOpenSurvey(survey.id) },
                  shape = MaterialTheme.shapes.small,
                ) {
                  Text(
                    text = stringResource(Res.string.mobile_open),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  )
                }
              } else {
                Button(onClick = { onOpenSurvey(survey.id) }, shape = MaterialTheme.shapes.small) {
                  Text(
                    text = stringResource(Res.string.mobile_switch),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  )
                }
              }
            }
            SurveyVisibilityIcon(
              survey = survey,
              publicDataLabel = stringResource(Res.string.mobile_survey_badge_public_data),
              privateSubmissionsLabel =
                stringResource(Res.string.mobile_survey_badge_private_submissions),
              showTooltip = false,
              tint =
                if (isActive) {
                  MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
              modifier = Modifier.align(Alignment.TopEnd),
            )
          }
        }
      }
    }

    // Primary Action Button to navigate to the full Download Surveys screen
    Button(
      onClick = onBrowseMoreSurveys,
      modifier = Modifier.fillMaxWidth().height(48.dp),
      shape = MaterialTheme.shapes.medium,
    ) {
      Icon(
        imageVector = Icons.Outlined.Download,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = stringResource(Res.string.mobile_browse_more_surveys),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
      )
    }
  }
}
