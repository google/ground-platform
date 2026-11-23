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
package org.openforis.collectone.v2.app.data.seed.backend

import kotlin.math.abs
import kotlin.math.roundToLong
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SubmissionGeometryPolygon
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.plainCoordinatesLabel
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds

/**
 * Sample data of the Mekong Delta mangrove survey
 * ([BackendStaticSurveyEditorData.ORGANIZATION_SHARED_SURVEY_ID]) and other non-Nyeri sample
 * surveys: the sample map features and submissions, moved from where they were authored in Nyeri to
 * the center of each survey's area, so they lie inside it.
 */
internal object MekongDeltaSampleData {
  private val relocation by lazy {
    relocationFor(
      authored = BackendStaticEntitiesData.defaultGeospatialEntities(),
      targetArea = BackendStaticSurveyEditorData.mekongDeltaSurveyArea(),
    )
  }

  fun entities(): List<GeospatialEntityItem> =
    relocateEntities(
      authored = BackendStaticEntitiesData.defaultGeospatialEntities(),
      targetArea = BackendStaticSurveyEditorData.mekongDeltaSurveyArea(),
    )

  fun standaloneSubmissions(): List<SubmissionPreviewItem> =
    BackendStaticSubmissionsData.defaultStandaloneSubmissions().map { sub ->
      relocated(sub, relocation)
    }

  fun submissionGeometries(): List<SubmissionGeometryPolygon> =
    BackendStaticSubmissionsData.defaultSubmissionGeometries().map {
      it.copy(geometry = relocation.geometry(it.geometry))
    }

  fun relocateEntities(
    authored: List<GeospatialEntityItem>,
    targetArea: SurveyArea,
  ): List<GeospatialEntityItem> {
    val reloc = relocationFor(authored, targetArea)
    return authored.map { entity ->
      entity.copy(
        geometry = entity.geometry?.let(reloc::geometry),
        submissions = entity.submissions.map { sub -> relocated(sub, reloc) },
      )
    }
  }

  fun relocateSubmissionGeometries(
    authoredEntities: List<GeospatialEntityItem>,
    geometries: List<SubmissionGeometryPolygon>,
    targetArea: SurveyArea,
  ): List<SubmissionGeometryPolygon> {
    val reloc = relocationFor(authoredEntities, targetArea)
    return geometries.map { it.copy(geometry = reloc.geometry(it.geometry)) }
  }

  private fun relocationFor(
    authored: List<GeospatialEntityItem>,
    targetArea: SurveyArea,
  ): SeedRelocation {
    val coords = authored.flatMap { it.geometry?.coordinates.orEmpty() }
    return SeedRelocation(
      from = LngLatBounds.of(coords).center,
      to = targetArea.center,
    )
  }

  /** [submission] recorded at its relocated position, with answers that name it rewritten. */
  private fun relocated(
    submission: SubmissionPreviewItem,
    reloc: SeedRelocation,
  ): SubmissionPreviewItem {
    val location = submission.location ?: return submission
    val moved = reloc.position(location)
    val authoredText = plainCoordinatesLabel(location)
    val authoredOdk = odkCoordPrefix(location)
    return submission.copy(
      location = moved,
      fields =
        submission.fields.map { field ->
          when {
            field.answerValue.startsWith(authoredText) ->
              field.copy(
                answerValue =
                  plainCoordinatesLabel(moved) + field.answerValue.drop(authoredText.length)
              )
            field.answerValue.startsWith(authoredOdk) ->
              field.copy(
                answerValue = odkCoordPrefix(moved) + field.answerValue.drop(authoredOdk.length)
              )
            else -> field
          }
        },
    )
  }

  private fun odkCoordPrefix(point: LatLng): String =
    "${formatSixDecimals(point.latitude)} ${formatSixDecimals(point.longitude)}"

  private fun formatSixDecimals(value: Double): String {
    val sign = if (value < 0.0) "-" else ""
    val scaled = (abs(value) * 1_000_000.0).roundToLong()
    val whole = scaled / 1_000_000L
    val frac = (scaled % 1_000_000L).toString().padStart(6, '0')
    return "$sign$whole.$frac"
  }
}
