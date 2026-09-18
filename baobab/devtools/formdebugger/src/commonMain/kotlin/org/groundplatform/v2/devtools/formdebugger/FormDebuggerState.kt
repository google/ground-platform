/*
 * IGNORE_COPYRIGHT: Ground is a Google-developed open-source project (The Ground Authors)
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import org.groundplatform.v2.core.forms.serialization.ProtoJsonSerializer
import org.groundplatform.v2.core.forms.serialization.TextProtoSerializer
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.xpath.EvaluationContext
import org.groundplatform.v2.core.forms.xpath.XPathEngine
import org.groundplatform.v2.core.forms.xpath.model.XPathNode
import org.groundplatform.v2.core.forms.xpath.model.XPathValue

/**
 * Supported Protocol Buffer text representations for the right-hand editor panes in the Form
 * Debugger.
 */
enum class ProtoRepresentation(val radioLabel: String, val displayName: String) {
  TEXTPROTO(radioLabel = "textproto", displayName = "TextProto"),
  JSON(radioLabel = "JSON", displayName = "JSON"),
}

/**
 * Reactive state holder for the Form Debugger web app.
 *
 * Manages bidirectional synchronization between:
 * 1. FormDef XForms XML <-> FormDef (textproto or JSON)
 * 2. RecordInstance XML <-> RecordInstance (textproto or JSON)
 * 3. Embedded Mobile Form Runner ([FormWizardController]) <-> RecordInstance & XPath Evaluator
 */
class FormDebuggerState(loadInitialSample: Boolean = true) {

  var protoRepresentation by mutableStateOf(ProtoRepresentation.TEXTPROTO)
    private set

  var formXml by mutableStateOf("")
    private set

  var formTextProto by mutableStateOf("")
    private set

  var formXmlError by mutableStateOf<String?>(null)
    private set

  var formTextProtoError by mutableStateOf<String?>(null)
    private set

  var recordXml by mutableStateOf("")
    private set

  var recordTextProto by mutableStateOf("")
    private set

  var recordXmlError by mutableStateOf<String?>(null)
    private set

  var recordTextProtoError by mutableStateOf<String?>(null)
    private set

  var xpathExpression by mutableStateOf("")
    private set

  var xpathOutput by mutableStateOf("")
    private set

  var currentFormDef: FormDef? = null
    private set

  var currentRecordInstance: RecordInstance? = null
    private set

  var isFormRunnerOpen by mutableStateOf(false)
    private set

  var wizardController by mutableStateOf<FormWizardController?>(null)
    private set

  init {
    if (loadInitialSample) {
      loadSampleData()
    }
  }

  /**
   * Launches or refreshes the embedded mobile form runner in the Form section using the active
   * [currentFormDef] and [currentRecordInstance].
   */
  fun runForm() {
    val formDef = currentFormDef ?: return
    wizardController =
      FormWizardController(
        formDef = formDef,
        existingRecord = currentRecordInstance,
        onRecordUpdated = { updatedRecord, _ -> syncRecordFromRunner(updatedRecord) },
      )
    isFormRunnerOpen = true
  }

  /**
   * Restarts the embedded mobile form runner with a fresh empty record instance (executing
   * `EVENT_INSTANCE_FIRST_LOAD` actions, default values, and preloads).
   */
  fun restartFormFresh() {
    val formDef = currentFormDef ?: return
    wizardController =
      FormWizardController(
        formDef = formDef,
        existingRecord = null,
        onRecordUpdated = { updatedRecord, _ -> syncRecordFromRunner(updatedRecord) },
      )
    isFormRunnerOpen = true
  }

  /** Closes the embedded mobile form runner panel. */
  fun closeFormRunner() {
    isFormRunnerOpen = false
  }

