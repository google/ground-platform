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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import groundplatform.v2.forms.FormDef
import org.groundplatform.v2.core.forms.serialization.ProtoJsonSerializer
import org.groundplatform.v2.core.forms.serialization.TextProtoSerializer
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.GroundBadgeTone
import org.groundplatform.v2.core.forms.ui.GroundTonalBadge
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.ui.state.WorkbenchUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.WorkbenchActions

private val defaultResolveFormDefUseCase =
  org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveFormDefForLaunchUseCase()

/**
 * Built-in fallback XForms `<h:html>` XML definitions keyed by [FormPreviewItem.id], used when
 * [WorkbenchUiState.customXFormsXml] is cleared/blank.
 */
fun builtInFallbackXFormsXmlForForm(form: FormPreviewItem): String =
  defaultResolveFormDefUseCase.builtInFallbackXFormsXmlForForm(form)

/** Parses the initial default XForms XML into a [FormDef] via [XFormsXmlSerializer]. */
fun parseDefaultPrototypeFormDef(): FormDef =
  XFormsXmlSerializer.deserializeFormDef(DEFAULT_PROTOTYPE_XFORMS_XML)

/**
 * Prominent section inside `UxDesignerInspectorPanel` (the Prototype App page Chrome) allowing an
 * XForms `FormDef` (`<h:html>...</h:html>`) to be pasted or edited for live testing in the
 * prototype app's data collection flow.
 */
@Composable
fun PrototypeXFormsWorkbenchPanel(state: PrototypeAppState) {
  val uiState by state.workbench.uiState.collectAsState()
  val isFormOpen = state.dataCollectionUiState.isDataCollectionFormOpen
  PrototypeXFormsWorkbenchPanel(
    uiState = uiState,
    actions = state.workbench,
    isFormOpen = isFormOpen,
    onLaunchOrCloseForm = {
      if (isFormOpen) {
        state.dataCollection.closeActiveFormRunner()
      } else {
        state.dataCollection.launchActiveOrDefaultFormForTesting()
      }
    },
  )
}

