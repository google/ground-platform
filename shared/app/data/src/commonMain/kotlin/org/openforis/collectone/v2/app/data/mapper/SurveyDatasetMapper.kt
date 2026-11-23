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

import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.library.ConceptRef
import openforis.collectone.v1.survey.DataTableRow
import openforis.collectone.v1.survey.EntityDatasetDef
import openforis.collectone.v1.survey.EntityPropertyDefinition
import openforis.collectone.v1.survey.EntityType
import openforis.collectone.v1.survey.FormGeometrySource
import openforis.collectone.v1.survey.GeometryStyle
import openforis.collectone.v1.survey.LayerDef
import openforis.collectone.v1.survey.SamplingDesign
import openforis.collectone.v1.survey.SurveyArea as SurveyAreaProto
import org.openforis.collectone.v2.app.domain.model.ConceptLink
import org.openforis.collectone.v2.app.domain.model.LayerSourceType
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.editor.AllocationMode
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.GenerationRecord
import org.openforis.collectone.v2.app.domain.model.editor.LayerStyle
import org.openforis.collectone.v2.app.domain.model.editor.PlotShapeOption
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.app.domain.model.editor.SampleAreaSource
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SampleMethod
import org.openforis.collectone.v2.app.domain.model.editor.SubPlotMode
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.isoUtc
import org.openforis.collectone.v2.app.domain.model.formatHexColorCss
import org.openforis.collectone.v2.app.domain.model.parseHexColorOrDefault
import org.openforis.collectone.v2.core.sampling.Allocation
import org.openforis.collectone.v2.core.sampling.PlotLayout
import org.openforis.collectone.v2.core.sampling.PlotShape
import org.openforis.collectone.v2.core.sampling.SAMPLING_ENGINE_VERSION
import org.openforis.collectone.v2.core.sampling.SamplingDesignProtos
import org.openforis.collectone.v2.core.sampling.SubPlotLayout
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

/**
 * Maps a survey's map layers, survey area, and entity datasets (data tables and map layers) between
 * the domain models the survey editor works on and their `SurveyDef` messages.
 */
internal object SurveyDatasetMapper {
  /**
   * `LayerDef.source` is a oneof, so a layer keeps one source. The map only draws map features
   * (entities; see `docs/product/xforms-integration.md`), so a layer's dataset takes precedence
   * over the Form geometry question it may also name, which is then dropped.
   */
  fun mapLayerToProto(layer: MapLayerItem): LayerDef {
    val datasetId = layer.datasetId?.takeIf { it.isNotBlank() }
    return LayerDef(
      id = layer.id,
      label = layer.label,
      visible_by_default = layer.isVisible,
      default_style =
        GeometryStyle(
          color = formatHexColorCss(layer.colorHex),
          icon = layer.iconName.orEmpty(),
        ),
      entity_dataset_id = datasetId,
      form_geometry =
        layer.formId?.let { formId ->
          layer.fieldPath
            ?.takeIf { datasetId == null }
            ?.let { fieldPath -> FormGeometrySource(form_id = formId, field_path = fieldPath) }
        },
      geometry_type = GeometryMapper.typeToProto(layer.geometryType),
      singular_item_label = layer.singularNoun,
      plural_item_label = layer.pluralNoun,
      form_title = layer.formTitle.orEmpty(),
    )
  }

  fun mapLayerFromProto(proto: LayerDef): MapLayerItem =
    MapLayerItem(
      id = proto.id,
      label = proto.label,
      colorHex = parseHexColorOrDefault(proto.default_style?.color, 0xFF2E7D32L),
      geometryType =
        GeometryMapper.typeFromProto(proto.geometry_type) ?: LegacyEntityGeometry.typeOf(proto),
      isVisible = proto.visible_by_default,
      sourceType = LayerSourceType.ENTITY_DATASET,
      formId = proto.form_geometry?.form_id?.takeIf { it.isNotBlank() },
      formTitle = proto.form_title.takeIf { it.isNotBlank() },
      fieldPath = proto.form_geometry?.field_path?.takeIf { it.isNotBlank() },
      singularNoun = proto.singular_item_label.takeUnless { it == LEGACY_SINGULAR_NOUN }.orEmpty(),
      pluralNoun = proto.plural_item_label.takeUnless { it == LEGACY_PLURAL_NOUN }.orEmpty(),
      datasetId = proto.entity_dataset_id?.takeIf { it.isNotBlank() },
      iconName = proto.default_style?.icon?.takeIf { it.isNotBlank() },
    )

  /**
   * The English nouns earlier clients stored for a layer whose author gave none. They read as
   * blank, so the UI names the features generically in the user's language.
   */
  const val LEGACY_SINGULAR_NOUN = "location"
  const val LEGACY_PLURAL_NOUN = "locations"

  fun surveyAreaToProto(area: SurveyArea): SurveyAreaProto =
    SurveyAreaProto(
      name = area.name,
      boundary = SurveyGeometryMapper.areaBoundaryToProto(area.parts),
      area_source = SurveyAreaSourceMapper.toProto(area.source),
      center = SurveyGeometryMapper.areaCenterToProto(area.center),
      zoom = area.zoom,
    )

