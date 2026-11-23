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
package org.openforis.collectone.v2.app.domain.model

import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.map.LatLng

/** A country or territory with an ISO 3166-1 alpha-2 [code] (e.g., `KE`) and English [name]. */
data class Country(val code: String, val name: String) {
  /** Default single-part [SurveyArea] covering this country's bounding box. */
  val surveyArea: SurveyArea
    get() = Countries.surveyAreaFor(this)
}

/**
 * The 249 officially assigned ISO 3166-1 alpha-2 codes with short English names, sorted by name.
 *
 * Names follow common English usage (as in the Unicode CLDR) rather than the formal ISO names, so
 * they read naturally in a picker; search also matches the code.
 */
object Countries {
  val all: List<Country> =
    listOf(
      Country("AF", "Afghanistan"),
      Country("AX", "Åland Islands"),
      Country("AL", "Albania"),
      Country("DZ", "Algeria"),
      Country("AS", "American Samoa"),
      Country("AD", "Andorra"),
      Country("AO", "Angola"),
      Country("AI", "Anguilla"),
      Country("AQ", "Antarctica"),
      Country("AG", "Antigua and Barbuda"),
      Country("AR", "Argentina"),
      Country("AM", "Armenia"),
      Country("AW", "Aruba"),
      Country("AU", "Australia"),
      Country("AT", "Austria"),
      Country("AZ", "Azerbaijan"),
      Country("BS", "Bahamas"),
      Country("BH", "Bahrain"),
      Country("BD", "Bangladesh"),
      Country("BB", "Barbados"),
      Country("BY", "Belarus"),
      Country("BE", "Belgium"),
      Country("BZ", "Belize"),
      Country("BJ", "Benin"),
      Country("BM", "Bermuda"),
      Country("BT", "Bhutan"),
      Country("BO", "Bolivia"),
      Country("BA", "Bosnia and Herzegovina"),
      Country("BW", "Botswana"),
      Country("BV", "Bouvet Island"),
      Country("BR", "Brazil"),
      Country("IO", "British Indian Ocean Territory"),
      Country("VG", "British Virgin Islands"),
      Country("BN", "Brunei"),
      Country("BG", "Bulgaria"),
      Country("BF", "Burkina Faso"),
      Country("BI", "Burundi"),
      Country("CV", "Cabo Verde"),
      Country("KH", "Cambodia"),
      Country("CM", "Cameroon"),
      Country("CA", "Canada"),
      Country("BQ", "Caribbean Netherlands"),
      Country("KY", "Cayman Islands"),
      Country("CF", "Central African Republic"),
      Country("TD", "Chad"),
      Country("CL", "Chile"),
      Country("CN", "China"),
      Country("CX", "Christmas Island"),
      Country("CC", "Cocos (Keeling) Islands"),
      Country("CO", "Colombia"),
      Country("KM", "Comoros"),
      Country("CK", "Cook Islands"),
      Country("CR", "Costa Rica"),
      Country("CI", "Côte d'Ivoire"),
      Country("HR", "Croatia"),
      Country("CU", "Cuba"),
      Country("CW", "Curaçao"),
      Country("CY", "Cyprus"),
      Country("CZ", "Czechia"),
      Country("CD", "Democratic Republic of the Congo"),
      Country("DK", "Denmark"),
      Country("DJ", "Djibouti"),
      Country("DM", "Dominica"),
      Country("DO", "Dominican Republic"),
      Country("EC", "Ecuador"),
      Country("EG", "Egypt"),
      Country("SV", "El Salvador"),
      Country("GQ", "Equatorial Guinea"),
      Country("ER", "Eritrea"),
      Country("EE", "Estonia"),
      Country("SZ", "Eswatini"),
      Country("ET", "Ethiopia"),
      Country("FK", "Falkland Islands"),
      Country("FO", "Faroe Islands"),
      Country("FJ", "Fiji"),
      Country("FI", "Finland"),
      Country("FR", "France"),
      Country("GF", "French Guiana"),
      Country("PF", "French Polynesia"),
      Country("TF", "French Southern Territories"),
      Country("GA", "Gabon"),
      Country("GM", "Gambia"),
      Country("GE", "Georgia"),
      Country("DE", "Germany"),
      Country("GH", "Ghana"),
      Country("GI", "Gibraltar"),
      Country("GR", "Greece"),
      Country("GL", "Greenland"),
      Country("GD", "Grenada"),
      Country("GP", "Guadeloupe"),
      Country("GU", "Guam"),
      Country("GT", "Guatemala"),
      Country("GG", "Guernsey"),
      Country("GN", "Guinea"),
      Country("GW", "Guinea-Bissau"),
      Country("GY", "Guyana"),
      Country("HT", "Haiti"),
      Country("HM", "Heard Island and McDonald Islands"),
      Country("HN", "Honduras"),
      Country("HK", "Hong Kong"),
      Country("HU", "Hungary"),
      Country("IS", "Iceland"),
      Country("IN", "India"),
      Country("ID", "Indonesia"),
      Country("IR", "Iran"),
      Country("IQ", "Iraq"),
      Country("IE", "Ireland"),
      Country("IM", "Isle of Man"),
      Country("IL", "Israel"),
      Country("IT", "Italy"),
      Country("JM", "Jamaica"),
      Country("JP", "Japan"),
      Country("JE", "Jersey"),
      Country("JO", "Jordan"),
      Country("KZ", "Kazakhstan"),
      Country("KE", "Kenya"),
      Country("KI", "Kiribati"),
      Country("KW", "Kuwait"),
      Country("KG", "Kyrgyzstan"),
      Country("LA", "Laos"),
      Country("LV", "Latvia"),
      Country("LB", "Lebanon"),
      Country("LS", "Lesotho"),
      Country("LR", "Liberia"),
      Country("LY", "Libya"),
      Country("LI", "Liechtenstein"),
      Country("LT", "Lithuania"),
      Country("LU", "Luxembourg"),
      Country("MO", "Macao"),
      Country("MG", "Madagascar"),
      Country("MW", "Malawi"),
      Country("MY", "Malaysia"),
      Country("MV", "Maldives"),
      Country("ML", "Mali"),
      Country("MT", "Malta"),
      Country("MH", "Marshall Islands"),
      Country("MQ", "Martinique"),
      Country("MR", "Mauritania"),
      Country("MU", "Mauritius"),
      Country("YT", "Mayotte"),
      Country("MX", "Mexico"),
      Country("FM", "Micronesia"),
      Country("MD", "Moldova"),
      Country("MC", "Monaco"),
      Country("MN", "Mongolia"),
      Country("ME", "Montenegro"),
      Country("MS", "Montserrat"),
      Country("MA", "Morocco"),
      Country("MZ", "Mozambique"),
      Country("MM", "Myanmar"),
      Country("NA", "Namibia"),
      Country("NR", "Nauru"),
      Country("NP", "Nepal"),
      Country("NL", "Netherlands"),
      Country("NC", "New Caledonia"),
      Country("NZ", "New Zealand"),
      Country("NI", "Nicaragua"),
      Country("NE", "Niger"),
      Country("NG", "Nigeria"),
      Country("NU", "Niue"),
      Country("NF", "Norfolk Island"),
      Country("KP", "North Korea"),
      Country("MK", "North Macedonia"),
      Country("MP", "Northern Mariana Islands"),
      Country("NO", "Norway"),
      Country("OM", "Oman"),
      Country("PK", "Pakistan"),
      Country("PW", "Palau"),
      Country("PS", "Palestine"),
      Country("PA", "Panama"),
      Country("PG", "Papua New Guinea"),
      Country("PY", "Paraguay"),
      Country("PE", "Peru"),
      Country("PH", "Philippines"),
      Country("PN", "Pitcairn Islands"),
      Country("PL", "Poland"),
      Country("PT", "Portugal"),
      Country("PR", "Puerto Rico"),
      Country("QA", "Qatar"),
      Country("CG", "Republic of the Congo"),
      Country("RE", "Réunion"),
      Country("RO", "Romania"),
      Country("RU", "Russia"),
      Country("RW", "Rwanda"),
      Country("BL", "Saint Barthélemy"),
      Country("SH", "Saint Helena"),
      Country("KN", "Saint Kitts and Nevis"),
      Country("LC", "Saint Lucia"),
      Country("MF", "Saint Martin"),
      Country("PM", "Saint Pierre and Miquelon"),
      Country("VC", "Saint Vincent and the Grenadines"),
      Country("WS", "Samoa"),
      Country("SM", "San Marino"),
      Country("ST", "São Tomé and Príncipe"),
      Country("SA", "Saudi Arabia"),
      Country("SN", "Senegal"),
      Country("RS", "Serbia"),
      Country("SC", "Seychelles"),
      Country("SL", "Sierra Leone"),
      Country("SG", "Singapore"),
      Country("SX", "Sint Maarten"),
      Country("SK", "Slovakia"),
      Country("SI", "Slovenia"),
      Country("SB", "Solomon Islands"),
      Country("SO", "Somalia"),
      Country("ZA", "South Africa"),
      Country("GS", "South Georgia and South Sandwich Islands"),
      Country("KR", "South Korea"),
      Country("SS", "South Sudan"),
      Country("ES", "Spain"),
      Country("LK", "Sri Lanka"),
      Country("SD", "Sudan"),
      Country("SR", "Suriname"),
      Country("SJ", "Svalbard and Jan Mayen"),
      Country("SE", "Sweden"),
      Country("CH", "Switzerland"),
      Country("SY", "Syria"),
      Country("TW", "Taiwan"),
      Country("TJ", "Tajikistan"),
      Country("TZ", "Tanzania"),
      Country("TH", "Thailand"),
      Country("TL", "Timor-Leste"),
      Country("TG", "Togo"),
      Country("TK", "Tokelau"),
      Country("TO", "Tonga"),
      Country("TT", "Trinidad and Tobago"),
      Country("TN", "Tunisia"),
      Country("TR", "Türkiye"),
      Country("TM", "Turkmenistan"),
      Country("TC", "Turks and Caicos Islands"),
      Country("TV", "Tuvalu"),
      Country("VI", "U.S. Virgin Islands"),
      Country("UG", "Uganda"),
      Country("UA", "Ukraine"),
      Country("AE", "United Arab Emirates"),
      Country("GB", "United Kingdom"),
      Country("US", "United States"),
      Country("UM", "United States Minor Outlying Islands"),
      Country("UY", "Uruguay"),
      Country("UZ", "Uzbekistan"),
      Country("VU", "Vanuatu"),
      Country("VA", "Vatican City"),
      Country("VE", "Venezuela"),
      Country("VN", "Vietnam"),
      Country("WF", "Wallis and Futuna"),
      Country("EH", "Western Sahara"),
      Country("YE", "Yemen"),
      Country("ZM", "Zambia"),
      Country("ZW", "Zimbabwe"),
    )

