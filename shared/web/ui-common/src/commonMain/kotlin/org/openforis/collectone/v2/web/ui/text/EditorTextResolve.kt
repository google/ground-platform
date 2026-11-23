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

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Resolves this text in the current app language. Arguments that are themselves a [StringResource]
 * or an [EditorText] (e.g. an enum label) are resolved first.
 */
@Composable
fun EditorText.resolve(): String =
  when (this) {
    is EditorText.Resource -> stringResource(res, *args.map { resolveArg(it) }.toTypedArray())
    is EditorText.Plural ->
      pluralStringResource(res, quantity, *args.map { resolveArg(it) }.toTypedArray())
    is EditorText.Verbatim -> text
    is EditorText.Localized -> text.resolve()
  }

@Composable
private fun resolveArg(arg: Any): Any =
  when (arg) {
    is StringResource -> stringResource(arg)
    is EditorText -> arg.resolve()
    else -> arg
  }
