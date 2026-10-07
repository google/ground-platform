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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.GeoPoint
import kotlin.math.abs
import kotlin.math.roundToInt
import org.groundplatform.v2.core.forms.media.MediaCapture
import org.groundplatform.v2.core.forms.model.ComponentState
import org.groundplatform.v2.core.forms.model.FieldState
import org.groundplatform.v2.core.forms.model.ResolvedChoiceOption
import org.groundplatform.v2.core.forms.model.ValidationStatus

/**
 * State passed to an optional host-provided map viewport ([LocalGeoPointMapViewport]) inside
 * [GeoPointInputWidget] so host apps (such as `devtools/prototypeApp`) can render a live Mapbox GL
 * JS basemap synchronized with the question's pan/zoom and GPS state.
 */
data class GeoPointMapViewportState(
  val path: String,
  val panAllowed: Boolean,
  val isPanned: Boolean,
  val liveGpsLatitude: Double,
  val liveGpsLongitude: Double,
  val targetLatitude: Double,
  val targetLongitude: Double,
  val accuracyMeters: Double,
  val capturedPoint: GeoPoint?,
  val panOffsetLat: Double,
  val panOffsetLon: Double,
  val zoomLevel: Float,
  val onPanDeltaPixels: (dxPx: Float, dyPx: Float, widthPx: Float, heightPx: Float) -> Unit,
  val onZoomDelta: (deltaZoom: Float) -> Unit,
  val onRecenterGps: () -> Unit,
)

/**
 * Optional composition local allowing a host application (such as `devtools/prototypeApp`) to
 * render a live platform basemap inside [GeoPointInputWidget]'s map viewport. When `null`,
 * [GeoPointInputWidget] renders its built-in interactive satellite/topographic Compose `Canvas`
 * basemap.
 */
val LocalGeoPointMapViewport:
  ProvidableCompositionLocal<(@Composable (GeoPointMapViewportState) -> Unit)?> =
  compositionLocalOf {
    null
  }

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
  val labelText =
    control.label?.text?.takeIf { it.isNotBlank() } ?: control.canonicalPath.substringAfterLast('/')
  val hintText = control.hint?.text?.takeIf { it.isNotBlank() }
  val guidanceText =
    control.label?.guidanceText?.takeIf { it.isNotBlank() }
      ?: control.hint?.guidanceText?.takeIf { it.isNotBlank() }
  var isGuidanceExpanded by remember(control.canonicalPath) { mutableStateOf(false) }

  ElevatedCard(
    modifier =
      modifier
        .fillMaxWidth()
        .border(1.dp, colors.outlineVariant.copy(alpha = 0.45f), MaterialTheme.shapes.large),
    shape = MaterialTheme.shapes.large,
    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainerLowest),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Top metadata badges (required, read-only / calculated). The field's data name is internal
      // and never shown to collectors.
      if (fieldState.isRequired || fieldState.isCalculated || fieldState.isReadOnly) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (fieldState.isRequired) {
            GroundTonalBadge(text = "Required *", tone = GroundBadgeTone.ERROR)
          }
          if (fieldState.isCalculated) {
            GroundTonalBadge(text = "Calculated", tone = GroundBadgeTone.TERTIARY)
          } else if (fieldState.isReadOnly) {
            GroundTonalBadge(text = "Read-only", tone = GroundBadgeTone.NEUTRAL)
          }
        }
      }

      // Primary Question Prompt Label
      Row(verticalAlignment = Alignment.Top) {
        Text(
          text = labelText,
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
            Text(
              text =
                if (isGuidanceExpanded) "▼ Hide Enumerator Guidance"
                else "▶ Show Enumerator Guidance",
              style =
                MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = colors.onSecondaryContainer,
                ),
            )
            if (isGuidanceExpanded) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = guidanceText,
                style =
                  MaterialTheme.typography.bodySmall.copy(color = colors.onSecondaryContainer),
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
            GroundTonalBadge(media.image_uri, GroundBadgeTone.PRIMARY)
          }
          if (media.audio_uri.isNotBlank()) {
            GroundTonalBadge(media.audio_uri, GroundBadgeTone.SECONDARY)
          }
          if (media.video_uri.isNotBlank()) {
            GroundTonalBadge(media.video_uri, GroundBadgeTone.TERTIARY)
          }
        }
      }

      // Interactive Control Widget
      ControlWidget(control = control, controller = controller)

      // Validation Error Banner
      val status = fieldState.validationStatus
      if (status is ValidationStatus.Invalid && (showValidationErrors || !fieldState.isEmpty)) {
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
        text = formatFieldValueForDisplay(fieldState.value, fieldState.dataType),
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
  val isMultiline = control.appearance.split(' ').contains("multiline")

  when (fieldState.dataType) {
    DataType.TYPE_BOOLEAN -> BooleanInputWidget(path, fieldState, controller)
    DataType.TYPE_INT32,
    DataType.TYPE_INT64 -> IntegerInputWidget(path, fieldState, controller)
    DataType.TYPE_DOUBLE -> DecimalInputWidget(path, fieldState, controller)
    DataType.TYPE_DATE -> DateInputWidget(path, fieldState, controller)
    DataType.TYPE_TIME -> TimeInputWidget(path, fieldState, controller)
    DataType.TYPE_DATETIME -> TimestampInputWidget(path, fieldState, controller)
    DataType.TYPE_GEOPOINT -> GeoPointInputWidget(control, path, fieldState, controller)
    DataType.TYPE_GEOTRACE ->
      GeoGeometryDrawingWidget(control, path, fieldState, controller, isClosedShape = false)
    DataType.TYPE_GEOSHAPE ->
      GeoGeometryDrawingWidget(control, path, fieldState, controller, isClosedShape = true)
    else -> StringInputWidget(path, fieldState, isMultiline, controller)
  }
}

