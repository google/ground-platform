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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.geo.geoid.AgStackGeoId
import org.openforis.collectone.v2.core.ids.RecordIds
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/** Projecting a published draft's Forms onto the runtime Forms list. */
class SurveyEditorProjectionTest {
  private val plots =
    EntityDataset(
      key = "d1",
      kind = DatasetKind.MAP_LAYER,
      id = "plots",
      displayName = "Plots",
      keyProperty = "plot_id",
      labelProperty = "plot_id",
      properties = listOf(EntityProperty("plot_id", "Plot ID")),
    )

  private fun project(form: EditorForm) =
    SurveyEditorProjection.project(
        draft =
          SurveyEditorDraft.blank("s")
            .copy(forms = listOf(SurveyEditorForm("f1", form)), datasets = listOf(plots)),
        previous = SurveyEditorDraft.blank("s"),
        surveyId = "s",
        forms = emptyList(),
        mapLayers = emptyList(),
        entities = emptyList(),
      )
      .forms
      .single()

  @Test
  fun newFormThatUpdatesTheSelectedFeature_isOfferedOnThatFeature() {
    val item =
      project(
        EditorFormTemplates.blank("form_check", "Check")
          .copy(
            saveTo = EditorSaveTo(mode = SaveToMode.UPDATE, targetDatasetId = plots.id),
            availability = FormAvailability.WEB,
          )
      )
    assertTrue(item.requiresEntity)
    assertEquals(plots.id, item.targetDatasetId)
    // The web dashboard only offers Forms with an entity selection on a selected feature.
    assertTrue(item.hasEntitySelectQuestion)
  }

  @Test
  fun formThatAddsFeatures_hasNoEntitySelection() {
    val item = project(EditorFormTemplates.blank("form_add", "Add"))
    assertFalse(item.requiresEntity)
    assertFalse(item.hasEntitySelectQuestion)
  }

  @Test
  fun samplePlotRowsProjectToDeterministicUuidsAndRoundTripPlotIds() {
    val surveyId = "00000099-56d7-5c1d-9457-7457267120a3"
    val sampleLayer = samplePlotDataset()
    val draft = SurveyEditorDraft.blank(surveyId).copy(datasets = listOf(sampleLayer))

    val projected =
      SurveyEditorProjection.project(
        draft = draft,
        previous = SurveyEditorDraft.blank(surveyId),
        surveyId = surveyId,
        forms = emptyList(),
        mapLayers = emptyList(),
        entities = emptyList(),
      )

    assertEquals(2, projected.entities.size)
    assertTrue(projected.entities.all { RecordIds.isUuid(it.id) }, "${projected.entities}")
    assertTrue(projected.entities.all { it.isGenerated }, "${projected.entities}")
    assertEquals(
      sampleLayer.generator?.toSampleDesign(),
      projected.mapLayers.single().sampleDesign,
    )
    assertEquals(listOf("P001", "P002"), projected.entities.map { it.label })
    assertEquals(
      listOf(
        AgStackGeoId.computeCandidate(GeometryProtos.point(-1.29, 36.82)),
        AgStackGeoId.computeCandidate(GeometryProtos.point(-1.30, 36.83)),
      ),
      projected.entities.map { it.geoId },
    )
    assertEquals(
      listOf("P001", "P002"),
      projected.entities.map { it.properties[SamplePlotProperties.PLOT_ID] },
    )

    val refreshedDataset =
      SurveyEditorDerivation.refresh(
          draft = draft,
          surveyId = surveyId,
          mapLayers = projected.mapLayers,
          entities = projected.entities,
        )
        .datasets
        .single()
    assertEquals(SamplePlotProperties.schema, refreshedDataset.properties)
    assertEquals(listOf("d1-P001", "d1-P002"), refreshedDataset.rows.map { it.key })
    assertEquals(
      listOf("P001", "P002"),
      refreshedDataset.rows.map { it.values[SamplePlotProperties.PLOT_ID] },
    )
    assertEquals(emptyList(), EntityDatasetValidator.validate(refreshedDataset, emptyList()))

    // Deriving directly from runtime entities of a generated sample plots layer preserves plot_id
    // as the key property without injecting a blank required "id" column.
    val derivedDataset =
      SurveyEditorDerivation.deriveDataset(projected.mapLayers.single(), projected.entities)
    assertEquals(SamplePlotProperties.PLOT_ID, derivedDataset.keyProperty)
    assertEquals(SamplePlotProperties.PLOT_ID, derivedDataset.labelProperty)
    assertEquals(SamplePlotProperties.schema, derivedDataset.properties)
    assertEquals(
      listOf("P001", "P002"),
      derivedDataset.rows.map { it.values[SamplePlotProperties.PLOT_ID] },
    )
    assertEquals(emptyList(), EntityDatasetValidator.validate(derivedDataset, emptyList()))

    // Previously saved drafts polluted with derived "id"/"label" columns are healed on refresh.
    val healedDataset =
      SurveyEditorDerivation.refresh(
          draft = pollutedSamplePlotDraft(draft, sampleLayer),
          surveyId = surveyId,
          mapLayers = projected.mapLayers,
          entities = projected.entities,
        )
        .datasets
        .single()
    assertEquals(SamplePlotProperties.PLOT_ID, healedDataset.keyProperty)
    assertEquals(SamplePlotProperties.schema, healedDataset.properties)
    assertEquals(emptyList(), EntityDatasetValidator.validate(healedDataset, emptyList()))
  }

