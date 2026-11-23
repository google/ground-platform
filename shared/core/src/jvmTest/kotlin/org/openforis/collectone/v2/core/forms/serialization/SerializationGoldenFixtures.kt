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
package org.openforis.collectone.v2.core.forms.serialization

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.ofEpochSecond
import okio.ByteString.Companion.toByteString
import openforis.collectone.v1.forms.ActionDef
import openforis.collectone.v1.forms.AuditConfig
import openforis.collectone.v1.forms.AuditEvent
import openforis.collectone.v1.forms.AuditLog
import openforis.collectone.v1.forms.ChoiceItem
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.EventType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GeoConfig
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.GeoShape
import openforis.collectone.v1.forms.GeoTrace
import openforis.collectone.v1.forms.GroupDef
import openforis.collectone.v1.forms.IntentConfig
import openforis.collectone.v1.forms.LanguageTranslation
import openforis.collectone.v1.forms.LocalizedString
import openforis.collectone.v1.forms.MediaRef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PreloadType
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RangeConfig
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordMetadata
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.RecordNodeList
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.RepeatDef
import openforis.collectone.v1.forms.SecondaryInstance
import openforis.collectone.v1.forms.SubmissionConfig
import openforis.collectone.v1.forms.TranslationCatalog
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.TypedValueList
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import openforis.collectone.v1.library.Aggregation
import openforis.collectone.v1.library.CodeList
import openforis.collectone.v1.library.CodeListItem
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.ExportProfileDef
import openforis.collectone.v1.library.FormTemplateDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LibraryStatus
import openforis.collectone.v1.library.LocalizedText
import openforis.collectone.v1.library.OrganizationLibrarySettings
import openforis.collectone.v1.library.Pillar
import openforis.collectone.v1.library.PrivacyClass
import openforis.collectone.v1.library.PurposePackDef
import openforis.collectone.v1.library.StarterTemplateDef

/** Edge-case messages and inputs for [SerializationGoldenTest]. */
internal object SerializationGoldenFixtures {

  private const val TRICKY_TEXT = "say \"hi\" \\ back\nslash\ttab ünïcødé 🌳"

  private fun scalar(value: TypedValue) = FieldValue(scalar_value = value)

  private fun node(vararg fields: Pair<String, FieldValue>) =
    RecordNode(fields = linkedMapOf(*fields))

  val formDefs: Map<String, FormDef> =
    linkedMapOf(
      "form_empty" to FormDef(),
      "form_empty_children" to FormDef(model = ModelDef(), view = ViewDef()),
      "form_edge" to
        FormDef(
          form_id = TRICKY_TEXT,
          version = "v1",
          default_language = "en",
          model =
            ModelDef(
              primary_instance =
                PrimaryInstance(
                  record_schema =
                    RecordSchema(
                      name = "data",
                      fields =
                        listOf(
                          FieldDefinition(name = "height", type = DataType.TYPE_DOUBLE),
                          FieldDefinition(
                            name = "trees",
                            type = DataType.TYPE_MESSAGE,
                            is_repeated = true,
                            fields =
                              listOf(FieldDefinition(name = "dbh", type = DataType.TYPE_INT64)),
                          ),
                        ),
                    ),
                  // Written only when it has fields, so its id is dropped.
                  default_values = RecordNode(id = "no-fields"),
                  message_type_name = "m",
                ),
              secondary_instances =
                listOf(
                  SecondaryInstance(
                    id = "list",
                    rows =
                      listOf(
                        RecordNode(
                          fields = linkedMapOf("name" to scalar(TypedValue(string_value = "a"))),
                          id = "r1",
                          sort_order = 2,
                        )
                      ),
                  )
                ),
              bindings =
                listOf(
                  FieldBinding(
                    field_path = "/data/height",
                    type = DataType.TYPE_DOUBLE,
                    preload = PreloadType.PRELOAD_UID,
                    max_pixels = -5,
                  ),
                  FieldBinding(),
                ),
              translations =
                TranslationCatalog(
                  languages =
                    listOf(
                      LanguageTranslation(
                        language = "fr",
                        strings =
                          linkedMapOf(
                            "b" to LocalizedString(value_ = "B"),
                            "a" to LocalizedString(),
                            "c" to LocalizedString(media = MediaRef(image_uri = "x.png")),
                          ),
                      )
                    )
                ),
              actions =
                listOf(
                  ActionDef(
                    events =
                      listOf(
                        EventType.EVENT_TYPE_UNSPECIFIED,
                        EventType.EVENT_VALUE_CHANGED,
                        EventType.EVENT_TYPE_UNSPECIFIED,
                      ),
                    value_expression = "",
                  ),
                  ActionDef(literal_value = TypedValue()),
                  ActionDef(literal_value = TypedValue(int32_value = 0)),
                  ActionDef(literal_value = TypedValue(string_value = "2026-01-02")),
                  ActionDef(literal_value = TypedValue(string_value = "")),
                ),
              metadata = RecordMetadata(),
              submission = SubmissionConfig(auto_send = true),
            ),
          view =
            ViewDef(
              components =
                listOf(
                  ViewComponent(
                    control =
                      ControlDef(
                        type = ControlType.CONTROL_RANGE,
                        range_config =
                          RangeConfig(start = -0.0, end = 1e21, step = 0.1, tick_interval = -2.5),
                        geo_config = GeoConfig(accuracy_threshold_meters = 5.0),
                        choices =
                          listOf(
                            ChoiceItem(
                              value_ = "v",
                              properties = linkedMapOf("z" to "", "a" to "1"),
                            )
                          ),
                        intent = IntentConfig(parameters = mapOf("k" to "v")),
                      ),
                    id = "c1",
                  ),
                  ViewComponent(
                    group =
                      GroupDef(
                        components = listOf(ViewComponent(repeat = RepeatDef(no_add_remove = true)))
                      )
                  ),
                  ViewComponent(),
                )
            ),
          sort_order = -3,
        ),
    )

