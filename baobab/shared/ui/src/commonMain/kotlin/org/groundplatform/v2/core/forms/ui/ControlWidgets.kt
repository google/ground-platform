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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.GeoPoint
import kotlin.math.roundToInt
import org.groundplatform.v2.core.forms.model.ComponentState
import org.groundplatform.v2.core.forms.model.FieldState
import org.groundplatform.v2.core.forms.model.ResolvedChoiceOption
import org.groundplatform.v2.core.forms.model.ValidationStatus

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
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainerLowest),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Top metadata badges (path, required, read-only / calculated)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = control.canonicalPath,
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = colors.onSurfaceVariant,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            GroundTonalBadge("🖼 ${media.image_uri}", GroundBadgeTone.PRIMARY)
          }
          if (media.audio_uri.isNotBlank()) {
            GroundTonalBadge("🔊 ${media.audio_uri}", GroundBadgeTone.SECONDARY)
          }
          if (media.video_uri.isNotBlank()) {
            GroundTonalBadge("🎬 ${media.video_uri}", GroundBadgeTone.TERTIARY)
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
          colors = CardDefaults.cardColors(containerColor = colors.errorContainer),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            status.errors.forEach { err ->
              Text(
                text = "⚠ ${err.message}",
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
    ControlType.CONTROL_UPLOAD -> UploadControlWidget(control = control, controller = controller)
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
      GeoVertexListWidget(control, path, fieldState, controller, isClosedShape = false)
    DataType.TYPE_GEOSHAPE ->
      GeoVertexListWidget(control, path, fieldState, controller, isClosedShape = true)
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
    OutlinedTextField(
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

@Composable
private fun IntegerInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
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

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FilledTonalButton(
        onClick = { applyNumber((initialNumber ?: 0L) - 1L) },
        modifier = Modifier.height(52.dp),
      ) {
        Text("-1", fontWeight = FontWeight.Bold)
      }
      OutlinedTextField(
        value = text,
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
        modifier = Modifier.weight(1f),
        singleLine = true,
        isError = parseError != null,
        placeholder = { Text("0") },
      )
      FilledTonalButton(
        onClick = { applyNumber((initialNumber ?: 0L) + 1L) },
        modifier = Modifier.height(52.dp),
      ) {
        Text("+1", fontWeight = FontWeight.Bold)
      }
    }
    if (parseError != null) {
      Text(
        text = parseError!!,
        style = MaterialTheme.typography.labelSmall.copy(color = colors.error),
      )
    }
  }
}

@Composable
private fun DecimalInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
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

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FilledTonalButton(
        onClick = {
          val next = (((initialDouble ?: 0.0) - 0.5) * 100.0).roundToInt() / 100.0
          text = next.toString()
          parseError = null
          controller.updateDouble(path, next)
        },
        modifier = Modifier.height(52.dp),
      ) {
        Text("-0.5", fontWeight = FontWeight.Bold)
      }
      OutlinedTextField(
        value = text,
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
        modifier = Modifier.weight(1f),
        singleLine = true,
        isError = parseError != null,
        placeholder = { Text("0.0") },
      )
      FilledTonalButton(
        onClick = {
          val next = (((initialDouble ?: 0.0) + 0.5) * 100.0).roundToInt() / 100.0
          text = next.toString()
          parseError = null
          controller.updateDouble(path, next)
        },
        modifier = Modifier.height(52.dp),
      ) {
        Text("+0.5", fontWeight = FontWeight.Bold)
      }
    }
    if (parseError != null) {
      Text(
        text = parseError!!,
        style = MaterialTheme.typography.labelSmall.copy(color = colors.error),
      )
    }
  }
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
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
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
      OutlinedTextField(
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
        Text("Today")
      }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(
        onClick = { controller.updateDate(path, year, month, (day - 1).coerceIn(1, 28)) },
        modifier = Modifier.weight(1f),
      ) {
        Text("-1 Day", style = MaterialTheme.typography.labelSmall)
      }
      OutlinedButton(
        onClick = { controller.updateDate(path, year, month, (day + 1).coerceIn(1, 28)) },
        modifier = Modifier.weight(1f),
      ) {
        Text("+1 Day", style = MaterialTheme.typography.labelSmall)
      }
      OutlinedButton(
        onClick = { controller.updateDate(path, year, (month % 12) + 1, day) },
        modifier = Modifier.weight(1f),
      ) {
        Text("+1 Month", style = MaterialTheme.typography.labelSmall)
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
    OutlinedTextField(
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
      Text("Now")
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
          color = if (ts != null) colors.onSurface else colors.onSurfaceVariant,
        ),
      modifier = Modifier.weight(1f),
    )
    Button(
      onClick = {
        val epochSeconds = controller.session.environment.clockEpochMillis() / 1000L
        controller.updateTimestamp(path, epochSeconds)
      }
    ) {
      Text("Capture Timestamp")
    }
  }
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
  val appearanceTokens = control.appearance.split(' ').filter { it.isNotBlank() }
  val panAllowed = appearanceTokens.contains("placement-map") || appearanceTokens.contains("map")
  val accuracyThreshold =
    control.controlDef.geo_config?.accuracy_threshold_meters?.takeIf { it > 0.0 }

  var latText by remember(path, gp) { mutableStateOf(gp?.latitude?.toString() ?: "") }
  var lonText by remember(path, gp) { mutableStateOf(gp?.longitude?.toString() ?: "") }
  var altText by remember(path, gp) { mutableStateOf(gp?.altitude_meters?.toString() ?: "1680.0") }
  var accText by remember(path, gp) { mutableStateOf(gp?.accuracy_meters?.toString() ?: "3.2") }

  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Geospatial capability & constraint badges
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        GroundTonalBadge(
          text = if (panAllowed) "🖐 Map Pan Allowed" else "🔒 No Pan Allowed (GPS Only)",
          tone = if (panAllowed) GroundBadgeTone.PRIMARY else GroundBadgeTone.TERTIARY,
        )
        if (accuracyThreshold != null) {
          val meetsAccuracy = gp == null || gp.accuracy_meters <= accuracyThreshold
          GroundTonalBadge(
            text = "🎯 Required GPS <= ${accuracyThreshold}m",
            tone = if (meetsAccuracy) GroundBadgeTone.SECONDARY else GroundBadgeTone.TERTIARY,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            if (gp != null) {
              "📍 ${gp.latitude}, ${gp.longitude} (±${gp.accuracy_meters}m)"
            } else {
              "📍 No GPS coordinates captured"
            },
          style =
            MaterialTheme.typography.bodySmall.copy(
              fontWeight = FontWeight.SemiBold,
              color = colors.onSurface,
            ),
        )
      }

      // GPS Fix & Pan Simulation Controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FilledTonalButton(
          onClick = {
            controller.updateGeoPoint(
              path = path,
              latitude = -1.292066,
              longitude = 36.821946,
              altitudeMeters = 1680.0,
              accuracyMeters = 3.2,
            )
          },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Text("🛰 GPS Fix (±3.2m ✓)", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
          onClick = {
            controller.updateGeoPoint(
              path = path,
              latitude = -1.292180,
              longitude = 36.822090,
              altitudeMeters = 1680.0,
              accuracyMeters = 14.2,
            )
          },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Text("⚠ Weak GPS (±14.2m)", style = MaterialTheme.typography.labelSmall)
        }
        if (panAllowed) {
          OutlinedButton(
            onClick = {
              val baseLat = gp?.latitude ?: -1.292066
              val baseLon = gp?.longitude ?: 36.821946
              val baseAcc = gp?.accuracy_meters ?: 4.0
              val pannedLat = ((baseLat + 0.00012) * 1000000.0).toInt() / 1000000.0
              val pannedLon = ((baseLon + 0.00015) * 1000000.0).toInt() / 1000000.0
              controller.updateGeoPoint(
                path = path,
                latitude = pannedLat,
                longitude = pannedLon,
                altitudeMeters = gp?.altitude_meters ?: 1680.0,
                accuracyMeters = baseAcc,
              )
            },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Text("🖐 Pan Map (+15m)", style = MaterialTheme.typography.labelSmall)
          }
        }
      }

      if (!panAllowed) {
        Text(
          text =
            "Manual map panning is locked for this point. Coordinates must come from a hardware GNSS fix" +
              (if (accuracyThreshold != null) " with <= ${accuracyThreshold}m accuracy." else "."),
          style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = latText,
          onValueChange = {
            if (!panAllowed) return@OutlinedTextField
            latText = it
            val lat = it.toDoubleOrNull()
            val lon = lonText.toDoubleOrNull()
            if (lat != null && lon != null) {
              controller.updateGeoPoint(
                path,
                lat,
                lon,
                altText.toDoubleOrNull() ?: 1680.0,
                accText.toDoubleOrNull() ?: 3.2,
              )
            }
          },
          enabled = panAllowed,
          label = { Text(if (panAllowed) "Latitude (Pan OK)" else "Latitude (GPS Locked)") },
          modifier = Modifier.weight(1f),
          singleLine = true,
        )
        OutlinedTextField(
          value = lonText,
          onValueChange = {
            if (!panAllowed) return@OutlinedTextField
            lonText = it
            val lat = latText.toDoubleOrNull()
            val lon = it.toDoubleOrNull()
            if (lat != null && lon != null) {
              controller.updateGeoPoint(
                path,
                lat,
                lon,
                altText.toDoubleOrNull() ?: 1680.0,
                accText.toDoubleOrNull() ?: 3.2,
              )
            }
          },
          enabled = panAllowed,
          label = { Text(if (panAllowed) "Longitude (Pan OK)" else "Longitude (GPS Locked)") },
          modifier = Modifier.weight(1f),
          singleLine = true,
        )
      }
    }
  }
}

