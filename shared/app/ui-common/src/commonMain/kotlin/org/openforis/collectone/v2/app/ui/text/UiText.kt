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
package org.openforis.collectone.v2.app.ui.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * User-facing text built outside composition (by ViewModels, UI state, and the presentation mappers
 * in this package, e.g. from a domain message), shown in the user's language by the UI with
 * [resolve], or by coroutines with [resolveString].
 *
 * Arguments that are themselves a [UiText] or a [StringResource] are resolved first; any other
 * argument (user data such as names, IDs, or numbers) is passed through as-is.
 */
sealed interface UiText {

  /** Text shown as-is, e.g. a person's name. */
  data class Plain(val text: String) : UiText

  /** A string [resource] with positional `%1$s`-style [args]. */
  data class Resource(val resource: StringResource, val args: List<Any> = emptyList()) : UiText

  /** A plural resource selected by [quantity], with positional [args]. */
  data class Plural(
    val resource: PluralStringResource,
    val quantity: Int,
    val args: List<Any> = emptyList(),
  ) : UiText

  /** [text] in lowercase, for labels shown mid-sentence (e.g. a property type). */
  data class Lowercase(val text: UiText) : UiText

  /** The localized text, in the current composition's language. */
  @Composable
  fun resolve(): String =
    when (this) {
      is Plain -> text
      is Resource -> stringResource(resource, *args.map { resolveArg(it) }.toTypedArray())
      is Plural ->
        pluralStringResource(resource, quantity, *args.map { resolveArg(it) }.toTypedArray())
      is Lowercase -> text.resolve().lowercase()
    }

  /** The localized text, resolved outside composition (e.g. in a coroutine). */
  suspend fun resolveString(): String =
    when (this) {
      is Plain -> text
      is Resource -> getString(resource, *args.map { resolveArgString(it) }.toTypedArray())
      is Plural ->
        getPluralString(resource, quantity, *args.map { resolveArgString(it) }.toTypedArray())
      is Lowercase -> text.resolveString().lowercase()
    }

  private companion object {
    @Composable
    fun resolveArg(arg: Any): Any =
      when (arg) {
        is UiText -> arg.resolve()
        is StringResource -> stringResource(arg)
        else -> arg
      }

    suspend fun resolveArgString(arg: Any): Any =
      when (arg) {
        is UiText -> arg.resolveString()
        is StringResource -> getString(arg)
        else -> arg
      }
  }
}

/** This resource as [UiText], formatted with [args]. */
fun StringResource.toUiText(vararg args: Any): UiText = UiText.Resource(this, args.toList())

/** This plural resource as [UiText] for [quantity], formatted with [args]. */
fun PluralStringResource.toUiText(quantity: Int, vararg args: Any): UiText =
  UiText.Plural(this, quantity, args.toList())