  fun surveyAreaFromProto(proto: SurveyAreaProto): SurveyArea {
    val parts = SurveyGeometryMapper.areaParts(proto)
    val center = SurveyGeometryMapper.areaCenter(proto) ?: LatLng(0.0, 0.0)
    return SurveyArea(
      name = proto.name,
      parts = parts,
      center = center,
      zoom = if (proto.zoom > 0.0) proto.zoom else 12.0,
      source = SurveyAreaSourceMapper.fromProto(proto),
    )
  }

  fun entityDatasetToProto(ds: EntityDataset): EntityDatasetDef =
    EntityDatasetDef(
      id = ds.id,
      display_name = ds.displayName,
      description = ds.description,
      type =
        when (ds.kind) {
          DatasetKind.DATA_TABLE -> EntityType.TABULAR
          DatasetKind.MAP_LAYER -> EntityType.GEOSPATIAL
        },
      key_property = ds.keyProperty,
      label_property = ds.labelProperty,
      field_creation_enabled = ds.fieldCreationEnabled,
      properties =
        ds.properties.map { p ->
          EntityPropertyDefinition(
            name = p.name,
            label = p.label,
            required = p.required,
            type =
              when (p.type) {
                PropertyType.TEXT -> DataType.TYPE_STRING
                PropertyType.INTEGER -> DataType.TYPE_INT32
                PropertyType.DECIMAL -> DataType.TYPE_DOUBLE
                PropertyType.BOOLEAN -> DataType.TYPE_BOOLEAN
                PropertyType.DATE -> DataType.TYPE_DATE
              },
            concept_ref =
              p.conceptLink?.let { link ->
                ConceptRef(concept_id = link.conceptId, version = link.version)
              },
          )
        },
      geometry_type =
        when (ds.geometryType) {
          GeometryType.POINT -> DataType.TYPE_GEOPOINT
          GeometryType.LINE_STRING -> DataType.TYPE_GEOTRACE
          GeometryType.POLYGON -> DataType.TYPE_GEOSHAPE
        },
      sampling =
        ds.generator?.let { gen ->
          val strataRef =
            (gen.areaSource as? SampleAreaSource.StrataLayer)?.let { s ->
              SamplingDesign.StrataLayerRef(
                entity_dataset_id = s.datasetKey,
                stratum_property = s.stratumProperty,
              )
            }
          SamplingDesignProtos.toProto(gen.toSampleDesign(), strataRef)
            .copy(
              generated_at = gen.lastRun?.generatedAt?.let(::parseIsoToInstant),
              input_hash = gen.lastRun?.inputHash.orEmpty(),
              feature_count = (gen.lastRun?.featureCount ?: ds.rows.size).toLong(),
            )
        },
      editor_key = ds.key,
      linked_form_key = ds.linkedFormKey.orEmpty(),
      default_style =
        GeometryStyle(
          color = ds.style.colorHex,
          icon = ds.style.iconName.orEmpty(),
          stroke_width = ds.style.strokeWidth,
          fill_opacity = ds.style.fillOpacity,
        ),
      visible_by_default = ds.style.visibleByDefault,
      rows =
        ds.rows.map { r ->
          DataTableRow(
            row_key = r.key,
            values = r.values,
            geometry = SurveyGeometryMapper.rowGeometryToProto(ds.geometryType, r.geometry),
          )
        },
    )

  fun entityDatasetFromProto(proto: EntityDatasetDef): EntityDataset {
    val generator = proto.sampling?.let { sampleDesignConfigFromProto(it, proto.rows.size) }
    val raw =
      EntityDataset(
        key = proto.editor_key.ifBlank { proto.id },
        kind =
          when (proto.type) {
            EntityType.TABULAR -> DatasetKind.DATA_TABLE
            else -> DatasetKind.MAP_LAYER
          },
        id = proto.id,
        displayName = proto.display_name,
        description = proto.description,
        geometryType =
          when (proto.geometry_type) {
            DataType.TYPE_GEOTRACE -> GeometryType.LINE_STRING
            DataType.TYPE_GEOSHAPE -> GeometryType.POLYGON
            else -> GeometryType.POINT
          },
        keyProperty = proto.key_property,
        labelProperty = proto.label_property,
        fieldCreationEnabled = proto.field_creation_enabled,
        linkedFormKey = proto.linked_form_key.takeIf { it.isNotBlank() },
        properties =
          proto.properties.map { p ->
            EntityProperty(
              name = p.name,
              label = p.label,
              type =
                when (p.type) {
                  DataType.TYPE_INT32,
                  DataType.TYPE_INT64 -> PropertyType.INTEGER
                  DataType.TYPE_DOUBLE -> PropertyType.DECIMAL
                  DataType.TYPE_BOOLEAN -> PropertyType.BOOLEAN
                  DataType.TYPE_DATE -> PropertyType.DATE
                  else -> PropertyType.TEXT
                },
              required = p.required,
              conceptLink =
                p.concept_ref
                  ?.takeIf { it.concept_id.isNotBlank() }
                  ?.let { ConceptLink(it.concept_id, it.version) },
            )
          },
        rows =
          proto.rows.map { r ->
            EntityRow(
              key = r.row_key,
              values = r.values,
              geometry = SurveyGeometryMapper.rowVertices(r),
            )
          },
        style =
          proto.default_style?.let { s ->
            LayerStyle(
              colorHex = s.color.ifBlank { "#2E7D32" },
              strokeWidth = if (s.stroke_width > 0.0) s.stroke_width else 2.0,
              fillOpacity = if (s.fill_opacity > 0.0) s.fill_opacity else 0.3,
              visibleByDefault = proto.visible_by_default,
              iconName = s.icon.takeIf { it.isNotBlank() },
            )
          } ?: LayerStyle(),
        generator = generator,
      )
    return SurveyEditorDerivation.normalizeSamplePlotDataset(raw)
  }

