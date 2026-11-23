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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.model.FieldState
import org.openforis.collectone.v2.core.forms.temporal.formatIsoInstant
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.clickable
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_cancel
import org.openforis.collectone.v2.core.forms.ui.resources.forms_capture_timestamp
import org.openforis.collectone.v2.core.forms.ui.resources.forms_clear_date
import org.openforis.collectone.v2.core.forms.ui.resources.forms_date_placeholder
import org.openforis.collectone.v2.core.forms.ui.resources.forms_no_timestamp
import org.openforis.collectone.v2.core.forms.ui.resources.forms_now
import org.openforis.collectone.v2.core.forms.ui.resources.forms_ok
import org.openforis.collectone.v2.core.forms.ui.resources.forms_select_date
import org.openforis.collectone.v2.core.forms.ui.resources.forms_time_placeholder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val focusManager = LocalFocusManager.current
  val dateProto = fieldState.value?.scalar_value?.date_value
  val formatted =
    if (dateProto != null && dateProto.year > 0) {
      formatIsoDate(dateProto.year, dateProto.month, dateProto.day)
    } else {
      ""
    }
  var text by remember(path) { mutableStateOf(formatted) }
  var parseError by remember(path) { mutableStateOf<FormUiText?>(null) }
  var showDatePicker by remember(path) { mutableStateOf(false) }

  LaunchedEffect(path, formatted) {
    val currentParsed = parseDateInput(text)
    if (formatted.isEmpty() && text.isNotEmpty() && parseError == null) {
      text = ""
    } else if (
      formatted.isNotEmpty() &&
        (currentParsed as? DateInput.Valid)?.let { formatIsoDate(it.year, it.month, it.day) } !=
          formatted
    ) {
      text = formatted
      parseError = null
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FormOutlinedTextField(
      value = text,
      onValueChange = { raw ->
        text = raw
        when (val parsed = parseDateInput(raw)) {
          is DateInput.Empty -> {
            parseError = null
            controller.clearField(path)
          }
          is DateInput.Invalid -> {
            parseError = parsed.message
          }
          is DateInput.Valid -> {
            parseError = null
            controller.updateDate(path, parsed.year, parsed.month, parsed.day)
          }
        }
      },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      isError = parseError != null,
      placeholder = { Text(stringResource(Res.string.forms_date_placeholder)) },
      trailingIcon = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (text.isNotEmpty()) {
            IconButton(
              onClick = {
                text = ""
                parseError = null
                controller.clearField(path)
              }
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.forms_clear_date),
              )
            }
          }
          IconButton(onClick = { showDatePicker = true }) {
            Icon(
              imageVector = Icons.Default.DateRange,
              contentDescription = stringResource(Res.string.forms_select_date),
            )
          }
        }
      },
    )
    if (parseError != null) {
      Text(
        text = parseError?.resolve() ?: "",
        style = MaterialTheme.typography.labelSmall.copy(color = colors.error),
      )
    }
  }

  if (showDatePicker) {
    val initialSelectedDate =
      if (
        dateProto != null &&
          dateProto.year > 0 &&
          dateProto.month in 1..12 &&
          dateProto.day in 1..daysInMonth(dateProto.year, dateProto.month)
      ) {
        DateInput.Valid(dateProto.year, dateProto.month, dateProto.day)
      } else {
        null
      }
    val today = utcMillisToCivilDate(controller.session.environment.localClockEpochMillis())
    FormDatePickerDialog(
      initialSelectedDate = initialSelectedDate,
      today = today,
      onDismissRequest = { showDatePicker = false },
      onDateSelected = { picked ->
        text = formatIsoDate(picked.year, picked.month, picked.day)
        parseError = null
        focusManager.clearFocus()
        controller.updateDate(path, picked.year, picked.month, picked.day)
        showDatePicker = false
      },
    )
  }
}

