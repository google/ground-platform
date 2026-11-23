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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceColors
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoiceImage
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.ui.common.FlatFilterChip
import org.openforis.collectone.v2.app.ui.common.PlatformPickResult
import org.openforis.collectone.v2.app.ui.common.isPlatformMediaPickerAvailable
import org.openforis.collectone.v2.app.ui.common.openPlatformMediaPicker
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.core.forms.media.MediaCapture
import org.openforis.collectone.v2.core.forms.ui.ChoiceImageRegistry
import org.openforis.collectone.v2.core.forms.ui.controls.AssistChip
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.DropdownMenuItem
import org.openforis.collectone.v2.core.forms.ui.controls.FilledTonalButton
import org.openforis.collectone.v2.core.forms.ui.controls.IconButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_add_choice
import org.openforis.collectone.v2.web.resources.formeditor_add_choice_image
import org.openforis.collectone.v2.web.resources.formeditor_allow_add_new
import org.openforis.collectone.v2.web.resources.formeditor_any_unfiltered
import org.openforis.collectone.v2.web.resources.formeditor_append
import org.openforis.collectone.v2.web.resources.formeditor_cancel
import org.openforis.collectone.v2.web.resources.formeditor_cascading_filter
import org.openforis.collectone.v2.web.resources.formeditor_choice_color
import org.openforis.collectone.v2.web.resources.formeditor_choice_color_named
import org.openforis.collectone.v2.web.resources.formeditor_choice_color_none
import org.openforis.collectone.v2.web.resources.formeditor_choice_image
import org.openforis.collectone.v2.web.resources.formeditor_choice_image_too_large
import org.openforis.collectone.v2.web.resources.formeditor_choices
import org.openforis.collectone.v2.web.resources.formeditor_choices_from_external_list
import org.openforis.collectone.v2.web.resources.formeditor_choose
import org.openforis.collectone.v2.web.resources.formeditor_code_value
import org.openforis.collectone.v2.web.resources.formeditor_collectors_can_add_new
import org.openforis.collectone.v2.web.resources.formeditor_collectors_choose_from
import org.openforis.collectone.v2.web.resources.formeditor_create_form_for
import org.openforis.collectone.v2.web.resources.formeditor_dataset_has_no_form
import org.openforis.collectone.v2.web.resources.formeditor_filter_all
import org.openforis.collectone.v2.web.resources.formeditor_filter_label
import org.openforis.collectone.v2.web.resources.formeditor_import_choices
import org.openforis.collectone.v2.web.resources.formeditor_import_choices_file
import org.openforis.collectone.v2.web.resources.formeditor_import_choices_message
import org.openforis.collectone.v2.web.resources.formeditor_label
import org.openforis.collectone.v2.web.resources.formeditor_label_in_language
import org.openforis.collectone.v2.web.resources.formeditor_link_to_standard_choice
import org.openforis.collectone.v2.web.resources.formeditor_missing_question
import org.openforis.collectone.v2.web.resources.formeditor_new_data_table
import org.openforis.collectone.v2.web.resources.formeditor_new_map_layer
import org.openforis.collectone.v2.web.resources.formeditor_no_data_tables_available
import org.openforis.collectone.v2.web.resources.formeditor_no_map_layers_available
import org.openforis.collectone.v2.web.resources.formeditor_none
import org.openforis.collectone.v2.web.resources.formeditor_not_linked
import org.openforis.collectone.v2.web.resources.formeditor_open_form
import org.openforis.collectone.v2.web.resources.formeditor_parent_choice
import org.openforis.collectone.v2.web.resources.formeditor_parent_value
import org.openforis.collectone.v2.web.resources.formeditor_remove_choice
import org.openforis.collectone.v2.web.resources.formeditor_remove_image
import org.openforis.collectone.v2.web.resources.formeditor_replace
import org.openforis.collectone.v2.web.resources.formeditor_replace_image
import org.openforis.collectone.v2.web.resources.formeditor_sample_plots_cant_be_added
import org.openforis.collectone.v2.web.resources.formeditor_text_choice
import org.openforis.collectone.v2.web.resources.formeditor_uses_form_to_add_new
import org.openforis.collectone.v2.web.resources.formeditor_visual_colors
import org.openforis.collectone.v2.web.resources.formeditor_visual_images
import org.openforis.collectone.v2.web.resources.formeditor_visual_style
import org.openforis.collectone.v2.web.resources.formeditor_visual_text_only
import org.openforis.collectone.v2.web.ui.common.DropdownSelector
import org.openforis.collectone.v2.web.ui.common.SectionLabel
import org.openforis.collectone.v2.web.ui.common.TextFilePickResult
import org.openforis.collectone.v2.web.ui.common.openPlatformTextFilePicker

