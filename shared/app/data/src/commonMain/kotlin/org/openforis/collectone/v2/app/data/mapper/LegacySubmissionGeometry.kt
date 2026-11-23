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
package org.openforis.collectone.v2.app.data.mapper

import openforis.collectone.v1.data.SubmissionGeometryPolygonDef
import openforis.collectone.v1.data.SubmissionRecord
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

/**
 * Reads the positions of submissions stored before they had WGS 84 geometry
 * (`docs/technical/model/data/05-geometry.md`, "Migration"). Those rows placed a submission by a
 * normalized position on its survey's sample map, or only named it in a coordinates label, and
 * recorded geometry answers as a normalized rectangle on that map.
 *
 * The fields are reserved now; this reads them with [ReservedFields].
 */
internal object LegacySubmissionGeometry {
  /** Reserved field number of `SubmissionRecord.coordinates_label`. */
  const val SUBMISSION_COORDINATES_LABEL = 23

  /** Reserved field numbers of `SubmissionRecord.normalized_x` and `normalized_y`. */
  const val SUBMISSION_NORMALIZED_X = 24
  const val SUBMISSION_NORMALIZED_Y = 25

  /** Reserved field numbers of the rectangle of `SubmissionGeometryPolygonDef`. */
  const val POLYGON_NORMALIZED_X = 14
  const val POLYGON_NORMALIZED_Y = 15
  const val POLYGON_WIDTH_FRACTION = 16
  const val POLYGON_HEIGHT_FRACTION = 17

  private val coordinatesPattern =
    Regex("""(-?\d+(?:\.\d+)?)°\s*([NS])?\s*,\s*(-?\d+(?:\.\d+)?)°\s*([EW])?""")

  /**
   * Where a legacy [record] was recorded: its normalized position in its survey's map frame, else
   * the coordinates its label starts with, else `null`.
   */
  fun locationOf(record: SubmissionRecord): LatLng? {
    val x = ReservedFields.float(record.unknownFields, SUBMISSION_NORMALIZED_X)
    val y = ReservedFields.float(record.unknownFields, SUBMISSION_NORMALIZED_Y)
    if (x != null && y != null) {
      return SurveyMapAnchor.forSurvey(record.survey_id).toLatLng(x.toDouble(), y.toDouble())
    }
    return parseCoordinates(
      ReservedFields.string(record.unknownFields, SUBMISSION_COORDINATES_LABEL)
    )
  }

  /**
   * The rectangle a legacy [polygon] of a submission in [surveyId] covered on the survey's map, or
   * `null` when it has none.
   */
  fun geometryOf(polygon: SubmissionGeometryPolygonDef, surveyId: String): Geometry? {
    val fields = polygon.unknownFields
    val width = ReservedFields.float(fields, POLYGON_WIDTH_FRACTION) ?: return null
    val height = ReservedFields.float(fields, POLYGON_HEIGHT_FRACTION) ?: return null
    val centerX = (ReservedFields.float(fields, POLYGON_NORMALIZED_X) ?: 0f).toDouble()
    val centerY = (ReservedFields.float(fields, POLYGON_NORMALIZED_Y) ?: 0f).toDouble()
    val anchor = SurveyMapAnchor.forSurvey(surveyId)
    val left = centerX - width / 2.0
    val right = centerX + width / 2.0
    val top = centerY - height / 2.0
    val bottom = centerY + height / 2.0
    return Geometry.Polygon(
      listOf(
        listOf(
          anchor.toLatLng(left, top),
          anchor.toLatLng(right, top),
          anchor.toLatLng(right, bottom),
          anchor.toLatLng(left, bottom),
        )
      )
    )
  }

  /**
   * The coordinates a legacy label starts with, signed (`-0.4198°, 36.9512°`) or with hemispheres
   * (`0.4182° S, 36.9491° E`), or `null`.
   */
  fun parseCoordinates(label: String): LatLng? {
    val match = coordinatesPattern.find(label) ?: return null
    val groups = match.groupValues
    val latitude = groups[1].toDouble() * if (groups[2] == "S") -1 else 1
    val longitude = groups[3].toDouble() * if (groups[4] == "W") -1 else 1
    return if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
      LatLng(latitude, longitude)
    } else {
      null
    }
  }
}
