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
package org.groundplatform.v2.core.forms

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.ofEpochSecond
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.forms.GeoShape
import groundplatform.v2.forms.GeoTrace
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.RecordMetadata
import groundplatform.v2.forms.RecordNode
import groundplatform.v2.forms.RecordNodeList
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.forms.TypedValueList

/**
 * Ergonomic Kotlin Multiplatform DSL builders for constructing ProtoForms Protocol Buffer instances
 * ([RecordInstance], [RecordNode], [FieldValue], [TypedValue]).
 */
class RecordNodeBuilder {
  private val fields = linkedMapOf<String, FieldValue>()

  fun string(name: String, value: String) {
    fields[name] = FieldValue(scalar_value = TypedValue(string_value = value))
  }

  fun int32(name: String, value: Int) {
    fields[name] = FieldValue(scalar_value = TypedValue(int32_value = value))
  }

  fun int64(name: String, value: Long) {
    fields[name] = FieldValue(scalar_value = TypedValue(int64_value = value))
  }

  fun double(name: String, value: Double) {
    fields[name] = FieldValue(scalar_value = TypedValue(double_value = value))
  }

  fun bool(name: String, value: Boolean) {
    fields[name] = FieldValue(scalar_value = TypedValue(bool_value = value))
  }

  fun date(name: String, year: Int, month: Int, day: Int) {
    fields[name] =
      FieldValue(
        scalar_value = TypedValue(date_value = Date(year = year, month = month, day = day))
      )
  }

  fun time(name: String, hours: Int, minutes: Int, seconds: Int = 0, nanos: Int = 0) {
    fields[name] =
      FieldValue(
        scalar_value =
          TypedValue(
            time_value =
              TimeOfDay(hours = hours, minutes = minutes, seconds = seconds, nanos = nanos)
          )
      )
  }

  fun timestamp(name: String, epochSeconds: Long, nanos: Long = 0L) {
    fields[name] =
      FieldValue(scalar_value = TypedValue(timestamp_value = ofEpochSecond(epochSeconds, nanos)))
  }

  fun geopoint(name: String, lat: Double, lon: Double, alt: Double = 0.0, acc: Double = 0.0) {
    fields[name] =
      FieldValue(
        scalar_value =
          TypedValue(
            geopoint_value =
              GeoPoint(
                latitude = lat,
                longitude = lon,
                altitude_meters = alt,
                accuracy_meters = acc,
              )
          )
      )
  }

  fun geotrace(name: String, vararg points: GeoPoint) {
    fields[name] =
      FieldValue(scalar_value = TypedValue(geotrace_value = GeoTrace(points = points.toList())))
  }

  fun geoshape(name: String, vararg points: GeoPoint) {
    fields[name] =
      FieldValue(scalar_value = TypedValue(geoshape_value = GeoShape(points = points.toList())))
  }

  fun multiSelect(name: String, vararg choices: String) {
    fields[name] =
      FieldValue(
        list_value = TypedValueList(values = choices.map { TypedValue(string_value = it) })
      )
  }

  fun emptyField(name: String) {
    fields[name] = FieldValue()
  }

  fun group(name: String, block: RecordNodeBuilder.() -> Unit) {
    val builder = RecordNodeBuilder()
    builder.block()
    fields[name] = FieldValue(node_value = builder.build())
  }

  fun repeat(name: String, block: RepeatListBuilder.() -> Unit) {
    val builder = RepeatListBuilder()
    builder.block()
    fields[name] = FieldValue(repeat_value = RecordNodeList(nodes = builder.build()))
  }

  fun build(): RecordNode = RecordNode(fields = fields)
}

class RepeatListBuilder {
  private val items = mutableListOf<RecordNode>()

  fun item(block: RecordNodeBuilder.() -> Unit) {
    val builder = RecordNodeBuilder()
    builder.block()
    items.add(builder.build())
  }

  fun build(): List<RecordNode> = items
}

fun buildRecordNode(block: RecordNodeBuilder.() -> Unit): RecordNode {
  val builder = RecordNodeBuilder()
  builder.block()
  return builder.build()
}

fun buildRecordInstance(
  formId: String = "household",
  version: String = "1",
  instanceId: String = "uuid:test-1234",
  deviceId: String = "device-abc",
  schemaName: String = formId,
  block: RecordNodeBuilder.() -> Unit,
): RecordInstance {
  val nodeBuilder = RecordNodeBuilder()
  nodeBuilder.block()
  return RecordInstance(
    form_id = formId,
    form_version = version,
    metadata = RecordMetadata(instance_id = instanceId, device_id = deviceId),
    data_ = nodeBuilder.build(),
  )
}