  private val countriesByCode: Map<String, Country> = all.associateBy { it.code }

  /**
   * Trims and upper-cases [code], returning `null` when it's blank. Doesn't check that the code
   * exists; see [isValidCode].
   */
  fun normalizeCode(code: String?): String? = code?.trim()?.uppercase()?.ifEmpty { null }

  /** The country with [code] (case-insensitive, surrounding spaces ignored), or `null`. */
  fun byCode(code: String?): Country? = normalizeCode(code)?.let { countriesByCode[it] }

  /** Default [SurveyArea] for the country with [code], or `null` if [code] is blank or unknown. */
  fun surveyArea(code: String?): SurveyArea? = byCode(code)?.surveyArea

  /** Whether [code] is an assigned ISO 3166-1 alpha-2 code. */
  fun isValidCode(code: String?): Boolean = byCode(code) != null

  internal fun surveyAreaFor(country: Country): SurveyArea {
    val b = boundsByCode[country.code] ?: DEFAULT_BOUNDS
    val ring =
      listOf(
        LatLng(b.maxLat, b.minLng),
        LatLng(b.maxLat, b.maxLng),
        LatLng(b.minLat, b.maxLng),
        LatLng(b.minLat, b.minLng),
      )
    return SurveyArea(
      name = country.name,
      parts = listOf(ring),
    )
  }

