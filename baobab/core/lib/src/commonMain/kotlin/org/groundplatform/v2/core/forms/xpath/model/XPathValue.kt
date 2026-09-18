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
package org.groundplatform.v2.core.forms.xpath.model

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.Instant
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.forms.GeoShape
import groundplatform.v2.forms.GeoTrace
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.forms.TypedValueList
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * Rich runtime value ADT evaluated by the ProtoForms XPath engine.
 *
 * Extends standard XPath 1.0's four types (`NodeSet`, `Boolean`, `Number`, `String`) with native
 * strongly-typed Protocol Buffer representations (`Date`, `TimeOfDay`, `Instant`, `GeoPoint`,
 * `GeoTrace`, `GeoShape`, `ValueList`) to prevent string-casting bugs and preserve rich types
 * across functions like `if()`, `once()`, `coalesce()`, and `calculate_expression` bindings.
 */
sealed interface XPathValue {

  /** Coerces this value to a boolean according to XPath 1.0 + ProtoForms truthiness rules. */
  fun toBoolean(): Boolean

  /** Coerces this value to a double-precision number (dates/timestamps convert to epoch days). */
  fun toNumber(): Double

  /** Formats this value to its canonical XPath string representation. */
  fun toXPathString(): String

  /** Returns true if this value represents an empty / unpopulated field answer. */
  fun isEmptyValue(): Boolean

  /**
   * Unwraps a singleton [NodeSet] to its underlying scalar/composite value, or returns `this` if
   * already a non-NodeSet value. Empty NodeSets unwrap to `Str("")`.
   */
  fun unwrapNodeSet(): XPathValue =
    when (this) {
      is NodeSet -> if (nodes.isEmpty()) Str("") else nodes.first().extractValue()
      else -> this
    }