private enum class ChoiceVisualOption(val label: StringResource) {
  NONE(Res.string.formeditor_visual_text_only),
  COLOR(Res.string.formeditor_visual_colors),
  IMAGE(Res.string.formeditor_visual_images),
}

private fun choiceSourceIcon(source: ChoiceSource): ImageVector =
  when (source) {
    ChoiceSource.MANUAL -> Icons.AutoMirrored.Outlined.List
    ChoiceSource.MAP_LAYER -> Icons.Outlined.Layers
    ChoiceSource.DATA_TABLE -> Icons.Outlined.TableChart
  }

private fun choiceVisualIcon(visual: ChoiceVisualOption): ImageVector =
  when (visual) {
    ChoiceVisualOption.NONE -> Icons.Outlined.TextFields
    ChoiceVisualOption.COLOR -> Icons.Outlined.Palette
    ChoiceVisualOption.IMAGE -> Icons.Outlined.Image
  }

@Composable
internal fun ChoicesEditor(
  uiState: FormEditorUiState,
  actions: FormEditorActions,
  question: EditorQuestion,
  onOpenDataset: ((String) -> Unit)? = null,
  onCreateFormForDataset: ((String) -> Unit)? = null,
  onOpenForm: ((String) -> Unit)? = null,
  onCreateChoiceDataset: ((kind: DatasetKind, questionKey: String) -> Unit)? = null,
) {
  val key = question.key
  val colors = MaterialTheme.colorScheme
  val effectiveSource = uiState.choiceSourceFor(question)
  val questionIndex = uiState.form.questions.indexOfFirst { it.key == question.key }
  var imageError by remember { mutableStateOf<String?>(null) }
  var importDialogChoices by remember(key) { mutableStateOf<List<EditorChoice>?>(null) }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SectionLabel(stringResource(Res.string.formeditor_choices))
      if (!uiState.isReadOnly && effectiveSource == ChoiceSource.MANUAL) {
        TextButton(
          onClick = {
            openPlatformTextFilePicker(".csv,.tsv,.txt,text/csv,text/plain") { result ->
              if (result is TextFilePickResult.Picked) {
                val outcome = actions.importChoicesFromFile(key, result.text, result.fileName)
                if (outcome is ChoiceFileImport.NeedsConfirmation) {
                  importDialogChoices = outcome.choices
                }
              }
            }
          }
        ) {
          Icon(
            Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(stringResource(Res.string.formeditor_import_choices_file))
        }
      }
    }

    importDialogChoices?.let { imported ->
      AlertDialog(
        onDismissRequest = { importDialogChoices = null },
        title = { Text(stringResource(Res.string.formeditor_import_choices)) },
        text = {
          Text(
            pluralStringResource(
              Res.plurals.formeditor_import_choices_message,
              imported.size,
              imported.size,
              question.choices.size,
            )
          )
        },
        confirmButton = {
          Button(
            onClick = {
              actions.importQuestionChoices(key, imported, append = false)
              importDialogChoices = null
            }
          ) {
            Text(stringResource(Res.string.formeditor_replace))
          }
        },
        dismissButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importDialogChoices = null }) {
              Text(stringResource(Res.string.formeditor_cancel))
            }
            FilledTonalButton(
              onClick = {
                actions.importQuestionChoices(key, imported, append = true)
                importDialogChoices = null
              }
            ) {
              Text(stringResource(Res.string.formeditor_append))
            }
          }
        },
      )
    }

    // A template's choices are always listed: it has no Map layers or Data tables to load from.
    if (!uiState.isTemplate) {
      DropdownSelector(
        label = stringResource(Res.string.formeditor_choices),
        selectedText = stringResource(effectiveSource.labelRes),
        options = ChoiceSource.entries,
        optionText = { stringResource(it.labelRes) },
        optionIcon = ::choiceSourceIcon,
        selectedIcon = choiceSourceIcon(effectiveSource),
        enabled = !uiState.isReadOnly,
        onSelect = { source ->
          actions.setChoiceSource(key, source)
          // With no map layers or data tables of that kind yet, go straight to creating one (the
          // same "start empty or import" dialog as the survey editor).
          if (source.isDataset && uiState.choiceDatasetsFor(source).isEmpty()) {
            val kind =
              if (source == ChoiceSource.MAP_LAYER) DatasetKind.MAP_LAYER
              else DatasetKind.DATA_TABLE
            if (onCreateChoiceDataset != null) {
              onCreateChoiceDataset(kind, key)
            } else if (questionIndex >= 0) {
              actions.createDatasetForChoiceQuestion(questionIndex, kind)
            }
          }
        },
      )
    }
    when (effectiveSource) {
      ChoiceSource.MANUAL -> {
        val hasImages = question.choices.any { it.image != null }
        val hasColors = question.choices.any { it.colorHex != null }
        var visualOverride by remember(question.key) { mutableStateOf<ChoiceVisualOption?>(null) }
        val currentVisual =
          visualOverride
            ?: when {
              hasImages -> ChoiceVisualOption.IMAGE
              hasColors -> ChoiceVisualOption.COLOR
              else -> ChoiceVisualOption.NONE
            }

        // In the Color style every choice has a color, including ones added or imported later.
        if (!uiState.isReadOnly && currentVisual == ChoiceVisualOption.COLOR) {
          val missingColors = question.choices.count { it.colorHex == null }
          LaunchedEffect(key, missingColors) {
            if (missingColors > 0) {
              actions.updateQuestion(key) { q ->
                q.copy(
                  choices =
                    q.choices.mapIndexed { idx, c ->
                      if (c.colorHex != null) c
                      else
                        c.copy(
                          colorHex = ChoiceColors.palette[idx % ChoiceColors.palette.size].second,
                          image = null,
                        )
                    }
                )
              }
            }
          }
        }

        val visualLabels = ChoiceVisualOption.entries.associateWith { stringResource(it.label) }
        DropdownSelector(
          label = stringResource(Res.string.formeditor_visual_style),
          selectedText = visualLabels.getValue(currentVisual),
          options = ChoiceVisualOption.entries,
          optionText = { visualLabels.getValue(it) },
          optionIcon = ::choiceVisualIcon,
          selectedIcon = choiceVisualIcon(currentVisual),
          enabled = !uiState.isReadOnly,
          onSelect = { visual ->
            visualOverride = visual
            when (visual) {
              ChoiceVisualOption.NONE -> {
                actions.updateQuestion(key) { q ->
                  q.copy(choices = q.choices.map { it.copy(colorHex = null, image = null) })
                }
              }
              ChoiceVisualOption.COLOR -> {
                actions.updateQuestion(key) { q ->
                  q.copy(
                    choices =
                      q.choices.mapIndexed { idx, c ->
                        val color =
                          c.colorHex ?: ChoiceColors.palette[idx % ChoiceColors.palette.size].second
                        c.copy(colorHex = color, image = null)
                      }
                  )
                }
              }
              ChoiceVisualOption.IMAGE -> {
                actions.updateQuestion(key) { q ->
                  q.copy(choices = q.choices.map { it.copy(colorHex = null) })
                }
              }
            }
          },
        )

        val precedingChoiceQuestions = uiState.form.eligibleCascadeParents(key)

        var parentChoiceFilter by
          remember(key, question.parentQuestionKey) {
            mutableStateOf<String?>(null)
          }

        val selectedParent = precedingChoiceQuestions.firstOrNull {
          it.key == question.parentQuestionKey || it.name == question.parentQuestionKey
        }

        CascadingParentSelector(
          uiState = uiState,
          question = question,
          precedingChoiceQuestions = precedingChoiceQuestions,
          selectedParent = selectedParent,
          onSelect = { selected ->
            actions.updateQuestionParentKey(key, selected?.key ?: "")
            parentChoiceFilter = null
          },
        )

        if (selectedParent != null) {
          val parentChoices = selectedParent.resolvedChoices(uiState.datasets)
          if (parentChoices.isNotEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = stringResource(Res.string.formeditor_filter_label),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
              )
              FlatFilterChip(
                selected = parentChoiceFilter == null,
                onClick = { parentChoiceFilter = null },
                label = { Text(stringResource(Res.string.formeditor_filter_all)) },
              )
              parentChoices.forEach { pChoice ->
                FlatFilterChip(
                  selected = parentChoiceFilter == pChoice.value,
                  onClick = {
                    parentChoiceFilter =
                      if (parentChoiceFilter == pChoice.value) null else pChoice.value
                  },
                  label = { Text(pChoice.label.ifBlank { pChoice.value }) },
                )
              }
            }
          }
        }

        // A linked question maps each choice to a value of the concept's code list.
        val concept = uiState.conceptOf(question)?.takeIf { it.codeList.isNotEmpty() }
        val activeLang = uiState.activeLanguage
        val defaultLang = uiState.defaultLanguage
        val isDefaultLang = uiState.isDefaultLanguageSelected
        val activeLangName = languageDisplayName(activeLang)
        val imageTooLargeMessage =
          stringResource(
            Res.string.formeditor_choice_image_too_large,
            EditorChoiceImage.MAX_BYTES / 1024,
          )

        val indexedChoices = question.choices.mapIndexed { idx, c -> idx to c }
        val visibleChoices =
          if (selectedParent != null && parentChoiceFilter != null) {
            indexedChoices.filter { (_, c) -> c.parentValue == parentChoiceFilter }
          } else {
            indexedChoices
          }

        visibleChoices.forEach { (i, choice) ->
          val choiceColorStyle =
            if (currentVisual == ChoiceVisualOption.COLOR) {
              ChoiceImageRegistry.parseChoiceColor(choice.colorHex)?.let { choiceColor ->
                ChoiceImageRegistry.choiceOptionColorStyle(
                  choiceColor = choiceColor,
                  isSelected = false,
                  surfaceColor = colors.surface,
                  onSurfaceColor = colors.onSurface,
                )
              }
            } else {
              null
            }
          val rowContent: @Composable () -> Unit = {
            Row(
              modifier =
                if (choiceColorStyle != null) {
                  Modifier.fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 8.dp)
                } else {
                  Modifier.fillMaxWidth()
                },
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(modifier = Modifier.padding(top = 12.dp)) {
                when (currentVisual) {
                  ChoiceVisualOption.NONE -> ChoiceTextStyleIndicator()
                  ChoiceVisualOption.COLOR ->
                    ChoiceColorButton(
                      colorHex = choice.colorHex,
                      onSelect = { actions.setChoiceColor(key, i, it) },
                      enabled = !uiState.isReadOnly,
                    )
                  ChoiceVisualOption.IMAGE ->
                    ChoiceImageButton(
                      image = choice.image,
                      onPicked = { image ->
                        imageError =
                          if (actions.setChoiceImage(key, i, image)) null else imageTooLargeMessage
                      },
                      onRemove = { actions.setChoiceImage(key, i, null) },
                      onError = { imageError = it },
                      enabled = !uiState.isReadOnly,
                    )
                }
              }
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                OutlinedTextField(
                  value = choice.labelIn(activeLang, defaultLang),
                  onValueChange = { actions.updateChoiceLabel(key, i, it) },
                  enabled = !uiState.isReadOnly,
                  label = {
                    Text(
                      if (isDefaultLang) stringResource(Res.string.formeditor_label)
                      else stringResource(Res.string.formeditor_label_in_language, activeLangName)
                    )
                  },
                  placeholder =
                    if (!isDefaultLang && choice.label.isNotBlank()) ({ Text(choice.label) })
                    else null,
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                )
                if (selectedParent != null) {
                  val parentChoices = selectedParent.resolvedChoices(uiState.datasets)
                  if (parentChoices.isNotEmpty()) {
                    val anyUnfilteredText = stringResource(Res.string.formeditor_any_unfiltered)
                    DropdownSelector(
                      label =
                        stringResource(
                          Res.string.formeditor_parent_choice,
                          selectedParent.label.ifBlank { selectedParent.name },
                        ),
                      selectedText =
                        parentChoices.firstOrNull { it.value == choice.parentValue }?.label
                          ?: choice.parentValue.ifBlank { anyUnfilteredText },
                      options = listOf(null) + parentChoices,
                      optionText = { it?.label ?: anyUnfilteredText },
                      enabled = !uiState.isReadOnly,
                      onSelect = { selected ->
                        actions.updateChoiceParentValue(key, i, selected?.value ?: "")
                      },
                    )
                  } else {
                    OutlinedTextField(
                      value = choice.parentValue,
                      onValueChange = { actions.updateChoiceParentValue(key, i, it) },
                      enabled = !uiState.isReadOnly,
                      label = {
                        Text(
                          stringResource(
                            Res.string.formeditor_parent_value,
                            selectedParent.label.ifBlank { selectedParent.name },
                          )
                        )
                      },
                      singleLine = true,
                      modifier = Modifier.fillMaxWidth(),
                    )
                  }
                }
                if (concept != null) {
                  ChoiceConceptLinkChip(
                    concept = concept,
                    choice = choice,
                    language = uiState.activeLanguage,
                    onSelect = { actions.setChoiceCode(key, i, it) },
                    enabled = !uiState.isReadOnly,
                  )
                }
              }
              if (!uiState.isReadOnly) {
                IconButton(
                  onClick = { actions.removeChoice(key, i) },
                  modifier = Modifier.padding(top = 12.dp).size(32.dp),
                ) {
                  Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.formeditor_remove_choice),
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }
          if (choiceColorStyle != null) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = choiceColorStyle.containerColor,
              border = BorderStroke(1.5.dp, choiceColorStyle.borderColor),
              modifier = Modifier.fillMaxWidth().testTag("editor-choice-color-row-${choice.value}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier.width(5.dp)
                      .fillMaxHeight()
                      .background(choiceColorStyle.accentStripeColor)
                )
                Box(modifier = Modifier.weight(1f)) { rowContent() }
              }
            }
          } else {
            rowContent()
          }
        }
        imageError?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
          )
        }
        if (!uiState.isReadOnly) {
          TextButton(
            onClick = {
              actions.addChoice(key)
              val assignedParentVal = parentChoiceFilter
              if (assignedParentVal != null) {
                actions.updateChoiceParentValue(key, question.choices.size, assignedParentVal)
              }
              if (currentVisual == ChoiceVisualOption.COLOR) {
                actions.updateQuestion(key) { q ->
                  val lastIdx = q.choices.lastIndex
                  if (lastIdx >= 0) {
                    val color = ChoiceColors.palette[lastIdx % ChoiceColors.palette.size].second
                    q.copy(
                      choices =
                        q.choices.mapIndexed { idx, c ->
                          if (idx == lastIdx && c.colorHex == null)
                            c.copy(colorHex = color, image = null)
                          else c
                        }
                    )
                  } else {
                    q
                  }
                }
              }
            }
          ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.formeditor_add_choice))
          }
        }
      }
      ChoiceSource.MAP_LAYER,
      ChoiceSource.DATA_TABLE -> {
        val availableDatasets = uiState.choiceDatasetsFor(effectiveSource)
        val selectedDataset =
          uiState.choiceDatasetFor(question)?.takeIf {
            it.isMapLayer == (effectiveSource == ChoiceSource.MAP_LAYER)
          }
        val isMapLayerSource = effectiveSource == ChoiceSource.MAP_LAYER
        val noDatasetsText =
          if (isMapLayerSource) stringResource(Res.string.formeditor_no_map_layers_available)
          else stringResource(Res.string.formeditor_no_data_tables_available)
        val newDatasetText =
          if (isMapLayerSource) stringResource(Res.string.formeditor_new_map_layer)
          else stringResource(Res.string.formeditor_new_data_table)
        val targetKind =
          if (effectiveSource == ChoiceSource.MAP_LAYER) DatasetKind.MAP_LAYER
          else DatasetKind.DATA_TABLE

        if (availableDatasets.isEmpty() && question.choiceDatasetId.isNullOrBlank()) {
          Text(
            text = noDatasetsText,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
          )
          if (!uiState.isReadOnly) {
            OutlinedButton(
              onClick = {
                if (onCreateChoiceDataset != null) {
                  onCreateChoiceDataset(targetKind, key)
                } else if (questionIndex >= 0) {
                  actions.createDatasetForChoiceQuestion(questionIndex, targetKind)
                }
              }
            ) {
              Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(newDatasetText)
            }
          }
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (availableDatasets.isNotEmpty()) {
              Box(modifier = Modifier.weight(1f)) {
                DropdownSelector(
                  label = stringResource(effectiveSource.labelRes),
                  selectedText =
                    selectedDataset?.displayName
                      ?: question.choiceDatasetId?.takeIf { it.isNotBlank() }
                      ?: stringResource(Res.string.formeditor_choose),
                  options = availableDatasets,
                  optionText = { it.displayName },
                  optionIcon = ::datasetIcon,
                  enabled = !uiState.isReadOnly,
                  onSelect = { actions.setChoiceDataset(key, it.id) },
                )
              }
            }
            if (!uiState.isReadOnly) {
              OutlinedButton(
                onClick = {
                  if (onCreateChoiceDataset != null) {
                    onCreateChoiceDataset(targetKind, key)
                  } else if (questionIndex >= 0) {
                    actions.createDatasetForChoiceQuestion(questionIndex, targetKind)
                  }
                }
              ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(newDatasetText)
              }
            }
          }
          if (selectedDataset != null) {
            val count = selectedDataset.rows.size
            val noun =
              if (count == 1) selectedDataset.featureNounText()
              else selectedDataset.featureNounPluralText()
            Text(
              text =
                pluralStringResource(
                  Res.plurals.formeditor_collectors_choose_from,
                  count,
                  count,
                  noun,
                  selectedDataset.displayName,
                ),
              style = MaterialTheme.typography.bodySmall,
              color = colors.onSurfaceVariant,
            )
            if (onOpenDataset != null) {
              AssistChip(
                onClick = { onOpenDataset(selectedDataset.id) },
                label = { Text(selectedDataset.displayName) },
                leadingIcon = {
                  Icon(
                    datasetIcon(selectedDataset),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                },
              )
            }
            AllowAddEntityControl(
              question = question,
              dataset = selectedDataset,
              actions = actions,
              onCreateFormForDataset = onCreateFormForDataset.takeIf { !uiState.isReadOnly },
              onOpenForm = onOpenForm,
              enabled = !uiState.isReadOnly,
            )
          } else if (!question.choiceDatasetId.isNullOrBlank()) {
            Text(
              text =
                stringResource(
                  Res.string.formeditor_choices_from_external_list,
                  question.choiceDatasetId.orEmpty(),
                ),
              style = MaterialTheme.typography.bodySmall,
              color = colors.onSurfaceVariant,
            )
          }
        }
        val precedingChoiceQuestions = uiState.form.eligibleCascadeParents(key)
        val selectedParent = precedingChoiceQuestions.firstOrNull {
          it.key == question.parentQuestionKey || it.name == question.parentQuestionKey
        }
        CascadingParentSelector(
          uiState = uiState,
          question = question,
          precedingChoiceQuestions = precedingChoiceQuestions,
          selectedParent = selectedParent,
          onSelect = { selected -> actions.updateQuestionParentKey(key, selected?.key ?: "") },
        )
      }
    }
  }
}

