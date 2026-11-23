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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.GeoPoint
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FieldState
import org.openforis.collectone.v2.core.forms.model.ValidationStatus
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.Card
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Slider
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_badge_calculated
import org.openforis.collectone.v2.core.forms.ui.resources.forms_badge_read_only
import org.openforis.collectone.v2.core.forms.ui.resources.forms_hide_guidance
import org.openforis.collectone.v2.core.forms.ui.resources.forms_range_max
import org.openforis.collectone.v2.core.forms.ui.resources.forms_range_min
import org.openforis.collectone.v2.core.forms.ui.resources.forms_range_selected
import org.openforis.collectone.v2.core.forms.ui.resources.forms_required_label
import org.openforis.collectone.v2.core.forms.ui.resources.forms_show_guidance
import org.openforis.collectone.v2.core.forms.ui.resources.forms_trigger_acknowledge
import org.openforis.collectone.v2.core.forms.ui.resources.forms_trigger_acknowledged

/** Renders a self-contained mobile question card for a single [ComponentState.ControlState]. */
@Composable
fun QuestionControlCard(
  control: ComponentState.ControlState,
  controller: FormWizardController,
  showValidationErrors: Boolean,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val fieldState = control.fieldState
  val variant = LocalFormVariant.current
  val incompatibleType = incompatibleQuestionTypeForVariant(control, variant)
  val labelText =
    control.label?.text?.takeIf { it.isNotBlank() } ?: control.canonicalPath.substringAfterLast('/')
  val hintText = control.hint?.text?.takeIf { it.isNotBlank() }
  val guidanceText =
    control.label?.guidanceText?.takeIf { it.isNotBlank() }
      ?: control.hint?.guidanceText?.takeIf { it.isNotBlank() }
  var isGuidanceExpanded by remember(control.canonicalPath) { mutableStateOf(false) }
  var hasFocus by remember(control.canonicalPath) { mutableStateOf(false) }
  var hasBlurred by remember(control.canonicalPath) { mutableStateOf(false) }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .onFocusChanged {
          if (hasFocus && !it.hasFocus) hasBlurred = true
          hasFocus = it.hasFocus
        }
        .padding(vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Top metadata badges (read-only / calculated). Required questions use an inline asterisk (*)
    // on the title rather than a red top chip.
    if (fieldState.isCalculated || fieldState.isReadOnly) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (fieldState.isCalculated) {
          TonalBadge(
            text = stringResource(Res.string.forms_badge_calculated),
            tone = BadgeTone.TERTIARY,
          )
        } else if (fieldState.isReadOnly) {
          TonalBadge(
            text = stringResource(Res.string.forms_badge_read_only),
            tone = BadgeTone.NEUTRAL,
          )
        }
      }
    }

    // Primary Question Prompt Label
    Row(verticalAlignment = Alignment.Top) {
      Text(
        text =
          if (fieldState.isRequired && incompatibleType == null) {
            stringResource(Res.string.forms_required_label, labelText)
          } else {
            labelText
          },
        style =
          MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            lineHeight = 22.sp,
          ),
        modifier = Modifier.weight(1f),
      )
    }

    // Secondary Helper Hint
    if (hintText != null) {
      Text(
        text = hintText,
        style =
          MaterialTheme.typography.bodyMedium.copy(
            color = colors.onSurfaceVariant,
            lineHeight = 20.sp,
          ),
      )
    }

    // Expandable Guidance Hint
    if (guidanceText != null) {
      Surface(
        onClick = { isGuidanceExpanded = !isGuidanceExpanded },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector =
                if (isGuidanceExpanded) Icons.Default.KeyboardArrowUp
                else Icons.Default.KeyboardArrowDown,
              contentDescription = null,
              tint = colors.onSecondaryContainer,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text =
                stringResource(
                  if (isGuidanceExpanded) Res.string.forms_hide_guidance
                  else Res.string.forms_show_guidance
                ),
              style =
                MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = colors.onSecondaryContainer,
                ),
            )
          }
          if (isGuidanceExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = guidanceText,
              style = MaterialTheme.typography.bodySmall.copy(color = colors.onSecondaryContainer),
            )
          }
        }
      }
    }

    // Media Attachment Indicators (if present on label)
    val media = control.label?.media
    if (media != null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (media.image_uri.isNotBlank()) {
          TonalBadge(media.image_uri, BadgeTone.PRIMARY)
        }
        if (media.big_image_uri.isNotBlank()) {
          TonalBadge(media.big_image_uri, BadgeTone.PRIMARY)
        }
        if (media.audio_uri.isNotBlank()) {
          TonalBadge(media.audio_uri, BadgeTone.SECONDARY)
        }
        if (media.video_uri.isNotBlank()) {
          TonalBadge(media.video_uri, BadgeTone.TERTIARY)
        }
      }
    }

    // Interactive Control Widget (or variant-incompatibility fallback warning)
    if (incompatibleType != null) {
      VariantIncompatibleQuestionWarning(
        incompatibleType = incompatibleType,
        variant = variant,
        fieldPath = control.canonicalPath,
      )
    } else {
      ControlWidget(control = control, controller = controller)
    }

    // Validation Error Banner (shown on blur or Next/Submit rather than on every keystroke)
    val status = fieldState.validationStatus
    if (
      status is ValidationStatus.Invalid &&
        (showValidationErrors || (hasBlurred && !hasFocus && !fieldState.isEmpty))
    ) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.cardColors(
            containerColor = colors.errorContainer,
            contentColor = colors.onErrorContainer,
          ),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          status.errors.forEach { err ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = colors.onErrorContainer,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = err.message,
                style =
                  MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onErrorContainer,
                  ),
              )
            }
          }
        }
      }
    }
  }
}

