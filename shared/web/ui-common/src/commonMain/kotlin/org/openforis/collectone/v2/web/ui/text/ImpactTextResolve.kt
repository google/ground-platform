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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.web.domain.model.ImpactText

/** [this] resolved in the current locale. */
@Composable
fun ImpactText.resolve(): String =
  when (this) {
    is ImpactText.Plain -> value
    is ImpactText.Message -> stringResource(key.resource, *resolveArgs(args))
    is ImpactText.Plural ->
      pluralStringResource(key.resource, quantity.toPluralQuantity(), *resolveArgs(args))
    is ImpactText.Measured -> impactMeasurementText(measurement).resolve()
  }

@Composable
private fun resolveArgs(args: List<Any>): Array<Any> =
  args.map { if (it is ImpactText) it.resolve() else it }.toTypedArray()