  private fun samplePlotDataset(): EntityDataset =
    EntityDataset(
      key = "d1",
      kind = DatasetKind.MAP_LAYER,
      id = "sample_plots",
      displayName = "Sample plots",
      keyProperty = SamplePlotProperties.PLOT_ID,
      labelProperty = SamplePlotProperties.PLOT_ID,
      properties = SamplePlotProperties.schema,
      rows =
        listOf(
          EntityRow(
            key = "d1-P001",
            values =
              mapOf(
                SamplePlotProperties.PLOT_ID to "P001",
                SamplePlotProperties.STRATUM to "1",
                SamplePlotProperties.SAMPLE_ORDER to "1",
              ),
            geometry = listOf(LatLng(-1.29, 36.82)),
          ),
          EntityRow(
            key = "d1-P002",
            values =
              mapOf(
                SamplePlotProperties.PLOT_ID to "P002",
                SamplePlotProperties.STRATUM to "1",
                SamplePlotProperties.SAMPLE_ORDER to "2",
              ),
            geometry = listOf(LatLng(-1.30, 36.83)),
          ),
        ),
      generator = SampleDesignConfig(),
    )

  private fun pollutedSamplePlotDraft(
    draft: SurveyEditorDraft,
    sampleLayer: EntityDataset,
  ): SurveyEditorDraft =
    draft.copy(
      datasets =
        listOf(
          sampleLayer.copy(
            properties =
              listOf(
                EntityProperty("id", "ID", PropertyType.TEXT, required = true),
                EntityProperty("label", "Label", PropertyType.TEXT),
                EntityProperty("plot_id", "plot_id", PropertyType.TEXT),
              )
          )
        )
    )

  @Test
  fun editingRowGeometry_recomputesAgStackGeoId_whileUnchangedGeometryPreservesFallbackGeoId() {
    val surveyId = "00000099-56d7-5c1d-9457-7457267120a3"
    val ring1 =
      listOf(
        LatLng(30.311450431756946, 76.88855767250062),
        LatLng(30.310732833916543, 76.88841819763184),
        LatLng(30.31070505582999, 76.88945889472961),
        LatLng(30.311399505631794, 76.8894535303116),
      )
    val candidates1 =
      AgStackGeoId.forGeometry(
        GeometryProtos.polygon(
          listOf(ring1.map { GeometryProtos.position(it.latitude, it.longitude) })
        )
      )!!
    val layer =
      plots.copy(
        geometryType = GeometryType.POLYGON,
        rows =
          listOf(
            EntityRow(
              key = "r1",
              values = mapOf("plot_id" to "P001"),
              geometry = ring1,
            )
          ),
      )
    val draft = SurveyEditorDraft.blank(surveyId).copy(datasets = listOf(layer))
    val initial =
      SurveyEditorProjection.project(
        draft = draft,
        previous = SurveyEditorDraft.blank(surveyId),
        surveyId = surveyId,
        forms = emptyList(),
        mapLayers = emptyList(),
        entities = emptyList(),
      )
    assertEquals(candidates1.primary, initial.entities.single().geoId)

    // Simulate the server/AgStack confirming the level-20 fallback GeoID on sync.
    val syncedWithFallback = initial.entities.map { it.copy(geoId = candidates1.fallback) }
    val reprojectedUnchanged =
      SurveyEditorProjection.project(
        draft = draft,
        previous = draft,
        surveyId = surveyId,
        forms = initial.forms,
        mapLayers = initial.mapLayers,
        entities = syncedWithFallback,
      )
    assertEquals(candidates1.fallback, reprojectedUnchanged.entities.single().geoId)

    // Moving a vertex must invalidate the stale GeoID and compute the new geometry's candidate.
    val movedRing = ring1.dropLast(1) + LatLng(30.3130, 76.8910)
    val movedDraft =
      draft.copy(
        datasets = listOf(layer.copy(rows = listOf(layer.rows.single().copy(geometry = movedRing))))
      )
    val reprojectedMoved =
      SurveyEditorProjection.project(
        draft = movedDraft,
        previous = draft,
        surveyId = surveyId,
        forms = initial.forms,
        mapLayers = initial.mapLayers,
        entities = syncedWithFallback,
      )
    val movedGeoId = reprojectedMoved.entities.single().geoId
    assertTrue(AgStackGeoId.isValidFormat(movedGeoId))
    assertNotEquals(candidates1.fallback, movedGeoId)
    assertNotEquals(candidates1.primary, movedGeoId)
  }
}