@Composable
private fun StringInputWidget(
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
    GroundOutlinedTextField(
      value = text,
      onValueChange = { newText ->
        text = newText
        controller.updateString(path, newText)
      },
      modifier = Modifier.fillMaxWidth().let { if (isMultiline) it.height(120.dp) else it },
      singleLine = !isMultiline,
      placeholder = { Text("Enter response...") },
    )
    if (text.isNotEmpty()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${text.length} chars",
          style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
        )
        TextButton(
          onClick = {
            text = ""
            controller.clearField(path)
          }
        ) {
          Text("Clear", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}

/**
 * Shared layout of the integer and decimal inputs: a single-line text field
 * ([GroundOutlinedTextField]), flanked by decrement / increment stepper buttons when
 * [showNumericSteppers] allows (the mobile layout), and the parse error underneath. Under
 * [FormDensity.COMPACT] there are no steppers and the text field takes the full width.
 */
@Composable
private fun NumberField(
  text: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  parseError: String?,
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
      GroundOutlinedTextField(
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
        text = parseError,
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
private fun IntegerInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val scalar = fieldState.value?.scalar_value
  val initialNumber: Long? = scalar?.int64_value ?: scalar?.int32_value?.toLong()
  var text by remember(path, initialNumber) { mutableStateOf(initialNumber?.toString() ?: "") }
  var parseError by remember(path) { mutableStateOf<String?>(null) }

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
private fun DecimalInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val initialDouble: Double? = fieldState.value?.scalar_value?.double_value
  var text by remember(path) { mutableStateOf(initialDouble?.toString() ?: "") }
  var parseError by remember(path) { mutableStateOf<String?>(null) }

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
          parseError = "Enter a valid decimal number"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BooleanInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val currentBool = fieldState.value?.scalar_value?.bool_value
  val options = listOf(true to "Yes (True)", false to "No (False)")
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    options.forEachIndexed { index, (boolVal, label) ->
      SegmentedButton(
        selected = currentBool == boolVal,
        onClick = { controller.updateBoolean(path, boolVal) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
        label = {
          Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
        },
      )
    }
  }
}

@Composable
private fun DateInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val dateProto = fieldState.value?.scalar_value?.date_value
  val year = dateProto?.year?.takeIf { it > 0 } ?: 2026
  val month = dateProto?.month?.takeIf { it in 1..12 } ?: 9
  val day = dateProto?.day?.takeIf { it in 1..31 } ?: 18
  val formatted =
    if (dateProto != null && dateProto.year > 0) {
      "${dateProto.year.toString().padStart(4, '0')}-${dateProto.month.toString().padStart(2, '0')}-${dateProto.day.toString().padStart(2, '0')}"
    } else {
      ""
    }
  var text by remember(path, formatted) { mutableStateOf(formatted) }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      GroundOutlinedTextField(
        value = text,
        onValueChange = { raw ->
          text = raw
          val parts = raw.trim().split('-')
          if (parts.size == 3) {
            val y = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            val d = parts[2].toIntOrNull()
            if (y != null && m != null && d != null && m in 1..12 && d in 1..31) {
              controller.updateDate(path, y, m, d)
            }
          } else if (raw.isBlank()) {
            controller.clearField(path)
          }
        },
        modifier = Modifier.weight(1f),
        singleLine = true,
        placeholder = { Text("YYYY-MM-DD") },
      )
      FilledTonalButton(
        onClick = {
          val epochDays = controller.session.environment.clockEpochMillis() / 86_400_000L
          val z = epochDays + 719468L
          val era = (if (z >= 0) z else z - 146096L) / 146097L
          val doe = z - era * 146097L
          val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
          val y = yoe + era * 400L
          val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
          val mp = (5L * doy + 2L) / 153L
          val d = (doy - (153L * mp + 2L) / 5L + 1L).toInt()
          val m = (mp + (if (mp < 10L) 3L else -9L)).toInt()
          val civilYear = (y + (if (m <= 2) 1L else 0L)).toInt()
          controller.updateDate(path, civilYear, m, d)
        }
      ) {
        Text("Today", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
      }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(
        onClick = { controller.updateDate(path, year, month, (day - 1).coerceIn(1, 28)) },
        modifier = Modifier.weight(1f),
      ) {
        Text(
          "-1 Day",
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          softWrap = false,
        )
      }
      OutlinedButton(
        onClick = { controller.updateDate(path, year, month, (day + 1).coerceIn(1, 28)) },
        modifier = Modifier.weight(1f),
      ) {
        Text(
          "+1 Day",
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          softWrap = false,
        )
      }
      OutlinedButton(
        onClick = { controller.updateDate(path, year, (month % 12) + 1, day) },
        modifier = Modifier.weight(1f),
      ) {
        Text(
          "+1 Month",
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          softWrap = false,
        )
      }
    }
  }
}

@Composable
private fun TimeInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val timeProto = fieldState.value?.scalar_value?.time_value
  val formatted =
    if (timeProto != null) {
      "${timeProto.hours.toString().padStart(2, '0')}:${timeProto.minutes.toString().padStart(2, '0')}:${timeProto.seconds.toString().padStart(2, '0')}"
    } else {
      ""
    }
  var text by remember(path, formatted) { mutableStateOf(formatted) }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GroundOutlinedTextField(
      value = text,
      onValueChange = { raw ->
        text = raw
        val parts = raw.trim().split(':')
        if (parts.size in 2..3) {
          val h = parts[0].toIntOrNull()
          val m = parts[1].toIntOrNull()
          val s = parts.getOrNull(2)?.toIntOrNull() ?: 0
          if (h != null && m != null && h in 0..23 && m in 0..59 && s in 0..59) {
            controller.updateTime(path, h, m, s)
          }
        } else if (raw.isBlank()) {
          controller.clearField(path)
        }
      },
      modifier = Modifier.weight(1f),
      singleLine = true,
      placeholder = { Text("HH:MM:SS") },
    )
    FilledTonalButton(
      onClick = {
        val totalSec =
          ((controller.session.environment.clockEpochMillis() / 1000L) % 86400L + 86400L) % 86400L
        val hours = (totalSec / 3600L).toInt()
        val minutes = ((totalSec % 3600L) / 60L).toInt()
        val seconds = (totalSec % 60L).toInt()
        controller.updateTime(path, hours, minutes, seconds)
      }
    ) {
      Text("Now", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
  }
}

@Composable
private fun TimestampInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val ts = fieldState.value?.scalar_value?.timestamp_value
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = ts?.toString() ?: "(No timestamp recorded)",
      style =
        MaterialTheme.typography.bodyMedium.copy(
          color = if (ts != null) colors.onSurface else colors.onSurfaceVariant
        ),
      modifier = Modifier.weight(1f),
    )
    Button(
      onClick = {
        val epochSeconds = controller.session.environment.clockEpochMillis() / 1000L
        controller.updateTimestamp(path, epochSeconds)
      }
    ) {
      Text("Capture Timestamp", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
  }
}

/**
 * Returns `true` when [appearance] allows panning the map to place a point manually
 * (`placement-map`, `map`, or `maps`). When `false`, point capture is locked to the user's live GPS
 * position.
 */
fun isGeoPointPanAllowed(appearance: String): Boolean {
  val tokens = appearance.split(' ').filter { it.isNotBlank() }
  return tokens.any {
    it.equals("placement-map", ignoreCase = true) ||
      it.equals("map", ignoreCase = true) ||
      it.equals("maps", ignoreCase = true)
  }
}

/**
 * Returns the primary action trigger label for a `geopoint` question:
 * - `"Add point"` when the map has been panned away from the user's GPS position ([isPanned] is
 *   `true`)
 * - `"Capture location"` when centered on the user's GPS position ([isPanned] is `false`)
 */
fun geoPointPrimaryTriggerLabel(isPanned: Boolean): String =
  if (isPanned) "Add point" else "Capture location"

/** Rounds a geographic coordinate to 6 decimal places (~0.11 m precision). */
internal fun roundGeoCoord6(value: Double): Double =
  (value * 1_000_000.0).roundToInt() / 1_000_000.0

/** Formats a decimal degree value to 6 decimal places. */
private fun formatDecimal6(value: Double): String {
  val rounded = roundGeoCoord6(abs(value))
  val whole = rounded.toInt()
  val frac = ((rounded - whole) * 1_000_000.0).roundToInt().toString().padStart(6, '0')
  return "$whole.$frac"
}

/**
 * Formats [latitude] and [longitude] into a cardinal degree string (e.g. `"1.292066° S, 36.821946°
 * E"`).
 */
fun formatGeoPointCoordinates(latitude: Double, longitude: Double): String {
  val latHemisphere = if (latitude < 0.0) "S" else "N"
  val lonHemisphere = if (longitude < 0.0) "W" else "E"
  return "${formatDecimal6(latitude)}° $latHemisphere, ${formatDecimal6(longitude)}° $lonHemisphere"
}

/** Formats horizontal GPS accuracy in meters (e.g. `"±3.2 m"`). */
fun formatGeoPointAccuracy(accuracyMeters: Double): String {
  val tenths = (accuracyMeters * 10.0).roundToInt() / 10.0
  return "±$tenths m"
}

/** Material Design 3 Undo icon. */
val UndoIcon: ImageVector by lazy {
  ImageVector.Builder(
      name = "Undo",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12.5f, 8.0f)
        curveToRelative(-2.65f, 0.0f, -5.05f, 0.99f, -6.9f, 2.6f)
        lineTo(2.0f, 7.0f)
        verticalLineToRelative(9.0f)
        horizontalLineToRelative(9.0f)
        lineToRelative(-3.62f, -3.62f)
        curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
        curveToRelative(3.54f, 0.0f, 6.55f, 2.31f, 7.6f, 5.5f)
        lineToRelative(2.37f, -0.78f)
        curveTo(21.08f, 11.03f, 17.15f, 8.0f, 12.5f, 8.0f)
        close()
      }
    }
    .build()
}