/**
 * Dropdown linking [question]'s choice list to an earlier choice question's answer (cascading
 * select). Shown whenever earlier choice questions exist, or when [question] still carries a
 * `parentQuestionKey` whose target was deleted, moved later, or changed away from a choice type so
 * the designer can clear or re-point it.
 */
@Composable
private fun CascadingParentSelector(
  uiState: FormEditorUiState,
  question: EditorQuestion,
  precedingChoiceQuestions: List<EditorQuestion>,
  selectedParent: EditorQuestion?,
  onSelect: (EditorQuestion?) -> Unit,
) {
  if (precedingChoiceQuestions.isEmpty() && question.parentQuestionKey.isBlank()) return
  val noneText = stringResource(Res.string.formeditor_none)
  val referencedParent = uiState.form.cascadeParentRefOf(question)
  val selectedText =
    when {
      selectedParent != null -> selectedParent.label.ifBlank { selectedParent.name }
      referencedParent != null -> referencedParent.label.ifBlank { referencedParent.name }
      question.parentQuestionKey.isNotBlank() ->
        stringResource(Res.string.formeditor_missing_question)
      else -> noneText
    }
  DropdownSelector(
    label = stringResource(Res.string.formeditor_cascading_filter),
    selectedText = selectedText,
    options = listOf(null) + precedingChoiceQuestions,
    optionText = { it?.label?.ifBlank { it.name } ?: noneText },
    enabled = !uiState.isReadOnly,
    onSelect = onSelect,
  )
}

