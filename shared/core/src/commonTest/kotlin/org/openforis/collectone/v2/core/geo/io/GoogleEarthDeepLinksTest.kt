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
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.openforis.collectone.v2.core.ids.RecordIds

class GoogleEarthDeepLinksTest {
  @Test
  fun openKmlUrlEncodesExactCommandsProtoWireFormat() {
    val kmlUrl = "https://example.org/balloon/demo-survey.kml"
    val earthUrl = GoogleEarthDeepLinks.openKmlUrl(kmlUrl)
    assertTrue(earthUrl.startsWith("https://earth.google.com/web/c/"))
    assertFalse("=" in earthUrl.removePrefix("https://earth.google.com/web/c/"))

    val encodedPayload = earthUrl.removePrefix("https://earth.google.com/web/c/")
    val decoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(encodedPayload)
    val uriBytes = kmlUrl.encodeToByteArray()

    // Commands { field 1 (0x0a) -> Command { field 12 (0x62) -> OpenKmlDocument { field 1 (0x0a) ->
    // uri } } }
    val expected =
      byteArrayOf(
        0x0A,
        (uriBytes.size + 4).toByte(),
        0x62,
        (uriBytes.size + 2).toByte(),
        0x0A,
        uriBytes.size.toByte(),
      ) + uriBytes
    assertContentEquals(expected, decoded)
  }

  @Test
  fun openKmlUrlHandlesMultiByteVarintLengthsForLongAuthenticatedUrls() {
    val longKmlUrl =
      "https://staging---api.example.org/kml/surveys/00000033-6b50-51cd-ac94-baea2ea06d65.kml?token=" +
        "a".repeat(300)
    val earthUrl = GoogleEarthDeepLinks.openKmlUrl(longKmlUrl)
    val encodedPayload = earthUrl.removePrefix("https://earth.google.com/web/c/")
    val decoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(encodedPayload)

    // Outer tag must still be 0x0A and trailing bytes must match the exact UTF-8 KML URL.
    assertEquals(0x0A.toByte(), decoded[0])
    val uriBytes = longKmlUrl.encodeToByteArray()
    assertContentEquals(uriBytes, decoded.copyOfRange(decoded.size - uriBytes.size, decoded.size))
  }

  @Test
  fun deterministicRecordIdsProduceCanonicalUuidsWhilePreservingExistingUuids() {
    val existingUuid = "0000000a-56d7-5c1d-9457-7457267120a3"
    assertTrue(RecordIds.isUuid(existingUuid))
    assertFalse(RecordIds.isUuid("P001"))
    assertEquals(existingUuid, RecordIds.uuidOrDeterministic(existingUuid, "survey:plots"))

    val id1 = RecordIds.uuidOrDeterministic("P001", "survey-1:sample_plots")
    val id2 = RecordIds.uuidOrDeterministic("P001", "survey-1:sample_plots")
    val idOtherPlot = RecordIds.uuidOrDeterministic("P002", "survey-1:sample_plots")
    assertTrue(RecordIds.isUuid(id1), id1)
    assertEquals(id1, id2)
    assertFalse(id1 == idOtherPlot)
  }
}
