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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewOptions
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewWorkflow
import org.openforis.collectone.v2.app.domain.model.editor.SurveyAreaGeometry
import org.openforis.collectone.v2.core.forms.ui.controls.Button
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedButton
import org.openforis.collectone.v2.core.forms.ui.controls.OutlinedCard
import org.openforis.collectone.v2.core.forms.ui.controls.Switch
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.core.forms.ui.controls.toggleable
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_cancel
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_confidence
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_confidence_description
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_description
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_desk
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_desk_form
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_desk_off
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_desk_on
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_field
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_field_off
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_field_on
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_open_form
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_remove
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_remove_body
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_remove_field
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_remove_field_body
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_remove_field_title
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_remove_title
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_show_desk
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_show_desk_off
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_show_desk_on
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_spot_checks
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_spot_checks_description
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_spot_checks_share
import org.openforis.collectone.v2.web.resources.surveyeditor_plot_review_spot_checks_unshuffled
import org.openforis.collectone.v2.web.ui.formeditor.previewTargetIcon

/** Review Forms a confirmation dialog of [PlotReviewPanel] is about to remove. */
private enum class PlotReviewRemoval {
  /** The desk and field Forms. */
  ALL,
  /** Only the field Form (and the desk Form's flagging questions). */
  FIELD,
}

/**
 * Plot review section of a generated Map layer's settings, under its sample design: whether
 * interpreters check each plot on imagery in the web dashboard, whether field crews then verify the
 * plots that need it in the mobile app, and which plots go to the field. Changes add, update, or
 * remove the review Forms right away (see [PlotReviewWorkflow]); turning a review off asks first.
 * While the survey is closed or archived, the review shows as it is but can't be changed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlotReviewPanel(
  uiState: SurveyEditorUiState,
  actions: SurveyEditorActions,
  dataset: EntityDataset,
) {
  val key = dataset.key
  // Cheap (one pass over the Forms), so it's not worth comparing the draft to cache it.
  val setup = PlotReviewWorkflow.setup(uiState.draft, key)
  val options = setup.options
  val enabled = !uiState.isReadOnly
  var removal by remember(key) { mutableStateOf<PlotReviewRemoval?>(null) }

  removal?.let { pending ->
    ConfirmRemovalDialog(
      removal = pending,
      onConfirm = {
        removal = null
        when (pending) {
          PlotReviewRemoval.ALL -> actions.removePlotReview(key)
          PlotReviewRemoval.FIELD ->
            actions.updatePlotReview(key, options.copy(fieldValidation = false))
        }
      },
      onDismiss = { removal = null },
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(Res.string.surveyeditor_plot_review_description),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
      ToggleRow(
        icon = previewTargetIcon(FormPreviewTarget.WEB),
        title = stringResource(Res.string.surveyeditor_plot_review_desk),
        caption =
          stringResource(
            if (setup.isEnabled) Res.string.surveyeditor_plot_review_desk_on
            else Res.string.surveyeditor_plot_review_desk_off
          ),
        checked = setup.isEnabled,
        onCheckedChange = { on ->
          if (on) actions.updatePlotReview(key, options) else removal = PlotReviewRemoval.ALL
        },
        enabled = enabled,
      )
      if (setup.isEnabled) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ToggleRow(
          icon = previewTargetIcon(FormPreviewTarget.MOBILE),
          title = stringResource(Res.string.surveyeditor_plot_review_field),
          caption =
            stringResource(
              if (options.fieldValidation) Res.string.surveyeditor_plot_review_field_on
              else Res.string.surveyeditor_plot_review_field_off
            ),
          checked = options.fieldValidation,
          onCheckedChange = { on ->
            if (on) actions.updatePlotReview(key, options.copy(fieldValidation = true))
            else removal = PlotReviewRemoval.FIELD
          },
          enabled = enabled,
        )
      }
    }
    if (setup.isEnabled && options.fieldValidation) {
      FieldValidationOptions(
        dataset,
        options,
        onChange = { actions.updatePlotReview(key, it) },
        enabled = enabled,
      )
    }
    if (setup.isEnabled) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOfNotNull(setup.deskFormKey, setup.fieldFormKey).forEach { formKey ->
          val title = uiState.form(formKey)?.form?.title.orEmpty()
          OutlinedButton(onClick = { actions.select(SurveyEditorSection.Form(formKey)) }) {
            Icon(
              Icons.AutoMirrored.Outlined.OpenInNew,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.surveyeditor_plot_review_open_form, title))
          }
        }
      }
    }
  }
}

/**
 * Field validation of a generated layer's plots in the create-survey wizard. The wizard's
 * interpretation Form ([deskFormTitle]) is the desk check, so this only offers field validation and
 * its options; turning it off needs no confirmation since nothing is lost.
 */