/** Material Design 3 Redo icon. */
val RedoIcon: ImageVector by lazy {
  ImageVector.Builder(
      name = "Redo",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .apply {
      path(fill = SolidColor(Color.Black)) {
        moveTo(18.4f, 10.6f)
        curveTo(16.55f, 8.99f, 14.15f, 8.0f, 11.5f, 8.0f)
        curveToRelative(-4.65f, 0.0f, -8.58f, 3.03f, -9.96f, 7.22f)
        lineTo(3.9f, 16.0f)
        curveToRelative(1.05f, -3.19f, 4.05f, -5.5f, 7.6f, -5.5f)
        curveToRelative(1.95f, 0.0f, 3.73f, 0.72f, 5.12f, 1.88f)
        lineTo(13.0f, 16.0f)
        horizontalLineToRelative(9.0f)
        verticalLineTo(7.0f)
        lineToRelative(-3.6f, 3.6f)
        close()
      }
    }
    .build()
}

@Composable
private fun GeoPointInputWidget(
  control: ComponentState.ControlState,
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val gp = fieldState.value?.scalar_value?.geopoint_value
  val panAllowed = isGeoPointPanAllowed(control.appearance)
  val accuracyThreshold =
    control.controlDef.geo_config?.accuracy_threshold_meters?.takeIf { it > 0.0 }

  var liveGpsLat by remember(path) { mutableStateOf(gp?.latitude ?: -1.292066) }
  var liveGpsLon by remember(path) { mutableStateOf(gp?.longitude ?: 36.821946) }
  var liveGpsAlt by remember(path) { mutableStateOf(gp?.altitude_meters ?: 1680.0) }
  var liveGpsAcc by remember(path) { mutableStateOf(gp?.accuracy_meters ?: 3.2) }
  var panOffsetLat by remember(path) { mutableStateOf(0.0) }
  var panOffsetLon by remember(path) { mutableStateOf(0.0) }
  var zoomLevel by remember(path) { mutableStateOf(17.5f) }
  var undoPointHistory by remember(path) { mutableStateOf<List<GeoPoint?>>(emptyList()) }
  var redoPointHistory by remember(path) { mutableStateOf<List<GeoPoint?>>(emptyList()) }

  val isPanned = panAllowed && (abs(panOffsetLat) > 0.0000005 || abs(panOffsetLon) > 0.0000005)
  val targetLat = roundGeoCoord6(liveGpsLat + if (panAllowed) panOffsetLat else 0.0)
  val targetLon = roundGeoCoord6(liveGpsLon + if (panAllowed) panOffsetLon else 0.0)

  val displayLat = if (isPanned) targetLat else (gp?.latitude ?: liveGpsLat)
  val displayLon = if (isPanned) targetLon else (gp?.longitude ?: liveGpsLon)
  val displayAlt = if (isPanned) liveGpsAlt else (gp?.altitude_meters ?: liveGpsAlt)
  val displayAcc = if (isPanned) liveGpsAcc else (gp?.accuracy_meters ?: liveGpsAcc)
  val meetsAccuracy = accuracyThreshold == null || displayAcc <= accuracyThreshold
  val triggerLabel = geoPointPrimaryTriggerLabel(isPanned)

  val viewportState =
    GeoPointMapViewportState(
      path = path,
      panAllowed = panAllowed,
      isPanned = isPanned,
      liveGpsLatitude = liveGpsLat,
      liveGpsLongitude = liveGpsLon,
      targetLatitude = targetLat,
      targetLongitude = targetLon,
      accuracyMeters = displayAcc,
      capturedPoint = gp,
      panOffsetLat = panOffsetLat,
      panOffsetLon = panOffsetLon,
      zoomLevel = zoomLevel,
      onPanDeltaPixels = { dxPx, dyPx, widthPx, heightPx ->
        if (panAllowed && widthPx > 0f && heightPx > 0f) {
          val spanDeg = 0.0016 * (17.5f / zoomLevel.coerceIn(13f, 20f))
          panOffsetLon = (panOffsetLon - (dxPx / widthPx) * spanDeg).coerceIn(-0.02, 0.02)
          panOffsetLat = (panOffsetLat + (dyPx / heightPx) * spanDeg).coerceIn(-0.02, 0.02)
        }
      },
      onZoomDelta = { delta -> zoomLevel = (zoomLevel + delta).coerceIn(13.5f, 19.5f) },
      onRecenterGps = {
        panOffsetLat = 0.0
        panOffsetLon = 0.0
      },
    )

  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Top capability & status badges
      @OptIn(ExperimentalLayoutApi::class)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        GroundTonalBadge(
          text = if (panAllowed) "Map pan allowed" else "GPS only (no pan)",
          icon = if (!panAllowed) Icons.Default.Lock else null,
          tone = if (panAllowed) GroundBadgeTone.PRIMARY else GroundBadgeTone.TERTIARY,
        )
        if (accuracyThreshold != null) {
          GroundTonalBadge(
            text = "Required ≤ ${accuracyThreshold} m",
            icon = Icons.Default.Info,
            tone = if (meetsAccuracy) GroundBadgeTone.SECONDARY else GroundBadgeTone.ERROR,
          )
        }
        GroundTonalBadge(
          text =
            when {
              isPanned -> "Panned"
              gp != null -> "Captured"
              else -> "Ready"
            },
          icon = if (gp != null) Icons.Default.Check else null,
          tone =
            when {
              isPanned -> GroundBadgeTone.TERTIARY
              gp != null -> GroundBadgeTone.SECONDARY
              else -> GroundBadgeTone.NEUTRAL
            },
        )
      }

      // Interactive Map Viewport for Add Point / Capture Location
      GeoPointInteractiveMapBox(viewportState = viewportState)

      // Read-only Coordinates & Accuracy Display Card (no manual coordinate text entry)
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainerHighest.copy(alpha = 0.65f),
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text =
                  when {
                    isPanned -> "Target coordinates (panned crosshair)"
                    gp != null -> "Captured coordinates"
                    else -> "Current GPS coordinates"
                  },
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                  ),
              )
              Text(
                text = formatGeoPointCoordinates(displayLat, displayLon),
                style =
                  MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                  ),
              )
              Text(
                text = "$displayLat, $displayLon • Alt ${displayAlt.roundToInt()} m",
                style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
              )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Accuracy Readout Pill
            Surface(
              shape = MaterialTheme.shapes.small,
              color =
                if (meetsAccuracy) {
                  colors.secondaryContainer
                } else {
                  colors.errorContainer
                },
              contentColor =
                if (meetsAccuracy) {
                  colors.onSecondaryContainer
                } else {
                  colors.onErrorContainer
                },
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.End,
              ) {
                Text(text = "Accuracy", style = MaterialTheme.typography.labelSmall)
                Text(
                  text = formatGeoPointAccuracy(displayAcc),
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
              }
            }
          }

          if (isPanned && gp != null) {
            Text(
              text =
                "Saved point: ${gp.latitude}, ${gp.longitude} (${formatGeoPointAccuracy(gp.accuracy_meters)}) — tap \"Add point\" to update, or use Undo / Redo",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = colors.primary,
                  fontWeight = FontWeight.Medium,
                ),
            )
          } else if (gp != null) {
            Text(
              text =
                "Location captured: ${gp.latitude}, ${gp.longitude} (${formatGeoPointAccuracy(gp.accuracy_meters)}) — tap \"Next →\" to continue, or use Undo / Redo",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = colors.primary,
                  fontWeight = FontWeight.Medium,
                ),
            )
          } else {
            Text(
              text =
                if (isPanned) {
                  "Map panned — tap \"Add point\" below to record this point"
                } else {
                  "Tap \"Capture location\" below to record your current GPS coordinates"
                },
              style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
            )
          }
        }
      }

      // Primary Action Row: [Undo] [Redo] [Capture location / Add point] [Recenter]
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedIconButton(
          onClick = {
            if (undoPointHistory.isNotEmpty()) {
              val prevPoint = undoPointHistory.last()
              undoPointHistory = undoPointHistory.dropLast(1)
              redoPointHistory = redoPointHistory + listOf(gp)
              if (prevPoint != null) {
                controller.updateGeoPoint(
                  path = path,
                  latitude = prevPoint.latitude,
                  longitude = prevPoint.longitude,
                  altitudeMeters = prevPoint.altitude_meters,
                  accuracyMeters = prevPoint.accuracy_meters,
                )
              } else {
                controller.clearField(path)
              }
            } else if (gp != null) {
              redoPointHistory = redoPointHistory + listOf(gp)
              controller.clearField(path)
            }
          },
          enabled = undoPointHistory.isNotEmpty() || gp != null,
        ) {
          Icon(imageVector = UndoIcon, contentDescription = "Undo", modifier = Modifier.size(20.dp))
        }

        OutlinedIconButton(
          onClick = {
            if (redoPointHistory.isNotEmpty()) {
              val nextPoint = redoPointHistory.last()
              redoPointHistory = redoPointHistory.dropLast(1)
              undoPointHistory = undoPointHistory + listOf(gp)
              if (nextPoint != null) {
                controller.updateGeoPoint(
                  path = path,
                  latitude = nextPoint.latitude,
                  longitude = nextPoint.longitude,
                  altitudeMeters = nextPoint.altitude_meters,
                  accuracyMeters = nextPoint.accuracy_meters,
                )
              } else {
                controller.clearField(path)
              }
            }
          },
          enabled = redoPointHistory.isNotEmpty(),
        ) {
          Icon(imageVector = RedoIcon, contentDescription = "Redo", modifier = Modifier.size(20.dp))
        }

        Button(
          onClick = {
            val captureLat = if (isPanned) targetLat else liveGpsLat
            val captureLon = if (isPanned) targetLon else liveGpsLon
            undoPointHistory = undoPointHistory + listOf(gp)
            redoPointHistory = emptyList()
            controller.updateGeoPoint(
              path = path,
              latitude = captureLat,
              longitude = captureLon,
              altitudeMeters = liveGpsAlt,
              accuracyMeters = liveGpsAcc,
            )
          },
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Icon(
            imageVector = if (isPanned) Icons.Default.Place else Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = triggerLabel,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
        }

        if (isPanned) {
          OutlinedIconButton(onClick = { viewportState.onRecenterGps() }) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Recenter on GPS",
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }

      if (!panAllowed) {
        Text(
          text =
            "Map panning is disabled for this question. Coordinates are captured directly from your GPS location" +
              (if (accuracyThreshold != null) " (requires ≤ ${accuracyThreshold} m accuracy)."
              else "."),
          style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
        )
      }
    }
  }
}