/** Dispatches to the appropriate multiplatform widget for [control]. */
@Composable
fun ControlWidget(control: ComponentState.ControlState, controller: FormWizardController) {
  val fieldState = control.fieldState
  if (fieldState.isReadOnly) {
    ReadOnlyValueBox(fieldState)
    return
  }

  when (control.controlDef.type) {
    ControlType.CONTROL_SELECT_ONE -> SelectOneWidget(control = control, controller = controller)
    ControlType.CONTROL_SELECT_MULTIPLE ->
      SelectMultipleWidget(control = control, controller = controller)
    ControlType.CONTROL_RANGE -> RangeControlWidget(control = control, controller = controller)
    ControlType.CONTROL_RANK -> RankControlWidget(control = control, controller = controller)
    ControlType.CONTROL_UPLOAD -> MediaCaptureWidget(control = control, controller = controller)
    ControlType.CONTROL_TRIGGER -> TriggerControlWidget(control = control, controller = controller)
    ControlType.CONTROL_INPUT,
    ControlType.CONTROL_TYPE_UNSPECIFIED ->
      InputByDataTypeWidget(control = control, controller = controller)
  }
}

@Composable
private fun ReadOnlyValueBox(fieldState: FieldState) {
  val colors = MaterialTheme.colorScheme
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainer),
  ) {
    Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
      Text(
        text = fieldValueDisplayText(fieldState.value, fieldState.dataType),
        style =
          MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
          ),
      )
    }
  }
}

