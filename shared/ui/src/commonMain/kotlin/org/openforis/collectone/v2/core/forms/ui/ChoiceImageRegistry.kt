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
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.max
import kotlin.math.min
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ViewComponent
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.openforis.collectone.v2.core.forms.media.ChoiceImages
import org.openforis.collectone.v2.core.forms.model.ResolvedChoiceOption

/**
 * Accessible color styling tokens computed for a multiple-choice option that has an associated
 * option color, for both unselected and selected states across light and dark themes.
 */
@Immutable
data class ChoiceOptionColorStyle(
  val choiceColor: Color,
  val containerColor: Color,
  val borderColor: Color,
  val borderWidth: Dp,
  val accentStripeColor: Color,
  val swatchColor: Color,
  val onSwatchColor: Color,
  val swatchBorderColor: Color,
  val contentColor: Color,
  val secondaryContentColor: Color,
)

/**
 * A resolved option color for displaying a collected multiple-choice answer in summary, receipt,
 * submission detail, or entity detail views.
 */
@Immutable
data class ResolvedChoiceAnswerColor(
  val label: String,
  val color: Color,
  val colorHex: String,
)

/**
 * Compose helpers for choice option images and colors of multiple-choice questions (`select1` and
 * `select`). Built-in icon payloads live in [ChoiceImages]; authored inline choice images travel in
 * the choice itemset's `collectone_image` property (`[inlineImageDataUri]`).
 */
object ChoiceImageRegistry {
  /**
   * Decodes the choice image for [inlineImageDataUri] (if present) or [imageUri] into an
   * [ImageBitmap], or `null` if unavailable.
   */
  @OptIn(ExperimentalResourceApi::class, ExperimentalEncodingApi::class)
  @Composable
  fun rememberChoiceImageBitmap(
    imageUri: String?,
    inlineImageDataUri: String? = null,
  ): ImageBitmap? {
    val media =
      ChoiceImages.lookup(inlineImageDataUri) ?: ChoiceImages.lookup(imageUri) ?: return null
    return remember(media.base64) {
      runCatching { Base64.decode(media.base64).decodeToImageBitmap() }.getOrNull()
    }
  }

  private val HEX_6_COLOR_REGEX = Regex("^#?[0-9A-Fa-f]{6}$")
  private val HEX_8_COLOR_REGEX = Regex("^#?[0-9A-Fa-f]{8}$")
  private val HEX_3_COLOR_REGEX = Regex("^#?[0-9A-Fa-f]{3}$")

  /**
   * Parses a `#RRGGBB`, `#AARRGGBB`, or `#RGB` choice color string into a Compose [Color], or
   * `null` if absent/invalid.
   */
  fun parseChoiceColor(hex: String?): Color? {
    val clean = hex?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val digits = clean.removePrefix("#")
    return when {
      HEX_6_COLOR_REGEX.matches(clean) -> {
        val rgb = digits.toLongOrNull(16) ?: return null
        Color(0xFF000000L or rgb)
      }
      HEX_8_COLOR_REGEX.matches(clean) -> {
        val argb = digits.toLongOrNull(16) ?: return null
        Color(argb)
      }
      HEX_3_COLOR_REGEX.matches(clean) -> {
        val r = digits[0]
        val g = digits[1]
        val b = digits[2]
        val rgb = "$r$r$g$g$b$b".toLongOrNull(16) ?: return null
        Color(0xFF000000L or rgb)
      }
      else -> null
    }
  }