@Composable
fun PlotFieldValidationPanel(
  dataset: EntityDataset,
  options: PlotReviewOptions,
  deskFormTitle: String,
  onFieldValidationChange: (Boolean) -> Unit,
  onOptionsChange: (PlotReviewOptions) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(Res.string.surveyeditor_plot_review_description),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
      ToggleRow(
        icon = previewTargetIcon(FormPreviewTarget.MOBILE),
        title = stringResource(Res.string.surveyeditor_plot_review_field),
        caption =
          stringResource(
            if (options.fieldValidation) Res.string.surveyeditor_plot_review_field_on
            else Res.string.surveyeditor_plot_review_field_off
          ),
        checked = options.fieldValidation,
        onCheckedChange = onFieldValidationChange,
      )
    }
    if (options.fieldValidation) {
      InfoLine(stringResource(Res.string.surveyeditor_plot_review_desk_form, deskFormTitle))
      FieldValidationOptions(dataset, options, onOptionsChange)
    }
  }
}

/** Which plots go to the field, and what field crews see on them. */
@Composable
private fun FieldValidationOptions(
  dataset: EntityDataset,
  options: PlotReviewOptions,
  onChange: (PlotReviewOptions) -> Unit,
  enabled: Boolean = true,
) {
  IntegerField(
    label = stringResource(Res.string.surveyeditor_plot_review_confidence),
    value = options.autoFlagBelowConfidence.toLong(),
    min = 0,
    max = 100,
    enabled = enabled,
    supportingText = stringResource(Res.string.surveyeditor_plot_review_confidence_description),
    onValue = { v -> onChange(options.copy(autoFlagBelowConfidence = v.toInt())) },
  )
  val plotCount = dataset.rows.size
  IntegerField(
    label = stringResource(Res.string.surveyeditor_plot_review_spot_checks),
    value = options.spotCheckCount.toLong(),
    min = 0,
    max = Int.MAX_VALUE.toLong(),
    enabled = enabled,
    supportingText =
      if (plotCount > 0 && options.spotCheckCount > 0) {
        val share = (options.spotCheckCount.coerceAtMost(plotCount) * 100.0 / plotCount)
        pluralStringResource(
          Res.plurals.surveyeditor_plot_review_spot_checks_share,
          plotCount,
          "${share.roundToInt().coerceAtLeast(1)}%",
          SurveyAreaGeometry.groupThousands(plotCount.toLong()),
        )
      } else {
        stringResource(Res.string.surveyeditor_plot_review_spot_checks_description)
      },
    onValue = { v -> onChange(options.copy(spotCheckCount = v.toInt())) },
  )
  if (options.spotCheckCount > 0 && dataset.generator?.shuffle == false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        Icons.Outlined.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(6.dp))
      Text(
        stringResource(Res.string.surveyeditor_plot_review_spot_checks_unshuffled),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
      )
    }
  }
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    ToggleRow(
      icon = null,
      title = stringResource(Res.string.surveyeditor_plot_review_show_desk),
      caption =
        stringResource(
          if (options.showDeskAnswersInField) Res.string.surveyeditor_plot_review_show_desk_on
          else Res.string.surveyeditor_plot_review_show_desk_off
        ),
      checked = options.showDeskAnswersInField,
      onCheckedChange = { on -> onChange(options.copy(showDeskAnswersInField = on)) },
      enabled = enabled,
    )
  }
}

/**
 * A row with an optional [icon], [title], a [caption] spelling out the effect, and a switch that
 * can't be changed unless [enabled].
 */
@Composable
private fun ToggleRow(
  icon: ImageVector?,
  title: String,
  caption: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  enabled: Boolean = true,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .toggleable(
          value = checked,
          enabled = enabled,
          role = Role.Switch,
          onValueChange = onCheckedChange,
        )
        .padding(horizontal = 14.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (checked) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = caption,
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
      )
    }
    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
  }
}

/** Asks before [removal] removes review Forms, which loses any edits made to them. */
@Composable
private fun ConfirmRemovalDialog(
  removal: PlotReviewRemoval,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val isAll = removal == PlotReviewRemoval.ALL
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        stringResource(
          if (isAll) Res.string.surveyeditor_plot_review_remove_title
          else Res.string.surveyeditor_plot_review_remove_field_title
        )
      )
    },
    text = {
      Text(
        stringResource(
          if (isAll) Res.string.surveyeditor_plot_review_remove_body
          else Res.string.surveyeditor_plot_review_remove_field_body
        )
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text(
          stringResource(
            if (isAll) Res.string.surveyeditor_plot_review_remove
            else Res.string.surveyeditor_plot_review_remove_field
          )
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.surveyeditor_cancel)) }
    },
  )
}
