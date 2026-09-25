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
package org.groundplatform.v2.core.forms.serialization

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.Instant
import com.squareup.wire.ofEpochSecond
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldDefinition
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.FormDef
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
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import org.groundplatform.v2.core.forms.serialization.xml.XmlElement
import org.groundplatform.v2.core.forms.serialization.xml.XmlParser
import org.groundplatform.v2.core.forms.serialization.xml.XmlText
import org.groundplatform.v2.core.forms.serialization.xml.XmlWriter

/**
 * Serializes and deserializes XForms submission instance XML (`<data id="...">...</data>`) to and
 * from `groundplatform.v2.forms.RecordInstance`.
 */
internal object RecordInstanceXmlSerializer {

  private data class CivilDate(val year: Int, val month: Int, val day: Int)

  private data class ParsedTimeAndOffset(val timePart: String, val offsetSeconds: Long)

  private val METADATA_TAG_NAMES =
    setOf(
      "meta",
      "orx:meta",
      "instanceID",
      "orx:instanceID",
      "timeStart",
      "timeEnd",
      "start",
      "end",
      "today",
      "deviceID",
      "deviceid",
      "subscriberID",
      "subscriberid",
      "simSerial",
      "simserial",
      "phoneNumber",
      "phonenumber",
      "audit",
    )

  /**
   * Deserializes an XForms submission XML string into a [RecordInstance].
   *
   * @param xml The XML string representing the submission instance.
   * @param schema Optional [RecordSchema] to guide exact field typing (e.g. single-item repeats,
   *   multi-selects, string vs numeric).
   * @param formDef Optional [FormDef] from which schema and bindings can be resolved if [schema] is
   *   not explicitly passed.
   */
  fun deserialize(
    xml: String,
    schema: RecordSchema? = null,
    formDef: FormDef? = null,
  ): RecordInstance {
    val root = XmlParser.parse(xml)
    return deserializeFromElement(
      root,
      schema ?: formDef?.model?.primary_instance?.record_schema,
      formDef,
    )
  }

  /**
   * Deserializes an [XmlElement] representing the root `<data>` element into a [RecordInstance].
   */
  fun deserializeFromElement(
    root: XmlElement,
    schema: RecordSchema? = null,
    formDef: FormDef? = null,
  ): RecordInstance {
    val formId = root.attributes["id"] ?: formDef?.form_id ?: schema?.name ?: ""
    val formVersion = root.attributes["version"] ?: formDef?.version ?: ""

    // Extract metadata from <meta> child and/or top-level preload nodes
    val metaElement = root.childElements.firstOrNull { it.localName == "meta" }

    val instanceId = findMetaText(metaElement, root, "instanceID")
    val startTimeStr =
      findMetaText(metaElement, root, "timeStart").ifEmpty {
        findMetaText(metaElement, root, "start")
      }
    val endTimeStr =
      findMetaText(metaElement, root, "timeEnd").ifEmpty { findMetaText(metaElement, root, "end") }
    val todayStr = findMetaText(metaElement, root, "today")
    val deviceId =
      findMetaText(metaElement, root, "deviceID").ifEmpty {
        findMetaText(metaElement, root, "deviceid")
      }
    val subscriberId =
      findMetaText(metaElement, root, "subscriberID").ifEmpty {
        findMetaText(metaElement, root, "subscriberid")
      }
    val simSerial =
      findMetaText(metaElement, root, "simSerial").ifEmpty {
        findMetaText(metaElement, root, "simserial")
      }
    val phoneNumber =
      findMetaText(metaElement, root, "phoneNumber").ifEmpty {
        findMetaText(metaElement, root, "phonenumber")
      }
    val auditUri = findMetaText(metaElement, root, "audit")

    val metadata =
      if (
        instanceId.isNotEmpty() ||
          startTimeStr.isNotEmpty() ||
          endTimeStr.isNotEmpty() ||
          todayStr.isNotEmpty() ||
          deviceId.isNotEmpty() ||
          subscriberId.isNotEmpty() ||
          simSerial.isNotEmpty() ||
          phoneNumber.isNotEmpty() ||
          auditUri.isNotEmpty()
      ) {
        RecordMetadata(
          instance_id = instanceId,
          start_time = startTimeStr.takeIf { it.isNotEmpty() }?.let { parseIsoInstant(it) },
          end_time = endTimeStr.takeIf { it.isNotEmpty() }?.let { parseIsoInstant(it) },
          today = todayStr.takeIf { it.isNotEmpty() }?.let { parseIsoInstantOrDate(it) },
          device_id = deviceId,
          subscriber_id = subscriberId,
          sim_serial = simSerial,
          phone_number = phoneNumber,
          audit_file_uri = auditUri,
        )
      } else {
        null
      }

    // Build field definitions map from schema and bindings
    val schemaFieldsByName = schema?.fields?.associateBy { it.name } ?: emptyMap()
    val bindingTypeByPath = buildMap {
      formDef?.model?.bindings?.forEach { binding ->
        if (binding.type != DataType.DATA_TYPE_UNSPECIFIED) {
          put(binding.field_path, binding.type)
          put(binding.field_path.substringAfterLast('/'), binding.type)
        }
      }
    }

    val rootPath = "/${root.name}"
    val dataNode =
      parseRecordNode(
        parentElement = root,
        currentPath = rootPath,
        fieldDefs = schemaFieldsByName,
        bindingTypes = bindingTypeByPath,
        isRoot = true,
      )

    return RecordInstance(
      form_id = formId,
      form_version = formVersion,
      metadata = metadata,
      data_ = dataNode,
    )
  }