/**
 * Lets the form designer allow collectors to add a new map feature or table row inline while
 * selecting from [dataset]. Only datasets with a creation Form (or for which one can be generated)
 * support adding items during collection; generated sample plots cannot be added by collectors.
 */
@Composable
private fun AllowAddEntityControl(
  question: EditorQuestion,
  dataset: EditorDataset,
  actions: ChoiceActions,
  onCreateFormForDataset: ((String) -> Unit)?,
  onOpenForm: ((String) -> Unit)?,
  enabled: Boolean = true,
) {
  val colors = MaterialTheme.colorScheme
  val key = question.key
  when {
    dataset.isGenerated -> {
      Text(
        text = stringResource(Res.string.formeditor_sample_plots_cant_be_added),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
      )
    }
    dataset.hasCreationForm || (dataset.key.isEmpty() && dataset.linkedFormKey == null) -> {
      val formLabel = dataset.linkedFormTitle?.takeIf { it.isNotBlank() }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text =
              stringResource(Res.string.formeditor_allow_add_new, dataset.featureNounPluralText()),
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            text =
              if (formLabel != null) {
                stringResource(
                  Res.string.formeditor_uses_form_to_add_new,
                  formLabel,
                  dataset.featureNounPluralText(),
                )
              } else {
                stringResource(
                  Res.string.formeditor_collectors_can_add_new,
                  dataset.featureNounText(),
                )
              },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
          )
        }
        Switch(
          checked = question.allowAddEntity,
          enabled = enabled,
          onCheckedChange = { actions.setAllowAddEntity(key, it) },
        )
      }
      val linkedFormKey = dataset.linkedFormKey
      if (onOpenForm != null && linkedFormKey != null) {
        AssistChip(
          onClick = { onOpenForm(linkedFormKey) },
          label = { Text(formLabel ?: stringResource(Res.string.formeditor_open_form)) },
          leadingIcon = {
            Icon(
              Icons.Outlined.Description,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          },
        )
      }
    }
    dataset.canCreateForm -> {
      Surface(
        color = colors.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text =
              stringResource(Res.string.formeditor_allow_add_new, dataset.featureNounPluralText()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
          Text(
            text =
              stringResource(
                Res.string.formeditor_dataset_has_no_form,
                dataset.displayName,
                dataset.featureNounPluralText(),
              ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
          )
          if (onCreateFormForDataset != null && dataset.key.isNotEmpty()) {
            OutlinedButton(
              onClick = {
                onCreateFormForDataset(dataset.key)
                actions.setAllowAddEntity(key, true)
              }
            ) {
              Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(Res.string.formeditor_create_form_for, dataset.displayName))
            }
          }
        }
      }
    }
  }
}

