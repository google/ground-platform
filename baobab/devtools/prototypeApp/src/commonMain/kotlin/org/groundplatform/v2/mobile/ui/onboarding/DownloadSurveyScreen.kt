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
package org.groundplatform.v2.mobile.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.withTimeoutOrNull
import org.groundplatform.v2.app.domain.model.EntityShapeKind
import org.groundplatform.v2.app.domain.model.SurveyPreviewItem
import org.groundplatform.v2.app.domain.model.SurveyThumbnail
import org.groundplatform.v2.app.domain.model.WebMercatorThumbnailProjection
import org.groundplatform.v2.app.ui.common.UserAvatarButton
import org.groundplatform.v2.app.ui.common.UserProfileSidePanelOverlay
import org.groundplatform.v2.app.ui.state.OnboardingUiState
import org.groundplatform.v2.app.ui.viewmodel.OnboardingActions
import org.groundplatform.v2.app.ui.viewmodel.createThumbnailHttpClient
import org.groundplatform.v2.app.ui.viewmodel.resolveMapboxPublicToken
import org.groundplatform.v2.app.ui.viewmodel.reverseGeocodeThumbnailLabels
import org.groundplatform.v2.core.forms.ui.GroundAlertDialogOverlay
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState
import org.groundplatform.v2.map.LatLng
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * 3. "Download survey" screen where users can see a list of all surveys shared with them, or search
 *    by name or location by typing in the search bar. Each survey item includes a title,
 *    description, map thumbnail, and an indicator for surveys that have already been downloaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSurveyScreen(
  uiState: OnboardingUiState,
  actions: OnboardingActions,
  notice: String?,
  isDarkTheme: Boolean,
) {
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  val filtered = uiState.filteredSurveys
  var isUserProfileOpen by remember { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(
                text = "Download survey",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text =
                  "${uiState.surveys.size} shared with you • ${uiState.downloadedSurveyCount} downloaded",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
          navigationIcon = {
            IconButton(onClick = { actions.navigateBackFromDownloadSurvey() }) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
              )
            }
          },
          actions = {
            UserAvatarButton(
              userName = uiState.profile.displayName,
              userInitials = uiState.profile.initials,
              onClick = { isUserProfileOpen = true },
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
      containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
      Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        // Search Bar for filtering by survey name or location
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
          OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { actions.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
              Text(
                text = "Search by name, location, or organization...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            },
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
            },
            trailingIcon = {
              if (uiState.searchQuery.isNotEmpty()) {
                IconButton(onClick = { actions.clearSearchQuery() }) {
                  Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Clear Search",
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            },
            shape = MaterialTheme.shapes.extraLarge,
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
              ),
          )
        }

        // Optional feedback toast banner when downloading/toggling a survey
        notice?.let { notice ->
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
              )
              Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        // Section Summary Header
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (uiState.searchQuery.isBlank()) {
                "Surveys shared with you (${filtered.size})"
              } else {
                "Matching surveys (${filtered.size} of ${uiState.surveys.size})"
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
                text = "Clear search",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }

        // Survey Items List
        if (filtered.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
          ) {
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
                text = "No surveys match \"${uiState.searchQuery}\"",
                style = MaterialTheme.typography.titleSmall,
                color = onSurfaceColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
              )
              Text(
                text =
                  "Try searching by another survey title, keyword, or location (e.g. Brazil, Kenya, Vietnam).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
            }
          }
        } else {
          Column(
            modifier =
              Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            filtered.forEach { survey ->
              SurveyListItemCard(
                survey = survey,
                organizationName = uiState.organizationNames[survey.organizationId],
                isDarkTheme = isDarkTheme,
                onDownloadClick = { actions.downloadSurvey(survey.id) },
                onToggleDownloadClick = { actions.promptRemoveDownloadedSurvey(survey.id) },
                onOpenSurveyClick = { actions.openSurvey(survey.id) },
              )
            }
            Spacer(modifier = Modifier.height(12.dp))
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
        onDismissRequest = { isUserProfileOpen = false },
        onSignOut = actions::confirmSignOut,
      )
    }

    if (uiState.isSignOutPromptOpen) {
      DownloadSurveySignOutPromptDialog(uiState, actions)
    }

    if (uiState.pendingRemovalSurveyId != null) {
      RemoveDownloadedSurveyConfirmationDialog(uiState, actions)
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

  GroundAlertDialogOverlay(
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
        text = "Remove offline survey?",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Text(
        text =
          "Removing \"${survey.title}\" will delete the offline copy (${survey.offlineSizeLabel}) from this device. You will need an internet connection to download and use it offline again.",
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
        Text("Remove", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = { actions.dismissRemoveDownloadedSurvey() }) { Text("Cancel") }
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
  GroundAlertDialogOverlay(
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
        text = "Sign out?",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Text(
        text =
          "Going back will sign out ${uiState.profile.email} and return to the Sign In screen.",
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
        Text("Sign out", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = { actions.dismissSignOutPrompt() }) { Text("Cancel") }
    },
  )
}

/**
 * Individual survey card showing:
 * - Map thumbnail placeholder (`SurveyMapThumbnail`)
 * - Survey title
 * - Survey location & coordinates
 * - Organization the survey belongs to, if any
 * - Survey description
 * - Indicator badge on surveys that have already been downloaded (`Downloaded`) or a Download CTA
 * - Action to open the survey in the Main Survey UI (`Open`)
 */
@Composable
private fun SurveyListItemCard(
  survey: SurveyPreviewItem,
  organizationName: String?,
  isDarkTheme: Boolean,
  onDownloadClick: () -> Unit,
  onToggleDownloadClick: () -> Unit,
  onOpenSurveyClick: () -> Unit,
) {
  val cardBg =
    if (survey.isDownloaded) {
      MaterialTheme.colorScheme.surfaceContainerLow
    } else {
      MaterialTheme.colorScheme.surface
    }
  val borderColor =
    if (survey.isDownloaded) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.outlineVariant
    }

  OutlinedCard(
    onClick = onOpenSurveyClick,
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.outlinedCardColors(containerColor = cardBg),
    border = BorderStroke(width = if (survey.isDownloaded) 1.5.dp else 1.dp, color = borderColor),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
      ) {
        // Satellite Map Thumbnail of Area of Interest & Map Features (80x80 dp)
        SurveyMapThumbnail(thumbnail = survey.thumbnail, isDownloaded = survey.isDownloaded)

        // Survey Metadata (Title, Location, Description)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = survey.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

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
              text = survey.location,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "• ${survey.coordinatesLabel}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (organizationName != null) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Groups,
                contentDescription = "Organization",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = organizationName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Text(
            text = survey.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Bottom Footer: Downloaded status indicator + Open Survey CTA or Download action
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "${survey.entityCount} locations • ${survey.offlineSizeLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (survey.isPublicData) {
            GroundTonalBadge(text = "Public data", tone = GroundBadgeTone.WARNING)
          } else if (survey.isPrivateSubmissions) {
            GroundTonalBadge(text = "Private submissions", tone = GroundBadgeTone.SECONDARY)
          }
        }

        if (survey.isDownloaded) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            AssistChip(
              onClick = onToggleDownloadClick,
              leadingIcon = {
                Icon(
                  imageVector = Icons.Outlined.Check,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                )
              },
              label = {
                Text(
                  text = "Downloaded",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  softWrap = false,
                )
              },
              modifier = Modifier.height(30.dp),
            )

            FilledTonalButton(
              onClick = onOpenSurveyClick,
              modifier = Modifier.height(30.dp),
              contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
              Text(
                text = "Open",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
              )
              Spacer(modifier = Modifier.width(3.dp))
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
              )
            }
          }
        } else {
          Button(
            onClick = onDownloadClick,
            modifier = Modifier.height(32.dp),
            contentPadding = ButtonDefaults.TextButtonContentPadding,
          ) {
            Icon(
              imageVector = Icons.Outlined.Download,
              contentDescription = null,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Download",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              softWrap = false,
            )
          }
        }
      }
    }
  }
}