/**
 * Responsive calendar dialog for [DateInputWidget] that adapts its header, row heights, and touch
 * target minimums to compact viewports (such as Google Earth KML balloons) so calendar weeks never
 * overlap the weekday header or the action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormDatePickerDialog(
  initialSelectedDate: DateInput.Valid?,
  today: DateInput.Valid,
  onDismissRequest: () -> Unit,
  onDateSelected: (DateInput.Valid) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  var selectedDate by remember(initialSelectedDate) { mutableStateOf(initialSelectedDate) }
  var displayedYear by
    remember(initialSelectedDate, today) {
      mutableStateOf((initialSelectedDate ?: today).year.coerceIn(MIN_PICKER_YEAR, MAX_PICKER_YEAR))
    }
  var displayedMonth by
    remember(initialSelectedDate, today) {
      mutableStateOf((initialSelectedDate ?: today).month.coerceIn(1, 12))
    }
  var showYearPicker by remember { mutableStateOf(false) }
  val weeks = remember(displayedYear, displayedMonth) { monthWeeks(displayedYear, displayedMonth) }

  BasicAlertDialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    BoxWithConstraints(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
      contentAlignment = Alignment.Center,
    ) {
      val isCompact =
        LocalFormDensity.current == FormDensity.COMPACT || maxHeight < 480.dp || maxWidth < 360.dp
      val navHeight = if (isCompact) 34.dp else 42.dp
      val weekdayHeight = if (isCompact) 22.dp else 30.dp
      val footerHeight = if (isCompact) 34.dp else 42.dp
      val chromeHeight =
        if (isCompact) {
          12.dp + navHeight + weekdayHeight + footerHeight
        } else {
          96.dp + navHeight + weekdayHeight + footerHeight
        }
      val availableGridHeight = (maxHeight - chromeHeight).coerceAtLeast(96.dp)
      val rowHeight =
        (availableGridHeight / weeks.size).coerceIn(24.dp, if (isCompact) 32.dp else 40.dp)

      CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides if (isCompact) 0.dp else 48.dp
      ) {
        Surface(
          modifier =
            Modifier.widthIn(max = if (isCompact) 316.dp else 336.dp)
              .fillMaxWidth()
              .heightIn(max = maxHeight),
          shape = if (isCompact) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge,
          color = colors.surfaceContainerHigh,
          tonalElevation = 6.dp,
        ) {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isCompact) 6.dp else 12.dp)
          ) {
            if (!isCompact) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = stringResource(Res.string.forms_select_date),
                  style = MaterialTheme.typography.labelMedium,
                  color = colors.onSurfaceVariant,
                )
                Text(
                  text =
                    selectedDate?.let { formatShortMonthDayYear(it.year, it.month, it.day) }
                      ?: stringResource(Res.string.forms_select_date),
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Medium,
                  color = colors.onSurface,
                )
              }
              HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            DatePickerMonthBar(
              displayedYear = displayedYear,
              displayedMonth = displayedMonth,
              showYearPicker = showYearPicker,
              isCompact = isCompact,
              height = navHeight,
              onToggleYearPicker = { showYearPicker = !showYearPicker },
              onShiftMonth = { delta ->
                val (y, m) = shiftYearMonth(displayedYear, displayedMonth, delta)
                displayedYear = y
                displayedMonth = m
              },
            )

            if (showYearPicker) {
              DatePickerYearGrid(
                displayedYear = displayedYear,
                isCompact = isCompact,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                onYearSelected = { year ->
                  displayedYear = year
                  showYearPicker = false
                },
              )
            } else {
              Row(
                modifier = Modifier.fillMaxWidth().height(weekdayHeight),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                WEEKDAY_HEADERS.forEach { dayInitial ->
                  Box(
                    modifier = Modifier.weight(1f).height(weekdayHeight),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = dayInitial,
                      style =
                        if (isCompact) MaterialTheme.typography.labelSmall
                        else MaterialTheme.typography.bodySmall,
                      color = colors.onSurfaceVariant,
                      textAlign = TextAlign.Center,
                    )
                  }
                }
              }

              Column(
                modifier =
                  Modifier.weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
              ) {
                weeks.forEach { week ->
                  Row(
                    modifier = Modifier.fillMaxWidth().height(rowHeight),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    week.forEach { day ->
                      Box(
                        modifier = Modifier.weight(1f).height(rowHeight),
                        contentAlignment = Alignment.Center,
                      ) {
                        if (day != null) {
                          val isSelected =
                            selectedDate?.let {
                              it.year == displayedYear &&
                                it.month == displayedMonth &&
                                it.day == day
                            } == true
                          val isToday =
                            today.year == displayedYear &&
                              today.month == displayedMonth &&
                              today.day == day
                          CalendarDayCell(
                            year = displayedYear,
                            month = displayedMonth,
                            day = day,
                            isSelected = isSelected,
                            isToday = isToday,
                            isCompact = isCompact,
                            rowHeight = rowHeight,
                            onClick = {
                              selectedDate = DateInput.Valid(displayedYear, displayedMonth, day)
                            },
                          )
                        }
                      }
                    }
                  }
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth().height(footerHeight),
              horizontalArrangement = Arrangement.End,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              val buttonPadding =
                if (isCompact) PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                else ButtonDefaults.TextButtonContentPadding
              val buttonTextStyle =
                if (isCompact) MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.labelLarge
              TextButton(onClick = onDismissRequest, contentPadding = buttonPadding) {
                Text(stringResource(Res.string.forms_cancel), style = buttonTextStyle)
              }
              TextButton(
                onClick = { selectedDate?.let(onDateSelected) },
                enabled = selectedDate != null,
                contentPadding = buttonPadding,
              ) {
                Text(stringResource(Res.string.forms_ok), style = buttonTextStyle)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DatePickerMonthBar(
  displayedYear: Int,
  displayedMonth: Int,
  showYearPicker: Boolean,
  isCompact: Boolean,
  height: Dp,
  onToggleYearPicker: () -> Unit,
  onShiftMonth: (Int) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier.fillMaxWidth().height(height),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier =
        Modifier.clip(MaterialTheme.shapes.small)
          .clickable(onClick = onToggleYearPicker)
          .padding(horizontal = 6.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = formatMonthYear(displayedYear, displayedMonth),
        style =
          if (isCompact) MaterialTheme.typography.labelLarge
          else MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = colors.onSurface,
      )
      Icon(
        imageVector =
          if (showYearPicker) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = colors.onSurfaceVariant,
      )
    }
    if (!showYearPicker) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        IconButton(
          onClick = { onShiftMonth(-1) },
          modifier = Modifier.size(if (isCompact) 30.dp else 36.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Previous month",
            modifier = Modifier.size(20.dp),
            tint = colors.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = { onShiftMonth(1) },
          modifier = Modifier.size(if (isCompact) 30.dp else 36.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Next month",
            modifier = Modifier.size(20.dp),
            tint = colors.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun CalendarDayCell(
  year: Int,
  month: Int,
  day: Int,
  isSelected: Boolean,
  isToday: Boolean,
  isCompact: Boolean,
  rowHeight: Dp,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val pillSize = (rowHeight - 2.dp).coerceIn(22.dp, if (isCompact) 28.dp else 36.dp)
  val description = formatFullDateDescription(year, month, day)
  val borderMod =
    if (isToday && !isSelected) {
      Modifier.border(BorderStroke(1.dp, colors.primary), CircleShape)
    } else {
      Modifier
    }
  Box(
    modifier =
      Modifier.size(pillSize)
        .clip(CircleShape)
        .background(if (isSelected) colors.primary else Color.Transparent)
        .then(borderMod)
        .clickable(onClick = onClick)
        .semantics(mergeDescendants = true) {
          text = AnnotatedString(description)
          role = Role.Button
          selected = isSelected
        },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = day.toString(),
      modifier = Modifier.clearAndSetSemantics {},
      style =
        if (isCompact) MaterialTheme.typography.labelMedium
        else MaterialTheme.typography.bodyMedium,
      fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
      color =
        when {
          isSelected -> colors.onPrimary
          isToday -> colors.primary
          else -> colors.onSurface
        },
      textAlign = TextAlign.Center,
      maxLines = 1,
    )
  }
}

@Composable
private fun DatePickerYearGrid(
  displayedYear: Int,
  isCompact: Boolean,
  modifier: Modifier = Modifier,
  onYearSelected: (Int) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val rowHeight = if (isCompact) 32.dp else 38.dp
  val scrollState = rememberScrollState()
  val years = remember { (MIN_PICKER_YEAR..MAX_PICKER_YEAR).chunked(3) }

  LaunchedEffect(displayedYear) {
    val rowIndex = ((displayedYear - MIN_PICKER_YEAR) / 3).coerceAtLeast(0)
    val rowPx = with(density) { rowHeight.toPx() }
    val targetPx = ((rowIndex - 2).coerceAtLeast(0) * rowPx).toInt()
    scrollState.scrollTo(targetPx)
  }

  Column(modifier = modifier.verticalScroll(scrollState)) {
    years.forEach { rowYears ->
      Row(
        modifier = Modifier.fillMaxWidth().height(rowHeight),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        rowYears.forEach { year ->
          val isSelected = year == displayedYear
          Box(
            modifier = Modifier.weight(1f).height(rowHeight).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
          ) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(rowHeight - 4.dp)
                  .clip(CircleShape)
                  .background(if (isSelected) colors.primary else Color.Transparent)
                  .clickable { onYearSelected(year) },
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = year.toString(),
                style =
                  if (isCompact) MaterialTheme.typography.labelMedium
                  else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) colors.onPrimary else colors.onSurface,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun TimeInputWidget(
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
    FormOutlinedTextField(
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
      placeholder = { Text(stringResource(Res.string.forms_time_placeholder)) },
    )
    FilledTonalButton(
      onClick = {
        // The local time of day, not UTC's.
        val localSeconds = controller.session.environment.localClockEpochMillis().floorDiv(1000L)
        val totalSec = localSeconds.mod(86400L)
        val hours = (totalSec / 3600L).toInt()
        val minutes = ((totalSec % 3600L) / 60L).toInt()
        val seconds = (totalSec % 60L).toInt()
        controller.updateTime(path, hours, minutes, seconds)
      }
    ) {
      Text(
        stringResource(Res.string.forms_now),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
      )
    }
  }
}

@Composable
internal fun TimestampInputWidget(
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
      text = ts?.let(::formatIsoInstant) ?: stringResource(Res.string.forms_no_timestamp),
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
      Text(
        stringResource(Res.string.forms_capture_timestamp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
      )
    }
  }
}
