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
package org.openforis.collectone.v2.app.domain.usecase

import openforis.collectone.v1.data.Position
import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.toProtoGeometry
import org.openforis.collectone.v2.app.domain.usecase.ExportSurveyDataUseCase.Companion.fileName
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.geo.io.KmlBalloonWriter
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/** A web-enabled [FormDef] embedded into a dataset's KML `<BalloonStyle>` for data collection. */
data class KmlEmbeddedForm(
  val id: String,
  val title: String,
  val version: String,
  val formDef: FormDef,
)

/**
 * Host and optional session configuration embedded in an exported KML so placemark balloons can
 * load the standalone Kotlin/Wasm form runner (`balloon-form.html`) and sync submissions back to
 * the Collect One backend.
 */
data class KmlBalloonSession(
  val balloonAppUrl: String = "",
  val serviceUrl: String = "",
  val authToken: String? = null,
  val devRole: String? = null,
  val collectorName: String = "",
  val collectorEmail: String = "",
)

/** One map layer exported as a `<Folder>` inside a multi-layer survey KML document. */
data class KmlLayerExport(
  val dataset: EntityDataset,
  val entities: List<GeospatialEntityItem>,
  val forms: List<KmlEmbeddedForm> = emptyList(),
  val defaultColorHex: Long = 0xFF2E7D32L,
)

/**
 * Exports one or more map-layer [EntityDataset]s and their [GeospatialEntityItem] features as an
 * OGC KML 2.2 document (`application/vnd.google-earth.kml+xml`) with one `<Folder>` per map layer,
 * whose per-layer `<BalloonStyle>` embeds Collect One's standalone Kotlin/Wasm web form runner
 * (`balloon-form.html`) inside a panel `<iframe>`.
 */
internal object KmlSurveyExporter {
  const val KML_MIME = KmlBalloonWriter.KML_MIME

  fun export(
    surveyId: String,
    surveyTitle: String,
    dataset: EntityDataset,
    entities: List<GeospatialEntityItem>,
    forms: List<KmlEmbeddedForm>,
    session: KmlBalloonSession = KmlBalloonSession(),
    defaultColorHex: Long = 0xFF2E7D32L,
  ): ExportFile {
    val documentName =
      if (surveyTitle.isNotBlank()) "$surveyTitle — ${dataset.displayName}" else dataset.displayName
    val kml =
      KmlBalloonWriter.writeDocument(
        surveyId = surveyId,
        surveyTitle = surveyTitle,
        documentName = documentName,
        documentDescription = dataset.description,
        layers =
          listOf(
            toFolderSpec(
              dataset = dataset,
              entities = entities,
              forms = forms,
              defaultColorHex = defaultColorHex,
            )
          ),
        session = session.toWriterConfig(),
      )
    return ExportFile(
      fileName = fileName(dataset.displayName, "kml"),
      mimeType = KML_MIME,
      content = kml,
    )
  }

  fun exportSurvey(
    surveyId: String,
    surveyTitle: String,
    surveyDescription: String = "",
    layers: List<KmlLayerExport>,
    session: KmlBalloonSession = KmlBalloonSession(),
    fallbackCenter: LatLng? = null,
  ): ExportFile {
    val mapLayers = layers.filter { it.dataset.kind == DatasetKind.MAP_LAYER }
    val title = surveyTitle.ifBlank { "Collect One Survey" }
    val kml =
      KmlBalloonWriter.writeDocument(
        surveyId = surveyId,
        surveyTitle = title,
        documentName = title,
        documentDescription = surveyDescription,
        layers =
          mapLayers.map { item ->
            toFolderSpec(
              dataset = item.dataset,
              entities = item.entities,
              forms = item.forms,
              defaultColorHex = item.defaultColorHex,
            )
          },
        session = session.toWriterConfig(),
        fallbackLookAt = fallbackCenter?.toProtoPosition(),
      )
    return ExportFile(
      fileName = fileName(title, "kml"),
      mimeType = KML_MIME,
      content = kml,
    )
  }

  private fun toFolderSpec(
    dataset: EntityDataset,
    entities: List<GeospatialEntityItem>,
    forms: List<KmlEmbeddedForm>,
    defaultColorHex: Long,
  ): KmlBalloonWriter.LayerFolderSpec {
    val datasetEntities = entities.filter { it.datasetId == dataset.id }
    val styleColor =
      KmlBalloonWriter.parseCssHexColor(dataset.style.colorHex, fallback = 0L).takeIf { it != 0L }
    val layerColor = datasetEntities.firstOrNull()?.colorHex ?: styleColor ?: defaultColorHex
    val placemarks =
      if (datasetEntities.isNotEmpty()) {
        datasetEntities.map { entity ->
          KmlBalloonWriter.PlacemarkSpec(
            id = entity.id,
            title = entity.displayTitle.ifBlank { entity.geoId.ifBlank { entity.id } },
            geoId = entity.geoId,
            status = entity.properties["status"].orEmpty(),
            markerSymbol = entity.rawMarkerSymbol,
            geometry = entity.geometry?.toProtoGeometry(),
            properties = entity.properties,
          )
        }
      } else {
        dataset.rows.mapNotNull { row -> rowToPlacemarkSpec(dataset, row) }
      }
    return KmlBalloonWriter.LayerFolderSpec(
      layerId = dataset.key.ifBlank { dataset.id },
      datasetId = dataset.id,
      name = dataset.displayName.ifBlank { dataset.id },
      description = dataset.description,
      colorHex = layerColor,
      isVisible = dataset.style.visibleByDefault,
      forms =
        forms.map { f ->
          KmlBalloonWriter.EmbeddedForm(
            id = f.id,
            title = f.title,
            version = f.version,
            formDef = f.formDef,
          )
        },
      placemarks = placemarks,
      sampleDesign = dataset.generator?.toSampleDesign(),
    )
  }

  private fun rowToPlacemarkSpec(
    dataset: EntityDataset,
    row: EntityRow,
  ): KmlBalloonWriter.PlacemarkSpec? {
    val geometry =
      when (dataset.geometryType) {
        GeometryType.POINT ->
          row.geometry.firstOrNull()?.let { GeometryProtos.point(it.latitude, it.longitude) }
        GeometryType.LINE_STRING ->
          row.geometry
            .takeIf { it.size >= 2 }
            ?.let { pts -> GeometryProtos.lineString(pts.map { it.toProtoPosition() }) }
        GeometryType.POLYGON ->
          row.geometry
            .takeIf { it.size >= 3 }
            ?.let { pts -> GeometryProtos.polygon(listOf(pts.map { it.toProtoPosition() })) }
      } ?: return null
    val rawKey = row.values[dataset.keyProperty]?.takeIf { it.isNotBlank() } ?: row.key
    return KmlBalloonWriter.PlacemarkSpec(
      id = row.key.ifBlank { rawKey },
      title = dataset.labelOf(row).takeUnless { it == "(unnamed)" } ?: rawKey,
      geoId = rawKey,
      status = row.values["status"].orEmpty(),
      markerSymbol = row.values["marker-symbol"].orEmpty(),
      geometry = geometry,
      properties = row.values,
    )
  }

  private fun KmlBalloonSession.toWriterConfig(): KmlBalloonWriter.SessionConfig =
    KmlBalloonWriter.SessionConfig(
      balloonAppUrl = balloonAppUrl,
      serviceUrl = serviceUrl,
      authToken = authToken,
      devRole = devRole,
      collectorName = collectorName,
      collectorEmail = collectorEmail,
    )

  private fun LatLng.toProtoPosition(): Position = GeometryProtos.position(latitude, longitude)
}
