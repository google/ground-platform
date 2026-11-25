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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pentagon
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.MediaSource
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.ui.BadgeTone
import org.openforis.collectone.v2.core.forms.ui.ChoiceImageRegistry
import org.openforis.collectone.v2.core.forms.ui.TonalBadge
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_add_new_feature
import org.openforis.collectone.v2.web.resources.formeditor_add_new_item
import org.openforis.collectone.v2.web.resources.formeditor_always_shown
import org.openforis.collectone.v2.web.resources.formeditor_answer
import org.openforis.collectone.v2.web.resources.formeditor_back
import org.openforis.collectone.v2.web.resources.formeditor_capture_point_gps
import org.openforis.collectone.v2.web.resources.formeditor_capture_point_or_draw
import org.openforis.collectone.v2.web.resources.formeditor_choose_data_table
import org.openforis.collectone.v2.web.resources.formeditor_choose_map_layer
import org.openforis.collectone.v2.web.resources.formeditor_drag_to_reorder
import org.openforis.collectone.v2.web.resources.formeditor_has_issues
import org.openforis.collectone.v2.web.resources.formeditor_linked_to_standard_field
import org.openforis.collectone.v2.web.resources.formeditor_more_choices
import org.openforis.collectone.v2.web.resources.formeditor_next
import org.openforis.collectone.v2.web.resources.formeditor_no_label
import org.openforis.collectone.v2.web.resources.formeditor_question_number_badge
import org.openforis.collectone.v2.web.resources.formeditor_question_type_required
import org.openforis.collectone.v2.web.resources.formeditor_read_only_note
import org.openforis.collectone.v2.web.resources.formeditor_record_audio
import org.openforis.collectone.v2.web.resources.formeditor_record_audio_or_upload
import org.openforis.collectone.v2.web.resources.formeditor_record_video
import org.openforis.collectone.v2.web.resources.formeditor_record_video_or_upload
import org.openforis.collectone.v2.web.resources.formeditor_take_photo
import org.openforis.collectone.v2.web.resources.formeditor_take_photo_or_upload
import org.openforis.collectone.v2.web.resources.formeditor_untitled_form
import org.openforis.collectone.v2.web.resources.formeditor_walk_line_gps
import org.openforis.collectone.v2.web.resources.formeditor_walk_line_or_draw
import org.openforis.collectone.v2.web.resources.formeditor_walk_polygon_gps
import org.openforis.collectone.v2.web.resources.formeditor_walk_polygon_or_draw
import org.openforis.collectone.v2.web.ui.common.GrabPointerIcon

