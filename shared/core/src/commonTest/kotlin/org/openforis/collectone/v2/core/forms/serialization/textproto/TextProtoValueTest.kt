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
package org.openforis.collectone.v2.core.forms.serialization.textproto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.ByteString.Companion.encodeUtf8
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoValue.BytesVal
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoValue.IdentifierVal
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoValue.ListVal
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoValue.NumberVal
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoValue.StringVal

/** The schema-free textproto values coerced for the field being read. */
class TextProtoValueTest {

  @Test
  fun numbers_acceptQuotedLiteralsButNotIdentifiers() {
    assertEquals(42, NumberVal("42").asIntOrNull())
    assertEquals(42, StringVal("42").asIntOrNull())
    assertNull(IdentifierVal("forty_two").asIntOrNull())
    assertEquals(9_000_000_000L, NumberVal("9000000000").asLongOrNull())
    assertNull(NumberVal("9000000000").asIntOrNull())
  }

  @Test
  fun doubles_acceptTextFormatSpecialValues() {
    assertTrue(IdentifierVal("NaN").asDoubleOrNull()!!.isNaN())
    assertEquals(Double.POSITIVE_INFINITY, IdentifierVal("inf").asDoubleOrNull())
    assertEquals(Double.NEGATIVE_INFINITY, IdentifierVal("-Infinity").asDoubleOrNull())
    assertEquals(1.5, StringVal("1.5").asDoubleOrNull())
    assertNull(ListVal(emptyList()).asDoubleOrNull())
  }

  @Test
  fun booleans_acceptTextFormatSpellings() {
    assertEquals(true, IdentifierVal("t").asBooleanOrNull())
    assertEquals(false, IdentifierVal("False").asBooleanOrNull())
    assertEquals(true, NumberVal("1").asBooleanOrNull())
    assertNull(NumberVal("2").asBooleanOrNull())
    assertEquals(true, StringVal("true").asBooleanOrNull())
    assertNull(StringVal("t").asBooleanOrNull())
  }

  @Test
  fun stringsAndIdentifiers_readEachOther() {
    assertEquals("ENUM_VALUE", StringVal("ENUM_VALUE").asIdentifierOrNull())
    assertEquals("ENUM_VALUE", IdentifierVal("ENUM_VALUE").asStringOrNull())
    assertEquals("12", NumberVal("12").asStringOrNull())
    assertNull(NumberVal("12").asIdentifierOrNull())
  }

  @Test
  fun bytes_comeFromBytesOrLatin1Strings() {
    assertEquals("hi".encodeUtf8(), BytesVal("hi".encodeUtf8()).asByteStringOrNull())
    // The parser decodes `\xff` to the character U+00FF, which maps back to the byte 0xFF.
    assertEquals(0xFF.toByte(), StringVal("\u00ff").asByteStringOrNull()!![0])
    assertNull(NumberVal("1").asByteStringOrNull())
    assertNull(StringVal("x").asMessageOrNull())
  }
}