/**
 * Interactive map viewport rendered inside [GeoPointInputWidget] for `TYPE_GEOPOINT` questions.
 *
 * Delegates basemap rendering to [LocalGeoPointMapViewport] when provided by the host (e.g., live
 * Mapbox GL JS basemap in `devtools/prototypeApp`), or renders a built-in interactive satellite and
 * topographic Compose `Canvas` basemap with drag-to-pan gestures, GPS blue dot + accuracy halo,
 * center target crosshair reticle, captured pin marker, zoom controls, and Recenter pill.
 */
@Composable
private fun GeoPointInteractiveMapBox(viewportState: GeoPointMapViewportState) {
  val hostMapViewport = LocalGeoPointMapViewport.current
  val mapShape = RoundedCornerShape(12.dp)

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .height(216.dp)
        .clip(mapShape)
        .border(1.dp, Color(0xFF2D5944), mapShape)
  ) {
    if (hostMapViewport != null) {
      hostMapViewport(viewportState)
    } else {
      GeoPointFallbackCanvasMap(viewportState = viewportState)
    }

    // Center Target Crosshair / Reticle + Captured Pin Overlay
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val spanDeg = 0.0016f * (17.5f / viewportState.zoomLevel.coerceIn(13f, 20f))

      // If a point is already captured, draw its saved pin marker relative to the current target
      // center
      val captured = viewportState.capturedPoint
      if (captured != null) {
        val dLon = (captured.longitude - viewportState.targetLongitude).toFloat()
        val dLat = (captured.latitude - viewportState.targetLatitude).toFloat()
        val savedX = center.x + (dLon / spanDeg) * size.width
        val savedY = center.y - (dLat / spanDeg) * size.height
        if (savedX in 0f..size.width && savedY in 0f..size.height) {
          drawCircle(
            color = Color(0xFF00E676).copy(alpha = 0.28f),
            radius = 14.dp.toPx(),
            center = Offset(savedX, savedY),
          )
          drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(savedX, savedY))
          drawCircle(
            color = Color(0xFF00C853),
            radius = 5.dp.toPx(),
            center = Offset(savedX, savedY),
          )
        }
      }

      // Draw center target reticle / crosshair
      val reticleColor =
        if (viewportState.isPanned) {
          Color(0xFFFFD54F)
        } else {
          Color(0xFF8BD6B1)
        }
      val ringRadius = 18.dp.toPx()
      val tickInner = 7.dp.toPx()
      val tickOuter = 25.dp.toPx()

      // Outer target ring
      drawCircle(
        color = Color(0xFF091812).copy(alpha = 0.65f),
        radius = ringRadius,
        center = center,
        style = Stroke(width = 3.5.dp.toPx()),
      )
      drawCircle(
        color = reticleColor,
        radius = ringRadius,
        center = center,
        style = Stroke(width = 2.dp.toPx()),
      )

      // 4 crosshair ticks (W, E, N, S)
      drawLine(
        color = reticleColor,
        start = Offset(center.x - tickOuter, center.y),
        end = Offset(center.x - tickInner, center.y),
        strokeWidth = 2.dp.toPx(),
      )
      drawLine(
        color = reticleColor,
        start = Offset(center.x + tickInner, center.y),
        end = Offset(center.x + tickOuter, center.y),
        strokeWidth = 2.dp.toPx(),
      )
      drawLine(
        color = reticleColor,
        start = Offset(center.x, center.y - tickOuter),
        end = Offset(center.x, center.y - tickInner),
        strokeWidth = 2.dp.toPx(),
      )
      drawLine(
        color = reticleColor,
        start = Offset(center.x, center.y + tickInner),
        end = Offset(center.x, center.y + tickOuter),
        strokeWidth = 2.dp.toPx(),
      )

      // Center point dot
      drawCircle(color = Color(0xFF091812), radius = 4.5.dp.toPx(), center = center)
      drawCircle(
        color = if (viewportState.isPanned) Color(0xFFFFB300) else Color(0xFF00E676),
        radius = 3.dp.toPx(),
        center = center,
      )
    }

    // Top-left floating status pill
    Surface(
      modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
      shape = RoundedCornerShape(8.dp),
      color = Color(0xFF0A1F16).copy(alpha = 0.88f),
      border = BorderStroke(1.dp, Color(0xFF2D5944)),
    ) {
      val textColor = if (viewportState.isPanned) Color(0xFFFFE082) else Color(0xFFB7F1B9)
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(
          imageVector =
            when {
              !viewportState.panAllowed -> Icons.Default.Lock
              viewportState.isPanned -> Icons.Default.Place
              else -> Icons.Default.LocationOn
            },
          contentDescription = null,
          tint = textColor,
          modifier = Modifier.size(13.dp),
        )
        Text(
          text =
            when {
              !viewportState.panAllowed -> "GPS locked"
              viewportState.isPanned -> "Panned • Crosshair active"
              else -> "Centered on GPS (drag map to pan)"
            },
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = textColor,
              fontWeight = FontWeight.SemiBold,
            ),
        )
      }
    }

    // Top-right Zoom +/- buttons
    Column(
      modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Surface(
        modifier =
          Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable {
            viewportState.onZoomDelta(0.75f)
          },
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF0A1F16).copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color(0xFF2D5944)),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = "+",
            style =
              MaterialTheme.typography.titleSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
              ),
          )
        }
      }
      Surface(
        modifier =
          Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable {
            viewportState.onZoomDelta(-0.75f)
          },
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF0A1F16).copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color(0xFF2D5944)),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = "−",
            style =
              MaterialTheme.typography.titleSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
              ),
          )
        }
      }
    }

    // Bottom-left GPS Accuracy & Zoom HUD pill
    Surface(
      modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
      shape = RoundedCornerShape(8.dp),
      color = Color(0xFF0A1F16).copy(alpha = 0.88f),
      border = BorderStroke(1.dp, Color(0xFF2D5944)),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Default.LocationOn,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(13.dp),
        )
        Text(
          text =
            "GPS ${formatGeoPointAccuracy(viewportState.accuracyMeters)} • ${((viewportState.zoomLevel * 10f).roundToInt() / 10f)}z",
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
            ),
        )
      }
    }

    // Bottom-right Recenter button when panned
    if (viewportState.isPanned) {
      Surface(
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { viewportState.onRecenterGps() },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0E2219).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, Color(0xFF8BD6B1)),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = Color(0xFF8BD6B1),
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Recenter on GPS",
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF8BD6B1),
                fontWeight = FontWeight.Bold,
              ),
          )
        }
      }
    }
  }
}

