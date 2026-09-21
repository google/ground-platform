/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
  val fieldState = control.fieldState
  val labelText =
    control.label?.text?.takeIf { it.isNotBlank() } ?: control.canonicalPath.substringAfterLast('/')
  val hintText = control.hint?.text?.takeIf { it.isNotBlank() }
  val guidanceText =
    control.label?.guidanceText?.takeIf { it.isNotBlank() }
      ?: control.hint?.guidanceText?.takeIf { it.isNotBlank() }
  var isGuidanceExpanded by remember(control.canonicalPath) { mutableStateOf(false) }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
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
              fontFamily = FontFamily.Monospace,
              color = Color(0xFF6B7280),
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (fieldState.isRequired) {
            BadgePill(
              text = "Required *",
              bgColor = Color(0xFFFEE2E2),
              textColor = Color(0xFFB91C1C),
            )
          }
          if (fieldState.isCalculated) {
            BadgePill(
              text = "Calculated",
              bgColor = Color(0xFFE0F2FE),
              textColor = Color(0xFF0369A1),
            )
          } else if (fieldState.isReadOnly) {
            BadgePill(
              text = "Read-only",
              bgColor = Color(0xFFF3F4F6),
              textColor = Color(0xFF4B5563),
            )
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
              color = Color(0xFF111827),
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
            MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF4B5563), lineHeight = 19.sp),
        )
      }

      // Expandable Guidance Hint
      if (guidanceText != null) {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFFF0FDF4))
              .clickable { isGuidanceExpanded = !isGuidanceExpanded }
              .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
          Text(
            text =
              if (isGuidanceExpanded) "▼ Hide Enumerator Guidance"
              else "▶ Show Enumerator Guidance",
            style =
              MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF15803D),
              ),
          )
          if (isGuidanceExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = guidanceText,
              style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF166534)),
            )
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
            BadgePill("🖼 ${media.image_uri}", Color(0xFFEDE9FE), Color(0xFF6D28D9))
          }
          if (media.audio_uri.isNotBlank()) {
            BadgePill("🔊 ${media.audio_uri}", Color(0xFFFEF3C7), Color(0xFFB45309))
          }
          if (media.video_uri.isNotBlank()) {
            BadgePill("🎬 ${media.video_uri}", Color(0xFFDBEAFE), Color(0xFF1D4ED8))
          }
        }
      }

      // Interactive Control Widget
      ControlWidget(control = control, controller = controller)

      // Validation Error Banner
      val status = fieldState.validationStatus
      if (status is ValidationStatus.Invalid && (showValidationErrors || !fieldState.isEmpty)) {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .background(Color(0xFFFEF2F2), RoundedCornerShape(10.dp))
              .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(10.dp))
              .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          status.errors.forEach { err ->
            Text(
              text = "⚠ ${err.message}",
              style =
                MaterialTheme.typography.bodySmall.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFFB91C1C),
                ),
            )
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
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
        .padding(14.dp)
  ) {
    Text(
      text = formatFieldValueForDisplay(fieldState.value, fieldState.dataType),
      style =
        MaterialTheme.typography.bodyLarge.copy(
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF0F172A),
        ),
    )
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
    DataType.TYPE_GEOPOINT -> GeoPointInputWidget(path, fieldState, controller)
    DataType.TYPE_GEOTRACE ->
      GeoVertexListWidget(path, fieldState, controller, isClosedShape = false)
    DataType.TYPE_GEOSHAPE ->
      GeoVertexListWidget(path, fieldState, controller, isClosedShape = true)
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
  val currentStr = fieldState.value?.scalar_value?.string_value ?: ""
  var text by remember(path, currentStr) { mutableStateOf(currentStr) }

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
          style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
        )
        OutlinedButton(
          onClick = {
            text = ""
            controller.clearField(path)
          },
          modifier = Modifier.height(30.dp),
        ) {
          Text("Clear", fontSize = 11.sp)
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
      OutlinedButton(
        onClick = { applyNumber((initialNumber ?: 0L) - 1L) },
        modifier = Modifier.height(52.dp),
      ) {
        Text("-1", fontWeight = FontWeight.Bold)
      }
      OutlinedTextField(
        value = text,
        onValueChange = { raw ->
          text = raw
          if (raw.isBlank()) {
            parseError = null
            controller.clearField(path)
          } else {
            val parsed = raw.trim().toLongOrNull()
            if (parsed != null) {
              parseError = null
              if (fieldState.dataType == DataType.TYPE_INT64) {
                controller.updateLong(path, parsed)
              } else {
                controller.updateInt(path, parsed.toInt())
              }
            } else {
              parseError = "Enter a whole integer number"
            }
          }
        },
        modifier = Modifier.weight(1f),
        singleLine = true,
        isError = parseError != null,
        placeholder = { Text("0") },
      )
      OutlinedButton(
        onClick = { applyNumber((initialNumber ?: 0L) + 1L) },
        modifier = Modifier.height(52.dp),
      ) {
        Text("+1", fontWeight = FontWeight.Bold)
      }
    }
    if (parseError != null) {
      Text(
        text = parseError!!,
        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFB91C1C)),
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
      OutlinedButton(
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
      OutlinedButton(
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
        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFB91C1C)),
      )
    }
  }
}

