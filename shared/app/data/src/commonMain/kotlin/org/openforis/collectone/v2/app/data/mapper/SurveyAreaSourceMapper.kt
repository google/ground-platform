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
package org.openforis.collectone.v2.app.data.mapper

import openforis.collectone.v1.survey.SurveyArea as SurveyAreaProto
import openforis.collectone.v1.survey.SurveyAreaSource as SurveyAreaSourceProto
import openforis.collectone.v1.survey.SurveyAreaSource.Kind
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAreaSource

/**
 * Maps how a survey area was defined between [SurveyAreaSource] and `SurveyArea.area_source`, and
 * decodes the English text older rows stored in the reserved `SurveyArea.source` (3).
 */
internal object SurveyAreaSourceMapper {
  /** Reserved field number of the legacy English source text of `SurveyArea`. */
  const val LEGACY_SOURCE_TEXT = 3

  fun toProto(source: SurveyAreaSource): SurveyAreaSourceProto =
    when (source) {
      SurveyAreaSource.SelectedBoundary -> SurveyAreaSourceProto(kind = Kind.SELECTED_BOUNDARY)
      SurveyAreaSource.Drawn -> SurveyAreaSourceProto(kind = Kind.DRAWN)
      is SurveyAreaSource.Uploaded ->
        SurveyAreaSourceProto(kind = Kind.UPLOADED, file_name = source.fileName)
      SurveyAreaSource.PlaceSearch -> SurveyAreaSourceProto(kind = Kind.PLACE_SEARCH)
      SurveyAreaSource.Coordinates -> SurveyAreaSourceProto(kind = Kind.COORDINATES)
      is SurveyAreaSource.CollectEarthGrid ->
        SurveyAreaSourceProto(kind = Kind.COLLECT_EARTH_GRID, plot_count = source.plotCount)
    }

  /**
   * The area's source: its `area_source`, else the one a legacy row named in `source`, else a
   * selected boundary, which is what clients showed for areas without a source.
   */
  fun fromProto(area: SurveyAreaProto): SurveyAreaSource {
    val source = area.area_source
    return when (source?.kind) {
      Kind.SELECTED_BOUNDARY -> SurveyAreaSource.SelectedBoundary
      Kind.DRAWN -> SurveyAreaSource.Drawn
      Kind.UPLOADED -> SurveyAreaSource.Uploaded(source.file_name)
      Kind.PLACE_SEARCH -> SurveyAreaSource.PlaceSearch
      Kind.COORDINATES -> SurveyAreaSource.Coordinates
      Kind.COLLECT_EARTH_GRID -> SurveyAreaSource.CollectEarthGrid(source.plot_count)
      Kind.KIND_UNSPECIFIED,
      null -> legacy(ReservedFields.string(area.unknownFields, LEGACY_SOURCE_TEXT))
    }
  }

  /**
   * The source a legacy row named in [text]: the English texts the survey editor and importers
   * wrote, or the `search`, `drawn` and `uploaded:<filename>` forms the schema once documented.
   */
  private fun legacy(text: String): SurveyAreaSource {
    LEGACY_UPLOADED_PREFIXES.firstOrNull { text.startsWith(it) }
      ?.let {
        return SurveyAreaSource.Uploaded(text.removePrefix(it).trim())
      }
    LEGACY_COLLECT_EARTH_GRID.matchEntire(text)?.let {
      return SurveyAreaSource.CollectEarthGrid(it.groupValues[1].toInt())
    }
    return when (text) {
      "Drawn on map",
      "drawn" -> SurveyAreaSource.Drawn
      "Places API",
      "search" -> SurveyAreaSource.PlaceSearch
      "Coordinates" -> SurveyAreaSource.Coordinates
      else -> SurveyAreaSource.SelectedBoundary
    }
  }

  private val LEGACY_UPLOADED_PREFIXES = listOf("Uploaded: ", "uploaded:")
  private val LEGACY_COLLECT_EARTH_GRID = Regex("""Collect Earth plot grid \((\d+) plots\)""")
}