/**
 * Built-in interactive satellite/topographic Compose [Canvas] map used when
 * [LocalGeoPointMapViewport] is not provided (e.g. in `devtools/formdebugger` or unit previews).
 */
@Composable
private fun GeoPointFallbackCanvasMap(viewportState: GeoPointMapViewportState) {
  Canvas(
    modifier =
      Modifier.fillMaxSize().background(Color(0xFF10261C)).pointerInput(
        viewportState.path,
        viewportState.panAllowed,
        viewportState.zoomLevel,
      ) {
        if (viewportState.panAllowed) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            viewportState.onPanDeltaPixels(
              dragAmount.x,
              dragAmount.y,
              size.width.toFloat(),
              size.height.toFloat(),
            )
          }
        }
      }
  ) {
    val spanDeg = 0.0016f * (17.5f / viewportState.zoomLevel.coerceIn(13f, 20f))
    val shiftX = (-viewportState.panOffsetLon.toFloat() / spanDeg) * size.width
    val shiftY = (viewportState.panOffsetLat.toFloat() / spanDeg) * size.height

    // Vegetated field parcels background
    drawRect(
      color = Color(0xFF173828),
      topLeft = Offset(size.width * 0.08f + shiftX, size.height * 0.10f + shiftY),
      size = Size(size.width * 0.36f, size.height * 0.34f),
    )
    drawRect(
      color = Color(0xFF1C422F),
      topLeft = Offset(size.width * 0.52f + shiftX, size.height * 0.16f + shiftY),
      size = Size(size.width * 0.38f, size.height * 0.42f),
    )
    drawRect(
      color = Color(0xFF153324),
      topLeft = Offset(size.width * 0.18f + shiftX, size.height * 0.56f + shiftY),
      size = Size(size.width * 0.44f, size.height * 0.32f),
    )

    // Subtle survey coordinate grid lines
    val gridStepX = size.width / 6f
    val gridStepY = size.height / 4f
    val offsetModX = ((shiftX % gridStepX) + gridStepX) % gridStepX
    val offsetModY = ((shiftY % gridStepY) + gridStepY) % gridStepY
    for (i in -1..6) {
      val x = i * gridStepX + offsetModX
      drawLine(
        color = Color.White.copy(alpha = 0.08f),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 1f,
      )
    }
    for (j in -1..4) {
      val y = j * gridStepY + offsetModY
      drawLine(
        color = Color.White.copy(alpha = 0.08f),
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1f,
      )
    }

    // Topographic stream / trail line
    val streamPath =
      Path().apply {
        moveTo(0f + shiftX * 0.5f, size.height * 0.78f + shiftY)
        cubicTo(
          size.width * 0.35f + shiftX,
          size.height * 0.62f + shiftY,
          size.width * 0.65f + shiftX,
          size.height * 0.40f + shiftY,
          size.width * 1.05f + shiftX * 0.5f,
          size.height * 0.18f + shiftY,
        )
      }
    drawPath(
      path = streamPath,
      color = Color(0xFF4FC3F7).copy(alpha = 0.28f),
      style =
        Stroke(
          width = 2.5.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
        ),
    )

    // User's live GPS blue dot + horizontal accuracy halo
    val gpsCenter = Offset(x = size.width / 2f + shiftX, y = size.height / 2f + shiftY)
    val haloRadiusDp = (viewportState.accuracyMeters.toFloat() * 3.2f).coerceIn(14f, 56f).dp
    drawCircle(
      color = Color(0xFF42A5F5).copy(alpha = 0.22f),
      radius = haloRadiusDp.toPx(),
      center = gpsCenter,
    )
    drawCircle(
      color = Color(0xFF64B5F6).copy(alpha = 0.55f),
      radius = haloRadiusDp.toPx(),
      center = gpsCenter,
      style = Stroke(width = 1.2.dp.toPx()),
    )
    drawCircle(color = Color.White, radius = 7.dp.toPx(), center = gpsCenter)
    drawCircle(color = Color(0xFF1E88E5), radius = 5.dp.toPx(), center = gpsCenter)
  }
}

/**
 * Interactive map-based drawing widget for `TYPE_GEOTRACE` (linestring / transect) and
 * `TYPE_GEOSHAPE` (polygon / boundary) questions.
 *
 * Supports both:
 * - **Pannable mode** (`appearance="placement-map"`, `"map"`, `"maps"`, or `"walk-or-draw"`):
 *   collector can pan the map to place vertices with `"Add point"`.
 * - **Locked GPS mode** (default / GPS walk mode): map is locked to user GPS; collector records
 *   vertices along their walking path with `"Capture vertex"`.
 *
 * Provides:
 * - Interactive Compose Canvas map rendering live crosshair target reticle, GPS position dot,
 *   vertices, connecting polylines, and closed polygon fill.
 * - Primary action button: `"Add point"` (when panned) or `"Capture vertex"` (when on GPS).
 * - Action buttons: `"Undo vertex"`, `"Clear"`, and `"Recenter"`.
 * - Status HUD indicating vertex count, shape completion, and pan / lock mode.
 */