@Composable
private fun BooleanInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val currentBool = fieldState.value?.scalar_value?.bool_value
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    listOf(true to "Yes (True)", false to "No (False)").forEach { (boolVal, label) ->
      val isSelected = currentBool == boolVal
      Box(
        modifier =
          Modifier.weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFFE8F5E9) else Color(0xFFF9FAFB))
            .border(
              width = if (isSelected) 2.dp else 1.dp,
              color = if (isSelected) Color(0xFF1B5E20) else Color(0xFFD1D5DB),
              shape = RoundedCornerShape(12.dp),
            )
            .clickable { controller.updateBoolean(path, boolVal) }
            .padding(vertical = 14.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = label,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) Color(0xFF1B5E20) else Color(0xFF374151),
            ),
        )
      }
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
      OutlinedButton(
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
        Text("-1 Day", fontSize = 11.sp)
      }
      OutlinedButton(
        onClick = { controller.updateDate(path, year, month, (day + 1).coerceIn(1, 28)) },
        modifier = Modifier.weight(1f),
      ) {
        Text("+1 Day", fontSize = 11.sp)
      }
      OutlinedButton(
        onClick = { controller.updateDate(path, year, (month % 12) + 1, day) },
        modifier = Modifier.weight(1f),
      ) {
        Text("+1 Month", fontSize = 11.sp)
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
    OutlinedButton(
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
          fontFamily = FontFamily.Monospace,
          color = if (ts != null) Color(0xFF111827) else Color(0xFF9CA3AF),
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
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val gp = fieldState.value?.scalar_value?.geopoint_value
  var latText by remember(path, gp) { mutableStateOf(gp?.latitude?.toString() ?: "") }
  var lonText by remember(path, gp) { mutableStateOf(gp?.longitude?.toString() ?: "") }
  var altText by remember(path, gp) { mutableStateOf(gp?.altitude_meters?.toString() ?: "0.0") }
  var accText by remember(path, gp) { mutableStateOf(gp?.accuracy_meters?.toString() ?: "3.5") }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
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
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1E293B),
          ),
      )
      Button(
        onClick = {
          controller.updateGeoPoint(
            path = path,
            latitude = -18.7669,
            longitude = 46.8691,
            altitudeMeters = 240.0,
            accuracyMeters = 2.8,
          )
        },
        modifier = Modifier.height(34.dp),
      ) {
        Text("Capture GPS Fix", fontSize = 11.sp)
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = latText,
        onValueChange = {
          latText = it
          val lat = it.toDoubleOrNull()
          val lon = lonText.toDoubleOrNull()
          if (lat != null && lon != null) {
            controller.updateGeoPoint(
              path,
              lat,
              lon,
              altText.toDoubleOrNull() ?: 0.0,
              accText.toDoubleOrNull() ?: 3.5,
            )
          }
        },
        label = { Text("Latitude") },
        modifier = Modifier.weight(1f),
        singleLine = true,
      )
      OutlinedTextField(
        value = lonText,
        onValueChange = {
          lonText = it
          val lat = latText.toDoubleOrNull()
          val lon = it.toDoubleOrNull()
          if (lat != null && lon != null) {
            controller.updateGeoPoint(
              path,
              lat,
              lon,
              altText.toDoubleOrNull() ?: 0.0,
              accText.toDoubleOrNull() ?: 3.5,
            )
          }
        },
        label = { Text("Longitude") },
        modifier = Modifier.weight(1f),
        singleLine = true,
      )
    }
  }
}

@Composable
private fun GeoVertexListWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
  isClosedShape: Boolean,
) {
  val existingPoints =
    if (isClosedShape) {
      fieldState.value?.scalar_value?.geoshape_value?.points ?: emptyList()
    } else {
      fieldState.value?.scalar_value?.geotrace_value?.points ?: emptyList()
    }

  var newLat by remember(path) { mutableStateOf("-18.7669") }
  var newLon by remember(path) { mutableStateOf("46.8691") }

  fun updateVertices(points: List<GeoPoint>) {
    if (isClosedShape) {
      controller.updateGeoShape(path, points)
    } else {
      controller.updateGeoTrace(path, points)
    }
  }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
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
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
      )
      OutlinedButton(
        onClick = {
          val sample =
            listOf(
              GeoPoint(latitude = -18.7660, longitude = 46.8680),
              GeoPoint(latitude = -18.7660, longitude = 46.8695),
              GeoPoint(latitude = -18.7675, longitude = 46.8695),
              GeoPoint(latitude = -18.7660, longitude = 46.8680),
            )
          updateVertices(sample)
        },
        modifier = Modifier.height(32.dp),
      ) {
        Text("Sample Geometry", fontSize = 11.sp)
      }
    }

    existingPoints.forEachIndexed { idx, pt ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "#${idx + 1}: (${pt.latitude}, ${pt.longitude})",
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        Text(
          text = "✕",
          color = Color(0xFFB91C1C),
          modifier =
            Modifier.clickable { updateVertices(existingPoints.filterIndexed { i, _ -> i != idx }) }
              .padding(4.dp),
        )
      }
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = newLat,
        onValueChange = { newLat = it },
        label = { Text("Lat") },
        modifier = Modifier.weight(1f),
        singleLine = true,
      )
      OutlinedTextField(
        value = newLon,
        onValueChange = { newLon = it },
        label = { Text("Lon") },
        modifier = Modifier.weight(1f),
        singleLine = true,
      )
      Button(
        onClick = {
          val lat = newLat.toDoubleOrNull() ?: -18.7669
          val lon = newLon.toDoubleOrNull() ?: 46.8691
          updateVertices(existingPoints + GeoPoint(latitude = lat, longitude = lon))
        }
      ) {
        Text("+ Pt")
      }
    }
  }
}

