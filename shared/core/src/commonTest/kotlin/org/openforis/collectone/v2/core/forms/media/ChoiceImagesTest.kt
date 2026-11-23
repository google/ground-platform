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
package org.openforis.collectone.v2.core.forms.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChoiceImagesTest {

  @Test
  fun builtInIcons_areFoundByUriOrFileName() {
    val byUri = ChoiceImages.lookup("jr://images/commodity_type-coffee.png")

    assertEquals(ChoiceImageMedia("image/png", ChoiceImages.ICON_COFFEE), byUri)
    assertEquals(byUri, ChoiceImages.lookup("  commodity_type-coffee.png "))
  }

  @Test
  fun unknownOrBlankUris_findNothing() {
    assertNull(ChoiceImages.lookup(null))
    assertNull(ChoiceImages.lookup("   "))
    assertNull(ChoiceImages.lookup("jr://images/never-registered.png"))
  }

  @Test
  fun dataUris_roundTripAndDefaultBlankMimeTypeToPng() {
    val encoded = ChoiceImages.encodeDataUri(mimeType = "image/jpeg", base64 = " /9j/4AAQ ")
    assertEquals("data:image/jpeg;base64,/9j/4AAQ", encoded)
    assertEquals(ChoiceImageMedia("image/jpeg", "/9j/4AAQ"), ChoiceImages.lookup(encoded))

    val defaultMime = ChoiceImages.encodeDataUri(mimeType = " ", base64 = "AAAA")
    assertEquals(ChoiceImageMedia("image/png", "AAAA"), ChoiceImages.lookup(defaultMime))
    assertEquals(
      ChoiceImageMedia("image/png", "BBBB"),
      ChoiceImages.decodeDataUri("data:;base64,BBBB"),
    )
  }

  @Test
  fun malformedOrBlankDataUris_returnNull() {
    assertNull(ChoiceImages.decodeDataUri(null))
    assertNull(ChoiceImages.decodeDataUri("jr://images/commodity_type-coffee.png"))
    assertNull(ChoiceImages.decodeDataUri("data:image/png;utf8,hello"))
    assertNull(ChoiceImages.decodeDataUri("data:image/png;base64,   "))
  }
}
