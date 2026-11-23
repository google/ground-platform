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
@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package org.openforis.collectone.v2.core.geo.io

import kotlin.io.encoding.Base64

/**
 * Builds Google Earth Web deep-link URLs (`https://earth.google.com/web/c/<commands>`) that open an
 * external HTTPS KML document directly in Google Earth on the web.
 *
 * Google Earth Web encodes URL-launched commands as an unpadded URL-safe Base64 protobuf
 * serialization of `geo.earth.proto.Commands`:
 * - `Commands.commands` (field 1, length-delimited): repeated `Command`
 * - `Command.open_kml_document` (field 12, length-delimited): `OpenKmlDocument`
 * - `OpenKmlDocument.uri` (field 1, length-delimited): UTF-8 HTTPS KML URL
 */
object GoogleEarthDeepLinks {
  const val EARTH_WEB_COMMAND_URL_PREFIX = "https://earth.google.com/web/c/"

  private const val COMMANDS_FIELD_1_TAG: Byte = 0x0A
  private const val OPEN_KML_DOCUMENT_FIELD_12_TAG: Byte = 0x62
  private const val URI_FIELD_1_TAG: Byte = 0x0A

  /**
   * Returns a `https://earth.google.com/web/c/<payload>` URL that instructs Google Earth Web to
   * fetch and open [kmlUrl] via `OpenKmlDocument`.
   */
  fun openKmlUrl(kmlUrl: String): String {
    val trimmed = kmlUrl.trim()
    require(trimmed.isNotEmpty()) { "kmlUrl must not be blank" }
    val protoBytes = encodeOpenKmlCommandsProto(trimmed)
    val encoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(protoBytes)
    return "$EARTH_WEB_COMMAND_URL_PREFIX$encoded"
  }

  /**
   * Encodes `Commands(commands = [Command(open_kml_document = OpenKmlDocument(uri = kmlUrl))])`.
   */
  internal fun encodeOpenKmlCommandsProto(kmlUrl: String): ByteArray {
    val uriBytes = kmlUrl.encodeToByteArray()
    val openKmlBytes = lengthDelimitedField(URI_FIELD_1_TAG, uriBytes)
    val commandBytes = lengthDelimitedField(OPEN_KML_DOCUMENT_FIELD_12_TAG, openKmlBytes)
    return lengthDelimitedField(COMMANDS_FIELD_1_TAG, commandBytes)
  }

  /** RFC 3986 percent-encoding of a URI query parameter or path segment. */
  fun encodeUriComponent(value: String): String {
    val bytes = value.encodeToByteArray()
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
      val c = b.toInt() and 0xFF
      val unreserved =
        c in 'A'.code..'Z'.code ||
          c in 'a'.code..'z'.code ||
          c in '0'.code..'9'.code ||
          c == '-'.code ||
          c == '_'.code ||
          c == '.'.code ||
          c == '~'.code
      if (unreserved) {
        sb.append(c.toChar())
      } else {
        sb.append('%')
        sb.append(HEX[c ushr 4])
        sb.append(HEX[c and 0x0F])
      }
    }
    return sb.toString()
  }

  private fun lengthDelimitedField(tag: Byte, payload: ByteArray): ByteArray {
    val lengthVarint = encodeVarint(payload.size)
    val out = ByteArray(1 + lengthVarint.size + payload.size)
    out[0] = tag
    lengthVarint.copyInto(out, destinationOffset = 1)
    payload.copyInto(out, destinationOffset = 1 + lengthVarint.size)
    return out
  }

  private fun encodeVarint(value: Int): ByteArray {
    require(value >= 0) { "Negative length varint: $value" }
    var remaining = value
    val buf = ByteArray(5)
    var count = 0
    do {
      var bits = remaining and 0x7F
      remaining = remaining ushr 7
      if (remaining != 0) bits = bits or 0x80
      buf[count++] = bits.toByte()
    } while (remaining != 0)
    return buf.copyOf(count)
  }

  private val HEX = "0123456789ABCDEF".toCharArray()
}
