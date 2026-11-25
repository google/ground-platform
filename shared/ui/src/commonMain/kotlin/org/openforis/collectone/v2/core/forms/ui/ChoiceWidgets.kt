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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FieldState
import org.openforis.collectone.v2.core.forms.model.ResolvedChoiceOption
import org.openforis.collectone.v2.core.forms.model.ResolvedLabel
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.SegmentedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_add
import org.openforis.collectone.v2.core.forms.ui.resources.forms_add_new
import org.openforis.collectone.v2.core.forms.ui.resources.forms_cancel
import org.openforis.collectone.v2.core.forms.ui.resources.forms_new_item_placeholder
import org.openforis.collectone.v2.core.forms.ui.resources.forms_no
import org.openforis.collectone.v2.core.forms.ui.resources.forms_no_matching_options
import org.openforis.collectone.v2.core.forms.ui.resources.forms_select_parent_first
import org.openforis.collectone.v2.core.forms.ui.resources.forms_yes

internal val BooleanInputOptions: List<Pair<Boolean, StringResource>> =
  listOf(true to Res.string.forms_yes, false to Res.string.forms_no)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BooleanInputWidget(
  path: String,
  fieldState: FieldState,
  controller: FormWizardController,
) {
  val currentBool = fieldState.value?.scalar_value?.bool_value
  val options = BooleanInputOptions
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    options.forEachIndexed { index, (boolVal, label) ->
      SegmentedButton(
        selected = currentBool == boolVal,
        onClick = { controller.updateBoolean(path, boolVal) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
        label = {
          Text(
            stringResource(label),
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
internal fun SelectOneWidget(
  control: ComponentState.ControlState,
  controller: FormWizardController,
) {
  val colors = MaterialTheme.colorScheme
  val path = control.canonicalPath
  val selectedValue = control.fieldState.value?.scalar_value?.string_value ?: ""
  val appearanceTokens = appearanceTokens(control.appearance)
  val isQuick = appearanceTokens.contains("quick")
  val isLikert = appearanceTokens.contains("likert")
  val allowAddEntity = appearanceTokens.contains("add-entity")
  var addedOptions by remember(path) { mutableStateOf<List<ResolvedChoiceOption>>(emptyList()) }
  val allOptions = control.options + addedOptions

  val parentInfo =
    remember(control, controller.formState) {
      controller.parentQuestionInfoFor(control)
    }
  if (parentInfo != null && !parentInfo.isAnswered) {
    Text(
      text = stringResource(Res.string.forms_select_parent_first, parentInfo.label),
      style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
    )
    return
  }

  val candidateOptions = CascadingSelects.allowedOptions(allOptions, parentInfo?.selectedValues)

  if (candidateOptions.isEmpty() && !allowAddEntity) {
    Text(
      text = stringResource(Res.string.forms_no_matching_options),
      style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
    )
    return
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (candidateOptions.isEmpty()) {
      Text(
        text = stringResource(Res.string.forms_no_matching_options),
        style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
      )
    } else if (isLikert) {
      LikertScaleColumn(
        options = candidateOptions,
        selectedValue = selectedValue,
        onSelect = { value ->
          controller.updateString(path, value)
          if (isQuick) controller.nextStep()
        },
      )
    } else {
      candidateOptions.forEach { option ->
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
    if (allowAddEntity) {
      AddInlineEntitySection(
        existingValues = candidateOptions.map { it.value }.toSet(),
        onAdd = { added ->
          addedOptions = addedOptions + added
          controller.updateString(path, added.value)
        },
      )
    }
  }
}

/**
 * Strips a redundant leading 1-based ordinal prefix (such as `"1 - "`, `"1: "`, `"1. "`, `"1) "`,
 * or `"(1) "`) from [rawLabel] when it matches [stepNumber], so a Likert option rendered beside a
 * numbered step badge does not repeat the step number (for example, `"1 - Severe Dieback"` becomes
 * `"Severe Dieback"`). Preserves hyphenated ranges like `"1-5 trees"` and labels that consist only
 * of the number itself.
 */
internal fun formatLikertOptionLabel(rawLabel: String, stepNumber: Int): String {
  val trimmed = rawLabel.trim()
  if (trimmed.isEmpty() || stepNumber <= 0) return rawLabel
  val step = stepNumber.toString()
  val patterns =
    listOf(
      Regex("^\\($step\\)\\s+(.+)$"),
      Regex("^$step[.):]\\s+(.+)$"),
      Regex("^$step\\s+[-–—]\\s*(.+)$"),
      Regex("^$step\\s*[-–—]\\s+(.+)$"),
    )
  for (pattern in patterns) {
    val match = pattern.matchEntire(trimmed)
    val remainder = match?.groupValues?.getOrNull(1)?.trim()
    if (!remainder.isNullOrEmpty()) {
      return remainder
    }
  }
  return rawLabel
}

/**
 * Vertical ordinal scale for `select1` controls with `appearance="likert"`.
 *
 * Stacks full-width option cards vertically so long or translated labels are completely readable
 * before tapping, while visually connecting consecutive steps with a vertical continuum track
 * aligned to each option's 1-based ordinal badge (`1`..`N`).
 */
@Composable
private fun LikertScaleColumn(
  options: List<ResolvedChoiceOption>,
  selectedValue: String,
  onSelect: (String) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = Modifier.fillMaxWidth()) {
    options.forEachIndexed { index, option ->
      val stepNumber = index + 1
      val isSelected = selectedValue == option.value
      LikertChoiceCardRow(
        stepNumber = stepNumber,
        option = option,
        isSelected = isSelected,
        onClick = { onSelect(option.value) },
      )
      if (index < options.lastIndex) {
        // Center of the 28.dp badge inside a card with 1.dp border + 12.dp start padding is at
        // 27.dp; a 2.dp connector line starting at 26.dp aligns directly with the badge center.
        Box(
          modifier =
            Modifier.padding(start = 26.dp)
              .width(2.dp)
              .height(6.dp)
              .background(colors.outlineVariant)
        )
      }
    }
  }
}

@Composable
private fun LikertChoiceCardRow(
  stepNumber: Int,
  option: ResolvedChoiceOption,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val choiceColor = ChoiceImageRegistry.parseChoiceColor(option.properties["color"])
  val colorStyle = choiceColor?.let {
    ChoiceImageRegistry.choiceOptionColorStyle(
      choiceColor = it,
      isSelected = isSelected,
      surfaceColor = colors.surfaceContainerLow,
      onSurfaceColor = colors.onSurface,
      tintContainer = false,
    )
  }
  val choiceBitmap =
    ChoiceImageRegistry.rememberChoiceImageBitmap(
      imageUri = option.label.media?.image_uri,
      inlineImageDataUri = option.properties["collectone_image"],
    )
  val displayLabel = formatLikertOptionLabel(option.label.text, stepNumber)

  OutlinedCard(
    onClick = onClick,
    modifier =
      Modifier.fillMaxWidth()
        .then(
          if (colorStyle != null) {
            Modifier.testTag("choice-color-card-${option.value}")
          } else {
            Modifier
          }
        ),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor =
          colorStyle?.containerColor
            ?: if (isSelected) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor =
          colorStyle?.contentColor
            ?: if (isSelected) colors.onPrimaryContainer else colors.onSurface,
      ),
    border =
      BorderStroke(
        width = colorStyle?.borderWidth ?: if (isSelected) 2.dp else 1.dp,
        color =
          colorStyle?.borderColor ?: if (isSelected) colors.primary else colors.outlineVariant,
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier =
          Modifier.weight(1f)
            .padding(
              start = 12.dp,
              end = 12.dp,
              top = 9.dp,
              bottom = 9.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          modifier =
            Modifier.size(28.dp)
              .then(
                if (colorStyle != null) {
                  Modifier.testTag("choice-color-swatch-${option.value}")
                } else {
                  Modifier
                }
              ),
          shape = CircleShape,
          color =
            colorStyle?.swatchColor
              ?: if (isSelected) colors.primary else colors.secondaryContainer,
          contentColor =
            colorStyle?.onSwatchColor
              ?: if (isSelected) colors.onPrimary else colors.onSecondaryContainer,
          border =
            when {
              colorStyle != null -> BorderStroke(1.dp, colorStyle.swatchBorderColor)
              isSelected -> null
              else -> BorderStroke(1.dp, colors.outlineVariant)
            },
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = stepNumber.toString(),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              maxLines = 1,
              softWrap = false,
            )
          }
        }
        if (choiceBitmap != null) {
          Image(
            bitmap = choiceBitmap,
            contentDescription = displayLabel,
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp)),
          )
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = displayLabel,
            style =
              MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color =
                  colorStyle?.contentColor
                    ?: if (isSelected) colors.onPrimaryContainer else colors.onSurface,
              ),
          )
          if (!option.label.guidanceText.isNullOrBlank()) {
            Text(
              text = option.label.guidanceText.orEmpty(),
              style =
                MaterialTheme.typography.bodySmall.copy(
                  color =
                    colorStyle?.secondaryContentColor
                      ?: if (isSelected) colors.onPrimaryContainer.copy(alpha = 0.8f)
                      else colors.onSurfaceVariant
                ),
            )
          }
          val media = option.label.media
          if (media != null) {
            val showImageBadge = choiceBitmap == null && media.image_uri.isNotBlank()
            val hasBadges =
              showImageBadge ||
                media.big_image_uri.isNotBlank() ||
                media.audio_uri.isNotBlank() ||
                media.video_uri.isNotBlank()
            if (hasBadges) {
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showImageBadge) {
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
          }
        }
        if (isSelected) {
          if (colorStyle != null) {
            Surface(
              modifier = Modifier.size(24.dp),
              shape = CircleShape,
              color = colorStyle.swatchColor,
              contentColor = colorStyle.onSwatchColor,
              border = BorderStroke(1.dp, colorStyle.swatchBorderColor),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = colorStyle.onSwatchColor,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          } else {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = null,
              tint = colors.primary,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun SelectMultipleWidget(
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
  val allowAddEntity = "add-entity" in appearanceTokens(control.appearance)
  var addedOptions by remember(path) { mutableStateOf<List<ResolvedChoiceOption>>(emptyList()) }
  val allOptions = control.options + addedOptions

  val parentInfo =
    remember(control, controller.formState) {
      controller.parentQuestionInfoFor(control)
    }
  if (parentInfo != null && !parentInfo.isAnswered) {
    Text(
      text = stringResource(Res.string.forms_select_parent_first, parentInfo.label),
      style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
    )
    return
  }

  val candidateOptions = CascadingSelects.allowedOptions(allOptions, parentInfo?.selectedValues)

  if (candidateOptions.isEmpty() && !allowAddEntity) {
    Text(
      text = stringResource(Res.string.forms_no_matching_options),
      style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
    )
    return
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (candidateOptions.isEmpty()) {
      Text(
        text = stringResource(Res.string.forms_no_matching_options),
        style = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant),
      )
    }
    candidateOptions.forEach { option ->
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
    if (allowAddEntity) {
      AddInlineEntitySection(
        existingValues = candidateOptions.map { it.value }.toSet(),
        onAdd = { added ->
          addedOptions = addedOptions + added
          controller.updateMultiSelect(path, selectedValues + added.value)
        },
      )
    }
  }
}

@Composable
private fun AddInlineEntitySection(
  existingValues: Set<String>,
  onAdd: (ResolvedChoiceOption) -> Unit,
) {
  var isAdding by remember { mutableStateOf(false) }
  var label by remember { mutableStateOf("") }
  if (!isAdding) {
    OutlinedButton(
      onClick = { isAdding = true },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Icon(
        imageVector = Icons.Default.Add,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(stringResource(Res.string.forms_add_new))
    }
    return
  }
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      FormOutlinedTextField(
        value = label,
        onValueChange = { label = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(Res.string.forms_new_item_placeholder)) },
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
      ) {
        TextButton(
          onClick = {
            label = ""
            isAdding = false
          }
        ) {
          Text(stringResource(Res.string.forms_cancel))
        }
        Button(
          enabled = label.isNotBlank(),
          onClick = {
            val trimmed = label.trim()
            val baseValue =
              trimmed
                .lowercase()
                .map { if (it.isLetterOrDigit()) it else '_' }
                .joinToString("")
                .trim('_')
                .ifEmpty { "new_item" }
            var candidate = baseValue
            var suffix = 2
            while (candidate in existingValues) {
              candidate = "${baseValue}_${suffix++}"
            }
            onAdd(ResolvedChoiceOption(value = candidate, label = ResolvedLabel(text = trimmed)))
            label = ""
            isAdding = false
          },
        ) {
          Text(stringResource(Res.string.forms_add))
        }
      }
    }
  }
}

@Composable
private fun ChoiceCardRow(
  option: ResolvedChoiceOption,
  isSelected: Boolean,
  @Suppress("UnusedParameter") isMultiSelect: Boolean,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val choiceColor = ChoiceImageRegistry.parseChoiceColor(option.properties["color"])
  val colorStyle = choiceColor?.let {
    ChoiceImageRegistry.choiceOptionColorStyle(
      choiceColor = it,
      isSelected = isSelected,
      surfaceColor = colors.surfaceContainerLow,
      onSurfaceColor = colors.onSurface,
      tintContainer = false,
    )
  }
  val choiceBitmap =
    ChoiceImageRegistry.rememberChoiceImageBitmap(
      imageUri = option.label.media?.image_uri,
      inlineImageDataUri = option.properties["collectone_image"],
    )
  OutlinedCard(
    onClick = onClick,
    modifier =
      Modifier.fillMaxWidth()
        .then(
          if (colorStyle != null) {
            Modifier.testTag("choice-color-card-${option.value}")
          } else {
            Modifier
          }
        ),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor =
          colorStyle?.containerColor
            ?: if (isSelected) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor =
          colorStyle?.contentColor
            ?: if (isSelected) colors.onPrimaryContainer else colors.onSurface,
      ),
    border =
      BorderStroke(
        width = colorStyle?.borderWidth ?: if (isSelected) 2.dp else 1.dp,
        color =
          colorStyle?.borderColor ?: if (isSelected) colors.primary else colors.outlineVariant,
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier =
          Modifier.weight(1f)
            .padding(
              start = 14.dp,
              end = 14.dp,
              top = 12.dp,
              bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (choiceBitmap != null) {
          Image(
            bitmap = choiceBitmap,
            contentDescription = option.label.text,
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp)),
          )
        }
        if (colorStyle != null) {
          Surface(
            modifier = Modifier.size(24.dp).testTag("choice-color-swatch-${option.value}"),
            shape = RoundedCornerShape(7.dp),
            color = colorStyle.swatchColor,
            contentColor = colorStyle.onSwatchColor,
            border = BorderStroke(1.dp, colorStyle.swatchBorderColor),
          ) {
            if (isSelected) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = colorStyle.onSwatchColor,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = option.label.text,
            style =
              MaterialTheme.typography.bodyMedium.copy(
                fontWeight =
                  when {
                    isSelected -> FontWeight.Bold
                    colorStyle != null -> FontWeight.SemiBold
                    else -> FontWeight.Medium
                  },
                color =
                  colorStyle?.contentColor
                    ?: if (isSelected) colors.onPrimaryContainer else colors.onSurface,
              ),
          )
          if (!option.label.guidanceText.isNullOrBlank()) {
            Text(
              text = option.label.guidanceText.orEmpty(),
              style =
                MaterialTheme.typography.bodySmall.copy(
                  color =
                    colorStyle?.secondaryContentColor
                      ?: if (isSelected) colors.onPrimaryContainer.copy(alpha = 0.8f)
                      else colors.onSurfaceVariant
                ),
            )
          }
          val media = option.label.media
          if (media != null) {
            val showImageBadge = choiceBitmap == null && media.image_uri.isNotBlank()
            val hasBadges =
              showImageBadge ||
                media.big_image_uri.isNotBlank() ||
                media.audio_uri.isNotBlank() ||
                media.video_uri.isNotBlank()
            if (hasBadges) {
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showImageBadge) {
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
          }
        }
        if (isSelected && colorStyle == null) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

/**
 * Prominent color-coded pill displaying a multiple-choice option's label alongside a left color
 * accent stripe, a high-visibility swatch, a tinted surface, and accessible contrast.
 */
@Composable
fun ChoiceColorPill(
  label: String,
  color: Color,
  modifier: Modifier = Modifier,
  isSelected: Boolean = true,
  compact: Boolean = false,
) {
  val colors = MaterialTheme.colorScheme
  val colorStyle =
    ChoiceImageRegistry.choiceOptionColorStyle(
      choiceColor = color,
      isSelected = isSelected,
      surfaceColor = colors.surfaceContainerLow,
      onSurfaceColor = colors.onSurface,
    )
  val cornerRadius = if (compact) 6.dp else 8.dp
  Surface(
    modifier = modifier.testTag("choice-color-pill-$label"),
    shape = RoundedCornerShape(cornerRadius),
    color = colorStyle.containerColor,
    contentColor = colorStyle.contentColor,
    border =
      BorderStroke(
        width = if (compact) 1.dp else 1.5.dp,
        color = colorStyle.borderColor,
      ),
  ) {
    Row(
      modifier = Modifier.height(IntrinsicSize.Min),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.width(if (compact) 4.dp else 5.dp)
            .fillMaxHeight()
            .background(colorStyle.accentStripeColor)
      )
      Row(
        modifier =
          Modifier.padding(
            horizontal = if (compact) 6.dp else 9.dp,
            vertical = if (compact) 2.dp else 4.dp,
          ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
      ) {
        Surface(
          modifier = Modifier.size(if (compact) 10.dp else 13.dp),
          shape = RoundedCornerShape(if (compact) 3.dp else 4.dp),
          color = colorStyle.swatchColor,
          border = BorderStroke(1.dp, colorStyle.swatchBorderColor),
        ) {}
        Text(
          text = label,
          style =
            (if (compact) MaterialTheme.typography.labelSmall
              else MaterialTheme.typography.bodySmall)
              .copy(
                fontWeight = FontWeight.SemiBold,
                color = colorStyle.contentColor,
              ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/**
 * Displays one or more [ResolvedChoiceAnswerColor] items as a wrapping row of [ChoiceColorPill]s.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceColorPillsRow(
  items: List<ResolvedChoiceAnswerColor>,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    items.forEach { item ->
      ChoiceColorPill(
        label = item.label,
        color = item.color,
        isSelected = true,
        compact = compact,
      )
    }
  }
}
