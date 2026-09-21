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
package org.groundplatform.v2.devtools.formdebugger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.MobileFormRunner
import org.groundplatform.v2.core.forms.ui.MobilePhoneFrame

private val FormDebuggerColors: ColorScheme =
  lightColorScheme(
    primary = Color(0xFF1B5E20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5E9),
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF33691E),
    onSecondary = Color.White,
    background = Color(0xFFF5F7F6),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    error = Color(0xFFB3261E),
  )

@Composable
fun FormDebuggerApp(state: FormDebuggerState = remember { FormDebuggerState() }) {
  val repr = state.protoRepresentation
  val formatDisplayName = repr.displayName
  val formatRadioLabel = repr.radioLabel

  MaterialTheme(colorScheme = FormDebuggerColors) {
    Surface(
      modifier =
        Modifier.layout { measurable, constraints ->
            val boundedConstraints =
              if (constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
                constraints
              } else {
                val fallbackWidth =
                  if (constraints.hasBoundedWidth) constraints.maxWidth else 1280.dp.roundToPx()
                val fallbackHeight =
                  if (constraints.hasBoundedHeight) constraints.maxHeight else 900.dp.roundToPx()
                constraints.copy(
                  minWidth = constraints.minWidth.coerceAtMost(fallbackWidth),
                  maxWidth = fallbackWidth,
                  minHeight = constraints.minHeight.coerceAtMost(fallbackHeight),
                  maxHeight = fallbackHeight,
                )
              }
            val placeable = measurable.measure(boundedConstraints)
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
          }
          .fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        // Header Bar
        HeaderBar(
          protoRepresentation = repr,
          onLoadSample = state::loadSampleData,
          onClearAll = state::clearAll,
        )

        // Section 1: Form Definition (XForms XML <-> TextProto / JSON) + RUN button
        BidiEditorSection(
          sectionTitle = "1. Form Definition (FormDef)",
          sectionSubtitle =
            "Paste or edit ODK XForms XML (<h:html>) or ProtoForms FormDef $formatRadioLabel. Click RUN to execute the form in the embedded mobile view.",
          leftTitle = "XForms Form Definition (XML)",
          leftValue = state.formXml,
          onLeftChanged = state::onFormXmlChanged,
          leftError = state.formXmlError,
          leftPlaceholder = "Paste XForms <h:html>...</h:html> XML here...",
          rightTitle = "Equivalent FormDef ($formatDisplayName)",
          rightValue = state.formTextProto,
          onRightChanged = state::onFormTextProtoChanged,
          rightError = state.formTextProtoError,
          rightPlaceholder = "Paste or edit FormDef $formatRadioLabel here...",
          protoRepresentation = repr,
          onProtoRepresentationChanged = state::onProtoRepresentationChanged,
          editorHeight = 280.dp,
          headerActions = {
            val canRun =
              state.currentFormDef != null &&
                state.formXmlError == null &&
                state.formTextProtoError == null
            Button(
              onClick = {
                if (state.isFormRunnerOpen) {
                  state.closeFormRunner()
                } else {
                  state.runForm()
                }
              },
              enabled = canRun,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor =
                    if (state.isFormRunnerOpen) Color(0xFFB3261E) else Color(0xFF1B5E20)
                ),
            ) {
              Text(
                text = if (state.isFormRunnerOpen) "■ STOP / CLOSE FORM" else "▶ RUN",
                fontWeight = FontWeight.Bold,
              )
            }
          },
          extraContent = {
            val controller = state.wizardController
            if (state.isFormRunnerOpen && controller != null) {
              EmbeddedMobileFormRunnerPanel(
                controller = controller,
                onRunWithCurrentRecord = state::runForm,
                onRestartFreshRecord = state::restartFormFresh,
                onClose = state::closeFormRunner,
              )
            }
          },
        )

        // Section 2: Record Instance (Submission XML <-> TextProto / JSON)
        BidiEditorSection(
          sectionTitle = "2. Record Instance (RecordInstance)",
          sectionSubtitle =
            "Paste or edit ODK submission XML (<data id=\"...\">) or ProtoForms RecordInstance $formatRadioLabel. Automatically updates as you answer questions in the mobile runner.",
          leftTitle = "Record Instance (XML)",
          leftValue = state.recordXml,
          onLeftChanged = state::onRecordXmlChanged,
          leftError = state.recordXmlError,
          leftPlaceholder = "Paste <data id=\"...\">...</data> XML here...",
          rightTitle = "Equivalent RecordInstance ($formatDisplayName)",
          rightValue = state.recordTextProto,
          onRightChanged = state::onRecordTextProtoChanged,
          rightError = state.recordTextProtoError,
          rightPlaceholder = "Paste or edit RecordInstance $formatRadioLabel here...",
          protoRepresentation = repr,
          onProtoRepresentationChanged = state::onProtoRepresentationChanged,
          editorHeight = 240.dp,
        )

        // Section 3: Real-Time XPath Evaluator
        XPathEvaluatorSection(
          xpathExpression = state.xpathExpression,
          onXPathChanged = state::onXPathChanged,
          xpathOutput = state.xpathOutput,
        )
      }
    }
  }
}