/** Stateless XForms workbench panel driven by [WorkbenchUiState] and [WorkbenchActions]. */
@Composable
fun PrototypeXFormsWorkbenchPanel(
  uiState: WorkbenchUiState,
  actions: WorkbenchActions,
  isFormOpen: Boolean,
  onLaunchOrCloseForm: () -> Unit,
) {
  val parsedFormDef = uiState.customFormDef
  val xmlError = uiState.xformsXmlError
  val fieldCount = parsedFormDef?.model?.bindings?.size ?: 0
  var showRawXmlEditor by remember { mutableStateOf(false) }
  var showProtoPreview by remember { mutableStateOf(false) }
  var previewAsJson by remember { mutableStateOf(false) }

  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Section Header + Live Status Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "EXAMPLE SURVEYS & XFORMS WORKBENCH",
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.6.sp,
              ),
          )
          Text(
            text =
              "Select an example survey below to open it with its preloaded entities, submissions, and form.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val isError = xmlError != null
        GroundTonalBadge(
          text =
            when {
              isError -> "XML Parse Error"
              parsedFormDef != null -> "Valid FormDef • $fieldCount fields"
              else -> "Using Built-In FormDef"
            },
          tone =
            when {
              isError -> GroundBadgeTone.ERROR
              parsedFormDef != null -> GroundBadgeTone.PRIMARY
              else -> GroundBadgeTone.NEUTRAL
            },
        )
      }

      // Swappable Example Surveys Selector (5 Workbench Example Surveys with preloaded entities &
      // submissions)
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "EXAMPLE SURVEYS (CLICK TO OPEN SURVEY)",
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              letterSpacing = 0.5.sp,
            ),
        )
        WorkbenchExampleForm.entries.forEach { example ->
          val surveyId = uiState.surveyIdForExampleForm(example) ?: return@forEach
          val isSelected =
            uiState.activeSurveyId == surveyId || uiState.selectedWorkbenchExampleForm == example
          val preloadedEntityCount = uiState.entityCountForSurvey(surveyId)
          val preloadedSubmissionCount = uiState.submissionCountForSurvey(surveyId)
          OutlinedCard(
            onClick = { actions.selectWorkbenchExampleForm(example, launchImmediately = false) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors =
              CardDefaults.outlinedCardColors(
                containerColor =
                  if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                  } else {
                    MaterialTheme.colorScheme.surface
                  },
                contentColor =
                  if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                  } else {
                    MaterialTheme.colorScheme.onSurface
                  },
              ),
            border =
              BorderStroke(
                width = if (isSelected) 1.5.dp else 1.dp,
                color =
                  if (isSelected) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.outlineVariant
                  },
              ),
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
                Text(
                  text = example.shortLabel,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color =
                    if (isSelected) {
                      MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                      MaterialTheme.colorScheme.onSurface
                    },
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(8.dp))

                GroundTonalBadge(
                  text = if (isSelected) "✓ Active Survey" else "Open Survey",
                  tone = if (isSelected) GroundBadgeTone.PRIMARY else GroundBadgeTone.SECONDARY,
                )
              }

              Text(
                text = example.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )

              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                GroundTonalBadge(
                  text = example.badgeText,
                  tone = if (isSelected) GroundBadgeTone.PRIMARY else GroundBadgeTone.SECONDARY,
                )
                GroundTonalBadge(
                  text = "$preloadedEntityCount entities • $preloadedSubmissionCount submissions",
                  tone = GroundBadgeTone.NEUTRAL,
                )
              }
            }
          }
        }
      }

      // Preset / Action Row: Launch Active Form, Reset, and Toggle Raw XML Editor
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = onLaunchOrCloseForm,
          enabled = xmlError == null,
          colors =
            ButtonDefaults.buttonColors(
              containerColor =
                if (isFormOpen) {
                  MaterialTheme.colorScheme.error
                } else {
                  MaterialTheme.colorScheme.primary
                },
              contentColor =
                if (isFormOpen) {
                  MaterialTheme.colorScheme.onError
                } else {
                  MaterialTheme.colorScheme.onPrimary
                },
            ),
        ) {
          Text(
            text =
              if (isFormOpen) {
                "■ Close Active Form Runner"
              } else {
                "▶ Test / Launch Form Now"
              },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
          )
        }

        OutlinedButton(onClick = { showRawXmlEditor = !showRawXmlEditor }) {
          Text(
            text = if (showRawXmlEditor) "Hide XForms XML" else "Edit XForms XML",
            style = MaterialTheme.typography.labelMedium,
          )
        }

        OutlinedButton(onClick = { actions.resetDefaultXFormsXml() }) {
          Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "Reset Default", style = MaterialTheme.typography.labelMedium)
        }
      }

      // Collapsible Multi-line Monospace OutlinedTextField for pasting / editing XForms FormDef XML
      if (showRawXmlEditor || xmlError != null) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = { actions.updateCustomXFormsXml(BAOBAB_BIOMETRICS_SAMPLE_XFORMS_XML) }
          ) {
            Text(text = "Baobab Preset", style = MaterialTheme.typography.labelSmall)
          }

          OutlinedButton(onClick = { actions.updateCustomXFormsXml("") }) {
            Text(text = "Clear XML", style = MaterialTheme.typography.labelSmall)
          }
        }

        androidx.compose.runtime.key(uiState.activeSurveyId, uiState.selectedWorkbenchExampleForm) {
          OutlinedTextField(
            value = uiState.customXFormsXml,
            onValueChange = { actions.updateCustomXFormsXml(it) },
            modifier = Modifier.fillMaxWidth().height(195.dp),
            placeholder = {
              Text(
                text = "Paste XForms <h:html>...</h:html> XML here to test in MobileFormRunner...",
                style =
                  MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  ),
              )
            },
            textStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
            isError = xmlError != null,
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
              ),
          )
        }
      }

      // Error details banner if XML is invalid
      if (xmlError != null) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "XML Parse Error: $xmlError",
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onErrorContainer
              ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          )
        }
      }

      // Optional collapsible ProtoForms TextProto / JSON inspector (using TextProtoSerializer &
      // ProtoJsonSerializer)
      if (parsedFormDef != null && xmlError == null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              "Parsed FormDef: \"${parsedFormDef.title.ifBlank { parsedFormDef.form_id }}\" (v${parsedFormDef.version.ifBlank { "1" }})",
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
              ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )

          TextButton(onClick = { showProtoPreview = !showProtoPreview }) {
            Text(
              text = if (showProtoPreview) "Hide Proto" else "Inspect TextProto / JSON",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        if (showProtoPreview) {
          val serializedProto =
            remember(parsedFormDef, previewAsJson) {
              if (previewAsJson) {
                ProtoJsonSerializer.serializeFormDef(parsedFormDef, prettyPrint = true)
              } else {
                TextProtoSerializer.serializeFormDef(parsedFormDef)
              }
            }
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            GroundFilterChip(
              selected = !previewAsJson,
              onClick = { previewAsJson = false },
              label = {
                Text(
                  "TextProto",
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  softWrap = false,
                )
              },
            )
            GroundFilterChip(
              selected = previewAsJson,
              onClick = { previewAsJson = true },
              label = {
                Text(
                  "JSON",
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  softWrap = false,
                )
              },
            )
          }
          OutlinedTextField(
            value = serializedProto,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            textStyle = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
              ),
          )
        }
      }
    }
  }
}

/** Backward-compatible alias for [PrototypeXFormsWorkbenchPanel]. */
@Composable
fun XFormsFormDefChromeSection(state: PrototypeAppState) {
  PrototypeXFormsWorkbenchPanel(state)
}
