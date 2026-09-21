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

# Maps, Layers, and Geometry Styling

Ground 2.0 surveys configure map visualization (`MapConfig`), ordered map layer
stacks (`LayerDef`), and visual geometry styling (`GeometryStyle`) inside
`SurveyDef.map_config`.

> **Note:** Sensor accuracy thresholds (`GeoConfig`), hardware GPS requirements
> (`appearance`), and proximity validation guardrails (`constraint_expression`)
> are configured on individual questions in ProtoForms (`FormDef`), adhering to
> the ODK XForms specification. See
> [Form Controls and View](docs/model/forms/05-body.md#geospatial-configuration)
> and [Bindings and Field Rules](docs/model/forms/04-bindings.md).

## Map Configuration & Layer Stack (`MapConfig` and `LayerDef`)

`MapConfig` defines the default initial viewport center coordinate, zoom level,
and the ordered stack of map layers (`LayerDef`, rendered in bottom-to-top
z-order):

```protobuf
message MapConfig {
  groundplatform.v2.forms.GeoPoint initial_center = 1;
  double initial_zoom = 2;
  repeated LayerDef layers = 3;
}

message LayerDef {
  string id = 1;
  string label = 2;
  bool visible_by_default = 3;
  GeometryStyle default_style = 4;

  oneof source {
    string entity_dataset_id = 5;
    FormGeometrySource form_geometry = 6;
  }
}

message FormGeometrySource {
  string form_id = 1;
  string field_path = 2;
}
```

## Map Geometry Styling (`GeometryStyle`)

Visual styling for form geometry questions and geospatial entity layers is
defined via `GeometryStyle`:

```protobuf
message GeometryStyle {
  // Hex color code (e.g., "#2e7d32") for markers and outlines.
  string color = 1;

  // Material Symbols icon identifier (e.g., "forest", "agriculture").
  string icon = 2;

  // Stroke width in pixels for line traces and polygon boundaries.
  double stroke_width = 3;

  // Fill opacity (between 0.0 transparent and 1.0 opaque) for closed polygons.
  double fill_opacity = 4;
}
```

### Styling Precedence Cascade

Map layers apply visual styling using the following precedence rules:

1.  **Entity Dataset Layers (`entity_dataset_id`)**:
    *   **Per-Entity Override (Data-Driven)**: If an individual
        `EntityRecord.properties` map contains standard ODK / `simplestyle-spec`
        keys (`marker-color`, `marker-symbol`, `stroke`, `stroke-width`, `fill`,
        `fill-opacity`), those per-entity values take precedence. This supports
        dynamic status coloring (e.g., updating a plot's color via
        `entity_saveto: "marker-color"` upon verification).
    *   **Layer Default Fallback**: Any styling attribute not overridden on the
        individual entity falls back to `LayerDef.default_style`.
2.  **Form Geometry Layers (`form_geometry`)**:
    *   Because form submission records (`SubmissionRecord`) can contain
        multiple geometry questions without per-field style metadata, form
        geometry layers always render using `LayerDef.default_style`.

## Example

```textproto
map_config {
  initial_center { latitude: -0.2827 longitude: 34.7519 }
  initial_zoom: 13.0

  layers {
    id: "layer_plots_dataset"
    label: "Preloaded Smallholder Plots"
    visible_by_default: true
    entity_dataset_id: "plots"
    default_style {
      color: "#34A853"
      stroke_width: 2.0
      fill_opacity: 0.25
    }
  }

  layers {
    id: "layer_recorded_boundaries"
    label: "Surveyed Plot Perimeters"
    visible_by_default: true
    form_geometry {
      form_id: "plot_registration"
      field_path: "plot/boundary"
    }
    default_style {
      color: "#2e7d32"
      icon: "forest"
      stroke_width: 2.5
      fill_opacity: 0.35
    }
  }

  layers {
    id: "layer_access_paths"
    label: "Access Paths"
    visible_by_default: false
    form_geometry {
      form_id: "plot_registration"
      field_path: "plot/access_path"
    }
    default_style {
      color: "#FBBC04"
      stroke_width: 3.0
    }
  }
}
```