  private fun syncRecordFromRunner(updatedRecord: RecordInstance) {
    currentRecordInstance = updatedRecord
    recordXmlError = null
    recordTextProtoError = null
    recordTextProto = serializeRecordInstance(updatedRecord)
    val rootName =
      currentFormDef?.model?.primary_instance?.record_schema?.name?.takeIf { it.isNotEmpty() }
        ?: updatedRecord.form_id.takeIf { it.isNotEmpty() }
        ?: "data"
    recordXml =
      XFormsXmlSerializer.serializeRecordInstance(
        record = updatedRecord,
        rootElementName = rootName,
        prettyPrint = true,
      )
    evaluateXPath()
  }

  /**
   * Switches the right-hand representation between `textproto` and `JSON` and re-formats the active
   * [FormDef] and [RecordInstance] accordingly.
   */
  fun onProtoRepresentationChanged(newRepresentation: ProtoRepresentation) {
    if (protoRepresentation == newRepresentation) return
    protoRepresentation = newRepresentation

    currentFormDef?.let { formDef ->
      formTextProto = serializeFormDef(formDef)
      formTextProtoError = null
    }

    currentRecordInstance?.let { record ->
      recordTextProto = serializeRecordInstance(record)
      recordTextProtoError = null
    }
  }

  /** Updates XForms XML and synchronizes to the right-hand FormDef pane if valid. */
  fun onFormXmlChanged(newXml: String) {
    formXml = newXml
    if (newXml.isBlank()) {
      currentFormDef = null
      formXmlError = null
      formTextProtoError = null
      formTextProto = ""
      evaluateXPath()
      return
    }
    try {
      val parsed = XFormsXmlSerializer.deserializeFormDef(newXml)
      currentFormDef = parsed
      formXmlError = null
      formTextProtoError = null
      formTextProto = serializeFormDef(parsed)
      // Re-deserialize recordXml with the updated FormDef so field types stay accurate
      refreshRecordFromXmlIfValid()
      evaluateXPath()
    } catch (e: Exception) {
      formXmlError = e.message ?: "Invalid XForms XML"
      evaluateXPath()
    }
  }

  /** Updates FormDef (textproto or JSON) and synchronizes to XForms XML if valid. */
  fun onFormTextProtoChanged(newTextProto: String) {
    formTextProto = newTextProto
    if (newTextProto.isBlank()) {
      currentFormDef = null
      formTextProtoError = null
      formXmlError = null
      formXml = ""
      evaluateXPath()
      return
    }
    try {
      val parsed = deserializeFormDef(newTextProto)
      currentFormDef = parsed
      formTextProtoError = null
      formXmlError = null
      formXml = XFormsXmlSerializer.serializeFormDef(parsed, prettyPrint = true)
      refreshRecordFromXmlIfValid()
      evaluateXPath()
    } catch (e: Exception) {
      formTextProtoError = e.message ?: "Invalid FormDef ${protoRepresentation.radioLabel}"
      evaluateXPath()
    }
  }

  /** Updates RecordInstance XML and synchronizes to the right-hand RecordInstance pane if valid. */
  fun onRecordXmlChanged(newXml: String) {
    recordXml = newXml
    if (newXml.isBlank()) {
      currentRecordInstance = null
      recordXmlError = null
      recordTextProtoError = null
      recordTextProto = ""
      evaluateXPath()
      return
    }
    try {
      val parsed =
        XFormsXmlSerializer.deserializeRecordInstance(xml = newXml, formDef = currentFormDef)
      currentRecordInstance = parsed
      recordXmlError = null
      recordTextProtoError = null
      recordTextProto = serializeRecordInstance(parsed)
      evaluateXPath()
    } catch (e: Exception) {
      recordXmlError = e.message ?: "Invalid RecordInstance XML"
      evaluateXPath()
    }
  }

