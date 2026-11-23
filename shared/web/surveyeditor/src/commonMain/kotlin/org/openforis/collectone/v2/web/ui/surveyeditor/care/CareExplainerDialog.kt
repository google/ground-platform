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
package org.openforis.collectone.v2.web.ui.surveyeditor.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_auto_body
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_auto_heading
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_care_a
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_care_body
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_care_c
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_care_e
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_care_heading
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_care_r
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_close
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_defaults_consent
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_defaults_editable
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_defaults_heading
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_defaults_own_only
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_defaults_restricted
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_fpic_body
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_fpic_heading
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_intro
import org.openforis.collectone.v2.web.resources.wizard_care_explainer_title

/**
 * Plain-language explanation of the CARE Principles, FAO Free, Prior and Informed Consent (FPIC),
 * what the wizard's CARE defaults change in a survey, and when the wizard applies them by itself.
 */
@Composable
fun CareExplainerDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
    title = { Text(stringResource(Res.string.wizard_care_explainer_title)) },
    text = {
      Column(
        modifier =
          Modifier.widthIn(max = 560.dp)
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          stringResource(Res.string.wizard_care_explainer_intro),
          style = MaterialTheme.typography.bodyMedium,
        )
        ExplainerSection(
          heading = Res.string.wizard_care_explainer_care_heading,
          body = Res.string.wizard_care_explainer_care_body,
          bullets =
            listOf(
              "C" to Res.string.wizard_care_explainer_care_c,
              "A" to Res.string.wizard_care_explainer_care_a,
              "R" to Res.string.wizard_care_explainer_care_r,
              "E" to Res.string.wizard_care_explainer_care_e,
            ),
        )
        ExplainerSection(
          heading = Res.string.wizard_care_explainer_fpic_heading,
          body = Res.string.wizard_care_explainer_fpic_body,
        )
        ExplainerSection(
          heading = Res.string.wizard_care_explainer_defaults_heading,
          bullets =
            listOf(
              BULLET to Res.string.wizard_care_explainer_defaults_restricted,
              BULLET to Res.string.wizard_care_explainer_defaults_own_only,
              BULLET to Res.string.wizard_care_explainer_defaults_consent,
            ),
          footer = Res.string.wizard_care_explainer_defaults_editable,
        )
        ExplainerSection(
          heading = Res.string.wizard_care_explainer_auto_heading,
          body = Res.string.wizard_care_explainer_auto_body,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(Res.string.wizard_care_explainer_close))
      }
    },
  )
}

/**
 * A titled section of [CareExplainerDialog]: optional [body], [bullets] (marker to text), and
 * [footer].
 */
@Composable
private fun ExplainerSection(
  heading: StringResource,
  body: StringResource? = null,
  bullets: List<Pair<String, StringResource>> = emptyList(),
  footer: StringResource? = null,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      stringResource(heading),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
    )
    body?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
    bullets.forEach { (marker, text) ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          marker,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.widthIn(min = 14.dp),
        )
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
      }
    }
    footer?.let {
      Text(
        stringResource(it),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private const val BULLET = "•"