  val records: Map<String, RecordInstance> =
    linkedMapOf(
      "record_empty" to RecordInstance(),
      // Empty metadata, data and audit log are left out.
      "record_empty_children" to
        RecordInstance(
          form_id = "f",
          metadata = RecordMetadata(),
          data_ = RecordNode(),
          audit_log = AuditLog(),
        ),
      "record_nested_empty_metadata" to
        RecordInstance(metadata = RecordMetadata(audit_config = AuditConfig())),
      "record_edge" to
        RecordInstance(
          form_id = "f",
          metadata =
            RecordMetadata(
              instance_id = "uuid:1",
              start_time = ofEpochSecond(0L, 0L),
              end_time = ofEpochSecond(-1L, 500L),
              today = Date(year = 2026, month = 2, day = 3),
              audit_config = AuditConfig(enabled = true, location_priority = "high"),
            ),
          data_ =
            RecordNode(
              fields =
                linkedMapOf(
                  "s" to scalar(TypedValue(string_value = TRICKY_TEXT)),
                  "temporal" to scalar(TypedValue(string_value = "2026-01-02")),
                  "empty" to scalar(TypedValue(string_value = "")),
                  "i32" to scalar(TypedValue(int32_value = 0)),
                  "i64small" to scalar(TypedValue(int64_value = 5L)),
                  "i64mid" to scalar(TypedValue(int64_value = 3_000_000_000L)),
                  "i64big" to scalar(TypedValue(int64_value = Long.MAX_VALUE)),
                  "d" to scalar(TypedValue(double_value = 1.0)),
                  "dneg0" to scalar(TypedValue(double_value = -0.0)),
                  "dnan" to scalar(TypedValue(double_value = Double.NaN)),
                  "dinf" to scalar(TypedValue(double_value = Double.NEGATIVE_INFINITY)),
                  "dexp" to scalar(TypedValue(double_value = 1.0e-7)),
                  "b" to scalar(TypedValue(bool_value = false)),
                  "date" to scalar(TypedValue(date_value = Date(year = 2026, month = 1, day = 2))),
                  "time" to
                    scalar(
                      TypedValue(
                        time_value =
                          TimeOfDay(hours = 1, minutes = 2, seconds = 3, nanos = 4_000_000)
                      )
                    ),
                  "time_ns" to scalar(TypedValue(time_value = TimeOfDay(hours = 23, nanos = 5))),
                  "ts" to
                    scalar(
                      TypedValue(timestamp_value = ofEpochSecond(1_789_733_415L, 123_456_789L))
                    ),
                  "gp" to scalar(TypedValue(geopoint_value = GeoPoint(1.5, -2.0))),
                  "gp_acc" to
                    scalar(
                      TypedValue(
                        geopoint_value =
                          GeoPoint(1.0, 2.0, altitude_meters = 0.0, accuracy_meters = 3.0)
                      )
                    ),
                  "gp_alt" to
                    scalar(TypedValue(geopoint_value = GeoPoint(0.0, 0.0, altitude_meters = 7.0))),
                  "bin" to
                    scalar(
                      TypedValue(
                        binary_value =
                          byteArrayOf(0, -1, 10, '"'.code.toByte(), 'a'.code.toByte())
                            .toByteString()
                      )
                    ),
                  "trace" to
                    scalar(
                      TypedValue(
                        geotrace_value = GeoTrace(listOf(GeoPoint(1.0, 2.0), GeoPoint(3.0, 4.0)))
                      )
                    ),
                  "trace_closed" to scalar(TypedValue(geotrace_value = GeoTrace(closedRing()))),
                  "shape" to scalar(TypedValue(geoshape_value = GeoShape(closedRing()))),
                  "shape_open" to
                    scalar(TypedValue(geoshape_value = GeoShape(closedRing().dropLast(1)))),
                  "shape_empty" to scalar(TypedValue(geoshape_value = GeoShape())),
                  "list" to
                    FieldValue(
                      list_value =
                        TypedValueList(
                          listOf(TypedValue(string_value = "a"), TypedValue(int32_value = 1))
                        )
                    ),
                  "list_empty" to FieldValue(list_value = TypedValueList()),
                  "group" to
                    FieldValue(node_value = node("x" to scalar(TypedValue(bool_value = true)))),
                  "repeat" to
                    FieldValue(
                      repeat_value =
                        RecordNodeList(
                          listOf(
                            node("n" to scalar(TypedValue(int32_value = 1))),
                            RecordNode(id = "second", sort_order = 1),
                          )
                        )
                    ),
                  "repeat_empty" to FieldValue(repeat_value = RecordNodeList()),
                  "unset" to FieldValue(),
                ),
              id = "root",
              sort_order = 4,
            ),
          audit_log =
            AuditLog(
              events =
                listOf(
                  AuditEvent(
                    event = "e",
                    start_time = ofEpochSecond(10L, 0L),
                    location = GeoPoint(0.0, 0.0),
                  ),
                  AuditEvent(),
                )
            ),
        ),
    )