  /**
   * Converts this evaluated value into a strongly-typed [TypedValue] suitable for writing to a
   * [groundplatform.v2.forms.RecordInstance] field (e.g., from a `calculate_expression`).
   */
  fun toTypedValue(targetType: DataType? = null): TypedValue? {
    val unwrapped = unwrapNodeSet()
    if (unwrapped.isEmptyValue() && targetType != DataType.TYPE_STRING) {
      return null
    }
    return when (targetType) {
      DataType.TYPE_STRING,
      DataType.TYPE_SELECT_ONE -> TypedValue(string_value = unwrapped.toXPathString())
      DataType.TYPE_INT32 -> {
        val num = unwrapped.toNumber()
        if (num.isNaN()) null else TypedValue(int32_value = num.roundToInt())
      }
      DataType.TYPE_INT64 -> {
        val num = unwrapped.toNumber()
        if (num.isNaN()) null else TypedValue(int64_value = num.roundToLong())
      }
      DataType.TYPE_DOUBLE -> {
        val num = unwrapped.toNumber()
        if (num.isNaN()) null else TypedValue(double_value = num)
      }
      DataType.TYPE_BOOLEAN -> TypedValue(bool_value = unwrapped.toBoolean())
      DataType.TYPE_DATE -> {
        when (unwrapped) {
          is DateVal -> TypedValue(date_value = unwrapped.value)
          is TimestampVal ->
            TypedValue(date_value = TemporalUtils.epochDaysToDate(unwrapped.toNumber().toLong()))
          else -> {
            val parsed = TemporalUtils.tryParseDate(unwrapped.toXPathString())
            if (parsed != null) TypedValue(date_value = parsed)
            else {
              val num = unwrapped.toNumber()
              if (!num.isNaN()) TypedValue(date_value = TemporalUtils.epochDaysToDate(num.toLong()))
              else null
            }
          }
        }
      }
      DataType.TYPE_TIME -> {
        when (unwrapped) {
          is TimeVal -> TypedValue(time_value = unwrapped.value)
          else ->
            TemporalUtils.tryParseTime(unwrapped.toXPathString())?.let {
              TypedValue(time_value = it)
            }
        }
      }
      DataType.TYPE_DATETIME -> {
        when (unwrapped) {
          is TimestampVal -> TypedValue(timestamp_value = unwrapped.value)
          is DateVal ->
            TypedValue(timestamp_value = TemporalUtils.epochDaysToInstant(unwrapped.toNumber()))
          else -> {
            val epochDays = TemporalUtils.tryParseToEpochDays(unwrapped.toXPathString())
            if (epochDays != null)
              TypedValue(timestamp_value = TemporalUtils.epochDaysToInstant(epochDays))
            else null
          }
        }
      }
      DataType.TYPE_GEOPOINT -> {
        when (unwrapped) {
          is GeoPointVal -> TypedValue(geopoint_value = unwrapped.value)
          else ->
            parseGeoPointString(unwrapped.toXPathString())?.let { TypedValue(geopoint_value = it) }
        }
      }
      DataType.TYPE_GEOTRACE -> {
        when (unwrapped) {
          is GeoTraceVal -> TypedValue(geotrace_value = unwrapped.value)
          else ->
            parseGeoTraceString(unwrapped.toXPathString())?.let { TypedValue(geotrace_value = it) }
        }
      }
      DataType.TYPE_GEOSHAPE -> {
        when (unwrapped) {
          is GeoShapeVal -> TypedValue(geoshape_value = unwrapped.value)
          else ->
            parseGeoShapeString(unwrapped.toXPathString())?.let { TypedValue(geoshape_value = it) }
        }
      }
      DataType.TYPE_BINARY -> {
        when (unwrapped) {
          is BinaryVal -> TypedValue(binary_value = unwrapped.value)
          else -> TypedValue(binary_value = unwrapped.toXPathString().encodeUtf8())
        }
      }
      else -> {
        // Infer from runtime XPathValue variant when targetType is unspecified
        when (unwrapped) {
          is Bool -> TypedValue(bool_value = unwrapped.value)
          is Number -> {
            val d = unwrapped.value
            if (d.isNaN()) null
            else if (d % 1.0 == 0.0 && d >= Int.MIN_VALUE && d <= Int.MAX_VALUE) {
              TypedValue(int32_value = d.toInt())
            } else {
              TypedValue(double_value = d)
            }
          }
          is Str -> TypedValue(string_value = unwrapped.value)
          is DateVal -> TypedValue(date_value = unwrapped.value)
          is TimeVal -> TypedValue(time_value = unwrapped.value)
          is TimestampVal -> TypedValue(timestamp_value = unwrapped.value)
          is GeoPointVal -> TypedValue(geopoint_value = unwrapped.value)
          is GeoTraceVal -> TypedValue(geotrace_value = unwrapped.value)
          is GeoShapeVal -> TypedValue(geoshape_value = unwrapped.value)
          is BinaryVal -> TypedValue(binary_value = unwrapped.value)
          is ValueList -> TypedValue(string_value = unwrapped.toXPathString())
          is NodeSet -> null
        }
      }
    }
  }

  /**
   * Converts this evaluated value into a [FieldValue] (supporting multi-select lists and scalars).
   */
  fun toFieldValue(targetType: DataType? = null): FieldValue? {
    val unwrapped = unwrapNodeSet()
    if (targetType == DataType.TYPE_SELECT_MULTIPLE || unwrapped is ValueList) {
      val items =
        when (unwrapped) {
          is ValueList -> unwrapped.values.mapNotNull { it.toTypedValue(DataType.TYPE_STRING) }
          else ->
            unwrapped
              .toXPathString()
              .trim()
              .split(Regex("\\s+"))
              .filter { it.isNotEmpty() }
              .map { TypedValue(string_value = it) }
        }
      return FieldValue(list_value = TypedValueList(values = items))
    }
    val tv = toTypedValue(targetType) ?: return null
    return FieldValue(scalar_value = tv)
  }

  /** Ordered set of virtual tree nodes selected by a path or filter expression. */
  data class NodeSet(val nodes: List<XPathNode>) : XPathValue {
    override fun toBoolean(): Boolean {
      if (nodes.isEmpty()) return false
      if (nodes.size == 1) {
        val v = nodes.first().extractValue()
        if (v is Bool) return v.value
        if (v.isEmptyValue()) return false
      }
      return nodes.any { !it.extractValue().isEmptyValue() }
    }

    override fun toNumber(): Double =
      if (nodes.isEmpty()) Double.NaN else nodes.first().extractValue().toNumber()