  /**
   * Computes high-visibility, WCAG-accessible surface, border, accent stripe, swatch, and text
   * colors for a choice option with [choiceColor] in [isSelected] or unselected state.
   *
   * @param tintContainer whether the container takes a tint of [choiceColor]. Answer option cards
   *   pass `false`: they sit on the plain [surfaceColor] and show the color as a uniform border
   *   (heavier when selected) and their swatch, so a list of options stays calm.
   */
  fun choiceOptionColorStyle(
    choiceColor: Color,
    isSelected: Boolean,
    surfaceColor: Color,
    onSurfaceColor: Color,
    tintContainer: Boolean = true,
  ): ChoiceOptionColorStyle {
    val opaqueChoice = choiceColor.copy(alpha = 1f)
    val isDarkSurface = surfaceColor.luminance() < 0.5f
    val containerTintFraction =
      when {
        isSelected && isDarkSurface -> 0.34f
        isSelected -> 0.24f
        isDarkSurface -> 0.18f
        else -> 0.13f
      }
    val containerColor =
      if (tintContainer) lerp(surfaceColor, opaqueChoice, containerTintFraction) else surfaceColor
    val borderColor =
      if (isSelected) {
        opaqueChoice
      } else {
        lerp(surfaceColor, opaqueChoice, if (isDarkSurface) 0.68f else 0.58f)
      }
    val borderWidth = if (isSelected) 2.5.dp else 1.5.dp
    val onSwatchColor =
      if (contrastRatio(Color.Black, opaqueChoice) >= contrastRatio(Color.White, opaqueChoice)) {
        Color.Black
      } else {
        Color.White
      }
    val swatchBorderColor =
      when {
        opaqueChoice.luminance() > 0.68f && !isDarkSurface -> Color.Black.copy(alpha = 0.28f)
        opaqueChoice.luminance() < 0.16f && isDarkSurface -> Color.White.copy(alpha = 0.35f)
        else -> opaqueChoice
      }
    val highContrastInk = if (containerColor.luminance() > 0.5f) Color.Black else Color.White
    val contentColor =
      if (tintContainer) {
        lerp(onSurfaceColor, highContrastInk, if (isSelected) 0.45f else 0.22f)
      } else {
        onSurfaceColor
      }
    val secondaryContentColor = contentColor.copy(alpha = 0.78f)
    return ChoiceOptionColorStyle(
      choiceColor = opaqueChoice,
      containerColor = containerColor,
      borderColor = borderColor,
      borderWidth = borderWidth,
      accentStripeColor = opaqueChoice,
      swatchColor = opaqueChoice,
      onSwatchColor = onSwatchColor,
      swatchBorderColor = swatchBorderColor,
      contentColor = contentColor,
      secondaryContentColor = secondaryContentColor,
    )
  }

  /** WCAG 2.1 relative luminance contrast ratio (1.0 to 21.0) between [a] and [b]. */
  fun contrastRatio(a: Color, b: Color): Float {
    val lumA = a.luminance()
    val lumB = b.luminance()
    val lighter = max(lumA, lumB)
    val darker = min(lumA, lumB)
    return (lighter + 0.05f) / (darker + 0.05f)
  }

  /**
   * Resolves the selected [ResolvedChoiceOption]s that have a valid `color` property for a runtime
   * control with [options] and [selectedValues].
   */
  fun resolveSelectedOptionColors(
    options: List<ResolvedChoiceOption>,
    selectedValues: Collection<String>,
  ): List<ResolvedChoiceAnswerColor> {
    if (selectedValues.isEmpty() || options.isEmpty()) return emptyList()
    val byValue = options.associateBy { it.value }
    val resolved = selectedValues.mapNotNull { value ->
      val opt = byValue[value] ?: return@mapNotNull null
      val hex = opt.properties["color"]?.trim()?.takeIf { it.isNotEmpty() }
      val color = parseChoiceColor(hex)
      val label = opt.label.text.ifBlank { opt.value }
      if (color != null && hex != null) {
        ResolvedChoiceAnswerColor(label = label, color = color, colorHex = hex)
      } else {
        null
      }
    }
    return resolved
  }