@Composable
private fun HeaderBar(
  protoRepresentation: ProtoRepresentation,
  onLoadSample: () -> Unit,
  onClearAll: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = "ProtoForms Form Debugger",
          style =
            MaterialTheme.typography.headlineSmall.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text =
            "Interactive XForms XML ↔ ProtoForms (${protoRepresentation.displayName}) Converter & Real-Time XPath Engine",
          style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF555555)),
        )
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(onClick = onClearAll) { Text("Clear All") }
        Button(onClick = onLoadSample) { Text("Reset Sample Data") }
      }
    }
  }
}

@Composable
private fun ProtoRepresentationSelector(
  selected: ProtoRepresentation,
  onSelectedChange: (ProtoRepresentation) -> Unit,
) {
  Row(
    modifier =
      Modifier.background(Color(0xFFF1F5F2), RoundedCornerShape(8.dp))
        .padding(horizontal = 10.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "Format:",
      style =
        MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF2C3E50),
        ),
    )
    ProtoRepresentation.entries.forEach { option ->
      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(6.dp))
            .clickable { onSelectedChange(option) }
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(selected = selected == option, onClick = { onSelectedChange(option) })
        Text(
          text = option.radioLabel,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontFamily = FontFamily.Monospace,
              fontWeight = if (selected == option) FontWeight.Bold else FontWeight.Normal,
              color =
                if (selected == option) MaterialTheme.colorScheme.primary else Color(0xFF333333),
            ),
        )
      }
    }
  }
}

@Composable
private fun BidiEditorSection(
  sectionTitle: String,
  sectionSubtitle: String,
  leftTitle: String,
  leftValue: String,
  onLeftChanged: (String) -> Unit,
  leftError: String?,
  leftPlaceholder: String,
  rightTitle: String,
  rightValue: String,
  onRightChanged: (String) -> Unit,
  rightError: String?,
  rightPlaceholder: String,
  protoRepresentation: ProtoRepresentation,
  onProtoRepresentationChanged: (ProtoRepresentation) -> Unit,
  editorHeight: Dp,
  headerActions: (@Composable () -> Unit)? = null,
  extraContent: (@Composable () -> Unit)? = null,
) {
  val formatDisplayName = protoRepresentation.displayName

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = sectionTitle,
            style =
              MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              ),
          )
          Text(
            text = sectionSubtitle,
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF666666)),
          )
        }
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          headerActions?.invoke()
          ProtoRepresentationSelector(
            selected = protoRepresentation,
            onSelectedChange = onProtoRepresentationChanged,
          )
          StatusBadge(
            isError = leftError != null || rightError != null,
            text =
              when {
                leftError != null -> "XML Parse Error"
                rightError != null -> "$formatDisplayName Parse Error"
                else -> "In Sync (XML ↔ $formatDisplayName)"
              },
          )
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Left Field (XML)
        EditorPane(
          modifier = Modifier.weight(1f),
          title = leftTitle,
          value = leftValue,
          onValueChange = onLeftChanged,
          error = leftError,
          placeholder = leftPlaceholder,
          height = editorHeight,
        )

        // Right Field (TextProto or JSON)
        EditorPane(
          modifier = Modifier.weight(1f),
          title = rightTitle,
          value = rightValue,
          onValueChange = onRightChanged,
          error = rightError,
          placeholder = rightPlaceholder,
          height = editorHeight,
        )
      }

      extraContent?.invoke()
    }
  }
}