  private fun closedRing() =
    listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(1.0, 1.0), GeoPoint(0.0, 0.0))

  val bundles: Map<String, LibraryBundle> =
    linkedMapOf(
      "library_empty" to LibraryBundle(),
      "library_edge" to
        LibraryBundle(
          concepts =
            listOf(
              ConceptDef(
                id = "c.one",
                version = 2,
                label = LocalizedText(linkedMapOf("en" to TRICKY_TEXT, "fr" to "")),
                keywords = listOf(LocalizedText(), LocalizedText(mapOf("es" to "x"))),
                data_type = DataType.TYPE_SELECT_ONE,
                code_list =
                  CodeList(
                    items =
                      listOf(
                        CodeListItem(
                          code = "a",
                          external_ids = linkedMapOf("hs" to "1", "b" to ""),
                        ),
                        CodeListItem(),
                      )
                  ),
                aggregation = Aggregation.COUNT_BY_CODE,
                privacy_class = PrivacyClass.SENSITIVE,
                goals = listOf("", "g"),
                pillar = Pillar.ADAPTATION,
                status = LibraryStatus.DEPRECATED,
                sort_order = -1,
              ),
              ConceptDef(code_list = CodeList()),
            ),
          form_templates =
            listOf(
              FormTemplateDef(
                id = "t",
                title = LocalizedText(mapOf("en" to "T")),
                form = formDefs.getValue("form_edge"),
                status = LibraryStatus.STABLE,
                version = 3,
                sort_order = 1,
              ),
              FormTemplateDef(form = FormDef()),
            ),
          purpose_packs =
            listOf(
              PurposePackDef(
                id = "p",
                icon = "leaf",
                form_template_ids = listOf("t", ""),
                starter_templates =
                  listOf(
                    StarterTemplateDef(id = "s", program_ids = listOf("x")),
                    StarterTemplateDef(),
                  ),
                sort_order = 9,
              )
            ),
          export_profiles =
            listOf(
              ExportProfileDef(
                id = "e",
                format = "csv",
                field_concepts = linkedMapOf("z" to "c.one", "a" to ""),
                status = LibraryStatus.DRAFT,
              )
            ),
        ),
    )

  val settings: Map<String, OrganizationLibrarySettings> =
    linkedMapOf(
      "settings_empty" to OrganizationLibrarySettings(),
      "settings_edge" to
        OrganizationLibrarySettings(
          organization_id = TRICKY_TEXT,
          hidden_global_entry_ids = listOf("", "b"),
        ),
    )

  /** A form whose schema and bindings guide how flattened record JSON is typed. */
  val schemaFormDef =
    FormDef(
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  fields =
                    listOf(
                      FieldDefinition(name = "height", type = DataType.TYPE_DOUBLE),
                      FieldDefinition(name = "count", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "big", type = DataType.TYPE_INT64),
                      FieldDefinition(name = "when", type = DataType.TYPE_DATE),
                      FieldDefinition(name = "tags", type = DataType.TYPE_SELECT_MULTIPLE),
                      FieldDefinition(
                        name = "emptyArr",
                        type = DataType.TYPE_MESSAGE,
                        is_repeated = true,
                      ),
                      FieldDefinition(name = "loc", type = DataType.TYPE_GEOPOINT),
                      FieldDefinition(name = "trace", type = DataType.TYPE_GEOSHAPE),
                    )
                )
            ),
          bindings =
            listOf(
              FieldBinding(field_path = "/data/t", type = DataType.TYPE_TIME),
              FieldBinding(field_path = "/data/flag", type = DataType.TYPE_STRING),
            ),
        )
    )

  val formDefJsonInputs: Map<String, String> =
    linkedMapOf(
      "json_lenient" to
        """
        {"formId": "f", "form_id": "ignored", "sortOrder": "7", "title": null, "version": 5,
         "model": {
           "bindings": [
             {"fieldPath": "p", "type": "type_double", "readOnly": "true", "maxPixels": "12",
              "preload": "nonsense", "foreign_attributes": [{"value": "v"}]},
             5, "x"],
           "translations": {"languages": [{"language": "en", "isDefault": 1,
             "strings": [{"key": "k", "value": {"value": "V"}}, {"key": "k2"}]}]},
           "actions": [
             {"events": ["event_value_changed", "BOGUS", 3], "literalValue": "2026-01-02",
              "valueExpression": "x"},
             {"valueExpression": "y", "literal_value": null}],
           "metadata": {"startTime": "2026-01-02T03:04:05.5+01:30",
             "today": {"year": 2026, "month": 1, "day": 2},
             "endTime": {"seconds": "10", "nanos": 5}}},
         "view": {"components": [{"control": {
           "choices": [{"value": "a", "properties": [{"key": "p", "value": "q"}]}],
           "intent": {"parameters": {"a": 1, "b": null}},
           "rangeConfig": {"start": "1.5", "end": 2, "step": true}}}]},
         "unknown": {"x": 1}}
        """,
      "json_wrong_types" to """{"model": "str", "view": [1, 2], "formId": 5, "sortOrder": 1.5}""",
      "json_default_values" to
        """
        {"model": {"primaryInstance": {
          "defaultValues": {"height": "12.5", "when": "2026-01-02", "loc": [1, 2]},
          "recordSchema": {"fields": [{"name": "height", "type": "TYPE_DOUBLE"},
            {"name": "when", "type": "TYPE_STRING"}]}}}}
        """,
      "json_oneof_conflict" to """{"view": {"components": [{"control": {}, "group": {}}]}}""",
      "json_not_object" to "[1]",
      "json_invalid" to "{",
    )

  val recordJsonInputs: Map<String, String> =
    linkedMapOf(
      "json_flattened" to
        """
        {"formId": "f", "metadata": {"instanceId": "i", "today": "2026-01-02"},
         "data": {"height": "12.5", "count": 3, "big": "123", "when": "2026-01-02",
           "t": "10:11:12", "ts": "2026-01-02T03:04:05Z", "loc": [1, 2, 3, 4],
           "trace": [[1, 2], [3, 4]], "shape": [[0, 0], [0, 1], [1, 1], [0, 0]],
           "tags": ["a", "b"], "emptyArr": [], "grp": {"x": 1}, "rep": [{"a": 1}, {"a": 2}],
           "wrapped": {"int64": "99"}, "wrapped2": {"base64": "AAE="}, "nul": null,
           "flag": true, "dbl": 1.0, "legacy": {"scalarValue": {"stringValue": "s"}}},
         "auditLog": {"events": [{"event": "e", "location": {"latitude": 1, "longitude": 2}}]}}
        """,
      "json_legacy_fields_object" to
        """
        {"data": {"fields": {
          "a": {"scalarValue": {"int32Value": 1}},
          "b": {"listValue": {"values": [{"stringValue": "x"}]}},
          "c": {"nodeValue": {"fields": {}}},
          "d": {"repeatValue": {"nodes": [{"fields": {"e": {"scalar_value": {"bool_value": true}}}}]}}}}}
        """,
      "json_legacy_fields_array" to
        """{"data": {"fields": [{"key": "a", "value": {"scalarValue": {"doubleValue": 2.5}}}]}}""",
    )

  val formDefTextInputs: Map<String, String> =
    linkedMapOf(
      "text_lenient" to
        """
        # A comment.
        form_id: "a" form_id: "b"
        title: 'single'
        sort_order: "5"
        model {
          bindings { type: type_double preload: BOGUS read_only: t max_pixels: 16 }
          actions {
            events: [EVENT_VALUE_CHANGED, event_instance_load]
            value_expression: "v"
            literal_value { int32_value: 3 }
          }
          translations { languages {
            strings { key: "k" value { value: "v" } }
            strings { key: "no_value" }
          } }
          submission { action_url: "a" }
        }
        model { submission { method: "m" } }
        view {
          components [{ control {
            choices { value: "c" properties { key: "a" } }
            range_config { start: inf end: -1.5e3 step: nan }
          } }, { id: "two" }]
        }
        """,
      "text_oneof_conflict" to "view { components { control {} group {} } }",
    )

  val recordTextInputs: Map<String, String> =
    linkedMapOf(
      "text_record" to
        """
        form_id: "f"
        metadata { start_time { seconds: 5 nanos: 7 } today { year: 2026 month: 1 day: 2 } }
        data {
          fields { key: "g" value { scalar_value { geopoint_value {
            latitude: 1 longitude: 2 altitude_meters: 0 } } } }
          fields { key: "b" value { scalar_value { binary_value: "\x00\xff" } } }
          fields { key: "n" }
          id: "r"
          sort_order: 2
        }
        audit_log { events { location { latitude: 0 longitude: 0 } } }
        """,
      "text_typed_value_conflict" to
        """data { fields { key: "a" value { scalar_value { int32_value: 1 string_value: "x" } } } }""",
    )

  val bundleTextInputs: Map<String, String> =
    linkedMapOf(
      "text_bundle" to
        """
        concepts {
          id: "c" data_type: TYPE_STRING status: STABLE goals: ["a", "b"]
          label { values { key: "en" value: "L" } }
          code_list { items { code: "x" external_ids { key: "k" } } }
        }
        form_templates {
          id: "t" version: 3 sort_order: 1
          form { form_id: "f" model { bindings { type: type_string } } }
        }
        export_profiles {
          field_concepts { key: "a" value: "b" }
          field_concepts { key: "a" value: "c" }
        }
        """,
      "text_bundle_unknown_enum" to "concepts { status: BOGUS }",
      "text_bundle_lowercase_enum" to "concepts { status: stable }",
    )

  val settingsTextInputs: Map<String, String> =
    linkedMapOf(
      "text_settings" to
        """
        organization_id: "o"
        hidden_global_entry_ids: "a"
        hidden_global_entry_ids: ["b", ""]
        """
    )
}
