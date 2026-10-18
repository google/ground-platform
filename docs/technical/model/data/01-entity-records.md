<!--
  Copyright 2026 The Ground Authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Entity Records

`EntityRecord` (defined in `entity_record.proto`) represents a persistent,
versioned entity instance within an Entity Dataset (`EntityDatasetDef`).

## Schema Definition

```protobuf
message EntityRecord {
  string entity_id = 1;
  string dataset_id = 2;
  int64 revision = 3;
  string label = 4;

  // Exposed to XForms / ProtoForms XPath expressions and `entity_saveto` bindings
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
  string s2_cell_token = 12;
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

## Geometry Size

Polygon geometries (`shape`) are ordinary `forms.GeoShape` closed rings. Their
vertex count is limited by the per-tier `QuotaLimits.max_area_vertices` quota
(see [Access Control & Quotas](../survey/04-access-control-and-quotas.md)), not
by a fixed schema rule. Generated sample plots use 5 coordinates for squares
and 25 for circles (24 vertices plus the closing one).

## Spatial Cell Index (`s2_cell_token`)

Every geospatial entity carries the token of the
[S2](https://s2geometry.io/) level-13 cell (about 1 km²) containing its
representative point:

| Geometry | Representative point |
| :--- | :--- |
| `point` | The point itself |
| `trace` | The first vertex |
| `shape` | The average of the ring's vertices, excluding the closing vertex |

The token is the hex cell ID with trailing zeros removed (`S2CellId::ToToken`).
`shared/core` computes it with `s2CellToken(lat, lng)` in
`org.groundplatform.v2.core.geo.s2`. Writers set it alongside every geometry
change, and servers recompute it rather than trusting clients. Because S2 cell
IDs order cells along a space-filling curve, the backend can answer
bounding-box queries (`ListEntitiesRequest.bbox`) with cell-range scans on any
key-value store. This keeps viewport loading and selective mobile sync cheap to
add without a schema migration.

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