@Composable
private fun GeoVertexListWidget(
  control: ComponentState.ControlState,
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
  isClosedShape: Boolean,
) {
  val colors = MaterialTheme.colorScheme
  val appearanceTokens = control.appearance.split(' ').filter { it.isNotBlank() }
  val panOverrideAllowed =
    appearanceTokens.contains("placement-map") || appearanceTokens.contains("walk-or-draw")
  val existingPoints =
    if (isClosedShape) {
      fieldState.value?.scalar_value?.geoshape_value?.points ?: emptyList()
    } else {
      fieldState.value?.scalar_value?.geotrace_value?.points ?: emptyList()
    }

  var newLat by remember(path) { mutableStateOf("-1.2921") }
  var newLon by remember(path) { mutableStateOf("36.8219") }

  fun updateVertices(points: List<GeoPoint>) {
    if (isClosedShape) {
      controller.updateGeoShape(path, points)
    } else {
      controller.updateGeoTrace(path, points)
    }
  }

  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        GroundTonalBadge(
          text = if (isClosedShape) "🚶 Walk Plot Perimeter" else "🚶 Walk Transect",
          tone = GroundBadgeTone.PRIMARY,
        )
        GroundTonalBadge(
          text =
            if (panOverrideAllowed) "🖐 GPS Override / Pan Allowed While Walking"
            else "🔒 GPS Stream Only",
          tone = if (panOverrideAllowed) GroundBadgeTone.SECONDARY else GroundBadgeTone.TERTIARY,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            if (isClosedShape) {
              "Polygon Vertices (${existingPoints.size})"
            } else {
              "Trace Vertices (${existingPoints.size})"
            },
          style =
            MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.SemiBold,
              color = colors.onSurface,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilledTonalButton(
            onClick = {
              val idx = existingPoints.size
              val nextPt =
                GeoPoint(
                  latitude = -1.2921 - (idx * 0.0002),
                  longitude = 36.8219 + (idx * 0.0003),
                  altitude_meters = 1680.0,
                  accuracy_meters = 3.4,
                )
              val updated =
                if (isClosedShape && existingPoints.size >= 3) {
                  existingPoints.dropLast(1) + nextPt + existingPoints.first()
                } else {
                  existingPoints + nextPt
                }
              updateVertices(updated)
            },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Text("🛰 Walk +1 GPS Vertex", style = MaterialTheme.typography.labelSmall)
          }
          if (panOverrideAllowed) {
            OutlinedButton(
              onClick = {
                val idx = existingPoints.size
                val pannedPt =
                  GeoPoint(
                    latitude = -1.2918 + (idx * 0.00015),
                    longitude = 36.8226 + (idx * 0.0002),
                    altitude_meters = 1681.0,
                    accuracy_meters = 1.5,
                  )
                val updated =
                  if (isClosedShape && existingPoints.size >= 3) {
                    existingPoints.dropLast(1) + pannedPt + existingPoints.first()
                  } else {
                    existingPoints + pannedPt
                  }
                updateVertices(updated)
              },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Text("🖐 Pan Override Vertex", style = MaterialTheme.typography.labelSmall)
            }
          }
          OutlinedButton(
            onClick = {
              val sample =
                listOf(
                  GeoPoint(
                    latitude = -1.2921,
                    longitude = 36.8219,
                    altitude_meters = 1680.0,
                    accuracy_meters = 3.5,
                  ),
                  GeoPoint(
                    latitude = -1.2925,
                    longitude = 36.8224,
                    altitude_meters = 1681.0,
                    accuracy_meters = 3.8,
                  ),
                  GeoPoint(
                    latitude = -1.2918,
                    longitude = 36.8228,
                    altitude_meters = 1682.0,
                    accuracy_meters = 3.2,
                  ),
                  GeoPoint(
                    latitude = -1.2915,
                    longitude = 36.8221,
                    altitude_meters = 1680.0,
                    accuracy_meters = 3.4,
                  ),
                  GeoPoint(
                    latitude = -1.2921,
                    longitude = 36.8219,
                    altitude_meters = 1680.0,
                    accuracy_meters = 3.5,
                  ),
                )
              updateVertices(sample)
            },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Text("Reset Polygon", style = MaterialTheme.typography.labelSmall)
          }
        }
      }

      existingPoints.forEachIndexed { idx, pt ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "#${idx + 1}: (${pt.latitude}, ${pt.longitude}) ±${pt.accuracy_meters}m",
            style =
              MaterialTheme.typography.bodySmall.copy(
                color = colors.onSurface,
              ),
          )
          TextButton(
            onClick = { updateVertices(existingPoints.filterIndexed { i, _ -> i != idx }) },
            colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
          ) {
            Text("✕")
          }
        }
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = newLat,
          onValueChange = { newLat = it },
          enabled = panOverrideAllowed,
          label = { Text("Lat") },
          modifier = Modifier.weight(1f),
          singleLine = true,
        )
        OutlinedTextField(
          value = newLon,
          onValueChange = { newLon = it },
          enabled = panOverrideAllowed,
          label = { Text("Lon") },
          modifier = Modifier.weight(1f),
          singleLine = true,
        )
        Button(
          onClick = {
            val lat = newLat.toDoubleOrNull() ?: -1.2921
            val lon = newLon.toDoubleOrNull() ?: 36.8219
            updateVertices(
              existingPoints +
                GeoPoint(
                  latitude = lat,
                  longitude = lon,
                  altitude_meters = 1680.0,
                  accuracy_meters = 2.5,
                )
            )
          },
          enabled = panOverrideAllowed,
        ) {
          Text("+ Pt")
        }
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
                if (isSelected) colors.primaryContainer else colors.surfaceContainerLow
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
        containerColor = if (isSelected) colors.primaryContainer else colors.surfaceContainerLow
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
        Checkbox(checked = isSelected, onCheckedChange = { onClick() })
      } else {
        RadioButton(selected = isSelected, onClick = onClick)
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
        Text(
          text = "value: ${option.value}",
          style =
            MaterialTheme.typography.labelSmall.copy(
              color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      OutlinedButton(onClick = { applyRangeVal(currentVal - step) }) { Text("- $step") }
      OutlinedButton(onClick = { applyRangeVal(currentVal + step) }) { Text("+ $step") }
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
private fun UploadControlWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val path = control.canonicalPath
  val mediaType = control.controlDef.media_type.ifBlank { "image/*" }
  val currentFile =
    control.fieldState.value?.scalar_value?.let { it.string_value ?: it.binary_value?.utf8() } ?: ""

  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Accepted media: $mediaType",
        style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = currentFile,
          onValueChange = { controller.updateString(path, it) },
          modifier = Modifier.weight(1f),
          singleLine = true,
          placeholder = { Text("attachment_filename.jpg") },
        )
        Button(
          onClick = {
            val ext =
              when {
                mediaType.startsWith("audio") -> "m4a"
                mediaType.startsWith("video") -> "mp4"
                else -> "jpg"
              }
            controller.updateString(path, "capture_${path.substringAfterLast('/')}.$ext")
          }
        ) {
          Text("Capture")
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
      Text("✓ Acknowledged (OK)")
    }
  } else {
    FilledTonalButton(
      onClick = { controller.updateString(path, "OK") },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Acknowledge / Confirm")
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