/**
 * Non-interactive marker shown beside each choice when the question's visual style is text only.
 * Colors are picked only in the "Color" visual style.
 */
@Composable
private fun ChoiceTextStyleIndicator() {
  val colors = MaterialTheme.colorScheme
  val description = stringResource(Res.string.formeditor_text_choice)
  Surface(
    shape = MaterialTheme.shapes.extraSmall,
    color = colors.surfaceContainerHigh,
    border = BorderStroke(1.dp, colors.outlineVariant),
    modifier = Modifier.size(32.dp).semantics { contentDescription = description },
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        Icons.Outlined.TextFields,
        contentDescription = null,
        tint = colors.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

/**
 * Compact linked-concept chip rendered directly underneath each choice's label field when the
 * parent select question is linked to a standard dictionary concept with a code list.
 */
@Composable
private fun ChoiceConceptLinkChip(
  concept: LibraryConcept,
  choice: EditorChoice,
  language: String,
  onSelect: (String?) -> Unit,
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  val matched = concept.codeList.firstOrNull { it.code == choice.code }
  val isLinked = choice.code != null
  val chipText =
    when {
      matched != null -> "${matched.label.get(language)} (${matched.code})"
      choice.code != null -> stringResource(Res.string.formeditor_code_value, choice.code.orEmpty())
      else -> stringResource(Res.string.formeditor_link_to_standard_choice)
    }

  Box(modifier = modifier) {
    Surface(
      onClick = { expanded = true },
      enabled = enabled,
      shape = RoundedCornerShape(8.dp),
      color =
        if (isLinked) colors.secondaryContainer.copy(alpha = 0.65f) else colors.surfaceContainerLow,
      contentColor = if (isLinked) colors.onSecondaryContainer else colors.onSurfaceVariant,
      border =
        BorderStroke(
          1.dp,
          if (isLinked) colors.secondary.copy(alpha = 0.4f) else colors.outlineVariant,
        ),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Outlined.Link,
          contentDescription = null,
          tint = if (isLinked) colors.primary else colors.onSurfaceVariant,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = chipText,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = if (isLinked) FontWeight.Medium else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (enabled) {
          Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
          )
        }
      }
    }
    DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.formeditor_not_linked)) },
        onClick = {
          onSelect(null)
          expanded = false
        },
      )
      concept.codeList.forEach { item ->
        val itemLabel = "${item.label.get(language)} (${item.code})"
        val isSelected = item.code == choice.code
        DropdownMenuItem(
          text = { Text(itemLabel) },
          leadingIcon = {
            if (isSelected) {
              Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
              )
            } else {
              Icon(
                Icons.Outlined.Link,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
            }
          },
          onClick = {
            onSelect(item.code)
            expanded = false
          },
        )
      }
    }
  }
}