    override fun toXPathString(): String =
      if (nodes.isEmpty()) "" else nodes.first().extractValue().toXPathString()

    override fun isEmptyValue(): Boolean =
      nodes.isEmpty() || nodes.all { it.extractValue().isEmptyValue() }
  }

  /** Boolean value (`true` or `false`). */
  data class Bool(val value: Boolean) : XPathValue {
    override fun toBoolean(): Boolean = value

    override fun toNumber(): Double = if (value) 1.0 else 0.0

    override fun toXPathString(): String = if (value) "true" else "false"

    override fun isEmptyValue(): Boolean = false
  }

  /** Numeric IEEE 754 floating-point value. */
  data class Number(val value: Double) : XPathValue {
    override fun toBoolean(): Boolean = value != 0.0 && !value.isNaN()

    override fun toNumber(): Double = value

    override fun toXPathString(): String = formatXPathNumber(value)

    override fun isEmptyValue(): Boolean = value.isNaN()
  }

  /** UTF-8 string value. */
  data class Str(val value: String) : XPathValue {
    override fun toBoolean(): Boolean = value.isNotEmpty()

    override fun toNumber(): Double {
      val trimmed = value.trim()
      if (trimmed.isEmpty()) return Double.NaN
      trimmed.toDoubleOrNull()?.let {
        return it
      }
      TemporalUtils.tryParseToEpochDays(trimmed)?.let {
        return it
      }
      return Double.NaN
    }

    override fun toXPathString(): String = value

    override fun isEmptyValue(): Boolean = value.isEmpty()
  }

  /** Calendar date value (`google.type.Date`). */
  data class DateVal(val value: Date) : XPathValue {
    override fun toBoolean(): Boolean = true

    override fun toNumber(): Double = TemporalUtils.dateToEpochDays(value)

    override fun toXPathString(): String = TemporalUtils.formatDate(value)

    override fun isEmptyValue(): Boolean = value.year == 0 && value.month == 0 && value.day == 0
  }

  /** Time of day value (`google.type.TimeOfDay`). */
  data class TimeVal(val value: TimeOfDay) : XPathValue {
    override fun toBoolean(): Boolean = true

    override fun toNumber(): Double = TemporalUtils.timeOfDayToDecimal(value)

    override fun toXPathString(): String = TemporalUtils.formatTime(value)

    override fun isEmptyValue(): Boolean = false
  }

  /** UTC Timestamp point-in-time (`google.protobuf.Timestamp`). */
  data class TimestampVal(val value: Instant) : XPathValue {
    override fun toBoolean(): Boolean = true

    override fun toNumber(): Double = TemporalUtils.timestampToEpochDays(value)

    override fun toXPathString(): String = TemporalUtils.formatTimestamp(value)

    override fun isEmptyValue(): Boolean = false
  }

  /** Geographic coordinate point (`groundplatform.v2.forms.GeoPoint`). */
  data class GeoPointVal(val value: GeoPoint) : XPathValue {
    override fun toBoolean(): Boolean = true

    override fun toNumber(): Double = Double.NaN

    override fun toXPathString(): String = formatGeoPoint(value)

    override fun isEmptyValue(): Boolean = false
  }

  /** Geographic polyline path (`groundplatform.v2.forms.GeoTrace`). */
  data class GeoTraceVal(val value: GeoTrace) : XPathValue {
    override fun toBoolean(): Boolean = value.points.isNotEmpty()

    override fun toNumber(): Double = Double.NaN

    override fun toXPathString(): String = value.points.joinToString("; ") { formatGeoPoint(it) }

    override fun isEmptyValue(): Boolean = value.points.isEmpty()
  }

  /** Geographic closed polygon (`groundplatform.v2.forms.GeoShape`). */
  data class GeoShapeVal(val value: GeoShape) : XPathValue {
    override fun toBoolean(): Boolean = value.points.isNotEmpty()

    override fun toNumber(): Double = Double.NaN

    override fun toXPathString(): String = value.points.joinToString("; ") { formatGeoPoint(it) }

    override fun isEmptyValue(): Boolean = value.points.isEmpty()
  }

