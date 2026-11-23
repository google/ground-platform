/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.client.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the report colors so moving them onto theme tokens can't change a generated PDF. */
class PdfPaletteTest {
  @Test fun primary() = assertEquals(PdfColor.fromArgb(0xFF36693E), PdfPalette.primary)

  @Test fun onSurface() = assertEquals(PdfColor.fromArgb(0xFF181D18), PdfPalette.onSurface)

  @Test
  fun onSurfaceVariant() = assertEquals(PdfColor.fromArgb(0xFF424940), PdfPalette.onSurfaceVariant)

  @Test fun outline() = assertEquals(PdfColor.fromArgb(0xFF727970), PdfPalette.outline)

  @Test
  fun outlineVariant() = assertEquals(PdfColor.fromArgb(0xFFC1C9BE), PdfPalette.outlineVariant)

  @Test
  fun surfaceContainer() = assertEquals(PdfColor.fromArgb(0xFFEBEFE7), PdfPalette.surfaceContainer)

  @Test fun error() = assertEquals(PdfColor.fromArgb(0xFFBA1A1A), PdfPalette.error)

  @Test fun tertiary() = assertEquals(PdfColor.fromArgb(0xFF39656C), PdfPalette.tertiary)
}
