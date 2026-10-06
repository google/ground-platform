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
package org.groundplatform.v2.core.forms.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * How much room the form widgets ([ControlWidget] and friends) take.
 *
 * - [COMFORTABLE]: the mobile one-question-per-screen runner ([MobileFormRunner]), where a single
 *   question owns the screen and touch targets are generous. This is the default.
 * - [COMPACT]: the stacked web layout ([CompactFormRunner]), where many question cards share a side
 *   panel and a pointer is the usual input. Numeric questions drop their stepper buttons
 *   ([showNumericSteppers]) and free-text fields are slightly shorter ([GroundOutlinedTextField]);
 *   everything else renders exactly as on mobile.
 *
 * The density is read through [LocalFormDensity], so the same widgets serve both layouts without
 * extra parameters; the Form designer's web preview inherits the compact density from
 * [CompactFormRunner] and so matches the live web form.
 */
enum class FormDensity {
  COMFORTABLE,
  COMPACT,
}

/** The [FormDensity] of the enclosing form layout. Defaults to [FormDensity.COMFORTABLE]. */
val LocalFormDensity: ProvidableCompositionLocal<FormDensity> = compositionLocalOf {
  FormDensity.COMFORTABLE
}

/**
 * Whether numeric questions show stepper buttons (`-1` / `+1`, `-0.5` / `+0.5`) beside the text
 * field. They are a touch convenience, so only the [FormDensity.COMFORTABLE] mobile layout shows
 * them; the compact web layout keeps just the text field.
 */
fun showNumericSteppers(density: FormDensity): Boolean = density == FormDensity.COMFORTABLE

/** Returns [compact] under [FormDensity.COMPACT], otherwise [comfortable]. */
fun <T> FormDensity.select(compact: T, comfortable: T): T =
  if (this == FormDensity.COMPACT) compact else comfortable

/** Whether the enclosing layout is [FormDensity.COMPACT]. */
@Composable
internal fun isCompactDensity(): Boolean = LocalFormDensity.current == FormDensity.COMPACT
