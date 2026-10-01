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
package org.groundplatform.v2.devtools.prototypeapp.map

/**
 * The Ground map pin: a white-rimmed squircle filled with the feature's marker color, showing its
 * marker symbol, with its tip on the location.
 */
object GroundPin {
  const val WIDTH = 22
  const val HEIGHT = 24

  /** Tip of the pin in viewBox units; the shadow below it extends to the bottom edge. */
  const val TIP_Y = 20.6667

  /** Stable id for the pin image of a (color, symbol, pending) combination. */
  fun iconId(color: String, symbol: String, isPending: Boolean): String =
    "pin|$color|$symbol|${if (isPending) 1 else 0}"

  /**
   * Renders the pin SVG. Pending (unsynced) features get a lighter fill with a dashed edge;
   * features without a marker symbol keep the pin's small white square.
   */
  fun svg(color: String, symbol: String, isPending: Boolean): String {
    val safeColor = escapeXml(color)
    val mask =
      if (isPending) {
        """<path d="$MASK_PATH" fill="$safeColor" fill-opacity="0.55" stroke="$safeColor" """ +
          """stroke-width="1.2" stroke-dasharray="2 1.3"></path>"""
      } else {
        """<path d="$MASK_PATH" fill="$safeColor"></path>"""
      }
    val glyph =
      if (symbol.isNotEmpty()) {
        val fontSize = if (codePointCount(symbol) > 1) "6.5" else "9.5"
        """<text x="8" y="8.4" fill="#FFFFFF" font-family="Roboto, Arial, sans-serif" """ +
          """font-size="$fontSize" font-weight="700" text-anchor="middle" """ +
          """dominant-baseline="central">${escapeXml(symbol)}</text>"""
      } else {
        """<rect fill="#FFFFFF" x="6" y="6" width="4" height="4" rx="0.666666667"></rect>"""
      }
    return """<svg width="22px" height="24px" viewBox="0 0 22 24" version="1.1" """ +
      """xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">""" +
      "<defs>" +
      """<filter x="-37.5%" y="-60.0%" width="175.0%" height="220.0%" """ +
      """filterUnits="objectBoundingBox" id="filter-1">""" +
      """<feGaussianBlur stdDeviation="0.666666667" in="SourceGraphic"></feGaussianBlur>""" +
      "</filter>" +
      """<path d="$SQUIRCLE_PATH" id="path-2"></path>""" +
      """<filter x="-31.3%" y="-21.4%" width="162.5%" height="153.6%" """ +
      """filterUnits="objectBoundingBox" id="filter-3">""" +
      """<feOffset dx="0" dy="1" in="SourceAlpha" result="shadowOffsetOuter1"></feOffset>""" +
      """<feGaussianBlur stdDeviation="1.5" in="shadowOffsetOuter1" """ +
      """result="shadowBlurOuter1"></feGaussianBlur>""" +
      """<feColorMatrix values="0 0 0 0 0   0 0 0 0 0   0 0 0 0 0  0 0 0 0.24 0" """ +
      """type="matrix" in="shadowBlurOuter1"></feColorMatrix>""" +
      "</filter>" +
      "</defs>" +
      """<g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">""" +
      """<g transform="translate(3.000000, 2.000000)">""" +
      """<path d="M8,20 C6.52724067,20 5.33333333,19.4403559 5.33333333,18.75 """ +
      "C5.33333333,18.0596441 6.52724067,16.6666667 8,16.6666667 " +
      "C9.47275933,16.6666667 10.6666667,18.0596441 10.6666667,18.75 " +
      """C10.6666667,19.4403559 9.47275933,20 8,20 Z" fill="#000000" opacity="0.16" """ +
      """filter="url(#filter-1)"></path>""" +
      """<use fill="black" fill-opacity="1" filter="url(#filter-3)" xlink:href="#path-2"></use>""" +
      """<use fill="#FFFFFF" fill-rule="evenodd" xlink:href="#path-2"></use>""" +
      mask +
      glyph +
      "</g>" +
      "</g>" +
      "</svg>"
  }

  /** Counts Unicode code points, so an emoji or a single non-BMP symbol counts as one. */
  private fun codePointCount(s: String): Int {
    var count = 0
    var i = 0
    while (i < s.length) {
      i += if (s[i].isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) 2 else 1
      count++
    }
    return count
  }

  private fun escapeXml(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  private const val SQUIRCLE_PATH =
    "M8.57557949,0 C10.2564103,0 12.1025641,0.204700481 13.1282051,0.717520994 C14.1538462,1.23034151 14.974359,2.15384615 15.3846154,3.07649535 C15.7948718,3.99914455 16,5.74316202 16,7.42442051 L16,8.57557949 C16,10.256838 15.7948718,12.0008554 15.3846154,12.9235046 C14.974359,13.8461538 14.1538462,14.7696585 13.1282051,15.282479 C12.8174048,15.4378792 12.431259,15.5649855 11.9971999,15.6666757 C10.6600393,15.9662272 9.8851064,16.237264 9.38085937,16.6148478 C8.87661235,16.9924316 8.60695903,17.233195 8.41038513,17.7877197 C8.21381124,18.3422445 8.32758077,18.6666667 8.00067647,18.6666667 C7.6730957,18.6666667 7.78686523,18.3422445 7.59029134,17.7877197 C7.39371745,17.233195 7.12406413,16.9924316 6.6198171,16.6148478 C6.11557007,16.237264 5.34063721,15.9662272 4,15.6666667 L4.29942232,15.7308257 C3.74434979,15.6201602 3.25176514,15.4724641 2.87179487,15.282479 C1.84615385,14.7696585 1.02564103,13.8461538 0.615384615,12.9235046 C0.205128205,12.0008554 0,10.256838 0,8.57557949 L0,7.42442051 C0,5.74316202 0.205128205,3.99914455 0.615384615,3.07649535 C1.02564103,2.15384615 1.84615385,1.23034151 2.87179487,0.717520994 C3.8974359,0.204700481 5.74358974,0 7.42442051,0 L8.57557949,0 Z"

  private const val MASK_PATH =
    "M1,8.50363205 L1,7.49636795 C1,6.02526677 1.17948718,4.49925148 1.53846154,3.69193343 C1.8974359,2.88461538 2.61538462,2.07654882 3.51282051,1.62783087 C4.41025641,1.17911292 6.02564103,1 7.49636795,1 L8.50363205,1 C9.97435897,1 11.5897436,1.17911292 12.4871795,1.62783087 C13.3846154,2.07654882 14.1025641,2.88461538 14.4615385,3.69193343 C14.8205128,4.49925148 15,6.02526677 15,7.49636795 L15,8.50363205 C15,9.97473323 14.8205128,11.5007485 14.4615385,12.3080666 C14.1025641,13.1153846 13.3846154,13.9234512 12.4871795,14.3721691 C11.5897436,14.8208871 9.97435897,15 8.50363205,15 L7.49636795,15 C6.02564103,15 4.41025641,14.8208871 3.51282051,14.3721691 C2.61538462,13.9234512 1.8974359,13.1153846 1.53846154,12.3080666 C1.17948718,11.5007485 1,9.97473323 1,8.50363205 Z"
}