  private fun findMetaText(metaElement: XmlElement?, root: XmlElement, localName: String): String {
    val metaMatch =
      metaElement?.childElements?.firstOrNull { it.localName.equals(localName, ignoreCase = true) }
    val rootMatch =
      root.childElements.firstOrNull { it.localName.equals(localName, ignoreCase = true) }
    return (metaMatch ?: rootMatch)?.textContent?.trim() ?: ""
  }

  private fun parseRecordNode(
    parentElement: XmlElement,
    currentPath: String,
    fieldDefs: Map<String, FieldDefinition>,
    bindingTypes: Map<String, DataType>,
    isRoot: Boolean,
  ): RecordNode {
    val childElements =
      parentElement.childElements.filter { child ->
        !isRoot || (child.name !in METADATA_TAG_NAMES && child.localName !in METADATA_TAG_NAMES)
      }

    val groupedChildren = childElements.groupBy { it.name }

    val fieldsMap = groupedChildren.mapValues { (fieldName, elements) ->
      val childPath = "$currentPath/$fieldName"
      val fieldDef = fieldDefs[fieldName]
      val childFieldDefs = fieldDef?.fields?.associateBy { it.name } ?: emptyMap()
      val explicitType =
        fieldDef?.type?.takeIf { it != DataType.DATA_TYPE_UNSPECIFIED }
          ?: bindingTypes[childPath]
          ?: bindingTypes[fieldName]

      val isRepeatGroup =
        elements.size > 1 ||
          (fieldDef?.is_repeated == true &&
            (fieldDef.type == DataType.TYPE_MESSAGE || fieldDef.fields.isNotEmpty())) ||
          elements.any { it.attributes.containsKey("jr:template") }

      if (isRepeatGroup) {
        val repeatNodes = elements.map { elem ->
          parseRecordNode(
            parentElement = elem,
            currentPath = childPath,
            fieldDefs = childFieldDefs,
            bindingTypes = bindingTypes,
            isRoot = false,
          )
        }
        FieldValue(repeat_value = RecordNodeList(nodes = repeatNodes))
      } else {
        val singleElem = elements.first()
        val hasElementChildren = singleElem.children.any { it is XmlElement }
        val isGroup =
          hasElementChildren ||
            fieldDef?.type == DataType.TYPE_MESSAGE ||
            fieldDef?.fields?.isNotEmpty() == true

        if (isGroup) {
          val nestedNode =
            parseRecordNode(
              parentElement = singleElem,
              currentPath = childPath,
              fieldDefs = childFieldDefs,
              bindingTypes = bindingTypes,
              isRoot = false,
            )
          FieldValue(node_value = nestedNode)
        } else {
          val text = singleElem.textContent.trim()
          val isRepeatedScalar =
            fieldDef?.is_repeated == true || explicitType == DataType.TYPE_SELECT_MULTIPLE
          if (isRepeatedScalar) {
            val items =
              if (text.isEmpty()) {
                emptyList()
              } else {
                text.split(WHITESPACE_REGEX).map { token -> parseScalarValue(token, explicitType) }
              }
            FieldValue(list_value = TypedValueList(values = items))
          } else {
            FieldValue(scalar_value = parseScalarValue(text, explicitType))
          }
        }
      }
    }

    return RecordNode(fields = fieldsMap)
  }

