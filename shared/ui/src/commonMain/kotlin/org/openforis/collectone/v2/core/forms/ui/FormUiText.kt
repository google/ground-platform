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
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * User-facing text produced outside composition (parsers, formatters, the wizard controller) and
 * resolved against this module's string resources by the UI layer with [resolve].
 */
sealed interface FormUiText {
  /** A string resource, formatted with [args]. */
  data class Resource(val res: StringResource, val args: List<Any> = emptyList()) : FormUiText {
    constructor(res: StringResource, vararg args: Any) : this(res, args.toList())
  }

  /** A plural resource selected by [count], formatted with [args] (by default just [count]). */
  data class Plural(
    val res: PluralStringResource,
    val count: Int,
    val args: List<Any> = listOf(count),
  ) : FormUiText

  /** Text that is not translated, such as a collector's own answer. */
  data class Verbatim(val text: String) : FormUiText
}

/** Resolves this text in the current locale. */
@Composable
fun FormUiText.resolve(): String =
  when (this) {
    is FormUiText.Resource -> stringResource(res, *args.toTypedArray())
    is FormUiText.Plural -> pluralStringResource(res, count, *args.toTypedArray())
    is FormUiText.Verbatim -> text
  }
