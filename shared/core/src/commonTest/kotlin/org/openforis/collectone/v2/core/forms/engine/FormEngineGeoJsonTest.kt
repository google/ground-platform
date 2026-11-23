/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.core.forms.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ItemsetDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.SecondaryInstance
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.buildRecordNode

class FormEngineGeoJsonTest {

  @Test
  fun choiceName_onGeoJsonItemset_returnsFeatureTitle() {
    val session = stationsSession()

    assertEquals(
      "Seattle Station",
      session.state.findFieldState("station_label")?.value?.scalar_value?.string_value,
    )
  }

  @Test
  fun geoJsonSecondaryInstance_geometry_readsAsGeopointString() {
    val session = stationsSession()

    assertEquals(
      "47.6062 -122.3321 15 0",
      session.state.findFieldState("station_geom")?.value?.scalar_value?.string_value,
    )
  }

  @Test
  fun distance_withSeveralPoints_sumsTheSegments() {
    val session = stationsSession()

    session.updateGeoPoint("p1", 0.0, 0.0)
    session.updateGeoPoint("p2", 0.0, 1.0)
    session.updateGeoPoint("p3", 1.0, 1.0)

    val legDist = session.state.findFieldState("leg_dist")?.value?.scalar_value?.double_value ?: 0.0
    assertTrue(legDist > 200_000.0, "Expected 2-segment distance > 200km, got $legDist")
  }

  private fun stationsSession() =
    FormSession(
      stationsForm,
      environment =
        FormEnvironment(
          secondaryInstanceProvider =
            org.openforis.collectone.v2.core.forms.xpath.InMemorySecondaryInstanceProvider
              .fromGeoJson(mapOf("stations" to STATIONS_GEOJSON))
        ),
    )

  private val stationsForm: FormDef =
    FormDef(
      form_id = "geo_itemset_form",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  fields =
                    listOf(
                      FieldDefinition(name = "station", type = DataType.TYPE_SELECT_ONE),
                      FieldDefinition(name = "station_label", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "station_geom", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "p1", type = DataType.TYPE_GEOPOINT),
                      FieldDefinition(name = "p2", type = DataType.TYPE_GEOPOINT),
                      FieldDefinition(name = "p3", type = DataType.TYPE_GEOPOINT),
                      FieldDefinition(name = "leg_dist", type = DataType.TYPE_DOUBLE),
                    ),
                ),
              default_values = buildRecordNode { string("station", "station_a") },
            ),
          secondary_instances =
            listOf(SecondaryInstance(id = "stations", uri = "jr://file/stations.geojson")),
          bindings =
            listOf(
              FieldBinding(field_path = "station", type = DataType.TYPE_SELECT_ONE),
              FieldBinding(
                field_path = "station_label",
                type = DataType.TYPE_STRING,
                calculate_expression = "jr:choice-name( /data/station , ' /data/station ')",
              ),
              FieldBinding(
                field_path = "station_geom",
                type = DataType.TYPE_STRING,
                calculate_expression =
                  "instance('stations')/root/item[id = /data/station]/geometry",
              ),
              FieldBinding(field_path = "p1", type = DataType.TYPE_GEOPOINT),
              FieldBinding(field_path = "p2", type = DataType.TYPE_GEOPOINT),
              FieldBinding(field_path = "p3", type = DataType.TYPE_GEOPOINT),
              FieldBinding(
                field_path = "leg_dist",
                type = DataType.TYPE_DOUBLE,
                calculate_expression = "distance(/data/p1, /data/p2, /data/p3)",
              ),
            ),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                control =
                  ControlDef(
                    field_ref = "station",
                    type = ControlType.CONTROL_SELECT_ONE,
                    itemset =
                      ItemsetDef(
                        instance_id = "stations",
                        value_ref = "id",
                        label_ref = "title",
                      ),
                  )
              )
            )
        ),
    )

  private companion object {
    val STATIONS_GEOJSON =
      """
      {
        "type": "FeatureCollection",
        "features": [
          {
            "type": "Feature",
            "id": "station_a",
            "geometry": { "type": "Point", "coordinates": [-122.3321, 47.6062, 15] },
            "properties": { "title": "Seattle Station", "zone": "north" }
          },
          {
            "type": "Feature",
            "id": "station_b",
            "geometry": { "type": "Point", "coordinates": [-122.6765, 45.5231] },
            "properties": { "title": "Portland Station", "zone": "south" }
          }
        ]
      }
      """
        .trimIndent()
  }
}