  private fun parseScalarValue(text: String, explicitType: DataType?): TypedValue {
    if (explicitType != null) {
      return when (explicitType) {
        DataType.TYPE_STRING,
        DataType.TYPE_SELECT_ONE -> TypedValue(string_value = text)
        DataType.TYPE_INT32 -> TypedValue(int32_value = text.toIntOrNull() ?: 0)
        DataType.TYPE_INT64 -> TypedValue(int64_value = text.toLongOrNull() ?: 0L)
        DataType.TYPE_DOUBLE -> TypedValue(double_value = text.toDoubleOrNull() ?: 0.0)
        DataType.TYPE_BOOLEAN -> TypedValue(bool_value = parseBoolean(text))
        DataType.TYPE_DATE -> TypedValue(date_value = parseDate(text))
        DataType.TYPE_TIME -> TypedValue(time_value = parseTimeOfDay(text))
        DataType.TYPE_DATETIME -> TypedValue(timestamp_value = parseIsoInstant(text))
        DataType.TYPE_GEOPOINT -> TypedValue(geopoint_value = parseGeoPoint(text))
        DataType.TYPE_GEOTRACE -> TypedValue(geotrace_value = parseGeoTrace(text))
        DataType.TYPE_GEOSHAPE -> TypedValue(geoshape_value = parseGeoShape(text))
        DataType.TYPE_BINARY -> parseBinaryValue(text)
        else -> TypedValue(string_value = text)
      }
    }

    // Heuristic inference when no explicit schema DataType is provided
    if (text.isEmpty()) {
      return TypedValue(string_value = "")
    }

    // Check for GeoShape / GeoTrace (semicolon-separated coordinate list)
    if (text.contains(';')) {
      val points =
        text
          .split(';')
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .mapNotNull { tryParseGeoPoint(it) }
      if (points.size >= 2 && points.size == text.split(';').count { it.isNotBlank() }) {
        val first = points.first()
        val last = points.last()
        return if (
          points.size >= 3 && first.latitude == last.latitude && first.longitude == last.longitude
        ) {
          TypedValue(geoshape_value = GeoShape(points = points))
        } else {
          TypedValue(geotrace_value = GeoTrace(points = points))
        }
      }
    }

    // Check for single GeoPoint ("lat lon alt acc" or "lat lon")
    tryParseGeoPoint(text)?.let {
      val tokens = text.trim().split(WHITESPACE_REGEX)
      if (tokens.size == 4 || (tokens.size == 2 && tokens.all { t -> t.contains('.') })) {
        return TypedValue(geopoint_value = it)
      }
    }

    // Check for ISO-8601 DateTime (YYYY-MM-DDTHH:MM:SS...)
    if (ISO_INSTANT_REGEX.matches(text)) {
      return TypedValue(timestamp_value = parseIsoInstant(text))
    }

    // Check for ISO Date (YYYY-MM-DD)
    if (ISO_DATE_REGEX.matches(text)) {
      return TypedValue(date_value = parseDate(text))
    }

    // Check for ISO Time (HH:MM:SS or HH:MM:SS.sss)
    if (ISO_TIME_REGEX.matches(text)) {
      return TypedValue(time_value = parseTimeOfDay(text))
    }

    // Check for Boolean
    if (text == "true" || text == "false") {
      return TypedValue(bool_value = text.toBoolean())
    }

    // Check for Integer
    if (INT_REGEX.matches(text)) {
      val longVal = text.toLongOrNull()
      if (longVal != null) {
        return if (longVal in Int.MIN_VALUE..Int.MAX_VALUE) {
          TypedValue(int32_value = longVal.toInt())
        } else {
          TypedValue(int64_value = longVal)
        }
      }
    }

    // Check for Double
    if (DECIMAL_REGEX.matches(text)) {
      text.toDoubleOrNull()?.let {
        return TypedValue(double_value = it)
      }
    }

    return TypedValue(string_value = text)
  }

