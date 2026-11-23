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
package org.openforis.collectone.v2.web.ui.dashboard

import org.openforis.collectone.v2.app.domain.model.EntityPresentationPropertyKeys
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.WhispAnalysis
import org.openforis.collectone.v2.app.domain.model.WhispRiskLevel
import org.openforis.collectone.v2.app.domain.model.hasGeometry
import org.openforis.collectone.v2.app.domain.model.isWhispPropertyKey
import org.openforis.collectone.v2.app.domain.model.whispAnalysis

/** Kind of tabular view shown in the web dashboard's data table panel. */
enum class DashboardDataTableKind {
  /** Records of a map layer (`EntityDatasetDef` with geometry / `EntityRecord`). */
  MAP_LAYER,

  /** Records of a data table (`EntityDatasetDef` without geometry). */
  DATA_TABLE,
}

/** One row of a [DashboardDataTable]; [id] is the entity record ID. */
data class DashboardDataTableRow(
  val id: String,
  val cells: List<String>,
  val isSelected: Boolean = false,
  /** The record this row lists; used to render its [DASHBOARD_STATUS_COLUMN] cell as a chip. */
  val entity: GeospatialEntityItem? = null,
)

/** First column: the record's workflow status, rendered as a compact status chip. */
internal const val DASHBOARD_STATUS_COLUMN = "Status"

/** Column holding the record's label; it is wider than the other columns. */
internal const val DASHBOARD_LABEL_COLUMN = "Label"

/** Name of the column holding each record's GeoID, shown with a pending-sync icon until synced. */
internal const val DASHBOARD_GEO_ID_COLUMN = "GeoID"

/**
 * Name of the rolled-up Whisp column shown when a dataset has `whisp_*` properties and the user
 * hasn't expanded individual Whisp columns.
 */
internal const val DASHBOARD_WHISP_COLUMN = "Risk analysis"

/**
 * A table rendered in the web dashboard's collapsible data table panel, listing every record of one
 * entity dataset. Submissions are never shown here: their data can be hierarchical, so they are
 * only shown in a document-style view.
 */
data class DashboardDataTable(
  val id: String,
  val datasetId: String,
  val title: String,
  val kind: DashboardDataTableKind,
  val columns: List<String>,
  val rows: List<DashboardDataTableRow>,
  val whispColumnCount: Int = 0,
  val isWhispExpanded: Boolean = false,
  val exportColumns: List<String> = columns,
  val exportRows: List<List<String>> = rows.map { it.cells },
) {
  /** True when at least one record in this table has `whisp_*` enrichment properties. */
  val hasWhispColumns: Boolean
    get() = whispColumnCount > 0

  /** Index of the highlighted row, or `-1` when none is selected. */
  val selectedRowIndex: Int
    get() = rows.indexOfFirst { it.isSelected }
}

/**
 * Lightweight descriptor of one entity dataset tab in the dashboard's bottom data table panel.
 * Avoids eagerly materializing a `rows × columns` cell matrix in memory so wide/enriched datasets
 * can render lazily via [org.openforis.collectone.v2.web.ui.common.VirtualDataTable] and
 * [org.openforis.collectone.v2.web.domain.model.PagedEntityTableCache].
 */
internal data class DashboardDatasetTab(
  val id: String,
  val datasetId: String,
  val title: String,
  val kind: DashboardDataTableKind,
  val entities: List<GeospatialEntityItem>,
  val localPropertyKeys: List<String>,
)

/** Groups [entities] into lightweight [DashboardDatasetTab]s without copying all cell strings. */
internal fun buildDashboardDatasetTabs(
  entities: List<GeospatialEntityItem>
): List<DashboardDatasetTab> =
  entities
    .groupBy { it.datasetId }
    .map { (datasetId, datasetEntities) ->
      val localKeys = LinkedHashSet<String>()
      for (entity in datasetEntities) {
        for (key in entity.properties.keys) {
          if (key !in EntityPresentationPropertyKeys) localKeys += key
        }
      }
      DashboardDatasetTab(
        id = "dataset:$datasetId",
        datasetId = datasetId,
        title = datasetEntities.first().datasetName,
        kind =
          if (datasetEntities.any { it.hasGeometry }) {
            DashboardDataTableKind.MAP_LAYER
          } else {
            DashboardDataTableKind.DATA_TABLE
          },
        entities = datasetEntities,
        localPropertyKeys = localKeys.toList(),
      )
    }

/**
 * Builds the web dashboard's data tables: one per entity dataset in [entities] (in order of first
 * appearance), with the [selectedEntityId] row highlighted. Columns are [DASHBOARD_STATUS_COLUMN],
 * [DASHBOARD_LABEL_COLUMN], `Submissions`, [DASHBOARD_GEO_ID_COLUMN], optionally the rolled-up
 * [DASHBOARD_WHISP_COLUMN], then the dataset's properties. Presentation properties (marker and
 * stroke styling) are left out; property values that reference another record are shown as that
 * record's label via [relatedLabel].
 *
 * When [expandWhispColumns] is `false` (the default), `whisp_*` columns are rolled up into a single
 * [DASHBOARD_WHISP_COLUMN] summary column in the UI while [DashboardDataTable.exportColumns] and
 * [DashboardDataTable.exportRows] retain every individual `whisp_*` column for CSV export.
 */
fun buildDashboardDataTables(
  entities: List<GeospatialEntityItem>,
  selectedEntityId: String?,
  relatedLabel: (entity: GeospatialEntityItem, value: String) -> String? = { _, _ -> null },
  expandWhispColumns: Boolean = false,
): List<DashboardDataTable> =
  entities
    .groupBy { it.datasetId }
    .map { (datasetId, datasetEntities) ->
      buildDatasetDashboardTable(
        datasetId = datasetId,
        datasetEntities = datasetEntities,
        selectedEntityId = selectedEntityId,
        relatedLabel = relatedLabel,
        expandWhispColumns = expandWhispColumns,
      )
    }