@Composable
private fun GeoGeometryDrawingWidget(
  control: ComponentState.ControlState,
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
  isClosedShape: Boolean,
) {
  val colors = MaterialTheme.colorScheme
  val appearanceTokens = control.appearance.split(' ').filter { it.isNotBlank() }
  val panAllowed = appearanceTokens.any {
    it.equals("placement-map", ignoreCase = true) ||
      it.equals("walk-or-draw", ignoreCase = true) ||
      it.equals("map", ignoreCase = true) ||
      it.equals("maps", ignoreCase = true)
  }

  val existingPoints: List<GeoPoint> =
    if (isClosedShape) {
      fieldState.value?.scalar_value?.geoshape_value?.points ?: emptyList()
    } else {
      fieldState.value?.scalar_value?.geotrace_value?.points ?: emptyList()
    }

  val firstExisting = existingPoints.firstOrNull()
  var liveGpsLat by remember(path) { mutableStateOf(firstExisting?.latitude ?: -1.292066) }
  var liveGpsLon by remember(path) { mutableStateOf(firstExisting?.longitude ?: 36.821946) }
  var liveGpsAlt by remember(path) { mutableStateOf(firstExisting?.altitude_meters ?: 1680.0) }
  var liveGpsAcc by remember(path) { mutableStateOf(firstExisting?.accuracy_meters ?: 3.2) }
  var panOffsetLat by remember(path) { mutableStateOf(0.0) }
  var panOffsetLon by remember(path) { mutableStateOf(0.0) }
  var zoomLevel by remember(path) { mutableStateOf(17.5f) }
  var undoGeometryHistory by remember(path) { mutableStateOf<List<List<GeoPoint>>>(emptyList()) }
  var redoGeometryHistory by remember(path) { mutableStateOf<List<List<GeoPoint>>>(emptyList()) }

  val isPanned = panAllowed && (abs(panOffsetLat) > 0.0000005 || abs(panOffsetLon) > 0.0000005)
  val targetLat = roundGeoCoord6(liveGpsLat + if (panAllowed) panOffsetLat else 0.0)
  val targetLon = roundGeoCoord6(liveGpsLon + if (panAllowed) panOffsetLon else 0.0)

  val displayLat = if (isPanned) targetLat else liveGpsLat
  val displayLon = if (isPanned) targetLon else liveGpsLon
  val displayAcc = liveGpsAcc

  val minVerticesRequired = if (isClosedShape) 3 else 2
  val hasMinVertices = existingPoints.size >= minVerticesRequired

  fun updateVertices(points: List<GeoPoint>) {
    if (isClosedShape) {
      controller.updateGeoShape(path, points)
    } else {
      controller.updateGeoTrace(path, points)
    }
  }

  val triggerLabel = if (isPanned) "Add point" else "Capture vertex"

  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Top Status & Capability Badges
      @OptIn(ExperimentalLayoutApi::class)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        GroundTonalBadge(
          text = if (isClosedShape) "Polygon (≥3 pts)" else "Linestring (≥2 pts)",
          tone = GroundBadgeTone.PRIMARY,
        )
        GroundTonalBadge(
          text = if (panAllowed) "Walk or draw (Pan allowed)" else "GPS walk only (Locked)",
          icon = if (!panAllowed) Icons.Default.Lock else null,
          tone = if (panAllowed) GroundBadgeTone.SECONDARY else GroundBadgeTone.TERTIARY,
        )
        GroundTonalBadge(
          text = "${existingPoints.size} vertices",
          tone = if (hasMinVertices) GroundBadgeTone.PRIMARY else GroundBadgeTone.WARNING,
        )
      }

      // Interactive Map Viewport with Vertices, Lines, Polygon Fill, Crosshair & GPS Dot
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2D5944), RoundedCornerShape(12.dp))
      ) {
        Canvas(
          modifier =
            Modifier.fillMaxSize().background(Color(0xFF10261C)).pointerInput(
              path,
              panAllowed,
              zoomLevel,
            ) {
              if (panAllowed) {
                detectDragGestures { change, dragAmount ->
                  change.consume()
                  val spanDeg = 0.0016 * (17.5f / zoomLevel.coerceIn(13f, 20f))
                  panOffsetLon =
                    (panOffsetLon - (dragAmount.x / size.width.toFloat()) * spanDeg).coerceIn(
                      -0.02,
                      0.02,
                    )
                  panOffsetLat =
                    (panOffsetLat + (dragAmount.y / size.height.toFloat()) * spanDeg).coerceIn(
                      -0.02,
                      0.02,
                    )
                }
              }
            }
        ) {
          val spanDeg = 0.0016f * (17.5f / zoomLevel.coerceIn(13f, 20f))
          val shiftX = (-panOffsetLon.toFloat() / spanDeg) * size.width
          val shiftY = (panOffsetLat.toFloat() / spanDeg) * size.height
          val center = Offset(size.width / 2f, size.height / 2f)

          // Background Vegetated field parcels
          drawRect(
            color = Color(0xFF173828),
            topLeft = Offset(size.width * 0.08f + shiftX, size.height * 0.10f + shiftY),
            size = Size(size.width * 0.36f, size.height * 0.34f),
          )
          drawRect(
            color = Color(0xFF1C422F),
            topLeft = Offset(size.width * 0.52f + shiftX, size.height * 0.16f + shiftY),
            size = Size(size.width * 0.38f, size.height * 0.42f),
          )
          drawRect(
            color = Color(0xFF153324),
            topLeft = Offset(size.width * 0.18f + shiftX, size.height * 0.56f + shiftY),
            size = Size(size.width * 0.44f, size.height * 0.32f),
          )

          // Survey coordinate grid lines
          val gridStepX = size.width / 6f
          val gridStepY = size.height / 4f
          val offsetModX = ((shiftX % gridStepX) + gridStepX) % gridStepX
          val offsetModY = ((shiftY % gridStepY) + gridStepY) % gridStepY
          for (i in -1..6) {
            val x = i * gridStepX + offsetModX
            drawLine(
              color = Color.White.copy(alpha = 0.08f),
              start = Offset(x, 0f),
              end = Offset(x, size.height),
              strokeWidth = 1f,
            )
          }
          for (j in -1..4) {
            val y = j * gridStepY + offsetModY
            drawLine(
              color = Color.White.copy(alpha = 0.08f),
              start = Offset(0f, y),
              end = Offset(size.width, y),
              strokeWidth = 1f,
            )
          }

          // User's live GPS blue dot + horizontal accuracy halo
          val gpsCenter = Offset(x = size.width / 2f + shiftX, y = size.height / 2f + shiftY)
          val haloRadiusDp = (displayAcc.toFloat() * 3.2f).coerceIn(14f, 56f).dp
          drawCircle(
            color = Color(0xFF42A5F5).copy(alpha = 0.22f),
            radius = haloRadiusDp.toPx(),
            center = gpsCenter,
          )
          drawCircle(
            color = Color(0xFF64B5F6).copy(alpha = 0.55f),
            radius = haloRadiusDp.toPx(),
            center = gpsCenter,
            style = Stroke(width = 1.2.dp.toPx()),
          )
          drawCircle(color = Color.White, radius = 7.dp.toPx(), center = gpsCenter)
          drawCircle(color = Color(0xFF1E88E5), radius = 5.dp.toPx(), center = gpsCenter)

          // Map vertices coordinates to screen pixels relative to current center target
          val screenOffsets = existingPoints.map { pt ->
            val dLon = (pt.longitude - targetLon).toFloat()
            val dLat = (pt.latitude - targetLat).toFloat()
            Offset(
              x = center.x + (dLon / spanDeg) * size.width,
              y = center.y - (dLat / spanDeg) * size.height,
            )
          }

          // Closed polygon translucent fill
          if (isClosedShape && screenOffsets.size >= 3) {
            val polyPath =
              Path().apply {
                moveTo(screenOffsets[0].x, screenOffsets[0].y)
                for (i in 1 until screenOffsets.size) {
                  lineTo(screenOffsets[i].x, screenOffsets[i].y)
                }
                close()
              }
            drawPath(path = polyPath, color = Color(0xFF4CAF50).copy(alpha = 0.22f))
            drawPath(
              path = polyPath,
              color = Color(0xFF81C784),
              style = Stroke(width = 2.5.dp.toPx()),
            )
          } else if (screenOffsets.size >= 2) {
            // Linestring / polyline connecting consecutive vertices
            val linePath =
              Path().apply {
                moveTo(screenOffsets[0].x, screenOffsets[0].y)
                for (i in 1 until screenOffsets.size) {
                  lineTo(screenOffsets[i].x, screenOffsets[i].y)
                }
              }
            drawPath(
              path = linePath,
              color = Color(0xFF29B6F6),
              style = Stroke(width = 2.8.dp.toPx()),
            )
          }

          // Candidate line from last vertex to current target crosshair
          if (screenOffsets.isNotEmpty()) {
            val lastOffset = screenOffsets.last()
            drawLine(
              color = Color(0xFFFFD54F).copy(alpha = 0.75f),
              start = lastOffset,
              end = center,
              strokeWidth = 1.8.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )
          }

          // Draw vertex markers with vertex index circles
          screenOffsets.forEachIndexed { idx, off ->
            drawCircle(
              color = Color(0xFF091812).copy(alpha = 0.6f),
              radius = 9.dp.toPx(),
              center = off,
            )
            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = off)
            drawCircle(
              color =
                if (idx == 0) Color(0xFF00C853)
                else if (idx == screenOffsets.lastIndex) Color(0xFFFF9100) else Color(0xFF0288D1),
              radius = 5.dp.toPx(),
              center = off,
            )
          }

          // Center Target Reticle / Crosshair
          val reticleColor = if (isPanned) Color(0xFFFFD54F) else Color(0xFF8BD6B1)
          val ringRadius = 18.dp.toPx()
          val tickInner = 7.dp.toPx()
          val tickOuter = 25.dp.toPx()

          drawCircle(
            color = Color(0xFF091812).copy(alpha = 0.65f),
            radius = ringRadius,
            center = center,
            style = Stroke(width = 3.5.dp.toPx()),
          )
          drawCircle(
            color = reticleColor,
            radius = ringRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
          )
          drawLine(
            color = reticleColor,
            start = Offset(center.x - tickOuter, center.y),
            end = Offset(center.x - tickInner, center.y),
            strokeWidth = 2.dp.toPx(),
          )
          drawLine(
            color = reticleColor,
            start = Offset(center.x + tickInner, center.y),
            end = Offset(center.x + tickOuter, center.y),
            strokeWidth = 2.dp.toPx(),
          )
          drawLine(
            color = reticleColor,
            start = Offset(center.x, center.y - tickOuter),
            end = Offset(center.x, center.y - tickInner),
            strokeWidth = 2.dp.toPx(),
          )
          drawLine(
            color = reticleColor,
            start = Offset(center.x, center.y + tickInner),
            end = Offset(center.x, center.y + tickOuter),
            strokeWidth = 2.dp.toPx(),
          )
          drawCircle(color = Color(0xFF091812), radius = 4.5.dp.toPx(), center = center)
          drawCircle(
            color = if (isPanned) Color(0xFFFFB300) else Color(0xFF00E676),
            radius = 3.dp.toPx(),
            center = center,
          )
        }

        // Top-left floating status pill
        Surface(
          modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFF0A1F16).copy(alpha = 0.88f),
          border = BorderStroke(1.dp, Color(0xFF2D5944)),
        ) {
          val textColor = if (isPanned) Color(0xFFFFE082) else Color(0xFFB7F1B9)
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector =
                when {
                  !panAllowed -> Icons.Default.Lock
                  isPanned -> Icons.Default.Place
                  else -> Icons.Default.LocationOn
                },
              contentDescription = null,
              tint = textColor,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text =
                when {
                  !panAllowed -> "GPS stream only (Pan locked)"
                  isPanned -> "Panned • Crosshair active"
                  else -> "Centered on GPS (drag map to pan)"
                },
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = textColor,
                  fontWeight = FontWeight.SemiBold,
                ),
            )
          }
        }

        // Top-right Zoom +/- buttons
        Column(
          modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Surface(
            modifier =
              Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable {
                zoomLevel = (zoomLevel + 0.75f).coerceIn(13.5f, 19.5f)
              },
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF0A1F16).copy(alpha = 0.88f),
            border = BorderStroke(1.dp, Color(0xFF2D5944)),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = "+",
                style =
                  MaterialTheme.typography.titleSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                  ),
              )
            }
          }
          Surface(
            modifier =
              Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable {
                zoomLevel = (zoomLevel - 0.75f).coerceIn(13.5f, 19.5f)
              },
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF0A1F16).copy(alpha = 0.88f),
            border = BorderStroke(1.dp, Color(0xFF2D5944)),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = "−",
                style =
                  MaterialTheme.typography.titleSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                  ),
              )
            }
          }
        }

        // Bottom-left GPS Accuracy & Zoom HUD pill
        Surface(
          modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFF0A1F16).copy(alpha = 0.88f),
          border = BorderStroke(1.dp, Color(0xFF2D5944)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text =
                "GPS ${formatGeoPointAccuracy(displayAcc)} • ${((zoomLevel * 10f).roundToInt() / 10f)}z",
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = Color.White,
                  fontWeight = FontWeight.SemiBold,
                ),
            )
          }
        }

        // Bottom-right Recenter button when panned
        if (isPanned) {
          Surface(
            modifier =
              Modifier.align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                  panOffsetLat = 0.0
                  panOffsetLon = 0.0
                },
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0E2219).copy(alpha = 0.94f),
            border = BorderStroke(1.dp, Color(0xFF8BD6B1)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = Color(0xFF8BD6B1),
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = "Recenter on GPS",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF8BD6B1),
                    fontWeight = FontWeight.Bold,
                  ),
              )
            }
          }
        }
      }

      // Coordinate Telemetry Bar
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainer,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = if (isPanned) "Target Point Coordinates" else "GPS Location",
                style = MaterialTheme.typography.labelSmall,
              )
              Text(
                text = formatGeoPointCoordinates(displayLat, displayLon),
                style =
                  MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                  ),
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(text = "Accuracy", style = MaterialTheme.typography.labelSmall)
              Text(
                text = formatGeoPointAccuracy(displayAcc),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          }

          if (hasMinVertices) {
            Text(
              text =
                if (isClosedShape) {
                  "${existingPoints.size} vertices captured • Closed polygon ready — tap \"Next →\" to continue or add more vertices"
                } else {
                  "${existingPoints.size} vertices captured • Linestring ready — tap \"Next →\" to continue or add more vertices"
                },
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = colors.primary,
                  fontWeight = FontWeight.Medium,
                ),
            )
          } else {
            val needed = minVerticesRequired - existingPoints.size
            Text(
              text =
                "Need $needed more ${if (needed == 1) "vertex" else "vertices"} to complete ${if (isClosedShape) "polygon" else "linestring"}. Tap \"$triggerLabel\" below to record.",
              style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
            )
          }
        }
      }

      // Primary Controls Row: [Undo] [Redo] [Add point / Capture vertex] [Clear]
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedIconButton(
          onClick = {
            if (undoGeometryHistory.isNotEmpty()) {
              val prev = undoGeometryHistory.last()
              undoGeometryHistory = undoGeometryHistory.dropLast(1)
              redoGeometryHistory = redoGeometryHistory + listOf(existingPoints)
              updateVertices(prev)
            } else if (existingPoints.isNotEmpty()) {
              redoGeometryHistory = redoGeometryHistory + listOf(existingPoints)
              updateVertices(existingPoints.dropLast(1))
            }
          },
          enabled = undoGeometryHistory.isNotEmpty() || existingPoints.isNotEmpty(),
        ) {
          Icon(imageVector = UndoIcon, contentDescription = "Undo", modifier = Modifier.size(20.dp))
        }

        OutlinedIconButton(
          onClick = {
            if (redoGeometryHistory.isNotEmpty()) {
              val next = redoGeometryHistory.last()
              redoGeometryHistory = redoGeometryHistory.dropLast(1)
              undoGeometryHistory = undoGeometryHistory + listOf(existingPoints)
              updateVertices(next)
            }
          },
          enabled = redoGeometryHistory.isNotEmpty(),
        ) {
          Icon(imageVector = RedoIcon, contentDescription = "Redo", modifier = Modifier.size(20.dp))
        }

        Button(
          onClick = {
            val captureLat = if (isPanned) targetLat else liveGpsLat
            val captureLon = if (isPanned) targetLon else liveGpsLon
            val newPt =
              GeoPoint(
                latitude = captureLat,
                longitude = captureLon,
                altitude_meters = liveGpsAlt,
                accuracy_meters = liveGpsAcc,
              )
            undoGeometryHistory = undoGeometryHistory + listOf(existingPoints)
            redoGeometryHistory = emptyList()
            updateVertices(existingPoints + newPt)
          },
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Icon(
            imageVector = if (isPanned) Icons.Default.Place else Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = triggerLabel,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
        }

        if (existingPoints.isNotEmpty()) {
          OutlinedIconButton(
            onClick = {
              undoGeometryHistory = undoGeometryHistory + listOf(existingPoints)
              redoGeometryHistory = emptyList()
              controller.clearField(path)
            }
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Clear all vertices",
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }

      if (!panAllowed) {
        Text(
          text =
            "Map panning is disabled for this question. Vertices are captured directly along your GPS path.",
          style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
        )
      }
    }
  }
}