@Composable
private fun EmbeddedMobileFormRunnerPanel(
  controller: FormWizardController,
  onRunWithCurrentRecord: () -> Unit,
  onRestartFreshRecord: () -> Unit,
  onClose: () -> Unit,
) {
  val state = controller.formState
  val step = controller.currentStep

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .padding(top = 8.dp)
        .background(Color(0xFFF1F5F2), RoundedCornerShape(16.dp))
        .border(1.dp, Color(0xFFC8E6C9), RoundedCornerShape(16.dp))
        .padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = "Embedded Mobile Form Runner (Single Question Per Screen)",
          style =
            MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              color = Color(0xFF1B5E20),
            ),
        )
        Text(
          text =
            "Interactive Compose Multiplatform mobile preview. Answering questions updates Section 2 (RecordInstance) and Section 3 (XPath) live.",
          style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF374151)),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRestartFreshRecord) { Text("✨ New Blank Record") }
        OutlinedButton(onClick = onRunWithCurrentRecord) { Text("↻ Reload Record") }
        OutlinedButton(onClick = onClose) { Text("✕ Close") }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(24.dp),
      verticalAlignment = Alignment.Top,
    ) {
      // Embedded Mobile Phone Viewport
      MobilePhoneFrame(
        deviceLabel = "Mobile Viewport (${state.formDef.title.ifBlank { "Form" }})"
      ) {
        MobileFormRunner(controller = controller, onClose = onClose)
      }

      // Live Engine State Inspector Panel next to the phone preview
      Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "Live Form Engine State",
            style =
              MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B5E20),
              ),
          )

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusBadge(
              isError = !state.isValid,
              text =
                if (state.isValid) {
                  "All Relevant Fields Valid"
                } else {
                  "${state.validationErrors.size} Validation Error(s)"
                },
            )
            StatusBadge(
              isError = false,
              text = "Step ${controller.currentStepIndex + 1} of ${controller.totalSteps}",
            )
            StatusBadge(isError = false, text = "Language: ${state.activeLanguage}")
          }

          Text(
            text = "Current Screen: ${step.title} (${step.stepKey})",
            style =
              MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F2937),
              ),
          )

          Text(
            text = "Evaluated Field States (${state.fieldStates.size} total):",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          )

          Column(
            modifier =
              Modifier.fillMaxWidth()
                .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            state.fieldStates.values.forEach { fs ->
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable { controller.jumpToField(fs.canonicalPath) }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = fs.canonicalPath,
                  style =
                    MaterialTheme.typography.bodySmall.copy(
                      fontFamily = FontFamily.Monospace,
                      fontWeight = FontWeight.Medium,
                      color = if (fs.isRelevant) Color(0xFF0F172A) else Color(0xFF9CA3AF),
                    ),
                )
                Text(
                  text =
                    buildString {
                      if (!fs.isRelevant) {
                        append("[hidden / non-relevant]")
                      } else {
                        val v =
                          org.groundplatform.v2.core.forms.ui.formatFieldValueForDisplay(
                            fs.value,
                            fs.dataType,
                          )
                        append(v)
                        if (fs.isRequired) append(" *")
                        if (fs.isCalculated) append(" (calc)")
                      }
                    },
                  style =
                    MaterialTheme.typography.bodySmall.copy(
                      fontFamily = FontFamily.Monospace,
                      color = if (fs.isRelevant) Color(0xFF1B5E20) else Color(0xFF9CA3AF),
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

@Composable
private fun EditorPane(
  modifier: Modifier = Modifier,
  title: String,
  value: String,
  onValueChange: (String) -> Unit,
  error: String?,
  placeholder: String,
  height: Dp,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        style =
          MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2C3E50),
          ),
      )
      Text(
        text = "${value.lines().size} lines",
        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF888888)),
      )
    }

    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = Modifier.fillMaxWidth().height(height),
      placeholder = {
        Text(
          text = placeholder,
          style =
            TextStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = 13.sp,
              color = Color(0xFF999999),
            ),
        )
      },
      textStyle =
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
      isError = error != null,
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedContainerColor = Color(0xFFFAFAFA),
          unfocusedContainerColor = Color(0xFFFAFAFA),
          errorContainerColor = Color(0xFFFFF8F8),
        ),
    )

    if (error != null) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .background(Color(0xFFFDECEA), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Text(
          text = error,
          style =
            TextStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
              color = Color(0xFFB3261E),
            ),
        )
      }
    }
  }
}

