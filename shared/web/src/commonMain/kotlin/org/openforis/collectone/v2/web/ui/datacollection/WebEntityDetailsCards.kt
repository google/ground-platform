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
package org.openforis.collectone.v2.web.ui.datacollection

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
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.geometryType
import org.openforis.collectone.v2.app.domain.model.updatedAtLabel
import org.openforis.collectone.v2.app.domain.model.whispAnalysis
import org.openforis.collectone.v2.app.ui.dashboard.DashboardActions
import org.openforis.collectone.v2.app.ui.datacollection.DataCollectionActions
import org.openforis.collectone.v2.app.ui.datacollection.DetailsMenuAction
import org.openforis.collectone.v2.app.ui.datacollection.DetailsOverflowMenu
import org.openforis.collectone.v2.app.ui.datacollection.EntityDetailsActionButton
import org.openforis.collectone.v2.app.ui.datacollection.EntityDetailsActions
import org.openforis.collectone.v2.app.ui.datacollection.EntityDetailsHeader
import org.openforis.collectone.v2.app.ui.datacollection.EntityDetailsUiState
import org.openforis.collectone.v2.app.ui.datacollection.EntityPropertiesPane
import org.openforis.collectone.v2.app.ui.datacollection.EntitySubmissionsPane
import org.openforis.collectone.v2.app.ui.datacollection.SubmissionDetailsUiState
import org.openforis.collectone.v2.app.ui.datacollection.SubmissionFullDetailsCard
import org.openforis.collectone.v2.app.ui.datacollection.WhispRiskAnalysisPane
import org.openforis.collectone.v2.app.ui.datacollection.downloadPdfMenuAction
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane
import org.openforis.collectone.v2.app.ui.text.typeText
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Tab
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_back
import org.openforis.collectone.v2.web.resources.web_back_to_submissions
import org.openforis.collectone.v2.web.resources.web_collapse_details
import org.openforis.collectone.v2.web.resources.web_entity_tab_data
import org.openforis.collectone.v2.web.resources.web_entity_tab_history
import org.openforis.collectone.v2.web.resources.web_entity_tab_risk_analysis
import org.openforis.collectone.v2.web.resources.web_more_entity_actions
import org.openforis.collectone.v2.web.resources.web_more_submission_actions
import org.openforis.collectone.v2.web.resources.web_qr_code
import org.openforis.collectone.v2.web.resources.web_time_series_open

/** Secondary entity actions shown in the web header's overflow menu, sent to [actions]. */
@Composable
private fun entityOverflowActions(
  entity: GeospatialEntityItem,
  actions: DataCollectionActions,
): List<DetailsMenuAction> =
  listOf(
    DetailsMenuAction(stringResource(Res.string.web_qr_code), Icons.Outlined.QrCode) {
      actions.openEntityQrCode(entity.id)
    },
    downloadPdfMenuAction { actions.downloadEntityPdf(entity.id) },
  )

@Composable
private fun rememberHydratedEntityDetails(
  details: EntityDetailsUiState,
  surveyId: String,
  tableRepo: org.openforis.collectone.v2.app.domain.repository.EntityTableRepository?,
): EntityDetailsUiState {
  val rawEntity = details.entity
  var hydratedProperties by
    remember(surveyId, rawEntity.datasetId, rawEntity.id) {
      mutableStateOf<Map<String, String>?>(null)
    }
  LaunchedEffect(surveyId, rawEntity.datasetId, rawEntity.id, tableRepo) {
    val canHydrate =
      tableRepo?.isLazyLoadingEnabled == true &&
        surveyId.isNotBlank() &&
        rawEntity.datasetId.isNotBlank()
    if (canHydrate) {
      hydratedProperties =
        runCatching {
          tableRepo.fetchEntityProperties(surveyId, rawEntity.datasetId, rawEntity.id)
        }
          .getOrNull()
    }
  }
  return remember(details, hydratedProperties) {
    val fetched = hydratedProperties
    if (fetched.isNullOrEmpty()) details
    else
      details.copy(entity = details.entity.copy(properties = fetched + details.entity.properties))
  }
}