  private fun sampleDesignConfigFromProto(
    proto: SamplingDesign,
    rowCount: Int,
  ): SampleDesignConfig? {
    val design = SamplingDesignProtos.fromProto(proto) ?: return null
    val defaults = SampleDesignConfig()
    val areaSource =
      proto.strata_layer
        ?.takeIf { it.entity_dataset_id.isNotBlank() }
        ?.let { SampleAreaSource.StrataLayer(it.entity_dataset_id, it.stratum_property) }
        ?: SampleAreaSource.SurveyArea
    val plotShape =
      when (design.plotShape) {
        PlotShape.SQUARE -> PlotShapeOption.SQUARE
        PlotShape.CIRCLE -> PlotShapeOption.CIRCLE
        PlotShape.POINT -> PlotShapeOption.POINT
      }
    val subPlot =
      when (design.subPlot) {
        SubPlotLayout.None -> SubPlotMode.NONE
        SubPlotLayout.Center -> SubPlotMode.CENTER
        is SubPlotLayout.Grid -> SubPlotMode.GRID
        is SubPlotLayout.Random -> SubPlotMode.RANDOM
      }
    val subPlotGrid = design.subPlot as? SubPlotLayout.Grid
    val subPlotRandom = design.subPlot as? SubPlotLayout.Random
    val lastRun =
      if (proto.generated_at != null || proto.input_hash.isNotBlank() || proto.feature_count > 0L) {
        GenerationRecord(
          generatedAt = proto.generated_at?.let { isoUtc(it.toKotlinInstant()) }.orEmpty(),
          inputHash = proto.input_hash,
          featureCount = if (proto.feature_count > 0L) proto.feature_count.toInt() else rowCount,
          engineVersion =
            if (proto.engine_version > 0) proto.engine_version else SAMPLING_ENGINE_VERSION,
        )
      } else {
        null
      }
    val base =
      defaults.copy(
        areaSource = areaSource,
        plotShape = plotShape,
        plotSizeM = if (design.plotSizeM > 0.0) design.plotSizeM else defaults.plotSizeM,
        subPlot = subPlot,
        subPlotGridN = subPlotGrid?.n ?: defaults.subPlotGridN,
        subPlotSpacingM = subPlotGrid?.spacingM ?: defaults.subPlotSpacingM,
        subPlotRandomCount = subPlotRandom?.count ?: defaults.subPlotRandomCount,
        shuffle = design.shuffle,
        seed = design.seed,
        lastRun = lastRun,
      )
    return when (val layout = design.layout) {
      is PlotLayout.SystematicGrid ->
        base.copy(
          method = SampleMethod.SYSTEMATIC_GRID,
          gridSpacingM = if (layout.spacingM > 0.0) layout.spacingM else defaults.gridSpacingM,
        )
      is PlotLayout.SimpleRandom ->
        base.copy(
          method = SampleMethod.SIMPLE_RANDOM,
          count = layout.count,
          minDistanceM = layout.minDistanceM,
        )
      is PlotLayout.StratifiedRandom ->
        base.copy(
          method = SampleMethod.STRATIFIED_RANDOM,
          count = layout.totalCount,
          minDistanceM = layout.minDistanceM,
          allocation =
            when (layout.allocation) {
              Allocation.Equal -> AllocationMode.EQUAL
              Allocation.Proportional -> AllocationMode.PROPORTIONAL
              is Allocation.Custom -> AllocationMode.CUSTOM
            },
          customCounts = (layout.allocation as? Allocation.Custom)?.countsByStratum.orEmpty(),
        )
      is PlotLayout.Cluster ->
        base.copy(
          method = SampleMethod.CLUSTER,
          clusterCount = layout.clusterCount,
          plotsPerSide = layout.plotsPerSide,
          clusterSpacingM = layout.plotSpacingM,
          minDistanceM = layout.minDistanceM,
        )
    }
  }
}