@Composable
private fun XPathEvaluatorSection(
  xpathExpression: String,
  onXPathChanged: (String) -> Unit,
  xpathOutput: String,
) {
  val presets =
    listOf(
      "/data/species",
      "/data/height_m",
      "concat(/data/species, ' (height: ', /data/height_m, 'm, health: ', /data/health_status, ')')",
      "if(/data/height_m > 20, 'Giant Baobab', 'Standard Tree')",
      "round(/data/height_m div /data/circumference_m, 2)",
      "count(/data/*)",
    )

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "3. Real-Time XPath Evaluator",
            style =
              MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              ),
          )
          Text(
            text =
              "Enter an ODK XPath expression below to evaluate in real-time against the active FormDef and RecordInstance.",
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF666666)),
          )
        }
        StatusBadge(
          isError = xpathOutput.startsWith("XPath Error:"),
          text =
            if (xpathOutput.startsWith("XPath Error:")) "Evaluation Error" else "Live Evaluation",
        )
      }

      // Quick preset buttons
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Examples:",
          style =
            MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF555555),
            ),
        )
        presets.forEach { preset ->
          OutlinedButton(onClick = { onXPathChanged(preset) }, modifier = Modifier.height(32.dp)) {
            Text(
              text = preset,
              style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            )
          }
        }
      }

      // XPath Input Field
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "XPath Expression",
          style =
            MaterialTheme.typography.labelLarge.copy(
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF2C3E50),
            ),
        )
        OutlinedTextField(
          value = xpathExpression,
          onValueChange = onXPathChanged,
          modifier = Modifier.fillMaxWidth(),
          placeholder = {
            Text(
              text = "e.g. /data/species or concat(/data/species, ' - ', /data/height_m)",
              style =
                TextStyle(
                  fontFamily = FontFamily.Monospace,
                  fontSize = 14.sp,
                  color = Color(0xFF999999),
                ),
            )
          },
          textStyle =
            TextStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
            ),
          singleLine = true,
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = Color(0xFFFAFAFA),
              unfocusedContainerColor = Color(0xFFFAFAFA),
            ),
        )
      }

      // Read-only Output Field
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "Real-Time XPath Output (Read-Only)",
          style =
            MaterialTheme.typography.labelLarge.copy(
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF2C3E50),
            ),
        )
        OutlinedTextField(
          value = xpathOutput,
          onValueChange = {},
          readOnly = true,
          modifier = Modifier.fillMaxWidth().height(180.dp),
          textStyle =
            TextStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = 13.sp,
              lineHeight = 19.sp,
              color =
                if (xpathOutput.startsWith("XPath Error:")) Color(0xFFB3261E) else Color(0xFF1B5E20),
            ),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = Color(0xFFF1F8E9),
              unfocusedContainerColor = Color(0xFFF1F8E9),
              focusedBorderColor = Color(0xFF81C784),
              unfocusedBorderColor = Color(0xFFA5D6A7),
            ),
        )
      }
    }
  }
}

@Composable
private fun StatusBadge(isError: Boolean, text: String) {
  val bgColor = if (isError) Color(0xFFFDECEA) else Color(0xFFE8F5E9)
  val textColor = if (isError) Color(0xFFB3261E) else Color(0xFF1B5E20)
  Box(
    modifier =
      Modifier.background(bgColor, RoundedCornerShape(16.dp))
        .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Text(
      text = text,
      style =
        MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.SemiBold,
          color = textColor,
        ),
    )
  }
}