@Composable
private fun SelectOneWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val path = control.canonicalPath
  val selectedValue = control.fieldState.value?.scalar_value?.string_value ?: ""
  val isQuick = control.appearance.split(' ').contains("quick")
  val isLikert = control.appearance.split(' ').contains("likert")

  if (control.options.isEmpty()) {
    Text(
      text = "(No selectable options match the current filter)",
      style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
    )
    return
  }

  if (isLikert) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      control.options.forEach { option ->
        val isSelected = selectedValue == option.value
        OutlinedCard(
          onClick = {
            controller.updateString(path, option.value)
            if (isQuick) controller.nextStep()
          },
          modifier = Modifier.weight(1f),
          shape = MaterialTheme.shapes.medium,
          colors =
            CardDefaults.outlinedCardColors(
              containerColor =
                if (isSelected) colors.primaryContainer else colors.surfaceContainerLow,
              contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
            ),
          border =
            BorderStroke(
              width = if (isSelected) 2.dp else 1.dp,
              color = if (isSelected) colors.primary else colors.outlineVariant,
            ),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            RadioButton(
              selected = isSelected,
              onClick = {
                controller.updateString(path, option.value)
                if (isQuick) controller.nextStep()
              },
              colors =
                RadioButtonDefaults.colors(
                  selectedColor = colors.onPrimaryContainer,
                  unselectedColor = colors.onSurfaceVariant,
                ),
            )
            Text(
              text = option.label.text,
              style =
                MaterialTheme.typography.labelSmall.copy(
                  color = if (isSelected) colors.onPrimaryContainer else colors.onSurface
                ),
            )
          }
        }
      }
    }
  } else {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      control.options.forEach { option ->
        val isSelected = selectedValue == option.value
        ChoiceCardRow(
          option = option,
          isSelected = isSelected,
          isMultiSelect = false,
          onClick = {
            controller.updateString(path, option.value)
            if (isQuick) controller.nextStep()
          },
        )
      }
    }
  }
}