  /**
   * Countries matching [query], for the searchable country picker. Matching ignores case and
   * accents (`cote` finds Côte d'Ivoire) and checks the code and the name, so `ke`, `Ken`, and
   * `Kenya (KE)` all find Kenya. Results are ranked: exact code, then name prefix, then word
   * prefix, then anywhere in `Name (CODE)`; ties keep alphabetical order. A blank [query] returns
   * [all].
   */
  fun search(query: String): List<Country> {
    val q = fold(query.trim())
    if (q.isEmpty()) return all
    return all
      .mapNotNull { country ->
        val name = fold(country.name)
        val rank =
          when {
            country.code.equals(q, ignoreCase = true) -> 0
            name.startsWith(q) -> 1
            name.split(' ', '-').any { it.startsWith(q) } -> 2
            fold("${country.name} (${country.code})").contains(q) -> 3
            else -> null
          }
        rank?.let { it to country }
      }
      .sortedBy { it.first }
      .map { it.second }
  }

  /** Lower-cases [text] and strips the accents that appear in [all]'s names. */
  private fun fold(text: String): String =
    text.lowercase().map { ACCENT_FOLDS[it] ?: it }.joinToString("")

  private val ACCENT_FOLDS =
    mapOf('å' to 'a', 'ã' to 'a', 'ç' to 'c', 'é' to 'e', 'í' to 'i', 'ô' to 'o', 'ü' to 'u')

