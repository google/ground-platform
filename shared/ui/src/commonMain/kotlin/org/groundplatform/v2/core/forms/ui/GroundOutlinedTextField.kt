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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Minimum height of a [GroundOutlinedTextField] under [FormDensity.COMPACT] (Material: 56.dp). */
internal val COMPACT_TEXT_FIELD_MIN_HEIGHT: Dp = 48.dp

/** Inner padding of a [GroundOutlinedTextField] under [FormDensity.COMPACT] (Material: 16.dp). */
internal val COMPACT_TEXT_FIELD_CONTENT_PADDING: PaddingValues =
  PaddingValues(horizontal = 14.dp, vertical = 12.dp)

/**
 * The free-text field of the form widgets (string, integer, decimal, date, time inputs).
 *
 * Under [FormDensity.COMFORTABLE] this is a plain Material [OutlinedTextField], unchanged. Under
 * [FormDensity.COMPACT] it is the same outlined decoration drawn around a [BasicTextField] with
 * `bodyMedium` text and a reduced [COMPACT_TEXT_FIELD_CONTENT_PADDING], so the field is a touch
 * shorter ([COMPACT_TEXT_FIELD_MIN_HEIGHT] instead of Material's 56.dp minimum, which
 * [OutlinedTextField] enforces internally). Behaviour, colors and shape are Material's in both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroundOutlinedTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  singleLine: Boolean = false,
  isError: Boolean = false,
  label: @Composable (() -> Unit)? = null,
  placeholder: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  minLines: Int = 1,
  maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
  if (!isCompactDensity()) {
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = modifier,
      singleLine = singleLine,
      isError = isError,
      label = label,
      placeholder = placeholder,
      trailingIcon = trailingIcon,
      minLines = minLines,
      maxLines = maxLines,
      keyboardOptions = keyboardOptions,
    )
    return
  }

  val interactionSource = remember { MutableInteractionSource() }
  val focused by interactionSource.collectIsFocusedAsState()
  val colors = OutlinedTextFieldDefaults.colors()
  val textColor =
    when {
      isError -> colors.errorTextColor
      focused -> colors.focusedTextColor
      else -> colors.unfocusedTextColor
    }
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    modifier =
      modifier.defaultMinSize(
        minWidth = OutlinedTextFieldDefaults.MinWidth,
        minHeight = COMPACT_TEXT_FIELD_MIN_HEIGHT,
      ),
    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
    cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
    singleLine = singleLine,
    minLines = minLines,
    maxLines = maxLines,
    keyboardOptions = keyboardOptions,
    interactionSource = interactionSource,
    decorationBox = { innerTextField ->
      OutlinedTextFieldDefaults.DecorationBox(
        value = value,
        innerTextField = innerTextField,
        enabled = true,
        singleLine = singleLine,
        visualTransformation = VisualTransformation.None,
        interactionSource = interactionSource,
        isError = isError,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        colors = colors,
        contentPadding = COMPACT_TEXT_FIELD_CONTENT_PADDING,
      )
    },
  )
}
