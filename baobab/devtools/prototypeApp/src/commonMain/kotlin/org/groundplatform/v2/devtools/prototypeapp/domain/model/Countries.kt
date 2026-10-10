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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/** A country or territory with an ISO 3166-1 alpha-2 [code] (e.g., `KE`) and English [name]. */
data class Country(val code: String, val name: String) {
  /** Plain-text label shown in pickers and on the organization page, e.g. `Kenya (KE)`. */
  val label: String
    get() = "$name ($code)"
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

  /** Whether [code] is an assigned ISO 3166-1 alpha-2 code. */
  fun isValidCode(code: String?): Boolean = byCode(code) != null

  /**
   * Countries matching [query], for the searchable country picker. Matching ignores case and
   * accents (`cote` finds Côte d'Ivoire) and checks the code and the name, so `ke`, `Ken`, and
   * `Kenya (KE)` all find Kenya. Results are ranked: exact code, then name prefix, then word
   * prefix, then anywhere in the label; ties keep alphabetical order. A blank [query] returns
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
            fold(country.label).contains(q) -> 3
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
}