  /** Updates RecordInstance (textproto or JSON) and synchronizes to RecordInstance XML if valid. */
  fun onRecordTextProtoChanged(newTextProto: String) {
    recordTextProto = newTextProto
    if (newTextProto.isBlank()) {
      currentRecordInstance = null
      recordTextProtoError = null
      recordXmlError = null
      recordXml = ""
      evaluateXPath()
      return
    }
    try {
      val parsed = deserializeRecordInstance(newTextProto)
      currentRecordInstance = parsed
      recordTextProtoError = null
      recordXmlError = null
      val rootName =
        currentFormDef?.model?.primary_instance?.record_schema?.name?.takeIf { it.isNotEmpty() }
          ?: parsed.form_id.takeIf { it.isNotEmpty() }
          ?: "data"
      recordXml =
        XFormsXmlSerializer.serializeRecordInstance(
          record = parsed,
          rootElementName = rootName,
          prettyPrint = true,
        )
      evaluateXPath()
    } catch (e: Exception) {
      recordTextProtoError = e.message ?: "Invalid RecordInstance ${protoRepresentation.radioLabel}"
      evaluateXPath()
    }
  }

  /** Updates the XPath expression and evaluates it immediately in real-time. */
  fun onXPathChanged(newXPath: String) {
    xpathExpression = newXPath
    evaluateXPath()
  }

  /** Re-evaluates the current XPath expression against the active FormDef and RecordInstance. */
  fun evaluateXPath() {
    val expr = xpathExpression.trim()
    if (expr.isEmpty()) {
      xpathOutput = "(Enter an XPath expression above to see real-time evaluation results)"
      return
    }
    val record = currentRecordInstance ?: RecordInstance()
    try {
      val context =
        EvaluationContext.fromRecordInstance(recordInstance = record, formDef = currentFormDef)
      val value = XPathEngine.evaluate(expr, context)
      xpathOutput = formatXPathResult(expr, value)
    } catch (e: Exception) {
      xpathOutput = "XPath Error: ${e.message ?: e.toString()}"
    }
  }

  private fun serializeFormDef(formDef: FormDef): String =
    when (protoRepresentation) {
      ProtoRepresentation.TEXTPROTO -> TextProtoSerializer.serializeFormDef(formDef)
      ProtoRepresentation.JSON -> ProtoJsonSerializer.serializeFormDef(formDef, prettyPrint = true)
    }

  private fun deserializeFormDef(input: String): FormDef =
    when (protoRepresentation) {
      ProtoRepresentation.TEXTPROTO -> TextProtoSerializer.deserializeFormDef(input)
      ProtoRepresentation.JSON -> ProtoJsonSerializer.deserializeFormDef(input)
    }

  private fun serializeRecordInstance(record: RecordInstance): String =
    when (protoRepresentation) {
      ProtoRepresentation.TEXTPROTO -> TextProtoSerializer.serializeRecordInstance(record)
      ProtoRepresentation.JSON ->
        ProtoJsonSerializer.serializeRecordInstance(record, prettyPrint = true)
    }

  private fun deserializeRecordInstance(input: String): RecordInstance =
    when (protoRepresentation) {
      ProtoRepresentation.TEXTPROTO -> TextProtoSerializer.deserializeRecordInstance(input)
      ProtoRepresentation.JSON -> ProtoJsonSerializer.deserializeRecordInstance(input)
    }

  private fun refreshRecordFromXmlIfValid() {
    if (recordXmlError == null && recordXml.isNotBlank()) {
      try {
        val parsed =
          XFormsXmlSerializer.deserializeRecordInstance(xml = recordXml, formDef = currentFormDef)
        currentRecordInstance = parsed
        recordTextProto = serializeRecordInstance(parsed)
      } catch (_: Exception) {
        // Ignore if re-parsing fails
      }
    }
  }

  private fun formatXPathResult(expr: String, value: XPathValue): String =
    buildString {
        val stringRep = value.toXPathString()
        appendLine("String Output: $stringRep")
        appendLine("Result Type:   ${describeType(value)}")
        appendLine("Boolean Value: ${value.toBoolean()}")
        appendLine("Number Value:  ${XPathValue.formatXPathNumber(value.toNumber())}")

        if (value is XPathValue.NodeSet) {
          appendLine()
          if (value.nodes.isEmpty()) {
            appendLine("Nodes (0): <empty node-set>")
          } else {
            appendLine("Matched Nodes (${value.nodes.size}):")
            value.nodes.forEachIndexed { idx, node ->
              val nodeVal = node.extractValue().toXPathString()
              appendLine("  [${idx + 1}] ${formatNodePath(node)} = \"$nodeVal\"")
            }
          }
        }
      }
      .trimEnd()