  /** Serializes a [RecordInstance] into an XForms submission XML string. */
  fun serialize(
    record: RecordInstance,
    rootElementName: String = "data",
    prettyPrint: Boolean = true,
  ): String {
    val rootElement = serializeToElement(record, rootElementName)
    return XmlWriter(prettyPrint = prettyPrint, includeDeclaration = true).writeElement(rootElement)
  }

  /** Serializes a [RecordInstance] into an [XmlElement] representing the root `<data>` element. */
  fun serializeToElement(record: RecordInstance, rootElementName: String = "data"): XmlElement {
    val attrs = buildMap {
      if (record.form_id.isNotEmpty()) {
        put("id", record.form_id)
      }
      if (record.form_version.isNotEmpty()) {
        put("version", record.form_version)
      }
    }

    val children = buildList {
      // Emit <meta> block if metadata is present
      record.metadata?.let { meta ->
        val metaChildren = buildList {
          if (meta.instance_id.isNotEmpty()) {
            add(textElement("instanceID", meta.instance_id))
          }
          meta.start_time?.let { add(textElement("timeStart", formatIsoInstant(it))) }
          meta.end_time?.let { add(textElement("timeEnd", formatIsoInstant(it))) }
          meta.today?.let { add(textElement("today", formatIsoInstant(it))) }
          if (meta.device_id.isNotEmpty()) {
            add(textElement("deviceID", meta.device_id))
          }
          if (meta.subscriber_id.isNotEmpty()) {
            add(textElement("subscriberID", meta.subscriber_id))
          }
          if (meta.sim_serial.isNotEmpty()) {
            add(textElement("simSerial", meta.sim_serial))
          }
          if (meta.phone_number.isNotEmpty()) {
            add(textElement("phoneNumber", meta.phone_number))
          }
          if (meta.audit_file_uri.isNotEmpty()) {
            add(textElement("audit", meta.audit_file_uri))
          }
        }
        if (metaChildren.isNotEmpty()) {
          add(XmlElement(name = "meta", children = metaChildren))
        }
      }

      // Emit data fields from RecordNode
      record.data_?.let { rootNode -> addAll(serializeRecordNodeChildren(rootNode)) }
    }

    return XmlElement(name = rootElementName, attributes = attrs, children = children)
  }

  private fun textElement(name: String, text: String): XmlElement =
    XmlElement(name = name, children = if (text.isEmpty()) emptyList() else listOf(XmlText(text)))

