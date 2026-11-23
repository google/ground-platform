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
package org.openforis.collectone.v2.app.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.app.client.pdf.PdfReportStrings
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.LayerSourceType
import org.openforis.collectone.v2.app.domain.model.LocationLockState
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.MembershipStatus
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.SubmissionNotice
import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.StatusConditionSubject
import org.openforis.collectone.v2.app.domain.model.message

/**
 * The English string resources keep the English text the domain enums carried before their labels
 * moved to string resources, so the move changes nothing in English.
 */
class DomainStringsParityTest {

  private fun <T> assertEnglish(expected: Map<T, String>, res: (T) -> StringResource) {
    runBlocking {
      expected.forEach { (value, english) ->
        assertEquals(english, getString(res(value)), "$value")
      }
    }
  }

  // Labels the enums below carried in English until they moved to string resources.

  /** Map and settings labels. */
  @Test
  fun movedMapAndSettingsLabels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        EffortComparison.LESS to "Less",
        EffortComparison.SAME to "About the same",
        EffortComparison.MORE to "More",
      ),
      { it.labelRes },
    )
    assertEnglish(
      mapOf(
        LocationLockState.LOCKED to "GPS Auto-Center",
        LocationLockState.LOCKED_3D to "GPS 3D",
        LocationLockState.PANNED to "Panned",
      ),
      { it.labelRes },
    )
    assertEnglish(mapOf(LayerSourceType.ENTITY_DATASET to "Survey Layer"), { it.badgeLabelRes })
    assertEnglish(
      mapOf(BasemapType.NORMAL to "Terrain", BasemapType.SATELLITE to "Satellite"),
      { it.labelRes },
    )
    assertEnglish(
      mapOf(
        BasemapType.NORMAL to "Terrain map with contours, hillshade & roads",
        BasemapType.SATELLITE to "Mapbox Standard Satellite imagery with reference labels",
      ),
      { it.descriptionRes },
    )
    assertEnglish(
      mapOf(
        MeasurementUnitSystem.METRIC to "Metric (ha, m)",
        MeasurementUnitSystem.IMPERIAL to "Imperial (acres, ft)",
      ),
      { it.labelRes },
    )
    assertEnglish(
      mapOf(MeasurementUnitSystem.METRIC to "Metric", MeasurementUnitSystem.IMPERIAL to "Imperial"),
      { it.shortLabelRes },
    )
  }

  /** Uploads, membership, and invitation labels. */
  @Test
  fun movedUploadAndPeopleLabels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        UploadStatusFilter.PENDING to "Pending",
        UploadStatusFilter.IN_PROGRESS to "In progress",
        UploadStatusFilter.UPLOADED to "Uploaded",
        UploadStatusFilter.FAILED to "Failed",
      ),
      { it.labelRes },
    )
    assertEnglish(
      mapOf(
        MutationSyncState.UPLOADING to "In progress",
        MutationSyncState.QUEUED to "Pending",
        MutationSyncState.RETRYING to "In progress",
        MutationSyncState.FAILED to "Failed",
        MutationSyncState.UPLOADED to "Uploaded",
      ),
      { it.labelRes },
    )
    assertEnglish(
      mapOf(
        MembershipStatus.INVITED to "Invited",
        MembershipStatus.REQUESTED to "Requested to join",
        MembershipStatus.ACTIVE to "Member",
      ),
      { it.labelRes },
    )
    assertEnglish(
      mapOf(
        ImagerySourceType.XYZ_TILES to "XYZ tile URL",
        ImagerySourceType.EARTH_ENGINE to "Earth Engine layer",
      ),
      { it.labelRes },
    )
    assertEnglish(
      mapOf(InvitationStatus.PENDING to "Invited", InvitationStatus.ACCEPTED to "Joined"),
      { it.labelRes },
    )
  }

  /** Survey and form editor labels. */
  @Test
  fun movedEditorLabels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        StatusConditionSubject.SUBMISSIONS to "Submission count",
        StatusConditionSubject.QUESTION to "Question answer",
        StatusConditionSubject.ENTITY_PROPERTY to "Feature property",
      ),
      { it.labelRes },
    )
    assertEnglish(
      mapOf(PeerDataVisibility.ALL to "All data", PeerDataVisibility.OWN_ONLY to "Own data only"),
      { it.labelRes },
    )
    assertEnglish(
      mapOf(
        PeerDataVisibility.ALL to "Data collectors see everyone's submissions and map features.",
        PeerDataVisibility.OWN_ONLY to "Data collectors only see what they collected.",
      ),
      { it.descriptionRes },
    )
  }

  /** The text of record PDF reports, which they wrote in English before loading it. */
  @Test
  fun pdfReportStrings_keepTheirEnglishText() {
    assertEquals(
      PdfReportStrings(
        runningHeader = "Open Foris Collect One • %1\$s",
        pageOf = "Page %1\$d of %2\$d",
        locationPoint = "Location %1\$s, %2\$s",
        bounds = "Bounds: lat %1\$s to %2\$s, lng %3\$s to %4\$s (WGS 84)",
        entityFooter = "%1\$s report • Generated %2\$s",
        submissionFooter = "Submission report • Generated %1\$s",
        submissionSubtitle = "Submission • %1\$s",
        details = "Details",
        layer = "Layer",
        geometry = "Geometry",
        geoId = "GeoID",
        coordinates = "Coordinates",
        location = "Location",
        vertex = "Vertex %1\$d",
        properties = "Properties",
        submissions = "Submissions",
        noSubmissions = "No submissions recorded yet.",
        collectedBy = "Collected by %1\$s • Form version %2\$s",
        entityTarget = "%1\$s (%2\$s • GeoID %3\$s)",
        noAttachedFeatureAt = "No attached map feature (%1\$s)",
        noAttachedFeature = "No attached map feature",
        dataCollector = "Data collector",
        collected = "Collected",
        formVersion = "Form version",
        responses = "Responses",
        noResponses = "No responses recorded.",
        geometryPolygon = "Polygon • %1\$s, %2\$s perimeter",
        geometryLine = "Line • %1\$s",
        geometryPoint = "Point • %1\$s",
        geometryNone = "No geometry",
        coordinatePair = "%1\$s, %2\$s",
        latitudeNorth = "%1\$s°N",
        latitudeSouth = "%1\$s°S",
        longitudeEast = "%1\$s°E",
        longitudeWest = "%1\$s°W",
        measurement = "%1\$s %2\$s",
        unitNames = MeasurementUnit.entries.associateWith { it.symbol },
      ),
      runBlocking { PdfReportStrings.load() },
    )
  }

  /** Submission notices, which the domain also built as English text until they were typed. */
  @Test
  fun submissionNotices_keepTheirEnglishText() {
    fun english(message: DomainMessage) = runBlocking { message.toUiText().resolveString() }
    assertEquals(
      "Created new entity \"Plot 7\" via save_to (✓ Completed)",
      english(SubmissionNotice.ENTITY_CREATED.message("Plot 7")),
    )
    assertEquals(
      "Submitted standalone field log \"Rain gauge\" (0.4160°S, 36.9470°E)",
      english(SubmissionNotice.STANDALONE_SUBMITTED.message("Rain gauge", "0.4160°S, 36.9470°E")),
    )
    assertEquals(
      "Submitted \"Baseline\" for Plot 7 (Marker: ○ → ◐ In progress)",
      english(
        SubmissionNotice.ENTITY_SUBMITTED.message("Baseline", "Plot 7", "○", "◐", "In progress")
      ),
    )
  }
}
