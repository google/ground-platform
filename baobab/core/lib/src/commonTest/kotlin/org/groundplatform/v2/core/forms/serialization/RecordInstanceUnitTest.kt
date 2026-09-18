/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.serialization

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.ofEpochSecond
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldDefinition
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.forms.GeoShape
import groundplatform.v2.forms.GeoTrace
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.RecordMetadata
import groundplatform.v2.forms.RecordNode
import groundplatform.v2.forms.RecordNodeList
import groundplatform.v2.forms.RecordSchema
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.forms.TypedValueList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RecordInstanceUnitTest {

  @Test
  fun testDeserializeAndSerializeSubmissionMetadataAndScalars() {
    val xml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <data id="household_survey" version="20250201">
        <meta>
          <instanceID>uuid:12345678-1234-4321-8765-123456789abc</instanceID>
          <timeStart>2025-02-15T08:15:30.000Z</timeStart>
          <timeEnd>2025-02-15T08:45:10.000Z</timeEnd>
          <deviceID>android-device-99</deviceID>
          <audit>audit.csv</audit>
        </meta>
        <head_name>Maria Gonzalez</head_name>
        <household_size>5</household_size>
        <monthly_income>1450.75</monthly_income>
        <has_electricity>true</has_electricity>
        <survey_date>2025-02-15</survey_date>
        <visit_time>14:30:00</visit_time>
      </data>
      """
        .trimIndent()

    val record = XFormsXmlSerializer.deserializeRecordInstance(xml)

    assertEquals("household_survey", record.form_id)
    assertEquals("20250201", record.form_version)

    val meta = record.metadata
    assertNotNull(meta)
    assertEquals("uuid:12345678-1234-4321-8765-123456789abc", meta.instance_id)
    assertEquals("android-device-99", meta.device_id)
    assertEquals("audit.csv", meta.audit_file_uri)
    assertNotNull(meta.start_time)
    assertNotNull(meta.end_time)

    val data = record.data_
    assertNotNull(data)
    assertEquals("Maria Gonzalez", data.fields["head_name"]?.scalar_value?.string_value)
    assertEquals(5, data.fields["household_size"]?.scalar_value?.int32_value)
    assertEquals(1450.75, data.fields["monthly_income"]?.scalar_value?.double_value)
    assertEquals(true, data.fields["has_electricity"]?.scalar_value?.bool_value)
    assertEquals(
      Date(year = 2025, month = 2, day = 15),
      data.fields["survey_date"]?.scalar_value?.date_value,
    )
    assertEquals(
      TimeOfDay(hours = 14, minutes = 30, seconds = 0, nanos = 0),
      data.fields["visit_time"]?.scalar_value?.time_value,
    )

    // Round-trip
    val serializedXml = XFormsXmlSerializer.serialize(record)
    val deserialized = XFormsXmlSerializer.deserializeRecordInstance(serializedXml)
    assertEquals(record, deserialized)
  }

  @Test
  fun testGeospatialAndRepeatGroupsWithSchema() {
    val schema =
      RecordSchema(
        name = "forest_inventory",
        fields =
          listOf(
            FieldDefinition(name = "plot_location", type = DataType.TYPE_GEOPOINT),
            FieldDefinition(name = "transect_path", type = DataType.TYPE_GEOTRACE),
            FieldDefinition(name = "boundary_polygon", type = DataType.TYPE_GEOSHAPE),
            FieldDefinition(
              name = "equipment_used",
              type = DataType.TYPE_SELECT_MULTIPLE,
              is_repeated = true,
            ),
            FieldDefinition(
              name = "tree_measurement",
              type = DataType.TYPE_MESSAGE,
              is_repeated = true,
              fields =
                listOf(
                  FieldDefinition(name = "species", type = DataType.TYPE_STRING),
                  FieldDefinition(name = "dbh_cm", type = DataType.TYPE_DOUBLE),
                ),
            ),
          ),
      )

    val record =
      RecordInstance(
        form_id = "forest_inventory",
        form_version = "1.0",
        metadata =
          RecordMetadata(
            instance_id = "uuid:forest-001",
            start_time = ofEpochSecond(1739600000L, 0L),
          ),
        data_ =
          RecordNode(
            fields =
              mapOf(
                "plot_location" to
                  FieldValue(
                    scalar_value =
                      TypedValue(
                        geopoint_value =
                          GeoPoint(
                            latitude = -3.4653,
                            longitude = -62.2159,
                            altitude_meters = 85.5,
                            accuracy_meters = 3.2,
                          )
                      )
                  ),
                "transect_path" to
                  FieldValue(
                    scalar_value =
                      TypedValue(
                        geotrace_value =
                          GeoTrace(
                            points =
                              listOf(
                                GeoPoint(
                                  latitude = -3.4653,
                                  longitude = -62.2159,
                                  altitude_meters = 85.0,
                                  accuracy_meters = 3.0,
                                ),
                                GeoPoint(
                                  latitude = -3.4660,
                                  longitude = -62.2165,
                                  altitude_meters = 86.0,
                                  accuracy_meters = 3.5,
                                ),
                              )
                          )
                      )
                  ),
                "boundary_polygon" to
                  FieldValue(
                    scalar_value =
                      TypedValue(
                        geoshape_value =
                          GeoShape(
                            points =
                              listOf(
                                GeoPoint(
                                  latitude = -3.4650,
                                  longitude = -62.2150,
                                  altitude_meters = 80.0,
                                  accuracy_meters = 2.0,
                                ),
                                GeoPoint(
                                  latitude = -3.4670,
                                  longitude = -62.2150,
                                  altitude_meters = 81.0,
                                  accuracy_meters = 2.0,
                                ),
                                GeoPoint(
                                  latitude = -3.4670,
                                  longitude = -62.2170,
                                  altitude_meters = 82.0,
                                  accuracy_meters = 2.0,
                                ),
                                GeoPoint(
                                  latitude = -3.4650,
                                  longitude = -62.2150,
                                  altitude_meters = 80.0,
                                  accuracy_meters = 2.0,
                                ),
                              )
                          )
                      )
                  ),
                "equipment_used" to
                  FieldValue(
                    list_value =
                      TypedValueList(
                        values =
                          listOf(
                            TypedValue(string_value = "caliper"),
                            TypedValue(string_value = "laser_rangefinder"),
                            TypedValue(string_value = "gps_rover"),
                          )
                      )
                  ),
                "tree_measurement" to
                  FieldValue(
                    repeat_value =
                      RecordNodeList(
                        nodes =
                          listOf(
                            RecordNode(
                              fields =
                                mapOf(
                                  "species" to
                                    FieldValue(
                                      scalar_value =
                                        TypedValue(string_value = "Bertholletia excelsa")
                                    ),
                                  "dbh_cm" to
                                    FieldValue(scalar_value = TypedValue(double_value = 112.4)),
                                )
                            ),
                            RecordNode(
                              fields =
                                mapOf(
                                  "species" to
                                    FieldValue(
                                      scalar_value = TypedValue(string_value = "Hevea brasiliensis")
                                    ),
                                  "dbh_cm" to
                                    FieldValue(scalar_value = TypedValue(double_value = 48.2)),
                                )
                            ),
                          )
                      )
                  ),
              )
          ),
      )

    val xml = XFormsXmlSerializer.serialize(record, rootElementName = "forest_inventory")
    val deserialized = XFormsXmlSerializer.deserializeRecordInstance(xml, schema = schema)
    assertEquals(record, deserialized)
  }
}