@Composable
private fun InputByDataTypeWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
  val path = control.canonicalPath
  val fieldState = control.fieldState
  val isMultiline = "multiline" in appearanceTokens(control.appearance)

  when (fieldState.dataType) {
    DataType.TYPE_BOOLEAN -> BooleanInputWidget(path, fieldState, controller)
    DataType.TYPE_INT32,
    DataType.TYPE_INT64 -> IntegerInputWidget(path, fieldState, controller)
    DataType.TYPE_DOUBLE -> DecimalInputWidget(path, fieldState, controller)
    DataType.TYPE_DATE -> DateInputWidget(path, fieldState, controller)
    DataType.TYPE_TIME -> TimeInputWidget(path, fieldState, controller)
    DataType.TYPE_DATETIME -> TimestampInputWidget(path, fieldState, controller)
    DataType.TYPE_GEOPOINT ->
      GeoPointInputWidget(
        path = path,
        point = fieldState.value?.scalar_value?.geopoint_value,
        panAllowed = isMapPanAllowed(control.appearance),
        accuracyThreshold =
          control.controlDef.geo_config?.accuracy_threshold_meters?.takeIf { it > 0.0 },
        editState = controller.geoEditState(path),
        onPointChange = { point -> controller.answerGeoPoint(path, point) },
      )
    DataType.TYPE_GEOTRACE ->
      GeoGeometryDrawingWidget(
        vertices = fieldState.value?.scalar_value?.geotrace_value?.points.orEmpty(),
        isClosedShape = false,
        panAllowed = isMapPanAllowed(control.appearance),
        editState = controller.geoEditState(path),
        onVerticesChange = { points -> controller.updateGeoTrace(path, points) },
      )
    DataType.TYPE_GEOSHAPE ->
      GeoGeometryDrawingWidget(
        vertices = fieldState.value?.scalar_value?.geoshape_value?.points.orEmpty(),
        isClosedShape = true,
        panAllowed = isMapPanAllowed(control.appearance),
        editState = controller.geoEditState(path),
        onVerticesChange = { points -> controller.updateGeoShape(path, points) },
      )
    else -> StringInputWidget(path, fieldState, isMultiline, controller)
  }
}

@Composable
private fun RangeControlWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val path = control.canonicalPath
  val rangeCfg = control.controlDef.range_config
  val min = rangeCfg?.start ?: 0.0
  val max = (rangeCfg?.end ?: 100.0).let { if (it <= min) min + 10.0 else it }
  val step = (rangeCfg?.step ?: 1.0).let { if (it <= 0.0) 1.0 else it }

  val scalar = control.fieldState.value?.scalar_value
  val currentVal =
    scalar?.double_value
      ?: scalar?.int64_value?.toDouble()
      ?: scalar?.int32_value?.toDouble()
      ?: min

  fun applyRangeVal(nextVal: Double) {
    val snapped = (min + ((nextVal - min) / step).roundToInt() * step).coerceIn(min, max)
    if (
      control.fieldState.dataType == DataType.TYPE_INT32 ||
        control.fieldState.dataType == DataType.TYPE_INT64
    ) {
      controller.updateInt(path, snapped.roundToInt())
    } else {
      controller.updateDouble(path, (snapped * 1000.0).roundToInt() / 1000.0)
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(Res.string.forms_range_min, min.toString()),
        style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
      )
      TonalBadge(
        text = stringResource(Res.string.forms_range_selected, currentVal.toString()),
        tone = BadgeTone.PRIMARY,
      )
      Text(
        text = stringResource(Res.string.forms_range_max, max.toString()),
        style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
      )
    }
    Slider(
      value = currentVal.toFloat().coerceIn(min.toFloat(), max.toFloat()),
      onValueChange = { applyRangeVal(it.toDouble()) },
      valueRange = min.toFloat()..max.toFloat(),
    )
    // Like the number fields, the compact web layout relies on the slider alone.
    if (showNumericSteppers(LocalFormDensity.current)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(onClick = { applyRangeVal(currentVal - step) }) {
          Text("- $step", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        OutlinedButton(onClick = { applyRangeVal(currentVal + step) }) {
          Text("+ $step", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
      }
    }
  }
}

@Composable
private fun TriggerControlWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
  val path = control.canonicalPath
  val isAcknowledged = control.fieldState.value?.scalar_value?.string_value == "OK"

  if (isAcknowledged) {
    Button(onClick = { controller.clearField(path) }, modifier = Modifier.fillMaxWidth()) {
      Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        stringResource(Res.string.forms_trigger_acknowledged),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
      )
    }
  } else {
    FilledTonalButton(
      onClick = { controller.updateString(path, "OK") },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        stringResource(Res.string.forms_trigger_acknowledge),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
      )
    }
  }
}

/** Answers the geopoint question at [path] with [point], or clears it when [point] is `null`. */
private fun FormWizardController.answerGeoPoint(path: String, point: GeoPoint?) {
  if (point == null) {
    clearField(path)
  } else {
    updateGeoPoint(
      path = path,
      latitude = point.latitude,
      longitude = point.longitude,
      altitudeMeters = point.altitude_meters,
      accuracyMeters = point.accuracy_meters,
    )
  }
}