  private fun serializeRecordNodeChildren(node: RecordNode): List<XmlElement> = buildList {
    for ((fieldName, fieldValue) in node.fields) {
      when {
        fieldValue.scalar_value != null -> {
          val text = formatScalarValue(fieldValue.scalar_value)
          add(textElement(fieldName, text))
        }
        fieldValue.list_value != null -> {
          val joined = fieldValue.list_value.values.joinToString(" ") { formatScalarValue(it) }
          add(textElement(fieldName, joined))
        }
        fieldValue.node_value != null -> {
          val groupChildren = serializeRecordNodeChildren(fieldValue.node_value)
          add(XmlElement(name = fieldName, children = groupChildren))
        }
        fieldValue.repeat_value != null -> {
          for (repeatItem in fieldValue.repeat_value.nodes) {
            val itemChildren = serializeRecordNodeChildren(repeatItem)
            add(XmlElement(name = fieldName, children = itemChildren))
          }
        }
      }
    }
  }

  private fun formatScalarValue(typed: TypedValue): String =
    when {
      typed.string_value != null -> typed.string_value
      typed.int32_value != null -> typed.int32_value.toString()
      typed.int64_value != null -> typed.int64_value.toString()
      typed.double_value != null -> formatDouble(typed.double_value)
      typed.bool_value != null -> typed.bool_value.toString()
      typed.date_value != null -> formatDate(typed.date_value)
      typed.time_value != null -> formatTimeOfDay(typed.time_value)
      typed.timestamp_value != null -> formatIsoInstant(typed.timestamp_value)
      typed.geopoint_value != null -> formatGeoPoint(typed.geopoint_value)
      typed.geotrace_value != null ->
        typed.geotrace_value.points.joinToString("; ") { formatGeoPoint(it) }
      typed.geoshape_value != null ->
        typed.geoshape_value.points.joinToString("; ") { formatGeoPoint(it) }
      typed.binary_value != null -> formatBinaryValue(typed.binary_value)
      else -> ""
    }

  private const val BASE64_BINARY_PREFIX = "base64:"

  /**
   * Formats a [DataType.TYPE_BINARY] value for XForms submission XML.
   *
   * Per the ODK / OpenRosa Form Submission API (used by ODK Collect, KoboToolbox, and Enketo),
   * binary fields store the attachment **filename** in the XML element (`<photo>image.jpg</photo>`)
   * while the media bytes travel as a separate `multipart/form-data` part. When [bytes] is a valid
   * printable UTF-8 string (and does not itself start with [BASE64_BINARY_PREFIX]), it is emitted
   * verbatim as the attachment filename. When [bytes] holds raw binary data (non-UTF-8 sequences or
   * XML-invalid control bytes), calling `ByteString.utf8()` would silently corrupt non-UTF-8 bytes
   * into `U+FFFD`; we instead emit a `base64:`-prefixed payload so standalone XML serialization
   * still round-trips byte-for-byte.
   */
  private fun formatBinaryValue(bytes: ByteString): String {
    if (bytes.size == 0) return ""
    val decoded = bytes.utf8()
    val roundTripsAsPrintableUtf8 =
      !decoded.startsWith(BASE64_BINARY_PREFIX) &&
        !decoded.contains('\uFFFD') &&
        decoded.encodeUtf8() == bytes &&
        decoded.all { ch -> ch >= ' ' || ch == '\t' || ch == '\n' || ch == '\r' }
    return if (roundTripsAsPrintableUtf8) {
      decoded
    } else {
      BASE64_BINARY_PREFIX + bytes.base64()
    }
  }

  private fun parseBinaryValue(text: String): TypedValue {
    if (text.startsWith(BASE64_BINARY_PREFIX)) {
      val payload = text.removePrefix(BASE64_BINARY_PREFIX).decodeBase64()
      if (payload != null) {
        return TypedValue(binary_value = payload)
      }
    }
    return TypedValue(binary_value = text.encodeUtf8())
  }

  // --- Geospatial Formatting & Parsing ---

  private fun formatGeoPoint(pt: GeoPoint): String =
    "${formatDouble(pt.latitude)} ${formatDouble(pt.longitude)} ${formatDouble(pt.altitude_meters)} ${formatDouble(pt.accuracy_meters)}"

  private fun parseGeoPoint(text: String): GeoPoint = tryParseGeoPoint(text) ?: GeoPoint()