  /**
   * Multi-select choice list or repeated primitive values
   * (`groundplatform.v2.forms.TypedValueList`).
   */
  data class ValueList(val values: List<XPathValue>) : XPathValue {
    override fun toBoolean(): Boolean = values.isNotEmpty()

    override fun toNumber(): Double = values.firstOrNull()?.toNumber() ?: Double.NaN

    override fun toXPathString(): String = values.joinToString(" ") { it.toXPathString() }

    override fun isEmptyValue(): Boolean = values.isEmpty()
  }

  /** Binary payload (`bytes`). */
  data class BinaryVal(val value: ByteString) : XPathValue {
    override fun toBoolean(): Boolean = value.size > 0

    override fun toNumber(): Double = Double.NaN

    override fun toXPathString(): String = value.utf8()

    override fun isEmptyValue(): Boolean = value.size == 0
  }

  companion object {
    /** Converts a protobuf [TypedValue] into its corresponding [XPathValue]. */
    fun fromTypedValue(tv: TypedValue?): XPathValue {
      if (tv == null) return Str("")
      return when {
        tv.string_value != null -> Str(tv.string_value)
        tv.int32_value != null -> Number(tv.int32_value.toDouble())
        tv.int64_value != null -> Number(tv.int64_value.toDouble())
        tv.double_value != null -> Number(tv.double_value)
        tv.bool_value != null -> Bool(tv.bool_value)
        tv.date_value != null -> DateVal(tv.date_value)
        tv.time_value != null -> TimeVal(tv.time_value)
        tv.timestamp_value != null -> TimestampVal(tv.timestamp_value)
        tv.geopoint_value != null -> GeoPointVal(tv.geopoint_value)
        tv.geotrace_value != null -> GeoTraceVal(tv.geotrace_value)
        tv.geoshape_value != null -> GeoShapeVal(tv.geoshape_value)
        tv.binary_value != null -> BinaryVal(tv.binary_value)
        else -> Str("")
      }
    }

    /** Converts a protobuf [FieldValue] into its corresponding [XPathValue]. */
    fun fromFieldValue(fv: FieldValue?): XPathValue {
      if (fv == null) return Str("")
      return when {
        fv.scalar_value != null -> fromTypedValue(fv.scalar_value)
        fv.list_value != null -> ValueList(fv.list_value.values.map { fromTypedValue(it) })
        else -> Str("")
      }
    }

    fun formatXPathNumber(value: Double): String {
      if (value.isNaN()) return "NaN"
      if (value == Double.POSITIVE_INFINITY) return "Infinity"
      if (value == Double.NEGATIVE_INFINITY) return "-Infinity"
      if (value == 0.0) return "0"
      if (
        value % 1.0 == 0.0 &&
          value >= Long.MIN_VALUE.toDouble() &&
          value <= Long.MAX_VALUE.toDouble()
      ) {
        return value.toLong().toString()
      }
      return value.toString()
    }

    fun formatGeoPoint(pt: GeoPoint): String =
      "${formatXPathNumber(pt.latitude)} ${formatXPathNumber(pt.longitude)} ${formatXPathNumber(pt.altitude_meters)} ${formatXPathNumber(pt.accuracy_meters)}"

    fun parseGeoPointString(str: String): GeoPoint? {
      val tokens = str.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
      if (tokens.size < 2) return null
      val lat = tokens[0].toDoubleOrNull() ?: return null
      val lon = tokens[1].toDoubleOrNull() ?: return null
      val alt = tokens.getOrNull(2)?.toDoubleOrNull() ?: 0.0
      val acc = tokens.getOrNull(3)?.toDoubleOrNull() ?: 0.0
      return GeoPoint(latitude = lat, longitude = lon, altitude_meters = alt, accuracy_meters = acc)
    }

    fun parseGeoTraceString(str: String): GeoTrace? {
      val points = str.split(';').mapNotNull { parseGeoPointString(it) }
      return if (points.isNotEmpty()) GeoTrace(points = points) else null
    }

    fun parseGeoShapeString(str: String): GeoShape? {
      val points = str.split(';').mapNotNull { parseGeoPointString(it) }
      return if (points.isNotEmpty()) GeoShape(points = points) else null
    }
  }
}
