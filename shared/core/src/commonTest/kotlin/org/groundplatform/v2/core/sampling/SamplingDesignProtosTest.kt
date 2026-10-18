/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.core.sampling

import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.forms.GeoShape
import groundplatform.v2.survey.EntityDatasetDef
import groundplatform.v2.survey.MapConfig
import groundplatform.v2.survey.SamplingDesign
import groundplatform.v2.survey.SurveyArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SamplingDesignProtosTest {
  private val designs =
    listOf(
      SampleDesign(
        PlotLayout.SystematicGrid(500.0),
        PlotShape.CIRCLE,
        30.0,
        SubPlotLayout.Grid(5, 10.0),
        false,
        7,
      ),
      SampleDesign(
        PlotLayout.SimpleRandom(250, 100.0),
        PlotShape.POINT,
        0.0,
        SubPlotLayout.Center,
        true,
        -3,
      ),
      SampleDesign(
        PlotLayout.StratifiedRandom(1000, Allocation.Equal, 50.0),
        subPlot = SubPlotLayout.Random(9),
      ),
      SampleDesign(PlotLayout.StratifiedRandom(1000, Allocation.Proportional)),
      SampleDesign(
        PlotLayout.StratifiedRandom(0, Allocation.Custom(mapOf("forest" to 40, "crop" to 10)))
      ),
      SampleDesign(PlotLayout.Cluster(30, 3, 150.0, 2000.0), seed = Long.MAX_VALUE),
    )

  @Test
  fun designsRoundTripThroughProtoBytes() {
    for (design in designs) {
      val proto = SamplingDesignProtos.toProto(design)
      assertEquals(true, proto.survey_area)
      assertEquals(SAMPLING_ENGINE_VERSION, proto.engine_version)
      val decoded = SamplingDesign.ADAPTER.decode(SamplingDesign.ADAPTER.encode(proto))
      assertEquals(proto, decoded)
      assertEquals(design, SamplingDesignProtos.fromProto(decoded))
    }
  }

  @Test
  fun strataLayerAndUnspecifiedMethod() {
    val layer =
      SamplingDesign.StrataLayerRef(entity_dataset_id = "land_cover", stratum_property = "class")
    val proto = SamplingDesignProtos.toProto(designs[3], layer)
    assertNull(proto.survey_area)
    assertEquals(layer, proto.strata_layer)
    assertNull(SamplingDesignProtos.fromProto(SamplingDesign()))
  }

  @Test
  fun surveyAreaAndGeneratorPersistInSurveyProtos() {
    val ring =
      listOf(GeoCoord(0.0, 0.0), GeoCoord(0.0, 1.0), GeoCoord(1.0, 1.0), GeoCoord(0.0, 0.0))
    val area =
      SurveyArea(
        name = "Test area",
        parts =
          listOf(GeoShape(points = ring.map { GeoPoint(latitude = it.lat, longitude = it.lng) })),
        source = "uploaded:area.geojson",
      )
    val mapConfig = MapConfig(survey_area = area)
    assertEquals(mapConfig, MapConfig.ADAPTER.decode(MapConfig.ADAPTER.encode(mapConfig)))
    val dataset =
      EntityDatasetDef(
        id = "plots",
        sampling =
          SamplingDesignProtos.toProto(designs[0]).copy(feature_count = 1234, input_hash = "abc"),
      )
    val decoded = EntityDatasetDef.ADAPTER.decode(EntityDatasetDef.ADAPTER.encode(dataset))
    assertEquals(dataset, decoded)
    assertNull(decoded.imported)
    assertTrue(decoded.sampling?.feature_count == 1234L)
  }
}