  /**
   * Resolves choice option color(s) for a collected [answerValue] of [questionName] (a field path,
   * question name, or entity property key), consulting [formDefs] first and falling back to
   * built-in Collect One choice color definitions.
   */
  fun resolveChoiceAnswerColors(
    questionName: String,
    answerValue: String,
    formDefs: Iterable<FormDef> = emptyList(),
  ): List<ResolvedChoiceAnswerColor> {
    val trimmedAnswer = answerValue.trim()
    if (trimmedAnswer.isEmpty()) return emptyList()

    val leafName = questionName.trim().substringAfterLast('/').substringBefore('[').lowercase()

    val candidates = LinkedHashMap<String, ChoiceColorCandidate>()
    for (formDef in formDefs) {
      collectCandidatesFromFormDef(formDef, leafName).forEach { candidate ->
        candidates.getOrPut(candidate.value.lowercase()) { candidate }
      }
    }
    FALLBACK_CHOICE_COLORS_BY_QUESTION[leafName]?.forEach { candidate ->
      candidates.getOrPut(candidate.value.lowercase()) { candidate }
    }
    if (candidates.isEmpty()) {
      for (formDef in formDefs) {
        collectCandidatesFromFormDef(formDef, targetLeafName = null).forEach { candidate ->
          candidates.getOrPut(candidate.value.lowercase()) { candidate }
        }
      }
    }
    if (candidates.isEmpty()) return emptyList()

    matchSingleAnswer(trimmedAnswer, candidates)?.let {
      return listOf(it)
    }

    val tokens =
      if (',' in trimmedAnswer) {
        trimmedAnswer.split(',').map { it.trim() }.filter { it.isNotEmpty() }
      } else {
        trimmedAnswer.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
      }
    if (tokens.size <= 1) return emptyList()
    val matchedTokens = tokens.mapNotNull { token -> matchSingleAnswer(token, candidates) }
    return if (matchedTokens.size == tokens.size) matchedTokens else emptyList()
  }

  private data class ChoiceColorCandidate(
    val value: String,
    val label: String,
    val colorHex: String,
    val color: Color,
  )

  private fun matchSingleAnswer(
    rawAnswer: String,
    candidates: Map<String, ChoiceColorCandidate>,
  ): ResolvedChoiceAnswerColor? {
    val clean = rawAnswer.trim()
    if (clean.isEmpty()) return null
    val byValue = candidates[clean.lowercase()]
    if (byValue != null) {
      return ResolvedChoiceAnswerColor(
        label = clean,
        color = byValue.color,
        colorHex = byValue.colorHex,
      )
    }
    val beforeParen = clean.substringBefore(" (").trim()
    if (beforeParen != clean) {
      val byPrefix = candidates[beforeParen.lowercase()]
      if (byPrefix != null) {
        return ResolvedChoiceAnswerColor(
          label = clean,
          color = byPrefix.color,
          colorHex = byPrefix.colorHex,
        )
      }
    }
    val byLabel =
      candidates.values.firstOrNull {
        it.label.isNotBlank() && it.label.equals(clean, ignoreCase = true)
      }
    if (byLabel != null) {
      return ResolvedChoiceAnswerColor(
        label = clean,
        color = byLabel.color,
        colorHex = byLabel.colorHex,
      )
    }
    return null
  }

