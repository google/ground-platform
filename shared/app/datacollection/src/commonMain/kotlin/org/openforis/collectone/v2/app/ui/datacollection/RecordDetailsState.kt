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
package org.openforis.collectone.v2.app.ui.datacollection

import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.FormSubmissionsGroup
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.StraightLineVector
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SubmissionViewer
import org.openforis.collectone.v2.app.ui.navigation.EntityDetailsPane

/**
 * State of a submission's document-style details view ([SubmissionFullDetailsCard]).
 * - [viewer]: the signed-in user, deciding what they see of the submission's contributor.
 * - [fieldWayfinding]: the distance and bearing (shown as a badge, e.g. `452 m • 321° NW`) to the
 *   submission's geometry answers, by field. Fields without a geometry have no entry.
 */
data class SubmissionDetailsUiState(
  val submission: SubmissionPreviewItem,
  val viewer: SubmissionViewer,
  val fieldWayfinding: Map<SubmissionFieldEntry, StraightLineVector> = emptyMap(),
  val formDefs: List<FormDef> = emptyList(),
)

/**
 * An organizer-defined data collection button for [form] on a map feature. Forms are `1:N` with
 * features, so [isEnabled] reflects whether the user may collect data in the survey and [form]
 * targets the feature's dataset ([DataCollectionUiState.isFormButtonEnabled]).
 */
data class FormLauncher(val form: FormPreviewItem, val isEnabled: Boolean)

/**
 * State of a map feature's details surface: the mobile bottom sheet card ([EntityBottomSheetCard])
 * and the panes the web dashboard's details card reuses ([EntityPropertiesPane],
 * [EntitySubmissionsPane]).
 * - [pane]: whether the properties or the submissions are showing.
 * - [openSubmission]: the submission of [entity] opened for inspection, if any.
 * - [submissionGroups]: [entity]'s submissions by form. On mobile while offline only the
 *   submissions stored on the device are listed.
 * - [formLaunchers]: the forms the user can collect for [entity] on the current platform.
 * - [notice]: the active survey notice, shown under the [formLaunchers].
 * - [isNavigating] and [wayfinding]: straight-line navigation to [entity] and the distance and
 *   bearing to it (shown as a badge), `null` when it has no location.
 * - [relatedRecords]: the records that property values of [entity] reference, by value.
 */
data class EntityDetailsUiState(
  val entity: GeospatialEntityItem,
  val viewer: SubmissionViewer,
  val layer: MapLayerItem? = null,
  val pane: EntityDetailsPane = EntityDetailsPane.PROPERTIES,
  val openSubmission: SubmissionDetailsUiState? = null,
  val submissionGroups: List<FormSubmissionsGroup> = emptyList(),
  val isOnline: Boolean = true,
  val unitSystem: MeasurementUnitSystem = MeasurementUnitSystem.METRIC,
  val formLaunchers: List<FormLauncher> = emptyList(),
  val notice: String? = null,
  val isNavigating: Boolean = false,
  val wayfinding: StraightLineVector? = null,
  val pendingUploadCount: Int = 0,
  val relatedRecords: Map<String, GeospatialEntityItem> = emptyMap(),
  val formDefs: List<FormDef> = emptyList(),
)

/** User intents of a map feature's details surface. */
interface EntityDetailsActions {
  /** Selects the map feature with [entityId] (a related record), or clears the selection. */
  fun selectEntity(entityId: String?)

  /** Expands the web dashboard's bottom data table on the dataset with [datasetId]. */
  fun showDatasetInTable(datasetId: String)

  fun toggleNavigationToEntity(entityId: String)

  /** Opens the `Uploads` screen filtered to the map feature with [entityId]. */
  fun openUploadsForEntity(entityId: String)

  /** Shows the selected map feature's properties, closing its submissions or open submission. */
  fun showEntityProperties()

  /** Opens the submission with [submissionId] for inspection, or closes it when `null`. */
  fun selectSubmissionDetail(submissionId: String?)

  fun launchFormForEntity(entityId: String, formId: String)

  /** Cycles the simulated upload status of a map feature (workbench only; no-op elsewhere). */
  fun cycleEntitySyncStatus(entityId: String)

  /** Cycles the simulated upload status of a submission (workbench only; no-op elsewhere). */
  fun cycleSubmissionSyncStatus(submissionId: String)

  fun openEntityQrCode(entityId: String)

  /** Generates the map feature's PDF and opens the Share PDF sheet. */
  fun shareEntityPdf(entityId: String)

  /** Generates the submission's PDF and opens the Share PDF sheet. */
  fun shareSubmissionPdf(submissionId: String)
}