private val DASHBOARD_FIXED_COLUMNS =
  listOf(
    DASHBOARD_STATUS_COLUMN,
    DASHBOARD_LABEL_COLUMN,
    "Submissions",
    DASHBOARD_GEO_ID_COLUMN,
  )

private fun buildDatasetDashboardTable(
  datasetId: String,
  datasetEntities: List<GeospatialEntityItem>,
  selectedEntityId: String?,
  relatedLabel: (entity: GeospatialEntityItem, value: String) -> String?,
  expandWhispColumns: Boolean,
): DashboardDataTable {
  val allPropertyKeys =
    datasetEntities
      .asSequence()
      .flatMap { it.properties.keys.asSequence() }
      .filter { it !in EntityPresentationPropertyKeys }
      .distinct()
      .toList()
  val (whispKeys, regularKeys) = allPropertyKeys.partition { it.isWhispPropertyKey() }
  val rollUpWhisp = whispKeys.isNotEmpty() && !expandWhispColumns
  val displayColumns =
    if (rollUpWhisp) {
      DASHBOARD_FIXED_COLUMNS + DASHBOARD_WHISP_COLUMN + regularKeys
    } else {
      DASHBOARD_FIXED_COLUMNS + regularKeys + whispKeys
    }
  val exportKeys = regularKeys + whispKeys
  val exportRows = datasetEntities.map { entity ->
    buildEntityRowCells(entity, exportKeys, relatedLabel)
  }
  return DashboardDataTable(
    id = "dataset:$datasetId",
    datasetId = datasetId,
    title = datasetEntities.first().datasetName,
    kind =
      if (datasetEntities.any { it.hasGeometry }) {
        DashboardDataTableKind.MAP_LAYER
      } else {
        DashboardDataTableKind.DATA_TABLE
      },
    columns = displayColumns,
    rows =
      datasetEntities.mapIndexed { idx, entity ->
        val displayCells =
          if (rollUpWhisp) {
            buildRolledUpWhispRowCells(entity, regularKeys, relatedLabel)
          } else {
            exportRows[idx]
          }
        DashboardDataTableRow(
          id = entity.id,
          cells = displayCells,
          isSelected = entity.id == selectedEntityId,
          entity = entity,
        )
      },
    whispColumnCount = whispKeys.size,
    isWhispExpanded = expandWhispColumns && whispKeys.isNotEmpty(),
    exportColumns = DASHBOARD_FIXED_COLUMNS + exportKeys,
    exportRows = exportRows,
  )
}

private fun buildEntityRowCells(
  entity: GeospatialEntityItem,
  keys: List<String>,
  relatedLabel: (entity: GeospatialEntityItem, value: String) -> String?,
): List<String> =
  listOf(
    entity.workflowStatus,
    entity.label,
    entity.submissionCount.toString(),
    entity.geoId,
  ) +
    keys.map { key ->
      val value = entity.properties[key].orEmpty()
      relatedLabel(entity, value) ?: value
    }

private fun buildRolledUpWhispRowCells(
  entity: GeospatialEntityItem,
  regularKeys: List<String>,
  relatedLabel: (entity: GeospatialEntityItem, value: String) -> String?,
): List<String> =
  listOf(
    entity.workflowStatus,
    entity.label,
    entity.submissionCount.toString(),
    entity.geoId,
    whispSummaryCellText(entity.whispAnalysis),
  ) +
    regularKeys.map { key ->
      val value = entity.properties[key].orEmpty()
      relatedLabel(entity, value) ?: value
    }

/** Compact summary text for a rolled-up Whisp risk column cell. */
internal fun whispSummaryCellText(analysis: WhispAnalysis?): String {
  if (analysis == null) return ""
  return when (analysis.overallRisk) {
    WhispRiskLevel.HIGH -> "High risk"
    WhispRiskLevel.MORE_INFO_NEEDED -> "More info needed"
    WhispRiskLevel.LOW -> "Low risk"
    WhispRiskLevel.UNKNOWN -> "${analysis.rawEntries.size} Whisp fields"
  }
}

/**
 * Serializes [table] as CSV per RFC 4180: a header row of column names followed by one line per
 * row, with CRLF line breaks. Fields containing a comma, double quote, CR, or LF are enclosed in
 * double quotes, and embedded double quotes are escaped by doubling them. Always exports full
 * uncollapsed columns (including individual `whisp_*` properties).
 */
internal fun buildDashboardTableCsv(table: DashboardDataTable): String {
  val builder = StringBuilder()
  (listOf(table.exportColumns) + table.exportRows).forEach { fields ->
    fields.joinTo(builder, separator = ",") { escapeCsvField(it) }
    builder.append("\r\n")
  }
  return builder.toString()
}

private val CSV_SPECIAL_CHARS = charArrayOf(',', '"', '\r', '\n')

/** Quotes [field] for CSV when required by RFC 4180 (section 2, rules 6 and 7). */
private fun escapeCsvField(field: String): String =
  if (field.any { it in CSV_SPECIAL_CHARS }) {
    "\"" + field.replace("\"", "\"\"") + "\""
  } else {
    field
  }

/**
 * File name for downloading [table] as CSV: its title with characters that are invalid in file
 * names replaced by `_`, plus the `.csv` extension.
 */
internal fun dashboardTableCsvFileName(table: DashboardDataTable): String {
  val baseName =
    table.title.replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "_").trim().trim('.').ifEmpty {
      "table"
    }
  return "$baseName.csv"
}