@Composable
private fun SelectOneWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
  val path = control.canonicalPath
  val selectedValue = control.fieldState.value?.scalar_value?.string_value ?: ""
  val isQuick = control.appearance.split(' ').contains("quick")
  val isLikert = control.appearance.split(' ').contains("likert")

  if (control.options.isEmpty()) {
    Text(
      text = "(No selectable options match the current filter)",
      style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6B7280)),
    )
    return
  }

  if (isLikert) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      control.options.forEach { option ->
        val isSelected = selectedValue == option.value
        Column(
          modifier =
            Modifier.weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (isSelected) Color(0xFFE8F5E9) else Color(0xFFF9FAFB))
              .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF1B5E20) else Color(0xFFE5E7EB),
                shape = RoundedCornerShape(10.dp),
              )
              .clickable {
                controller.updateString(path, option.value)
                if (isQuick) controller.nextStep()
              }
              .padding(8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          RadioButton(
            selected = isSelected,
            onClick = {
              controller.updateString(path, option.value)
              if (isQuick) controller.nextStep()
            },
          )
          Text(text = option.label.text, style = MaterialTheme.typography.labelSmall)
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
      style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6B7280)),
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
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(if (isSelected) Color(0xFFE8F5E9) else Color(0xFFF9FAFB))
        .border(
          width = if (isSelected) 2.dp else 1.dp,
          color = if (isSelected) Color(0xFF1B5E20) else Color(0xFFE5E7EB),
          shape = RoundedCornerShape(12.dp),
        )
        .clickable { onClick() }
        .padding(horizontal = 12.dp, vertical = 10.dp),
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
            color = if (isSelected) Color(0xFF1B5E20) else Color(0xFF111827),
          ),
      )
      Text(
        text = "value: ${option.value}",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF6B7280),
          ),
      )
    }
  }
}

@Composable
private fun RangeControlWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
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
        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
      )
      BadgePill(
        text = "Selected: $currentVal",
        bgColor = Color(0xFFE8F5E9),
        textColor = Color(0xFF1B5E20),
      )
      Text(
        text = "Max: $max",
        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
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
      style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF4B5563)),
    )
    orderedOptions.forEachIndexed { index, option ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(Color(0xFFF9FAFB), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        BadgePill(
          text = "#${index + 1}",
          bgColor = Color(0xFFE8F5E9),
          textColor = Color(0xFF1B5E20),
        )
        Text(
          text = option.label.text,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
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
          modifier = Modifier.height(32.dp),
        ) {
          Text("↑", fontSize = 12.sp)
        }
        OutlinedButton(
          onClick = {
            if (index < orderedOptions.lastIndex) {
              val mutable = orderedOptions.map { it.value }.toMutableList()
              val tmp = mutable[index + 1]
              mutable[index + 1] = mutable[index]
              mutable[index + 1 - 1] = tmp
              controller.updateMultiSelect(path, mutable)
            }
          },
          enabled = index < orderedOptions.lastIndex,
          modifier = Modifier.height(32.dp),
        ) {
          Text("↓", fontSize = 12.sp)
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
  val path = control.canonicalPath
  val mediaType = control.controlDef.media_type.ifBlank { "image/*" }
  val currentFile = control.fieldState.value?.scalar_value?.string_value ?: ""

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = "Accepted media: $mediaType",
      style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)),
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

@Composable
private fun TriggerControlWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
  val path = control.canonicalPath
  val isAcknowledged = control.fieldState.value?.scalar_value?.string_value == "OK"

  Button(
    onClick = {
      if (isAcknowledged) controller.clearField(path) else controller.updateString(path, "OK")
    },
    colors =
      ButtonDefaults.buttonColors(
        containerColor = if (isAcknowledged) Color(0xFF1B5E20) else Color(0xFF374151)
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(if (isAcknowledged) "✓ Acknowledged (OK)" else "Acknowledge / Confirm")
  }
}

@Composable
internal fun BadgePill(text: String, bgColor: Color, textColor: Color) {
  Box(
    modifier =
      Modifier.background(bgColor, RoundedCornerShape(999.dp))
        .padding(horizontal = 10.dp, vertical = 3.dp)
  ) {
    Text(
      text = text,
      style =
        MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.SemiBold,
          color = textColor,
        ),
    )
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