@Composable
internal fun ScreenPreviewCard(
  index: Int,
  question: EditorQuestion,
  form: EditorForm,
  datasets: List<EditorDataset>,
  isSelected: Boolean,
  hasIssues: Boolean,
  isDragged: Boolean,
  onClick: () -> Unit,
  isLinked: Boolean = false,
  showDragHandle: Boolean = true,
  activeLanguage: String = "en",
  defaultLanguage: String = "en",
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  var isHovered by remember { mutableStateOf(false) }
  val isHoverHighlighted = isHovered && !isSelected && !isDragged
  // Subtle selection using AppTheme's muted secondary roles (the same treatment as selected
  // list rows elsewhere in the app) rather than the bright primaryContainer accent.
  // Hovering the card body highlights the entire card and shows a pointer cursor (click to select),
  // while hovering the drag handle icon shows an open-hand grab cursor (drag to reorder); active
  // dragging switches the whole canvas to a closed-hand grabbing cursor.
  OutlinedCard(
    onClick = onClick,
    modifier =
      modifier.width(CardWidth).height(CardHeight).pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent()
            when (event.type) {
              PointerEventType.Enter -> isHovered = true
              PointerEventType.Exit -> isHovered = false
            }
          }
        }
      },
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor =
          when {
            isSelected -> colors.secondaryContainer
            isHoverHighlighted -> colors.secondaryContainer.copy(alpha = 0.28f)
            else -> colors.surface
          },
        contentColor = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
      ),
    border =
      BorderStroke(
        width = if (isSelected || isDragged || isHoverHighlighted) 2.dp else 1.dp,
        color =
          when {
            isDragged -> colors.outline
            hasIssues -> colors.error
            isSelected -> colors.secondary
            isHoverHighlighted -> colors.primary.copy(alpha = 0.65f)
            else -> colors.outlineVariant
          },
      ),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (showDragHandle) {
          Icon(
            Icons.Outlined.DragIndicator,
            contentDescription = stringResource(Res.string.formeditor_drag_to_reorder),
            tint =
              when {
                isSelected -> colors.onSecondaryContainer
                isDragged -> colors.onSurface
                else -> colors.onSurfaceVariant
              },
            modifier = Modifier.size(16.dp).pointerHoverIcon(GrabPointerIcon),
          )
        }
        TonalBadge(
          text = stringResource(Res.string.formeditor_question_number_badge, index + 1),
          tone = BadgeTone.NEUTRAL,
        )
        val section =
          if (question.sectionId.isNotBlank()) form.section(question.sectionId) else null
        if (section != null) {
          TonalBadge(
            text = if (section.repeatable) "↻ ${section.title}" else "▤ ${section.title}",
            tone = if (section.repeatable) BadgeTone.PRIMARY else BadgeTone.SECONDARY,
          )
        }
        Icon(
          questionTypeIcon(question.type),
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        )
        Text(
          text = stringResource(question.type.labelRes),
          style = MaterialTheme.typography.labelSmall,
          color = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (isLinked) {
          Icon(
            Icons.Outlined.Link,
            contentDescription = stringResource(Res.string.formeditor_linked_to_standard_field),
            tint = if (isSelected) colors.onSecondaryContainer else colors.primary,
            modifier = Modifier.size(14.dp),
          )
        }
        if (hasIssues) {
          Icon(
            Icons.Outlined.Warning,
            contentDescription = stringResource(Res.string.formeditor_has_issues),
            tint = colors.error,
            modifier = Modifier.size(14.dp),
          )
        }
      }
      val isConditionallyShown = form.isShownConditionally(question)
      Text(
        text =
          form.describeRelevance(question, datasets)
            ?: stringResource(Res.string.formeditor_always_shown),
        style = MaterialTheme.typography.labelSmall,
        color =
          when {
            isSelected -> colors.onSecondaryContainer
            isConditionallyShown -> colors.primary
            else -> colors.onSurfaceVariant
          },
        fontWeight = if (isConditionallyShown) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
      )

      MiniScreen(
        question = question,
        formTitle = form.title,
        datasets = datasets,
        activeLanguage = activeLanguage,
        defaultLanguage = defaultLanguage,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      )

      val validationSummary =
        validationSummaryText(question.type, question.validation)?.resolve()?.takeIf {
          it.isNotBlank()
        }
      Text(
        text =
          buildString {
            if (question.required) {
              append(
                stringResource(
                  Res.string.formeditor_question_type_required,
                  stringResource(question.type.labelRes),
                )
              )
            } else {
              append(stringResource(question.type.labelRes))
            }
            if (validationSummary != null) {
              append(" · ")
              append(validationSummary)
            }
          },
        style = MaterialTheme.typography.labelSmall,
        color = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      )
    }
  }
}

/** Static, scaled-down mock of how the question screen looks on a device. */
@Composable
private fun MiniScreen(
  question: EditorQuestion,
  formTitle: String,
  datasets: List<EditorDataset>,
  activeLanguage: String = "en",
  defaultLanguage: String = "en",
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val displayLabel = question.displayLabel(activeLanguage, defaultLanguage)
  val displayHint = question.displayHint(activeLanguage, defaultLanguage)
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    color = colors.surfaceContainerLowest,
    contentColor = colors.onSurface,
    border = BorderStroke(1.dp, colors.outlineVariant),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .background(colors.primary)
            .padding(horizontal = 8.dp, vertical = 5.dp)
      ) {
        Text(
          text = formTitle.ifBlank { stringResource(Res.string.formeditor_untitled_form) },
          style = MaterialTheme.typography.labelSmall,
          color = colors.onPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text =
            displayLabel.ifBlank { stringResource(Res.string.formeditor_no_label) } +
              if (question.required) " *" else "",
          style = MaterialTheme.typography.labelLarge,
          color = colors.onSurface,
          fontWeight = FontWeight.Bold,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        if (displayHint.isNotBlank()) {
          Text(
            text = displayHint,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.height(2.dp))
        MiniWidget(question, datasets, activeLanguage, defaultLanguage)
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        MiniButton(stringResource(Res.string.formeditor_back), filled = false)
        MiniButton(stringResource(Res.string.formeditor_next), filled = true)
      }
    }
  }
}