/** Color dot for a choice; opens a compact palette. Every choice has a color in this style. */
@Composable
private fun ChoiceColorButton(
  colorHex: String?,
  onSelect: (String) -> Unit,
  enabled: Boolean = true,
) {
  var expanded by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  Box {
    IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.size(32.dp)) {
      val colorName = ChoiceColors.nameOf(colorHex) ?: colorHex
      ColorDot(
        colorHex = colorHex,
        contentDescription =
          if (colorName != null) stringResource(Res.string.formeditor_choice_color_named, colorName)
          else stringResource(Res.string.formeditor_choice_color_none),
        size = 24.dp,
      )
    }
    DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
      Text(
        text = stringResource(Res.string.formeditor_choice_color),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
      )
      ChoiceColors.palette.chunked(5).forEach { row ->
        Row(
          modifier = Modifier.padding(horizontal = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          row.forEach { (name, hex) ->
            IconButton(
              onClick = {
                onSelect(hex)
                expanded = false
              },
              modifier = Modifier.size(36.dp),
            ) {
              ColorDot(
                colorHex = hex,
                contentDescription = name,
                isSelected = hex.equals(colorHex, ignoreCase = true),
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Attaches a small image to a choice using the platform picker (downscaled to
 * [EditorChoiceImage.MAX_PIXELS] on the long edge). Shows a thumbnail with Replace / Remove once
 * set.
 */
@Composable
private fun ChoiceImageButton(
  image: EditorChoiceImage?,
  onPicked: (EditorChoiceImage) -> Unit,
  onRemove: () -> Unit,
  onError: (String) -> Unit,
  enabled: Boolean = true,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  val pick = {
    openPlatformMediaPicker(
      accept = "image/*",
      capture = "",
      maxPixels = EditorChoiceImage.MAX_PIXELS,
    ) { result ->
      when (result) {
        is PlatformPickResult.Picked -> {
          val file = result.file
          val mimeType =
            file.mimeType.ifBlank { MediaCapture.mimeTypeForFileName(file.fileName).orEmpty() }
          onPicked(EditorChoiceImage(mimeType, file.base64))
        }
        is PlatformPickResult.Failed -> onError(result.message)
        PlatformPickResult.Cancelled -> Unit
      }
    }
  }
  if (image == null) {
    IconButton(
      onClick = pick,
      enabled = enabled && isPlatformMediaPickerAvailable,
      modifier = Modifier.size(32.dp),
    ) {
      Icon(
        Icons.Outlined.AddPhotoAlternate,
        contentDescription = stringResource(Res.string.formeditor_add_choice_image),
        modifier = Modifier.size(20.dp),
      )
    }
    return
  }
  Box {
    val bitmap = rememberChoiceImageBitmap(image)
    Surface(
      onClick = { menuExpanded = true },
      enabled = enabled,
      shape = MaterialTheme.shapes.extraSmall,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.size(32.dp),
    ) {
      if (bitmap != null) {
        Image(
          bitmap = bitmap,
          contentDescription = stringResource(Res.string.formeditor_choice_image),
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Outlined.Photo,
            contentDescription = stringResource(Res.string.formeditor_choice_image),
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
    DropdownMenu(expanded = menuExpanded && enabled, onDismissRequest = { menuExpanded = false }) {
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.formeditor_replace_image)) },
        leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null) },
        enabled = isPlatformMediaPickerAvailable,
        onClick = {
          menuExpanded = false
          pick()
        },
      )
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.formeditor_remove_image)) },
        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        onClick = {
          menuExpanded = false
          onRemove()
        },
      )
    }
  }
}

/** Decodes [image] for display, or `null` if the bytes aren't a decodable bitmap. */
@OptIn(ExperimentalResourceApi::class, ExperimentalEncodingApi::class)
@Composable
internal fun rememberChoiceImageBitmap(image: EditorChoiceImage): ImageBitmap? =
  remember(image) { runCatching { Base64.decode(image.base64).decodeToImageBitmap() }.getOrNull() }
