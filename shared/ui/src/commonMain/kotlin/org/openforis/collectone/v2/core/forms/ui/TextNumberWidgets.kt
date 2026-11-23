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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import openforis.collectone.v1.forms.DataType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.model.FieldState
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_clear
import org.openforis.collectone.v2.core.forms.ui.resources.forms_decimal_invalid
import org.openforis.collectone.v2.core.forms.ui.resources.forms_text_char_count
import org.openforis.collectone.v2.core.forms.ui.resources.forms_text_placeholder

@Composable
internal fun StringInputWidget(
  path: String,
  fieldState: FieldState,
  isMultiline: Boolean,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val currentStr = fieldState.value?.scalar_value?.string_value ?: ""
  // Key on `path` only. Keying on `currentStr` too meant every keystroke changed the key (the
  // engine echoes the new value straight back), so `remember` discarded and recreated the state on
  // each character -- resetting cursor position, selection and any in-progress IME composition.
  var text by remember(path) { mutableStateOf(currentStr) }

  // Adopt values the engine changed on its own (a `calculate` firing, a clear, restoring a saved
  // draft) without clobbering what the user is typing. After a keystroke `currentStr` matches
  // `text`, so this is a no-op in the common case.
  LaunchedEffect(path, currentStr) {
    if (currentStr != text) {
      text = currentStr
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FormOutlinedTextField(
      value = text,
      onValueChange = { newText ->
        text = newText
        controller.updateString(path, newText)
      },
      modifier = Modifier.fillMaxWidth().let { if (isMultiline) it.height(120.dp) else it },
      singleLine = !isMultiline,
      placeholder = { Text(stringResource(Res.string.forms_text_placeholder)) },
    )
    if (text.isNotEmpty()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = pluralStringResource(Res.plurals.forms_text_char_count, text.length, text.length),
          style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
        )
        TextButton(
          onClick = {
            text = ""
            controller.clearField(path)
          }
        ) {
          Text(stringResource(Res.string.forms_clear), style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}

/**
 * Shared layout of the integer and decimal inputs: a single-line text field
 * ([FormOutlinedTextField]), flanked by decrement / increment stepper buttons when
 * [showNumericSteppers] allows (the mobile layout), and the parse error underneath. Under
 * [FormDensity.COMPACT] there are no steppers and the text field takes the full width.
 */
@Composable
private fun NumberField(
  text: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  parseError: FormUiText?,
  decrementLabel: String,
  onDecrement: () -> Unit,
  incrementLabel: String,
  onIncrement: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val steppers = showNumericSteppers(LocalFormDensity.current)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (steppers) {
        NumberStepperButton(label = decrementLabel, onClick = onDecrement)
      }
      FormOutlinedTextField(
        value = text,
        onValueChange = onValueChange,
        modifier = Modifier.weight(1f),
        singleLine = true,
        isError = parseError != null,
        placeholder = { Text(placeholder) },
      )
      if (steppers) {
        NumberStepperButton(label = incrementLabel, onClick = onIncrement)
      }
    }
    if (parseError != null) {
      Text(
        text = parseError.resolve(),
        style = MaterialTheme.typography.labelSmall.copy(color = colors.error),
      )
    }
  }
}

@Composable
private fun NumberStepperButton(label: String, onClick: () -> Unit) {
  FilledTonalButton(onClick = onClick, modifier = Modifier.height(52.dp)) {
    Text(
      label,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      softWrap = false,
    )
  }
}

@Composable
internal fun IntegerInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val scalar = fieldState.value?.scalar_value
  val initialNumber: Long? = scalar?.int64_value ?: scalar?.int32_value?.toLong()
  var text by remember(path, initialNumber) { mutableStateOf(initialNumber?.toString() ?: "") }
  var parseError by remember(path) { mutableStateOf<FormUiText?>(null) }

  fun applyNumber(newLong: Long?) {
    parseError = null
    text = newLong?.toString() ?: ""
    if (fieldState.dataType == DataType.TYPE_INT64) {
      controller.updateLong(path, newLong)
    } else {
      controller.updateInt(path, newLong?.toInt())
    }
  }

  NumberField(
    text = text,
    onValueChange = { raw ->
      text = raw
      when (val parsed = parseIntegerInput(raw, fieldState.dataType)) {
        is IntegerInput.Empty -> {
          parseError = null
          controller.clearField(path)
        }
        is IntegerInput.Invalid -> {
          parseError = parsed.message
        }
        is IntegerInput.Valid -> {
          parseError = null
          if (fieldState.dataType == DataType.TYPE_INT64) {
            controller.updateLong(path, parsed.value)
          } else {
            controller.updateInt(path, parsed.value.toInt())
          }
        }
      }
    },
    placeholder = "0",
    parseError = parseError,
    decrementLabel = "-1",
    onDecrement = { applyNumber((initialNumber ?: 0L) - 1L) },
    incrementLabel = "+1",
    onIncrement = { applyNumber((initialNumber ?: 0L) + 1L) },
  )
}

@Composable
internal fun DecimalInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val initialDouble: Double? = fieldState.value?.scalar_value?.double_value
  var text by remember(path) { mutableStateOf(initialDouble?.toString() ?: "") }
  var parseError by remember(path) { mutableStateOf<FormUiText?>(null) }

  LaunchedEffect(initialDouble) {
    val currentParsed = text.toDoubleOrNull()
    if (initialDouble == null && text.isNotEmpty() && parseError == null) {
      text = ""
    } else if (initialDouble != null && currentParsed != initialDouble) {
      text = initialDouble.toString()
    }
  }

  fun step(delta: Double) {
    val next = (((initialDouble ?: 0.0) + delta) * 100.0).roundToInt() / 100.0
    text = next.toString()
    parseError = null
    controller.updateDouble(path, next)
  }

  NumberField(
    text = text,
    onValueChange = { raw ->
      text = raw
      if (raw.isBlank()) {
        parseError = null
        controller.clearField(path)
      } else {
        val parsed = raw.trim().toDoubleOrNull()
        if (parsed != null) {
          parseError = null
          controller.updateDouble(path, parsed)
        } else {
          parseError = FormUiText.Resource(Res.string.forms_decimal_invalid)
        }
      }
    },
    placeholder = "0.0",
    parseError = parseError,
    decrementLabel = "-0.5",
    onDecrement = { step(-0.5) },
    incrementLabel = "+0.5",
    onIncrement = { step(0.5) },
  )
}
