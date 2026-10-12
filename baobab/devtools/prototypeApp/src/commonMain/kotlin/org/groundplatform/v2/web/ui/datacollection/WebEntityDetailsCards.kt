/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.web.ui.datacollection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CollapseContent
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.groundplatform.v2.app.domain.model.EntityDetailsPane
import org.groundplatform.v2.app.domain.model.GeospatialEntityItem
import org.groundplatform.v2.app.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.app.ui.datacollection.DetailsMenuAction
import org.groundplatform.v2.app.ui.datacollection.DetailsOverflowMenu
import org.groundplatform.v2.app.ui.datacollection.EntityDetailsHeader
import org.groundplatform.v2.app.ui.datacollection.EntityPropertiesPane
import org.groundplatform.v2.app.ui.datacollection.EntitySubmissionsPane
import org.groundplatform.v2.app.ui.datacollection.SubmissionFullDetailsCard
import org.groundplatform.v2.app.ui.datacollection.downloadPdfMenuAction
import org.groundplatform.v2.app.ui.viewmodel.DataCollectionActions
import org.groundplatform.v2.devtools.prototypeapp.PrototypeAppState

/** Secondary entity actions shown in the web header's overflow menu, sent to [actions]. */
private fun entityOverflowActions(
  entity: GeospatialEntityItem,
  actions: DataCollectionActions,
): List<DetailsMenuAction> =
  listOf(
    DetailsMenuAction("QR code", Icons.Outlined.QrCode) { actions.openEntityQrCode(entity.id) },
    downloadPdfMenuAction { actions.downloadEntityPdf(entity.id) },
  )

/**
 * Web dashboard's floating details card (upper-right corner of the map) for the selected entity,
 * with two tabs backed by [PrototypeAppState.entityDetailsPane]:
 * - `Data` ([EntityDetailsPane.PROPERTIES], default): a compact status chip and the entity's
 *   properties (its current state).
 * - `History` ([EntityDetailsPane.SUBMISSIONS]): the entity's submissions. An opened submission
 *   replaces the list in a document-style view, with back to the list. Opening a submission from
 *   elsewhere (e.g. `Uploads`) selects this tab, and switching to `Data` and back keeps it open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebEntityDetailsCard(
  entity: GeospatialEntityItem,
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
  onCollapse: (() -> Unit)? = null,
) {
  val submission =
    state.dataCollectionUiState.selectedSubmission?.takeIf { it.entityId == entity.id }
  val pane = state.entityDetailsPane
  // Each tab (and an opened submission) starts scrolled to the top.
  val scrollState = remember(entity.id, pane, submission?.id) { ScrollState(initial = 0) }

  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation = 6.dp,
  ) {
    Column {
      EntityDetailsHeader(
        entity = entity,
        onClose = { state.selectEntity(null) },
        onCollapse = onCollapse,
        layer = state.mapLayerFor(entity),
        // The status chip is shown, compact, at the top of the `Data` tab body instead.
        showStatus = false,
        trailingActions = {
          DetailsOverflowMenu(
            actions = entityOverflowActions(entity, state.dataCollection),
            contentDescription = "More ${entity.singularTypeLabel.lowercase()} actions",
          )
        },
        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
      )

      PrimaryTabRow(selectedTabIndex = pane.ordinal, containerColor = Color.Transparent) {
        EntityDetailsPane.entries.forEach { tabPane ->
          Tab(
            selected = pane == tabPane,
            onClick = { state.selectEntityDetailsTab(tabPane) },
            text = {
              Text(
                text =
                  when (tabPane) {
                    EntityDetailsPane.PROPERTIES -> "Data"
                    EntityDetailsPane.SUBMISSIONS -> "History"
                  },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }
      }

      Column(
        modifier =
          Modifier.weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
      ) {
        when {
          pane == EntityDetailsPane.PROPERTIES ->
            EntityPropertiesPane(entity = entity, state = state, isWeb = true)
          submission != null ->
            SubmissionFullDetailsCard(
              submission = submission,
              state = state,
              isDark = state.isDarkTheme,
              textColor = MaterialTheme.colorScheme.onSurface,
              backLabel = "Back to submissions",
              onBack = { state.dataCollection.selectSubmissionDetail(null) },
              onSharePdf = { state.dataCollection.downloadSubmissionPdf(submission.id) },
              isSidePanel = true,
            )
          else -> EntitySubmissionsPane(entity = entity, state = state, isWeb = true)
        }
      }
    }
  }
}

/**
 * Web dashboard's floating card for a standalone submission (one without an attached map feature),
 * shown in the same document-style view.
 */
@Composable
internal fun WebSubmissionDetailsCard(
  submission: SubmissionPreviewItem,
  state: PrototypeAppState,
  modifier: Modifier = Modifier,
  onCollapse: (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation = 6.dp,
  ) {
    Column {
      Row(
        modifier =
          Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        IconButton(
          onClick = { state.dataCollection.selectSubmissionDetail(null) },
          modifier = Modifier.size(32.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.size(20.dp),
          )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
          Text(
            text = submission.formTitle,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = submission.timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        DetailsOverflowMenu(
          actions =
            listOf(
              downloadPdfMenuAction { state.dataCollection.downloadSubmissionPdf(submission.id) }
            ),
          contentDescription = "More submission actions",
        )
        if (onCollapse != null) {
          IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Outlined.CollapseContent,
              contentDescription = "Collapse details",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        SubmissionFullDetailsCard(
          submission = submission,
          state = state,
          isDark = state.isDarkTheme,
          textColor = MaterialTheme.colorScheme.onSurface,
          backLabel = "Back",
          onBack = { state.dataCollection.selectSubmissionDetail(null) },
          onSharePdf = { state.dataCollection.downloadSubmissionPdf(submission.id) },
          isSidePanel = true,
          showHeader = false,
        )
      }
    }
  }
}