/**
 * Web dashboard's floating details card (upper-right corner of the map) for the selected entity,
 * with two tabs backed by the dashboard's [EntityDetailsPane]:
 * - `Data` ([EntityDetailsPane.PROPERTIES], default): a compact status chip and the entity's
 *   properties (its current state).
 * - `History` ([EntityDetailsPane.SUBMISSIONS]): the entity's submissions. An opened submission
 *   replaces the list in a document-style view, with back to the list. Opening a submission from
 *   elsewhere (e.g. `Uploads`) selects this tab, and switching to `Data` and back keeps it open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebEntityDetailsCard(
  details: EntityDetailsUiState,
  actions: EntityDetailsActions,
  dashboard: DashboardActions,
  dataCollection: DataCollectionActions,
  modifier: Modifier = Modifier,
  surveyId: String = "",
  onCollapse: (() -> Unit)? = null,
) {
  val effectiveDetails =
    rememberHydratedEntityDetails(
      details = details,
      surveyId = surveyId,
      tableRepo = dashboard.tableRepository(),
    )
  val entity = effectiveDetails.entity
  val submission = effectiveDetails.openSubmission
  val whispAnalysis = remember(entity.properties) { entity.whispAnalysis }
  val visiblePanes =
    remember(whispAnalysis != null) {
      buildList {
        add(EntityDetailsPane.PROPERTIES)
        add(EntityDetailsPane.SUBMISSIONS)
        if (whispAnalysis != null) add(EntityDetailsPane.RISK_ANALYSIS)
      }
    }
  val pane =
    if (effectiveDetails.pane in visiblePanes) effectiveDetails.pane
    else EntityDetailsPane.PROPERTIES
  // Each tab (and an opened submission) starts scrolled to the top.
  val scrollState =
    remember(entity.id, pane, submission?.submission?.id) { ScrollState(initial = 0) }

  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shadowElevation = 6.dp,
  ) {
    Column {
      EntityDetailsHeader(
        entity = entity,
        onClose = { actions.selectEntity(null) },
        onCollapse = onCollapse,
        layer = details.layer,
        // The status chip is shown, compact, at the top of the `Data` tab body instead.
        showStatus = false,
        trailingActions = {
          DetailsOverflowMenu(
            actions = entityOverflowActions(entity, dataCollection),
            contentDescription =
              stringResource(
                Res.string.web_more_entity_actions,
                entity.typeText.resolve().lowercase(),
              ),
          )
        },
        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
      )

      PrimaryTabRow(
        selectedTabIndex = visiblePanes.indexOf(pane).coerceAtLeast(0),
        containerColor = Color.Transparent,
      ) {
        visiblePanes.forEach { tabPane ->
          Tab(
            selected = pane == tabPane,
            onClick = { dashboard.selectEntityDetailsTab(tabPane) },
            text = {
              Text(
                text =
                  when (tabPane) {
                    EntityDetailsPane.PROPERTIES -> stringResource(Res.string.web_entity_tab_data)
                    EntityDetailsPane.SUBMISSIONS ->
                      stringResource(Res.string.web_entity_tab_history)
                    EntityDetailsPane.RISK_ANALYSIS ->
                      stringResource(Res.string.web_entity_tab_risk_analysis)
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
          pane == EntityDetailsPane.RISK_ANALYSIS && whispAnalysis != null ->
            WhispRiskAnalysisPane(analysis = whispAnalysis)
          pane == EntityDetailsPane.PROPERTIES ->
            EntityPropertiesPane(
              uiState = effectiveDetails,
              actions = actions,
              isWeb = true,
              trailingWebActions = {
                if (
                  entity.geometryType == GeometryType.POINT ||
                    entity.geometryType == GeometryType.POLYGON
                ) {
                  EntityDetailsActionButton(
                    text = stringResource(Res.string.web_time_series_open),
                    icon = Icons.Outlined.Timeline,
                    onClick = { dashboard.showTimeSeries() },
                  )
                }
              },
            )
          submission != null ->
            SubmissionFullDetailsCard(
              uiState = submission,
              backLabel = stringResource(Res.string.web_back_to_submissions),
              onBack = { dataCollection.selectSubmissionDetail(null) },
              onSharePdf = { dataCollection.downloadSubmissionPdf(submission.submission.id) },
              isSidePanel = true,
            )
          else ->
            EntitySubmissionsPane(
              uiState = effectiveDetails,
              actions = actions,
              isWeb = true,
            )
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
  details: SubmissionDetailsUiState,
  dataCollection: DataCollectionActions,
  modifier: Modifier = Modifier,
  onCollapse: (() -> Unit)? = null,
) {
  val submission = details.submission
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
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
          onClick = { dataCollection.selectSubmissionDetail(null) },
          modifier = Modifier.size(32.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(Res.string.web_back),
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
            text = submission.updatedAtLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        DetailsOverflowMenu(
          actions =
            listOf(downloadPdfMenuAction { dataCollection.downloadSubmissionPdf(submission.id) }),
          contentDescription = stringResource(Res.string.web_more_submission_actions),
        )
        if (onCollapse != null) {
          IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Outlined.CollapseContent,
              contentDescription = stringResource(Res.string.web_collapse_details),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        SubmissionFullDetailsCard(
          uiState = details,
          backLabel = stringResource(Res.string.web_back),
          onBack = { dataCollection.selectSubmissionDetail(null) },
          onSharePdf = { dataCollection.downloadSubmissionPdf(submission.id) },
          isSidePanel = true,
          showHeader = false,
        )
      }
    }
  }
}