@Composable
private fun SelectMultipleWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val path = control.canonicalPath
  val selectedValues: List<String> =
    control.fieldState.value?.list_value?.values?.mapNotNull { it.string_value }
      ?: control.fieldState.value?.scalar_value?.string_value?.split(' ')?.filter {
        it.isNotBlank()
      }
      ?: emptyList()

  if (control.options.isEmpty()) {
    Text(
      text = "(No selectable options match the current filter)",
      style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
    )
    return
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    control.options.forEach { option ->
      val isSelected = option.value in selectedValues
      ChoiceCardRow(
        option = option,
        isSelected = isSelected,
        isMultiSelect = true,
        onClick = {
          val nextList =
            if (isSelected) {
              selectedValues - option.value
            } else {
              selectedValues + option.value
            }
          controller.updateMultiSelect(path, nextList)
        },
      )
    }
  }
}

@Composable
private fun ChoiceCardRow(
  option: ResolvedChoiceOption,
  isSelected: Boolean,
  isMultiSelect: Boolean,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  OutlinedCard(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = if (isSelected) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
      ),
    border =
      BorderStroke(
        width = if (isSelected) 2.dp else 1.dp,
        color = if (isSelected) colors.primary else colors.outlineVariant,
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (isMultiSelect) {
        Checkbox(
          checked = isSelected,
          onCheckedChange = { onClick() },
          colors =
            CheckboxDefaults.colors(
              checkedColor = colors.onPrimaryContainer,
              checkmarkColor = colors.primaryContainer,
              uncheckedColor = colors.onSurfaceVariant,
            ),
        )
      } else {
        RadioButton(
          selected = isSelected,
          onClick = onClick,
          colors =
            RadioButtonDefaults.colors(
              selectedColor = colors.onPrimaryContainer,
              unselectedColor = colors.onSurfaceVariant,
            ),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = option.label.text,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
            ),
        )
      }
    }
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
        text = "Min: $min",
        style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
      )
      GroundTonalBadge(text = "Selected: $currentVal", tone = GroundBadgeTone.PRIMARY)
      Text(
        text = "Max: $max",
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
private fun RankControlWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val path = control.canonicalPath
  val currentRankedValues: List<String> =
    control.fieldState.value
      ?.list_value
      ?.values
      ?.mapNotNull { it.string_value }
      ?.takeIf { it.isNotEmpty() } ?: control.options.map { it.value }

  val orderedOptions =
    currentRankedValues.mapNotNull { code -> control.options.find { it.value == code } } +
      control.options.filter { opt -> opt.value !in currentRankedValues }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "Order items from highest (#1) to lowest priority:",
      style = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant),
    )
    orderedOptions.forEachIndexed { index, option ->
      OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          GroundTonalBadge(text = "#${index + 1}", tone = GroundBadgeTone.PRIMARY)
          Text(
            text = option.label.text,
            style =
              MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
              ),
            modifier = Modifier.weight(1f),
          )
          OutlinedButton(
            onClick = {
              if (index > 0) {
                val mutable = orderedOptions.map { it.value }.toMutableList()
                val tmp = mutable[index - 1]
                mutable[index - 1] = mutable[index]
                mutable[index] = tmp
                controller.updateMultiSelect(path, mutable)
              }
            },
            enabled = index > 0,
          ) {
            Text("↑", style = MaterialTheme.typography.labelMedium)
          }
          OutlinedButton(
            onClick = {
              if (index < orderedOptions.lastIndex) {
                val mutable = orderedOptions.map { it.value }.toMutableList()
                val tmp = mutable[index + 1]
                mutable[index + 1] = mutable[index]
                mutable[index] = tmp
                controller.updateMultiSelect(path, mutable)
              }
            },
            enabled = index < orderedOptions.lastIndex,
          ) {
            Text("↓", style = MaterialTheme.typography.labelMedium)
          }
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
      Text("Acknowledged (OK)", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
  } else {
    FilledTonalButton(
      onClick = { controller.updateString(path, "OK") },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        "Acknowledge / Confirm",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
      )
    }
  }
}

/**
 * Formats a [FieldValue] into a human-readable summary string for read-only displays and review
 * cards.
 */
fun formatFieldValueForDisplay(value: FieldValue?, dataType: DataType): String {
  if (value == null) return "(Unanswered)"
  value.list_value?.let { list ->
    if (list.values.isEmpty()) return "(Unanswered)"
    return list.values.mapNotNull { it.string_value }.joinToString(", ")
  }
  val scalar = value.scalar_value ?: return "(Unanswered)"
  return when (dataType) {
    DataType.TYPE_BOOLEAN -> scalar.bool_value?.toString() ?: "(Unanswered)"
    DataType.TYPE_INT32 ->
      (scalar.int32_value ?: scalar.int64_value?.toInt())?.toString() ?: "(Unanswered)"
    DataType.TYPE_INT64 ->
      (scalar.int64_value ?: scalar.int32_value?.toLong())?.toString() ?: "(Unanswered)"
    DataType.TYPE_DOUBLE -> scalar.double_value?.toString() ?: "(Unanswered)"
    DataType.TYPE_DATE ->
      scalar.date_value?.let { d ->
        if (d.year > 0) {
          "${d.year.toString().padStart(4, '0')}-${d.month.toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
        } else {
          "(Unanswered)"
        }
      } ?: "(Unanswered)"
    DataType.TYPE_TIME ->
      scalar.time_value?.let { t ->
        "${t.hours.toString().padStart(2, '0')}:${t.minutes.toString().padStart(2, '0')}:${t.seconds.toString().padStart(2, '0')}"
      } ?: "(Unanswered)"
    DataType.TYPE_DATETIME -> scalar.timestamp_value?.toString() ?: "(Unanswered)"
    DataType.TYPE_GEOPOINT ->
      scalar.geopoint_value?.let { gp ->
        "${gp.latitude}, ${gp.longitude} (±${gp.accuracy_meters}m)"
      } ?: "(Unanswered)"
    DataType.TYPE_GEOTRACE ->
      scalar.geotrace_value?.let { gt -> "${gt.points.size} trace points" } ?: "(Unanswered)"
    DataType.TYPE_GEOSHAPE ->
      scalar.geoshape_value?.let { gs -> "${gs.points.size} polygon vertices" } ?: "(Unanswered)"
    DataType.TYPE_BINARY -> MediaCapture.attachmentFileName(value) ?: "(Unanswered)"
    else ->
      scalar.string_value?.takeIf { it.isNotEmpty() }
        ?: scalar.int64_value?.toString()
        ?: scalar.int32_value?.toString()
        ?: scalar.double_value?.toString()
        ?: scalar.bool_value?.toString()
        ?: "(Unanswered)"
  }
}

/** Range of values representable by a proto `int32` field. */
private val INT32_RANGE = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()

/** Outcome of interpreting the raw text a user typed into an integer control. */
internal sealed interface IntegerInput {
  /** The control is empty, so the underlying field should be cleared. */
  data object Empty : IntegerInput

  /** The text is not a usable integer; [message] explains why. */
  data class Invalid(val message: String) : IntegerInput

  /** The text parsed to [value], which is in range for the target data type. */
  data class Valid(val value: Long) : IntegerInput
}

/**
 * Parses [raw] as an integer appropriate for [dataType].
 *
 * Values outside the `int32` range are reported as [IntegerInput.Invalid] rather than being
 * narrowed with `toInt()`, which would silently wrap (for example `3000000000` would be stored as
 * `-1294967296`). Only `TYPE_INT64` fields accept the full `Long` range.
 */
internal fun parseIntegerInput(raw: String, dataType: DataType): IntegerInput {
  if (raw.isBlank()) return IntegerInput.Empty
  val parsed =
    raw.trim().toLongOrNull() ?: return IntegerInput.Invalid("Enter a whole integer number")
  if (dataType != DataType.TYPE_INT64 && parsed !in INT32_RANGE) {
    return IntegerInput.Invalid("Enter a value between ${Int.MIN_VALUE} and ${Int.MAX_VALUE}")
  }
  return IntegerInput.Valid(parsed)
}
