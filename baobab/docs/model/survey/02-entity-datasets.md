# Entity Datasets and Schemas

Entity Datasets (`EntityDatasetDef` in `survey_def.proto`) define preloaded and
field-created ODK Entity Lists (datasets) that enumerators can query, create, or
update during data collection. Ground manages Entity Datasets internally, which
can be populated via CSV or GeoJSON imports.

## Entity Classifications (`EntityType`)

Ground 2.0 distinguishes between two entity dataset classifications:

1.  **`TABULAR`**: Relational, non-spatial records (such as registries of
    farmers, cooperative member rosters, or species taxonomies). Queried using
    `select_one_from_file` and offline `pulldata()`.
2.  **`GEOSPATIAL`**: Relational records tied to spatial geometries (points,
    polylines, polygons). Rendered directly on the web and mobile map as
    interactive vector features with searchable attribute cards.

## Schema Definition (`EntityDatasetDef` and `EntityPropertyDefinition`)

Each `EntityDatasetDef` specifies its unique key property (`key_property`),
human-readable label property (`label_property`), expected spatial geometry type
(`geometry_type` for `GEOSPATIAL` datasets), whether field enumerators can
register new entities on mobile (`field_creation_enabled`), and the typed
property definitions (`EntityPropertyDefinition`):

```protobuf
message EntityDatasetDef {
  string id = 1;
  string display_name = 2;
  string description = 3;
  EntityType type = 4;
  string key_property = 5;
  string label_property = 6;
  bool field_creation_enabled = 7;
  repeated EntityPropertyDefinition properties = 8;
  groundplatform.v2.forms.DataType geometry_type = 9;
}

message EntityPropertyDefinition {
  string name = 1;
  groundplatform.v2.forms.DataType type = 2;
  string label = 3;
  bool required = 4;
}
```

### Reserved System Properties

In ODK / ProtoForms XPath expressions (`instance('<id>')/root/item[...]`) and
entity bindings (`entity_saveto`), every entity automatically exposes the
following reserved system properties mapped from `EntityRecord`:

*   **`"name"` / `"__id"`**: Entity UUID (`EntityRecord.entity_id`)
*   **`"label"`**: Human-readable label (`EntityRecord.label`)
*   **`"__version"`**: Revision number (`EntityRecord.revision`)
*   **`"geometry"`**: Spatial feature geometry (`EntityRecord.geometry`, for
    `GEOSPATIAL` datasets)

> **Note:** Map layer visualization, ordering, visibility, and styling
> (`GeometryStyle`) for geospatial entity datasets are configured in
> `SurveyDef.map_config.layers` via `LayerDef`. See
> [Maps and Layer Styling](docs/model/survey/03-maps.md).

## Example: Geospatial Plot Dataset

```textproto
entity_datasets {
  id: "plots"
  display_name: "Smallholder Plots"
  description: "Registered agroforestry plots with verified perimeters."
  type: GEOSPATIAL
  key_property: "plot_id"
  label_property: "farmer_name"
  geometry_type: TYPE_GEOSHAPE
  field_creation_enabled: true
  properties {
    name: "plot_id"
    type: TYPE_STRING
    label: "Plot ID"
    required: true
  }
  properties {
    name: "farmer_name"
    type: TYPE_STRING
    label: "Farmer Name"
    required: true
  }
  properties {
    name: "area_hectares"
    type: TYPE_DOUBLE
    label: "Area (ha)"
    required: false
  }
}
```