  private fun collectCandidatesFromFormDef(
    formDef: FormDef,
    targetLeafName: String?,
  ): List<ChoiceColorCandidate> {
    val result = mutableListOf<ChoiceColorCandidate>()
    val model = formDef.model
    val defaultTranslations =
      model?.translations?.languages?.firstOrNull { it.is_default }?.strings
        ?: model?.translations?.languages?.firstOrNull()?.strings
        ?: emptyMap()

    val saveToFieldByProperty =
      model
        ?.bindings
        .orEmpty()
        .filter { it.entity_saveto.isNotBlank() }
        .associate {
          it.entity_saveto.trim().lowercase() to
            it.field_path.trim().substringAfterLast('/').lowercase()
        }
    val effectiveLeaf = targetLeafName?.let { saveToFieldByProperty[it] ?: it }

    val controls = mutableListOf<ControlDef>()
    fun walkComponents(components: List<ViewComponent>) {
      for (comp in components) {
        comp.control?.let { controls.add(it) }
        comp.group?.let { walkComponents(it.components) }
        comp.repeat?.let { walkComponents(it.components) }
      }
    }
    walkComponents(formDef.view?.components.orEmpty())

    val matchingInstanceIds = mutableSetOf<String>()
    for (control in controls) {
      val controlLeaf = control.field_ref.trim().substringAfterLast('/').lowercase()
      if (effectiveLeaf != null && controlLeaf != effectiveLeaf) continue
      for (choice in control.choices) {
        val hex = choice.properties["color"]?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val color = parseChoiceColor(hex) ?: continue
        val label =
          choice.label?.text?.takeIf { it.isNotBlank() }
            ?: choice.label?.text_id?.let { defaultTranslations[it]?.value_ }
            ?: choice.value_
        result.add(
          ChoiceColorCandidate(
            value = choice.value_,
            label = label,
            colorHex = hex,
            color = color,
          )
        )
      }
      control.itemset?.instance_id?.takeIf { it.isNotBlank() }?.let { matchingInstanceIds.add(it) }
    }

    for (instance in model?.secondary_instances.orEmpty()) {
      val instanceLeaf = instance.id.trim().lowercase()
      val matches =
        effectiveLeaf == null || instanceLeaf == effectiveLeaf || instance.id in matchingInstanceIds
      if (!matches) continue
      for (row in instance.rows) {
        val hex =
          row.fields["color"]?.scalar_value?.string_value?.trim()?.takeIf { it.isNotEmpty() }
            ?: continue
        val color = parseChoiceColor(hex) ?: continue
        val name =
          row.fields["name"]?.scalar_value?.string_value?.trim()?.takeIf { it.isNotEmpty() }
            ?: continue
        val itextId = row.fields["itextId"]?.scalar_value?.string_value?.trim()
        val label =
          row.fields["label"]?.scalar_value?.string_value?.trim()?.takeIf { it.isNotEmpty() }
            ?: itextId?.let { defaultTranslations[it]?.value_ }?.takeIf { it.isNotBlank() }
            ?: name
        result.add(
          ChoiceColorCandidate(
            value = name,
            label = label,
            colorHex = hex,
            color = color,
          )
        )
      }
    }
    return result
  }

  private fun candidate(value: String, label: String, colorHex: String): ChoiceColorCandidate =
    ChoiceColorCandidate(
      value = value,
      label = label,
      colorHex = colorHex,
      color = requireNotNull(parseChoiceColor(colorHex)),
    )

  private val LAND_USE_CANDIDATES =
    listOf(
      candidate("primary_forest", "Primary / Intact Natural Forest", "#1B5E20"),
      candidate("secondary_forest", "Secondary / Regenerating Forest", "#388E3C"),
      candidate("agroforestry_shade", "Agroforestry / Shade-Grown Tree Crop", "#689F38"),
      candidate("annual_cropland", "Annual Cropland", "#FBC02D"),
      candidate("pasture_grassland", "Pasture / Managed Grassland", "#AED581"),
      candidate("wetland_riparian", "Wetland / Riparian Buffer", "#0288D1"),
      candidate("settlement", "Settlement / Built-up Infrastructure", "#E64A19"),
      candidate("forest_land", "Forest land", "#3C8D40"),
      candidate("forest", "Forest land (≥10% canopy)", "#1B5E20"),
      candidate("agroforestry", "Shade agroforestry / perennial crop", "#689F38"),
      candidate("cropland", "Cropland", "#F9BF40"),
      candidate("grassland", "Grassland", "#F37C22"),
      candidate("wetlands", "Wetlands", "#2278CF"),
      candidate("wetland", "Wetland / riparian", "#0288D1"),
      candidate("settlements", "Settlements", "#D13135"),
      candidate("other_land", "Other land", "#8D6E63"),
      candidate("non_forest", "Non-forest", "#F37C22"),
    )

