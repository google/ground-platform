# Entity Records

`EntityRecord` (defined in `entity_record.proto`) represents a persistent,
versioned entity instance within an ODK Entity Dataset (`EntityDatasetDef`).

## Schema Definition

```protobuf
message EntityRecord {
  string entity_id = 1;
  string dataset_id = 2;
  int64 revision = 3;
  string label = 4;

  // Exposed to ODK / ProtoForms XPath expressions and `entity_saveto` bindings
  // under the reserved system property name "geometry".
  oneof geometry {
    groundplatform.v2.forms.GeoPoint point = 5;
    groundplatform.v2.forms.GeoTrace trace = 6;
    groundplatform.v2.forms.GeoShape shape = 7;
  }

  map<string, groundplatform.v2.forms.TypedValue> properties = 8;
  AuditInfo audit_info = 9;
  bool is_field_created = 10;
  bool is_deleted = 11;
}
```

When queried in ProtoForms XPath expressions
(`instance('<dataset_id>')/root/item[...]`) or updated via form entity bindings
(`entity_saveto`), `EntityRecord` exposes four reserved system properties
alongside custom `properties`:

*   **`"name"` / `"__id"`**: Maps to `entity_id`
*   **`"label"`**: Maps to `label`
*   **`"__version"`**: Maps to `revision`
*   **`"geometry"`**: Maps to `geometry` (`point`, `trace`, or `shape`)

## Example: Geospatial Plot Entity Record

```textproto
entity_id: "ent-plot-7721"
dataset_id: "plots"
revision: 1
label: "Plot 104 - Grace Wanjiku"
shape {
  points { latitude: -0.28270 longitude: 34.75190 altitude_meters: 1580.2 accuracy_meters: 2.8 }
  points { latitude: -0.28285 longitude: 34.75210 altitude_meters: 1581.0 accuracy_meters: 3.1 }
  points { latitude: -0.28300 longitude: 34.75195 altitude_meters: 1580.5 accuracy_meters: 2.9 }
  points { latitude: -0.28270 longitude: 34.75190 altitude_meters: 1580.2 accuracy_meters: 2.8 }
}
properties {
  key: "farmer_name"
  value { string_value: "Grace Wanjiku" }
}
properties {
  key: "area_hectares"
  value { double_value: 1.42 }
}
is_field_created: true
is_deleted: false
audit_info {
  created_by: "uid_112233445"
  create_time { seconds: 1789350000 }
}
```