  private data class CountryBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
  )

  private val DEFAULT_BOUNDS = CountryBounds(-5.0, -5.0, 5.0, 5.0)

  private val boundsByCode: Map<String, CountryBounds> =
    mapOf(
      "AF" to CountryBounds(29.38, 60.52, 38.49, 74.89),
      "AO" to CountryBounds(-18.04, 11.68, -4.38, 24.08),
      "AR" to CountryBounds(-55.06, -73.56, -21.78, -53.64),
      "AU" to CountryBounds(-43.63, 113.34, -10.67, 153.64),
      "BD" to CountryBounds(20.67, 88.01, 26.63, 92.67),
      "BF" to CountryBounds(9.40, -5.52, 15.08, 2.41),
      "BI" to CountryBounds(-4.47, 29.00, -2.31, 30.85),
      "BJ" to CountryBounds(6.23, 0.77, 12.42, 3.85),
      "BO" to CountryBounds(-22.90, -69.64, -9.68, -57.45),
      "BR" to CountryBounds(-33.75, -73.98, 5.27, -34.79),
      "BT" to CountryBounds(26.70, 88.75, 28.25, 92.13),
      "BW" to CountryBounds(-26.91, 19.99, -17.78, 29.36),
      "BZ" to CountryBounds(15.89, -89.22, 18.49, -87.49),
      "CA" to CountryBounds(41.68, -141.00, 70.00, -52.62),
      "CD" to CountryBounds(-13.46, 12.20, 5.39, 31.31),
      "CF" to CountryBounds(2.22, 14.42, 11.01, 27.46),
      "CG" to CountryBounds(-5.03, 11.15, 3.70, 18.65),
      "CH" to CountryBounds(45.82, 5.96, 47.81, 10.49),
      "CI" to CountryBounds(4.36, -8.60, 10.74, -2.49),
      "CL" to CountryBounds(-55.98, -75.64, -17.50, -66.42),
      "CM" to CountryBounds(1.65, 8.49, 13.08, 16.19),
      "CN" to CountryBounds(18.16, 73.50, 53.56, 134.77),
      "CO" to CountryBounds(-4.23, -79.00, 12.46, -66.87),
      "CR" to CountryBounds(8.03, -85.95, 11.22, -82.55),
      "CU" to CountryBounds(19.83, -84.95, 23.27, -74.13),
      "DE" to CountryBounds(47.27, 5.87, 55.06, 15.04),
      "DO" to CountryBounds(17.47, -72.00, 19.93, -68.32),
      "DZ" to CountryBounds(18.97, -8.67, 37.09, 11.98),
      "EC" to CountryBounds(-5.01, -81.08, 1.48, -75.19),
      "EG" to CountryBounds(22.00, 24.70, 31.67, 36.90),
      "ER" to CountryBounds(12.36, 36.44, 18.00, 43.14),
      "ES" to CountryBounds(36.00, -9.30, 43.79, 3.33),
      "ET" to CountryBounds(3.40, 32.99, 14.89, 47.99),
      "FI" to CountryBounds(59.81, 20.55, 70.09, 31.59),
      "FJ" to CountryBounds(-20.67, 177.00, -16.00, 180.00),
      "FR" to CountryBounds(41.33, -5.14, 51.09, 9.56),
      "GA" to CountryBounds(-3.98, 8.70, 2.32, 14.50),
      "GB" to CountryBounds(49.96, -8.65, 60.86, 1.76),
      "GF" to CountryBounds(2.11, -54.60, 5.75, -51.63),
      "GH" to CountryBounds(4.74, -3.26, 11.17, 1.20),
      "GM" to CountryBounds(13.06, -16.82, 13.83, -13.80),
      "GN" to CountryBounds(7.19, -15.08, 12.68, -7.64),
      "GQ" to CountryBounds(-1.47, 5.62, 3.79, 11.34),
      "GT" to CountryBounds(13.74, -92.23, 17.82, -88.22),
      "GW" to CountryBounds(10.87, -16.72, 12.69, -13.63),
      "GY" to CountryBounds(1.17, -61.40, 8.56, -56.48),
      "HN" to CountryBounds(12.98, -89.35, 16.51, -83.13),
      "HT" to CountryBounds(18.02, -74.48, 20.09, -71.62),
      "ID" to CountryBounds(-11.01, 95.01, 5.91, 141.02),
      "IN" to CountryBounds(6.75, 68.16, 35.50, 97.40),
      "IT" to CountryBounds(36.65, 6.63, 47.09, 18.52),
      "JM" to CountryBounds(17.70, -78.37, 18.52, -76.18),
      "JP" to CountryBounds(24.25, 122.93, 45.52, 145.82),
      "KE" to CountryBounds(-4.6780, 33.9098, 5.5060, 41.8995),
      "KH" to CountryBounds(10.41, 102.33, 14.69, 107.63),
      "KR" to CountryBounds(33.11, 126.10, 38.61, 129.58),
      "LA" to CountryBounds(13.91, 100.08, 22.50, 107.64),
      "LK" to CountryBounds(5.92, 79.65, 9.84, 81.88),
      "LR" to CountryBounds(4.35, -11.49, 8.55, -7.37),
      "LS" to CountryBounds(-30.67, 27.01, -28.57, 29.46),
      "MA" to CountryBounds(27.67, -13.17, 35.92, -0.99),
      "MG" to CountryBounds(-25.61, 43.22, -11.95, 50.48),
      "ML" to CountryBounds(10.16, -12.24, 25.00, 4.24),
      "MM" to CountryBounds(9.78, 92.19, 28.54, 101.18),
      "MN" to CountryBounds(41.58, 87.73, 52.15, 119.93),
      "MR" to CountryBounds(14.72, -17.07, 27.30, -4.83),
      "MW" to CountryBounds(-17.13, 32.67, -9.37, 35.92),
      "MX" to CountryBounds(14.53, -117.12, 32.72, -86.71),
      "MY" to CountryBounds(0.85, 99.64, 7.36, 119.27),
      "MZ" to CountryBounds(-26.87, 30.22, -10.47, 40.84),
      "NA" to CountryBounds(-28.97, 11.73, -16.96, 25.26),
      "NE" to CountryBounds(11.69, 0.17, 23.52, 15.99),
      "NG" to CountryBounds(4.27, 2.67, 13.89, 14.68),
      "NI" to CountryBounds(10.71, -87.69, 15.03, -83.15),
      "NO" to CountryBounds(57.96, 4.65, 71.19, 31.17),
      "NP" to CountryBounds(26.35, 80.06, 30.45, 88.20),
      "NZ" to CountryBounds(-47.29, 166.43, -34.39, 178.55),
      "PA" to CountryBounds(7.20, -83.05, 9.65, -77.17),
      "PE" to CountryBounds(-18.35, -81.33, -0.04, -68.65),
      "PG" to CountryBounds(-11.66, 140.84, -1.32, 155.96),
      "PH" to CountryBounds(4.59, 116.93, 21.12, 126.60),
      "PK" to CountryBounds(23.69, 60.87, 37.08, 77.84),
      "PL" to CountryBounds(49.00, 14.12, 54.84, 24.15),
      "PY" to CountryBounds(-27.61, -62.64, -19.29, -54.26),
      "RO" to CountryBounds(43.62, 20.26, 48.27, 29.69),
      "RU" to CountryBounds(41.19, 27.32, 68.00, 80.00),
      "RW" to CountryBounds(-2.84, 28.86, -1.05, 30.90),
      "SB" to CountryBounds(-11.85, 155.51, -6.59, 167.00),
      "SD" to CountryBounds(8.68, 21.81, 22.23, 38.58),
      "SE" to CountryBounds(55.34, 11.11, 69.06, 24.17),
      "SL" to CountryBounds(6.93, -13.30, 10.00, -10.27),
      "SN" to CountryBounds(12.31, -17.54, 16.69, -11.35),
      "SO" to CountryBounds(-1.66, 40.99, 11.99, 51.41),
      "SR" to CountryBounds(1.83, -58.09, 6.00, -53.98),
      "SS" to CountryBounds(3.49, 24.15, 12.24, 35.95),
      "SV" to CountryBounds(13.15, -90.13, 14.45, -87.69),
      "SZ" to CountryBounds(-27.32, 30.79, -25.72, 32.14),
      "TD" to CountryBounds(7.44, 13.47, 23.45, 24.00),
      "TG" to CountryBounds(6.10, -0.15, 11.14, 1.81),
      "TH" to CountryBounds(5.61, 97.34, 20.46, 105.64),
      "TL" to CountryBounds(-9.50, 124.04, -8.13, 127.34),
      "TR" to CountryBounds(35.82, 25.67, 42.11, 44.82),
      "TZ" to CountryBounds(-11.75, 29.33, -0.99, 40.44),
      "UA" to CountryBounds(44.39, 22.14, 52.38, 40.23),
      "UG" to CountryBounds(-1.48, 29.57, 4.23, 35.00),
      "US" to CountryBounds(24.52, -124.77, 49.38, -66.95),
      "UY" to CountryBounds(-34.97, -58.44, -30.09, -53.07),
      "VE" to CountryBounds(0.65, -73.35, 12.20, -59.80),
      "VN" to CountryBounds(8.56, 102.14, 23.39, 109.46),
      "VU" to CountryBounds(-20.25, 166.52, -13.07, 170.24),
      "ZA" to CountryBounds(-34.84, 16.45, -22.13, 32.89),
      "ZM" to CountryBounds(-18.08, 21.99, -8.22, 33.71),
      "ZW" to CountryBounds(-22.42, 25.24, -15.61, 33.06),
    )
}