@Composable
private fun MiniButton(text: String, filled: Boolean) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier =
      Modifier.clip(RoundedCornerShape(50))
        .background(if (filled) colors.primary else Color.Transparent)
        .border(1.dp, if (filled) colors.primary else colors.outline, RoundedCornerShape(50))
        .padding(horizontal = 10.dp, vertical = 2.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = if (filled) colors.onPrimary else colors.primary,
    )
  }
}

@Composable
private fun MiniWidget(
  question: EditorQuestion,
  datasets: List<EditorDataset>,
  activeLanguage: String = "en",
  defaultLanguage: String = "en",
) {
  val colors = MaterialTheme.colorScheme
  when (question.type) {
    EditorQuestionType.TEXT -> MiniField(stringResource(Res.string.formeditor_answer), 26.dp)
    EditorQuestionType.LONG_TEXT -> MiniField(stringResource(Res.string.formeditor_answer), 52.dp)
    EditorQuestionType.NUMBER -> MiniField(if (question.decimalPlaces <= 0) "123" else "1.5", 26.dp)
    EditorQuestionType.DATE -> MiniField("YYYY-MM-DD", 26.dp, Icons.Outlined.DateRange)
    EditorQuestionType.SELECT_ONE,
    EditorQuestionType.SELECT_MULTIPLE -> {
      val round = question.type == EditorQuestionType.SELECT_ONE
      val effectiveSource = question.effectiveChoiceSource(datasets)
      val datasetId = question.choiceDatasetId
      val dataset = datasetId?.let { id -> datasets.firstOrNull { it.id == id } }
      if (question.usesDatasetChoices) {
        val fallbackLabel =
          if (effectiveSource == ChoiceSource.DATA_TABLE) {
            stringResource(Res.string.formeditor_choose_data_table)
          } else stringResource(Res.string.formeditor_choose_map_layer)
        val defaultIcon =
          if (effectiveSource == ChoiceSource.DATA_TABLE) Icons.Outlined.TableChart
          else Icons.Outlined.Layers
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            dataset?.let(::datasetIcon) ?: defaultIcon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = dataset?.displayName ?: datasetId.orEmpty().ifBlank { fallbackLabel },
            style = MaterialTheme.typography.labelSmall,
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      val previewChoices = question.resolvedChoices(datasets)
      val maxItems = if (question.usesDatasetChoices) 3 else 4
      previewChoices.take(maxItems).forEach { choice ->
        val choiceColor = ChoiceImageRegistry.parseChoiceColor(choice.colorHex)
        val colorStyle = choiceColor?.let {
          ChoiceImageRegistry.choiceOptionColorStyle(
            choiceColor = it,
            isSelected = false,
            surfaceColor = colors.surfaceContainerLow,
            onSurfaceColor = colors.onSurface,
            tintContainer = false,
          )
        }
        if (colorStyle != null) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = colorStyle.containerColor,
            border = BorderStroke(1.dp, colorStyle.borderColor),
            modifier = Modifier.fillMaxWidth().testTag("preview-choice-color-card-${choice.value}"),
          ) {
            Row(
              modifier = Modifier.heightIn(min = 22.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier.size(10.dp)
                      .border(
                        1.5.dp,
                        colorStyle.accentStripeColor,
                        if (round) CircleShape else RoundedCornerShape(2.dp),
                      )
                )
                Spacer(Modifier.width(5.dp))
                Box(
                  modifier =
                    Modifier.size(10.dp)
                      .clip(RoundedCornerShape(3.dp))
                      .background(colorStyle.swatchColor)
                      .border(1.dp, colorStyle.swatchBorderColor, RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(5.dp))
                Text(
                  text =
                    choice.displayLabel(activeLanguage, defaultLanguage).ifBlank { choice.value },
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Medium,
                  color = colorStyle.contentColor,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        } else {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier.size(10.dp)
                  .border(
                    1.5.dp,
                    colors.primary,
                    if (round) CircleShape else RoundedCornerShape(2.dp),
                  )
            )
            Spacer(Modifier.width(6.dp))
            choice.image?.let { image ->
              rememberChoiceImageBitmap(image)?.let { bitmap ->
                Image(
                  bitmap = bitmap,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(4.dp))
              }
            }
            Text(
              text = choice.displayLabel(activeLanguage, defaultLanguage).ifBlank { choice.value },
              style = MaterialTheme.typography.labelSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
      if (previewChoices.size > maxItems) {
        Text(
          text = stringResource(Res.string.formeditor_more_choices, previewChoices.size - maxItems),
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
        )
      }
      if (question.usesDatasetChoices && question.allowAddEntity) {
        Text(
          text =
            if (dataset != null) {
              stringResource(Res.string.formeditor_add_new_feature, dataset.featureNounText())
            } else stringResource(Res.string.formeditor_add_new_item),
          style = MaterialTheme.typography.labelSmall,
          color = colors.primary,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
    EditorQuestionType.LOCATION ->
      MiniPlaceholder(
        geometryPlaceholderText(
          question,
          Res.string.formeditor_capture_point_gps,
          Res.string.formeditor_capture_point_or_draw,
        ),
        Icons.Outlined.LocationOn,
      )
    EditorQuestionType.LINE ->
      MiniPlaceholder(
        geometryPlaceholderText(
          question,
          Res.string.formeditor_walk_line_gps,
          Res.string.formeditor_walk_line_or_draw,
        ),
        Icons.Outlined.Timeline,
      )
    EditorQuestionType.POLYGON ->
      MiniPlaceholder(
        geometryPlaceholderText(
          question,
          Res.string.formeditor_walk_polygon_gps,
          Res.string.formeditor_walk_polygon_or_draw,
        ),
        Icons.Outlined.Pentagon,
      )
    EditorQuestionType.PHOTO ->
      MiniPlaceholder(
        mediaPlaceholderText(
          question,
          Res.string.formeditor_take_photo,
          Res.string.formeditor_take_photo_or_upload,
        ),
        Icons.Outlined.Add,
      )
    EditorQuestionType.VIDEO ->
      MiniPlaceholder(
        mediaPlaceholderText(
          question,
          Res.string.formeditor_record_video,
          Res.string.formeditor_record_video_or_upload,
        ),
        Icons.Outlined.Videocam,
      )
    EditorQuestionType.AUDIO ->
      MiniPlaceholder(
        mediaPlaceholderText(
          question,
          Res.string.formeditor_record_audio,
          Res.string.formeditor_record_audio_or_upload,
        ),
        Icons.Outlined.Mic,
      )
    EditorQuestionType.NOTE ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Outlined.Info,
          contentDescription = null,
          tint = colors.onSurfaceVariant,
          modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
          text = stringResource(Res.string.formeditor_read_only_note),
          style = MaterialTheme.typography.labelSmall,
          color = colors.onSurfaceVariant,
        )
      }
  }
}

@Composable
private fun MiniField(
  placeholder: String,
  height: Dp,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(height)
        .border(1.dp, colors.outline, RoundedCornerShape(4.dp))
        .padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = placeholder,
      style = MaterialTheme.typography.labelSmall,
      color = colors.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    if (icon != null) {
      Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(12.dp),
        tint = colors.onSurfaceVariant,
      )
    }
  }
}

/**
 * Caption of a geometry question's mini widget: [gpsOnly] when locked to GPS, or [gpsOrMap] (the
 * map alternative) when collectors may also draw on the map.
 */
@Composable
private fun geometryPlaceholderText(
  question: EditorQuestion,
  gpsOnly: StringResource,
  gpsOrMap: StringResource,
): String =
  when (question.capture) {
    GeometryCapture.GPS_ONLY -> stringResource(gpsOnly)
    GeometryCapture.GPS_OR_MAP -> stringResource(gpsOrMap)
  }

/**
 * Caption of a media question's mini widget: [captureOnly] when collectors must capture a fresh
 * file, or [captureOrUpload] (the upload alternative) when they may also pick one from their
 * device.
 */
@Composable
private fun mediaPlaceholderText(
  question: EditorQuestion,
  captureOnly: StringResource,
  captureOrUpload: StringResource,
): String =
  when (question.mediaSource) {
    MediaSource.CAPTURE_ONLY -> stringResource(captureOnly)
    MediaSource.CAPTURE_OR_UPLOAD -> stringResource(captureOrUpload)
  }

@Composable
private fun MiniPlaceholder(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
  val colors = MaterialTheme.colorScheme
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .height(64.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(colors.surfaceVariant),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = colors.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
  }
}