  private val FALLBACK_CHOICE_COLORS_BY_QUESTION: Map<String, List<ChoiceColorCandidate>> =
    mapOf(
      "land_use" to LAND_USE_CANDIDATES,
      "land_use_class" to LAND_USE_CANDIDATES,
      "ipcc_land_use" to LAND_USE_CANDIDATES,
      "baseline_land_use_ipcc" to LAND_USE_CANDIDATES,
      "current_land_use_ipcc" to LAND_USE_CANDIDATES,
      "desk_land_cover" to LAND_USE_CANDIDATES,
      "field_land_cover" to LAND_USE_CANDIDATES,
      "land_cover" to LAND_USE_CANDIDATES,
      "disturbance_indicators" to
        listOf(
          candidate("none", "None — Undisturbed Stand", "#2E7D32"),
          candidate("selective_logging", "Selective Pole / Timber Cutting", "#8D6E63"),
          candidate("fire_charcoal", "Charcoal Kiln / Fire Scars", "#D84315"),
          candidate("livestock_grazing", "Livestock Browsing / Trampling", "#F9A825"),
          candidate("invasive_species", "Invasive Understory Shrubs (e.g., Lantana)", "#7B1FA2"),
        ),
      "disturbance_drivers" to
        listOf(
          candidate("agricultural_expansion", "Agricultural expansion", "#F9A825"),
          candidate("selective_logging", "Selective logging", "#8D6E63"),
          candidate("fire", "Fire / burn scar", "#D84315"),
          candidate("road_access", "New road or track", "#5C6BC0"),
        ),
      "deforestation_free" to
        listOf(
          candidate("yes", "Yes — intact or established prior to 2020", "#2E7D32"),
          candidate("no", "No — clearing detected after 2020", "#C62828"),
          candidate("uncertain", "Uncertain — cloud cover / needs field check", "#F9A825"),
        ),
      "soil_moisture_class" to
        listOf(
          candidate("dry", "Dry / Cracked Surface", "#D84315"),
          candidate("moist_well_drained", "Moist & Well-Drained Loam", "#2E7D32"),
          candidate("saturated", "Waterlogged / Hydric", "#0277BD"),
        ),
      "canopy_health_rating" to
        listOf(
          candidate("very_poor", "1 - Severe Dieback", "#C62828"),
          candidate("poor", "2 - Moderate Stress", "#EF6C00"),
          candidate("fair", "3 - Acceptable", "#F9A825"),
          candidate("good", "4 - Vigorous Crown", "#689F38"),
          candidate("excellent", "5 - Optimal Closed Canopy", "#1B5E20"),
        ),
      "respondent_residency_status" to
        listOf(
          candidate(
            "resident_full_time",
            "Full-Time Resident (Present >= 9 months/year)",
            "#2E7D32",
          ),
          candidate(
            "seasonal_migrant",
            "Seasonal Labor Migrant (Present 3-8 months/year)",
            "#F9A825",
          ),
          candidate("relocated", "Relocated to Another Village / Urban Center", "#1565C0"),
        ),
      "primary_livelihood_sources" to
        listOf(
          candidate("agroforestry_cocoa", "Shade Coffee / Cocoa Agroforestry", "#388E3C"),
          candidate("annual_food_crops", "Food Crops (Maize, Beans, Cassava)", "#FBC02D"),
          candidate("beekeeping", "Apiculture / Forest Honey & Non-Timber Products", "#FFA000"),
          candidate(
            "tree_nursery_pes",
            "Tree Seedling Nursery / Restoration Payments (PES)",
            "#00897B",
          ),
          candidate("off_farm_wage", "Local Off-Farm Wage Labor / Trade", "#5C6BC0"),
        ),
      "certification_schemes" to
        listOf(
          candidate("rainforest_alliance", "Rainforest Alliance / UTZ", "#2E7D32"),
          candidate("organic_eu", "EU / USDA Certified Organic", "#689F38"),
          candidate("fairtrade", "Fairtrade International", "#0288D1"),
          candidate("carbon_verified", "Plan Vivo / Gold Standard Carbon", "#00897B"),
        ),
      "alert_cause" to
        listOf(
          candidate("windthrow", "Natural Windthrow / Storm Branch Fall", "#0288D1"),
          candidate("pruning", "Scheduled Shade Tree Pollarding / Pruning", "#689F38"),
          candidate("clearing", "Unauthorized Canopy Clearing / Encroachment", "#C62828"),
        ),
    )
}
