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

private val WHITESPACE = Regex("\\s+")

/** Appearance tokens that let the collector pan a geo question's map to place points by hand. */
private val MAP_PAN_APPEARANCES = setOf("placement-map", "map", "maps", "walk-or-draw")

/**
 * Splits an XForms `appearance` attribute into its whitespace-separated tokens, lowercased so
 * matching is case-insensitive (e.g. `"Likert quick"` → `{"likert", "quick"}`).
 */
internal fun appearanceTokens(appearance: String): Set<String> =
  appearance.split(WHITESPACE).filter { it.isNotEmpty() }.map { it.lowercase() }.toSet()

/**
 * Returns `true` when [appearance] lets the collector pan a geo question's map to place points by
 * hand (`placement-map`, `map`, `maps`, or `walk-or-draw`). When `false`, capture is locked to the
 * collector's live GPS position.
 */
fun isMapPanAllowed(appearance: String): Boolean =
  appearanceTokens(appearance).any { it in MAP_PAN_APPEARANCES }