  private fun tryParseGeoPoint(text: String): GeoPoint? {
    val parts = text.trim().split(WHITESPACE_REGEX)
    if (parts.size < 2 || parts.size > 4) {
      return null
    }
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null
    if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
      return null
    }
    val alt = if (parts.size >= 3) parts[2].toDoubleOrNull() ?: 0.0 else 0.0
    val acc = if (parts.size >= 4) parts[3].toDoubleOrNull() ?: 0.0 else 0.0
    return GeoPoint(latitude = lat, longitude = lon, altitude_meters = alt, accuracy_meters = acc)
  }

  private fun parseGeoTrace(text: String): GeoTrace {
    val points =
      text
        .split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { tryParseGeoPoint(it) }
    return GeoTrace(points = points)
  }

  private fun parseGeoShape(text: String): GeoShape {
    val points =
      text
        .split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { tryParseGeoPoint(it) }
    return GeoShape(points = points)
  }

  private fun formatDouble(value: Double): String {
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
  }

  private fun parseBoolean(text: String): Boolean =
    when (text.trim().lowercase()) {
      "true",
      "1",
      "yes" -> true
      else -> false
    }

  // --- Date / Time / Instant Pure-Kotlin Formatting & Parsing ---

  private fun formatDate(date: Date): String {
    val y = date.year.toString().padStart(4, '0')
    val m = date.month.toString().padStart(2, '0')
    val d = date.day.toString().padStart(2, '0')
    return "$y-$m-$d"
  }

  private fun parseDate(text: String): Date {
    val parts = text.trim().split('-')
    if (parts.size < 3) {
      return Date()
    }
    return Date(
      year = parts[0].toIntOrNull() ?: 0,
      month = parts[1].toIntOrNull() ?: 0,
      day = parts[2].take(2).toIntOrNull() ?: 0,
    )
  }

  private fun formatTimeOfDay(time: TimeOfDay): String {
    val h = time.hours.toString().padStart(2, '0')
    val m = time.minutes.toString().padStart(2, '0')
    val s = time.seconds.toString().padStart(2, '0')
    return if (time.nanos > 0) {
      val millis = (time.nanos / 1_000_000).toString().padStart(3, '0')
      "$h:$m:$s.$millis"
    } else {
      "$h:$m:$s"
    }
  }

  private fun parseTimeOfDay(text: String): TimeOfDay {
    val clean = text.trim().substringBefore('Z').substringBefore('+').substringBefore('-')
    val parts = clean.split(':')
    if (parts.size < 2) {
      return TimeOfDay()
    }
    val hours = parts[0].toIntOrNull() ?: 0
    val minutes = parts[1].toIntOrNull() ?: 0
    val secParts = parts.getOrNull(2)?.split('.') ?: emptyList()
    val seconds = secParts.getOrNull(0)?.toIntOrNull() ?: 0
    val nanos = secParts.getOrNull(1)?.padEnd(9, '0')?.take(9)?.toIntOrNull() ?: 0
    return TimeOfDay(hours = hours, minutes = minutes, seconds = seconds, nanos = nanos)
  }

  internal fun formatIsoInstant(instant: Instant): String {
    val epochSec = instant.getEpochSecond()
    val nano = instant.getNano()

    val days = epochSec.floorDiv(86400L)
    val remSec = epochSec.mod(86400L)
    val hour = (remSec / 3600L).toInt()
    val minute = ((remSec % 3600L) / 60L).toInt()
    val second = (remSec % 60L).toInt()

    val civil = civilFromDays(days)
    val yearStr = civil.year.toString().padStart(4, '0')
    val monthStr = civil.month.toString().padStart(2, '0')
    val dayStr = civil.day.toString().padStart(2, '0')
    val hourStr = hour.toString().padStart(2, '0')
    val minStr = minute.toString().padStart(2, '0')
    val secStr = second.toString().padStart(2, '0')

    val millis = nano / 1_000_000
    val fracStr = if (millis > 0) ".${millis.toString().padStart(3, '0')}" else ".000"
    return "$yearStr-$monthStr-${dayStr}T$hourStr:$minStr:$secStr${fracStr}Z"
  }

  private fun civilFromDays(days: Long): CivilDate {
    // Civil date from days since 1970-01-01 (Howard Hinnant algorithm)
    val z = days + 719468L
    val era = (if (z >= 0L) z else z - 146096L) / 146097L
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
    val y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = (doy - (153L * mp + 2L) / 5L + 1L).toInt()
    val m = (mp + (if (mp < 10L) 3L else -9L)).toInt()
    val year = (y + (if (m <= 2) 1L else 0L)).toInt()
    return CivilDate(year, m, d)
  }

  internal fun parseIsoInstant(text: String): Instant {
    val clean = text.trim()
    val tIndex = clean.indexOf('T').takeIf { it >= 0 } ?: clean.indexOf(' ')
    if (tIndex < 0) {
      return parseIsoInstantOrDate(clean)
    }
    val datePart = clean.substring(0, tIndex)
    val parsedTimeAndOffset = extractTimeAndOffset(clean.substring(tIndex + 1))

    val date = parseDate(datePart)
    val time = parseTimeOfDay(parsedTimeAndOffset.timePart)
    val days = daysFromCivil(date.year, date.month, date.day)
    val totalSeconds =
      days * 86400L + time.hours * 3600L + time.minutes * 60L + time.seconds -
        parsedTimeAndOffset.offsetSeconds
    return ofEpochSecond(totalSeconds, time.nanos.toLong())
  }

  private fun extractTimeAndOffset(rawTimeAndZone: String): ParsedTimeAndOffset {
    if (rawTimeAndZone.endsWith("Z", ignoreCase = true)) {
      return ParsedTimeAndOffset(rawTimeAndZone.dropLast(1), 0L)
    }
    val plusIdx = rawTimeAndZone.indexOf('+')
    val minusIdx = rawTimeAndZone.lastIndexOf('-')
    val signIdx = maxOf(plusIdx, minusIdx)
    if (signIdx > 0) {
      val sign = if (rawTimeAndZone[signIdx] == '+') 1 else -1
      val offsetStr = rawTimeAndZone.substring(signIdx + 1)
      val offsetParts = offsetStr.split(':')
      val offHours = offsetParts.getOrNull(0)?.toLongOrNull() ?: 0L
      val offMins = offsetParts.getOrNull(1)?.toLongOrNull() ?: 0L
      val offsetSeconds = sign * (offHours * 3600L + offMins * 60L)
      return ParsedTimeAndOffset(rawTimeAndZone.substring(0, signIdx), offsetSeconds)
    }
    return ParsedTimeAndOffset(rawTimeAndZone, 0L)
  }

  private fun parseIsoInstantOrDate(text: String): Instant {
    if (text.contains('T') || text.contains(' ')) {
      return parseIsoInstant(text)
    }
    val date = parseDate(text)
    val days = daysFromCivil(date.year, date.month, date.day)
    return ofEpochSecond(days * 86400L, 0L)
  }

  private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = year.toLong() - (if (month <= 2) 1L else 0L)
    val era = (if (y >= 0L) y else y - 399L) / 400L
    val yoe = y - era * 400L
    val m = month.toLong()
    val doy = (153L * (m + (if (m > 2L) -3L else 9L)) + 2L) / 5L + day - 1L
    val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
    return era * 146097L + doe - 719468L
  }

  private val WHITESPACE_REGEX = Regex("\\s+")
  private val ISO_INSTANT_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}.*$")
  private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
  private val ISO_TIME_REGEX = Regex("^\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?$")
  private val INT_REGEX = Regex("^-?\\d+$")
  private val DECIMAL_REGEX = Regex("^-?\\d+\\.\\d+$")
}