/**
 * In-memory cache and HTTP loader for 256×256 Web Mercator satellite raster tiles rendered inside
 * [SurveyMapThumbnail].
 */
private object SurveyThumbnailTileCache {
  private const val MAX_CACHED_TILES = 256
  private const val TILE_FETCH_TIMEOUT_MS = 5_000L
  private val httpClient: HttpClient? by lazy { createThumbnailHttpClient() }
  private val cache = LinkedHashMap<String, ImageBitmap>()

  fun get(url: String): ImageBitmap? = cache[url]

  suspend fun fetchTile(url: String): ImageBitmap? {
    cache[url]?.let {
      return it
    }
    val client = httpClient ?: return null
    return try {
      withTimeoutOrNull(TILE_FETCH_TIMEOUT_MS) {
        val response = client.get(url)
        if (!response.status.isSuccess()) return@withTimeoutOrNull null
        val bytes = response.body<ByteArray>()
        if (bytes.isEmpty()) return@withTimeoutOrNull null
        val bitmap = bytes.decodeToImageBitmap()
        if (cache.size >= MAX_CACHED_TILES) {
          cache.keys.firstOrNull()?.let(cache::remove)
        }
        cache[url] = bitmap
        bitmap
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }
  }
}

/**
 * Client-side satellite map thumbnail showing the survey's selected area of interest boundary
 * (transparent fill with dotted outline like the main map), Mapbox place labels, and map features
 * (polygons, lines, and points) projected over real XYZ satellite raster tiles.
 */
@Composable
internal fun SurveyMapThumbnail(
  thumbnail: SurveyThumbnail,
  isDownloaded: Boolean,
  modifier: Modifier = Modifier,
) {
  val textMeasurer = rememberTextMeasurer()
  val mapboxToken = remember { resolveMapboxPublicToken() }
  val effectiveSatelliteTemplate =
    remember(thumbnail.tileUrlTemplate, mapboxToken) {
      if (
        mapboxToken != null &&
          thumbnail.tileUrlTemplate == SurveyThumbnail.DEFAULT_SATELLITE_TILE_URL
      ) {
        "https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/tiles/256/{z}/{x}/{y}?access_token=$mapboxToken"
      } else {
        thumbnail.tileUrlTemplate
      }
    }
  val coveringTiles =
    remember(thumbnail.bounds, thumbnail.zoom) {
      WebMercatorThumbnailProjection.coveringTiles(thumbnail.bounds, thumbnail.zoom)
    }
  var loadedTiles by
    remember(coveringTiles, effectiveSatelliteTemplate) {
      mutableStateOf(
        coveringTiles
          .mapNotNull { coord ->
            val url = coord.resolveUrl(effectiveSatelliteTemplate)
            SurveyThumbnailTileCache.get(url)?.let { coord to it }
          }
          .toMap()
      )
    }
  var loadedLabelTiles by
    remember(coveringTiles, thumbnail.labelTileUrlTemplate, mapboxToken) {
      mutableStateOf(
        if (mapboxToken != null || thumbnail.labelTileUrlTemplate.isBlank()) {
          emptyMap()
        } else {
          coveringTiles
            .mapNotNull { coord ->
              val url = coord.resolveUrl(thumbnail.labelTileUrlTemplate)
              SurveyThumbnailTileCache.get(url)?.let { coord to it }
            }
            .toMap()
        }
      )
    }
  var placeLabels by remember(thumbnail.placeLabels) { mutableStateOf(thumbnail.placeLabels) }

  LaunchedEffect(coveringTiles, effectiveSatelliteTemplate, thumbnail.labelTileUrlTemplate) {
    for (coord in coveringTiles) {
      if (coord !in loadedTiles) {
        val url = coord.resolveUrl(effectiveSatelliteTemplate)
        val bitmap = SurveyThumbnailTileCache.fetchTile(url)
        if (bitmap != null) {
          loadedTiles = loadedTiles + (coord to bitmap)
        }
      }
      if (
        mapboxToken == null &&
          thumbnail.labelTileUrlTemplate.isNotBlank() &&
          coord !in loadedLabelTiles
      ) {
        val labelUrl = coord.resolveUrl(thumbnail.labelTileUrlTemplate)
        val labelBitmap = SurveyThumbnailTileCache.fetchTile(labelUrl)
        if (labelBitmap != null) {
          loadedLabelTiles = loadedLabelTiles + (coord to labelBitmap)
        }
      }
    }
  }

  LaunchedEffect(thumbnail.center, mapboxToken) {
    if (mapboxToken != null) {
      val geocoded = reverseGeocodeThumbnailLabels(thumbnail.center, limit = 2)
      if (geocoded.isNotEmpty()) {
        placeLabels = geocoded
      }
    }
  }

  Box(
    modifier =
      modifier
        .size(80.dp)
        .clip(MaterialTheme.shapes.medium)
        .background(Color(0xFF142E21))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      // Base satellite canopy/earth texture shown behind or before raster tiles resolve
      drawRect(color = Color(0xFF163223))
      drawCircle(
        color = Color(0xFF1F422D),
        radius = size.minDimension * 0.55f,
        center = Offset(size.width * 0.30f, size.height * 0.35f),
      )
      drawCircle(
        color = Color(0xFF284D34),
        radius = size.minDimension * 0.45f,
        center = Offset(size.width * 0.72f, size.height * 0.68f),
      )

      val minNormX =
        min(
          WebMercatorThumbnailProjection.lngToNormX(thumbnail.bounds.west),
          WebMercatorThumbnailProjection.lngToNormX(thumbnail.bounds.east),
        )
      val maxNormX =
        max(
          WebMercatorThumbnailProjection.lngToNormX(thumbnail.bounds.west),
          WebMercatorThumbnailProjection.lngToNormX(thumbnail.bounds.east),
        )
      val minNormY =
        min(
          WebMercatorThumbnailProjection.latToNormY(thumbnail.bounds.north),
          WebMercatorThumbnailProjection.latToNormY(thumbnail.bounds.south),
        )
      val maxNormY =
        max(
          WebMercatorThumbnailProjection.latToNormY(thumbnail.bounds.north),
          WebMercatorThumbnailProjection.latToNormY(thumbnail.bounds.south),
        )
      val centerNormX = (minNormX + maxNormX) / 2.0
      val centerNormY = (minNormY + maxNormY) / 2.0
      val spanNorm = max(max(maxNormX - minNormX, maxNormY - minNormY), 1e-6)
      val worldCanvasPx = min(size.width, size.height).toDouble() / spanNorm

      fun toCanvasX(normX: Double): Float =
        (size.width / 2.0 + (normX - centerNormX) * worldCanvasPx).toFloat()

      fun toCanvasY(normY: Double): Float =
        (size.height / 2.0 + (normY - centerNormY) * worldCanvasPx).toFloat()

      fun toOffset(point: LatLng): Offset =
        Offset(
          x = toCanvasX(WebMercatorThumbnailProjection.lngToNormX(point.longitude)),
          y = toCanvasY(WebMercatorThumbnailProjection.latToNormY(point.latitude)),
        )

      // 1. Draw covering XYZ satellite raster tiles
      for ((coord, bitmap) in loadedTiles) {
        val left = floor(toCanvasX(coord.normLeft)).toInt()
        val top = floor(toCanvasY(coord.normTop)).toInt()
        val right = ceil(toCanvasX(coord.normRight)).toInt()
        val bottom = ceil(toCanvasY(coord.normBottom)).toInt()
        drawImage(
          image = bitmap,
          dstOffset = IntOffset(left, top),
          dstSize = IntSize(max(1, right - left), max(1, bottom - top)),
        )
      }

      // 1b. Draw reference boundaries & place label tiles (matches SurveyBasemaps.Satellite)
      for ((coord, labelBitmap) in loadedLabelTiles) {
        val left = floor(toCanvasX(coord.normLeft)).toInt()
        val top = floor(toCanvasY(coord.normTop)).toInt()
        val right = ceil(toCanvasX(coord.normRight)).toInt()
        val bottom = ceil(toCanvasY(coord.normBottom)).toInt()
        drawImage(
          image = labelBitmap,
          dstOffset = IntOffset(left, top),
          dstSize = IntSize(max(1, right - left), max(1, bottom - top)),
          alpha = 0.88f,
        )
      }

      // 2. Draw Area of Interest square: transparent fill with dotted outline like the main map
      val aoiDashEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()), 0f)
      val aoiStrokeColor = Color(0xFF8BD6B1).copy(alpha = 0.85f)
      for (ring in thumbnail.areaParts) {
        if (ring.size < 3) continue
        val offsets = ring.map(::toOffset)
        val path =
          Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            for (i in 1 until offsets.size) {
              lineTo(offsets[i].x, offsets[i].y)
            }
            close()
          }
        drawPath(
          path = path,
          color = Color.Black.copy(alpha = 0.45f),
          style =
            Stroke(width = 2.6.dp.toPx(), join = StrokeJoin.Round, pathEffect = aoiDashEffect),
        )
        drawPath(
          path = path,
          color = aoiStrokeColor,
          style =
            Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round, pathEffect = aoiDashEffect),
        )
      }

      // Helper to ensure small polygon/line features remain legible when framed inside a wide AOI
      fun ensureMinSpan(points: List<Offset>, minSpanPx: Float): List<Offset> {
        if (points.size < 2) return points
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val span = max(maxX - minX, maxY - minY)
        if (span >= minSpanPx || span <= 1e-3f) return points
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val factor = minSpanPx / span
        return points.map { pt -> Offset(cx + (pt.x - cx) * factor, cy + (pt.y - cy) * factor) }
      }

      // 3. Draw Polygon Map Features
      for (feature in thumbnail.features) {
        if (feature.kind != EntityShapeKind.POLYGON || feature.vertices.size < 3) continue
        val pts = ensureMinSpan(feature.vertices.map(::toOffset), minSpanPx = 7.dp.toPx())
        val featureColor = Color(0xFF000000L or (feature.colorHex and 0xFFFFFFL))
        val path =
          Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
              lineTo(pts[i].x, pts[i].y)
            }
            close()
          }
        drawPath(path = path, color = featureColor.copy(alpha = 0.48f))
        drawPath(
          path = path,
          color = Color.White.copy(alpha = 0.90f),
          style = Stroke(width = 2.0.dp.toPx(), join = StrokeJoin.Round),
        )
        drawPath(
          path = path,
          color = featureColor,
          style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round),
        )
      }

      // 4. Draw Line Map Features
      for (feature in thumbnail.features) {
        if (feature.kind != EntityShapeKind.LINE || feature.vertices.size < 2) continue
        val pts = ensureMinSpan(feature.vertices.map(::toOffset), minSpanPx = 8.dp.toPx())
        val featureColor = Color(0xFF000000L or (feature.colorHex and 0xFFFFFFL))
        val path =
          Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
              lineTo(pts[i].x, pts[i].y)
            }
          }
        drawPath(
          path = path,
          color = Color.White.copy(alpha = 0.90f),
          style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
          path = path,
          color = featureColor,
          style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
      }

      // 5. Draw Point Map Features
      for (feature in thumbnail.features) {
        if (feature.kind != EntityShapeKind.POINT || feature.vertices.isEmpty()) continue
        val pt = toOffset(feature.vertices.first())
        val featureColor = Color(0xFF000000L or (feature.colorHex and 0xFFFFFFL))
        drawCircle(
          color = Color.Black.copy(alpha = 0.35f),
          radius = 3.8.dp.toPx(),
          center = Offset(pt.x, pt.y + 0.6.dp.toPx()),
        )
        drawCircle(color = Color.White, radius = 3.3.dp.toPx(), center = pt)
        drawCircle(color = featureColor, radius = 2.2.dp.toPx(), center = pt)
      }

      // 6. Draw Mapbox Place Labels (haloed satellite place names)
      val haloTextStyle =
        TextStyle(
          color = Color(0xD90A1912),
          fontSize = 8.5.sp,
          fontWeight = FontWeight.SemiBold,
        )
      val labelTextStyle =
        TextStyle(
          color = Color(0xFFF5F7F6),
          fontSize = 8.5.sp,
          fontWeight = FontWeight.SemiBold,
        )
      placeLabels.take(2).forEachIndexed { index, place ->
        val rawAnchor = toOffset(place.position)
        val measured =
          textMeasurer.measure(
            text = place.name,
            style = labelTextStyle,
            maxLines = 1,
            softWrap = false,
          )
        val textW = measured.size.width.toFloat()
        val textH = measured.size.height.toFloat()
        val pad = 3.dp.toPx()
        val defaultY =
          if (index == 0) {
            rawAnchor.y.coerceIn(pad + textH / 2f, size.height * 0.32f)
          } else {
            rawAnchor.y.coerceIn(size.height * 0.68f, size.height - pad - textH / 2f)
          }
        val topLeftX = (rawAnchor.x - textW / 2f).coerceIn(pad, max(pad, size.width - textW - pad))
        val topLeftY = (defaultY - textH / 2f).coerceIn(pad, max(pad, size.height - textH - pad))

        val haloLayout =
          textMeasurer.measure(
            text = place.name,
            style = haloTextStyle,
            maxLines = 1,
            softWrap = false,
          )
        val haloOffset = 0.9.dp.toPx()
        for (dx in listOf(-haloOffset, haloOffset)) {
          for (dy in listOf(-haloOffset, haloOffset)) {
            drawText(
              textLayoutResult = haloLayout,
              topLeft = Offset(topLeftX + dx, topLeftY + dy),
            )
          }
        }
        drawText(
          textLayoutResult = measured,
          topLeft = Offset(topLeftX, topLeftY),
        )
      }
    }

    // Top-right mini offline check icon on map thumbnail when downloaded
    if (isDownloaded) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(1.dp, Color.White),
        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = "Downloaded",
            modifier = Modifier.size(11.dp),
          )
        }
      }
    }
  }
}

/** Backward-compatible alias for [DownloadSurveyScreen]. */
@Composable
fun GroundDownloadSurveyScreen(state: PrototypeAppState) {
  val uiState by state.onboarding.uiState.collectAsState()
  DownloadSurveyScreen(
    uiState = uiState,
    actions = state.onboarding,
    notice = state.activeSurveyNotice,
    isDarkTheme = state.isDarkTheme,
  )
}