  private fun describeType(value: XPathValue): String =
    when (value) {
      is XPathValue.NodeSet ->
        "NodeSet (${value.nodes.size} node${if (value.nodes.size == 1) "" else "s"})"
      is XPathValue.Str -> "String"
      is XPathValue.Number -> "Number"
      is XPathValue.Bool -> "Boolean"
      is XPathValue.DateVal -> "Date (${value.toXPathString()})"
      is XPathValue.TimeVal -> "TimeOfDay (${value.toXPathString()})"
      is XPathValue.TimestampVal -> "Timestamp (${value.toXPathString()})"
      is XPathValue.GeoPointVal -> "GeoPoint"
      is XPathValue.GeoTraceVal -> "GeoTrace (${value.value.points.size} points)"
      is XPathValue.GeoShapeVal -> "GeoShape (${value.value.points.size} points)"
      is XPathValue.ValueList -> "ValueList (${value.values.size} items)"
      is XPathValue.BinaryVal -> "Binary (${value.value.size} bytes)"
    }

  private fun formatNodePath(node: XPathNode): String = node.canonicalPath()

  /** Loads default sample XForms FormDef, RecordInstance, and XPath expression. */
  fun loadSampleData() {
    onFormXmlChanged(SAMPLE_FORM_XML)
    onRecordXmlChanged(SAMPLE_RECORD_XML)
    onXPathChanged(
      "concat(/data/species, ' (height: ', /data/height_m, 'm, health: ', /data/health_status, ')')"
    )
  }

  /** Clears all fields. */
  fun clearAll() {
    onFormXmlChanged("")
    onRecordXmlChanged("")
    onXPathChanged("")
  }

  companion object {
    val SAMPLE_FORM_XML =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml"
              xmlns:jr="http://openrosa.org/javarosa">
        <h:head>
          <h:title>Baobab Tree Survey</h:title>
          <model>
            <instance>
              <data id="baobab_survey" version="2026091701">
                <meta>
                  <instanceID/>
                </meta>
                <species>Adansonia digitata</species>
                <height_m>18.5</height_m>
                <circumference_m>12.2</circumference_m>
                <health_status>healthy</health_status>
              </data>
            </instance>
            <bind nodeset="/data/meta/instanceID" type="string" jr:preload="uid"/>
            <bind nodeset="/data/species" type="string" required="true()"/>
            <bind nodeset="/data/height_m" type="decimal"/>
            <bind nodeset="/data/circumference_m" type="decimal"/>
            <bind nodeset="/data/health_status" type="string"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/species">
            <label>Tree Species</label>
          </input>
          <input ref="/data/height_m">
            <label>Height (meters)</label>
          </input>
          <input ref="/data/circumference_m">
            <label>Trunk Circumference (meters)</label>
          </input>
          <select1 ref="/data/health_status">
            <label>Health Status</label>
            <item>
              <label>Healthy</label>
              <value>healthy</value>
            </item>
            <item>
              <label>Stressed</label>
              <value>stressed</value>
            </item>
          </select1>
        </h:body>
      </h:html>
      """
        .trimIndent()

    val SAMPLE_RECORD_XML =
      """
      <data id="baobab_survey" version="2026091701">
        <meta>
          <instanceID>uuid:8f7e6d5c-4b3a-2109-8765-4321fedcba98</instanceID>
        </meta>
        <species>Adansonia digitata</species>
        <height_m>24.8</height_m>
        <circumference_m>15.4</circumference_m>
        <health_status>healthy</health_status>
      </data>
      """
        .trimIndent()
  }
}
