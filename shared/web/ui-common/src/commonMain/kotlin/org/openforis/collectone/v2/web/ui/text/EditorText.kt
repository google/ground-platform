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
package org.openforis.collectone.v2.web.ui.text

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.ui.text.UiText

/**
 * User-facing text produced outside the UI (view models, UI state, domain message mappers),
 * resolved in the app language by the UI (see `EditorText.resolve()` in the survey editor UI).
 */
sealed interface EditorText {
  /** A string resource formatted with [args]. */
  data class Resource(val res: StringResource, val args: List<Any> = emptyList()) : EditorText

  /** A plural resource chosen by [quantity] and formatted with [args]. */
  data class Plural(
    val res: PluralStringResource,
    val quantity: Int,
    val args: List<Any> = listOf(quantity),
  ) : EditorText

  /** Text that is already user-facing as is (e.g. a message from another layer). */
  data class Verbatim(val text: String) : EditorText

  /** Text localized by shared/app's presentation mappers (e.g. a shared domain message). */
  data class Localized(val text: UiText) : EditorText
}
