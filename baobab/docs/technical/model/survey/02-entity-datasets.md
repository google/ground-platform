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

# Entity Datasets and Schemas

Entity Datasets (`EntityDatasetDef` in `survey_def.proto`) define preloaded and
field-created XForms Entity Lists (datasets) that enumerators can query, create,
or update during data collection. Ground manages Entity Datasets internally,
which can be populated via CSV or GeoJSON imports.

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

In XForms / ProtoForms XPath expressions (`instance('<id>')/root/item[...]`) and
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

## Generated Datasets (Sample Designs)

A dataset records how its entities were produced in the `generator` oneof:

```protobuf
message EntityDatasetDef {
  // ... fields 1–9 above ...
  oneof generator {
    SamplingDesign sampling = 10;
    ImportProvenance imported = 11;
  }
}
```

*   **`sampling`**: the entities are sample plots produced by the
    deterministic sampling engine in `shared/core`
    (`org.groundplatform.v2.core.sampling`).
*   **`imported`**: the entities came from an uploaded file (`file_name`,
    `format`, `imported_at`, `input_hash`, `feature_count`).

Either way the entities are stored as ordinary `EntityRecord`s with real
geometry. The generator is provenance: it makes the dataset auditable, and lets
organizers regenerate it while no submissions reference it. Nothing recomputes
geometry at runtime.

### Sampling Design (`SamplingDesign`)

| Field | Meaning |
| :--- | :--- |
| `method` | `SYSTEMATIC_GRID`, `SIMPLE_RANDOM`, `STRATIFIED_RANDOM` or `CLUSTER` |
| `area_ref` | `survey_area` (the survey area's parts form one unnamed stratum) or `strata_layer` (a polygon dataset ID plus the property holding the stratum ID) |
| `spacing_m` | Distance between plot centers for grids |
| `count` | Number of plots, or of clusters for `CLUSTER` |
| `min_distance_m` | Minimum distance between plot (or cluster) centers; 0 disables it |
| `allocation`, `custom_counts` | `EQUAL`, `PROPORTIONAL` (to stratum area) or `CUSTOM` counts per stratum ID; counts are rounded with the largest-remainder method so they always sum to `count` |
| `cluster_plots_per_side`, `cluster_plot_spacing_m` | Square pattern of plots around each cluster center; plots outside the area are dropped |
| `plot_shape`, `plot_size_m` | `SQUARE` (side) or `CIRCLE` (diameter) stored as polygons, or `POINT` |
| `shuffle` | Shuffle `sample_order` with a seeded Fisher–Yates shuffle |
| `seed`, `engine_version` | Same inputs, seed and engine version give identical plots on every platform |
| `sub_plot` | `SubPlotDesign`: `NONE`, `CENTER`, `GRID` (`grid_n` × `grid_n`, `spacing_m` apart), `RANDOM` (`count` points inside the plot, seeded per plot) or `USER_DRAWN` (`allowed_geometry_types`) |
| `generated_at`, `input_hash`, `feature_count` | Detect stale designs: a hash mismatch with the current area, strata and parameters means the plots no longer match the design |

How the engine works:

*   Plot centers are laid out in a Lambert azimuthal equal-area projection
    centered on the area, so grids are in true meters near the center and
    random designs are uniform on the ground.
*   Coordinates are rounded to 1e-7° (about 1 cm).
*   A plot belongs to the first stratum whose polygon contains its center.
*   Randomness comes from a pinned SplitMix64 generator, not the platform's
    random API.

### Reserved Plot Properties

Generated plots carry these properties. Uploaded and migrated plots use the same
names, so clients treat all plots alike:

| Property | Content |
| :--- | :--- |
| `plot_id` | Stable plot ID unique within the design (`P000001`, …); the dataset `key_property` |
| `stratum` | Stratum ID; empty for unstratified designs |
| `sample_order` | 1-based visiting order, shuffled when `shuffle` is set |
| `inclusion_weight` | Hectares represented by the plot: stratum area ÷ plots in the stratum |
| `cluster_id` | Cluster ID for `CLUSTER` designs (`C000001`, …); empty otherwise |
| `samples` | Sub-plot sample points in ODK geotrace format (see below) |
| `sample_ids` | Optional space-separated sample IDs, one per point in `samples` |

Plot geometry is a `GeoShape` closed ring: 5 coordinates for squares, and 25
for circles (24 vertices plus the closing one). `POINT` plots use a
`GeoPoint`.

**`samples` encoding**: points separated by `;` with no surrounding spaces. Each
point is exactly four space-separated numbers, `lat lng altitude accuracy`, with
altitude and accuracy set to 0 and at most 7 decimals:

```text
-1.0001234 37.0004321 0 0;-1.0002 37.0005 0 0;-1.0003 37.0006 0 0
```

`SampleEncoding` in `shared/core` writes and reads this format
byte-identically on every platform.

*   `samples` is left out of map tiles and default list responses
    (`ListEntitiesRequest.field_mask`), avoiding a 25× entity record explosion
    while keeping all pre-defined sample points on the plot entity.

### Interpretation Form Template

Desk labels for sample points are **repeat instances** in the interpretation
submission, one per sample point, keyed by `sample_id`. The template uses only
standard ODK XForms constructs, so it behaves the same in Ground, ODK Collect
and Enketo:

| type | name | label | calculation / repeat_count | read_only |
| :--- | :--- | :--- | :--- | :--- |
| select_one_from_file plots.csv | `plot` | Plot | | |
| calculate | `samples` | | `instance('plots')/root/item[name=${plot}]/samples` | |
| calculate | `sample_ids` | | `instance('plots')/root/item[name=${plot}]/sample_ids` | |
| calculate | `sample_count` | | `if(string-length(${samples}) = 0, 0, string-length(${samples}) - string-length(translate(${samples}, ';', '')) + 1)` | |
| begin repeat | `sample` | Sample | repeat_count: `${sample_count}` | |
| calculate | `sample_id` | | `if(string-length(${sample_ids}) = 0, position(..), selected-at(${sample_ids}, position(..) - 1))` | |
| geopoint | `location` | Sample location | `concat(selected-at(translate(${samples}, ';', ' '), 4 * (position(..) - 1)), ' ', selected-at(translate(${samples}, ';', ' '), 4 * (position(..) - 1) + 1), ' 0 0')` | yes |
| select_one land_cover | `land_cover` | Land cover | | |
| end repeat | | | | |

*   **Why `translate()`**: `selected-at()` splits its argument on spaces, while
    geotrace points are separated by `;` and contain spaces themselves.
    `translate(samples, ';', ' ')` flattens the geotrace into one
    space-separated list. Because every point has exactly four numbers, point
    *i* starts at token `4 × (i − 1)`.
*   **Launching from a plot**: when the form is launched from a plot
    (`FormLaunchConfig.target_entity_dataset_id`), Ground is expected to
    pre-fill `plot` with the plot entity's `name`. Interpretation clients
    still have to implement this.
*   **Tests**: the XForms equivalent of this template is
    `InterpretationFormTemplateTest` in `shared/core`. The test checks that the
    shared form engine creates one pre-filled repeat instance per sample, and
    that the form round-trips XML → proto → XML unchanged.

### Client Map Rendering & Active Question Coloring

Once `plot` is set on `FormSession`, the repeat instances `/data/sample[1..M]`
become the single client-side source of truth for both sample geometry and
answers:

*   **Geometry & Selection**: the client renders each repeat instance's first
    spatial field (`location`) on the map. Clicking or box-selecting points on
    the map selects the corresponding repeat indices in `FormSession`. In
    `USER_DRAWN` mode (`no_add_remove = false`, no `repeat_count`), drawing a
    feature on the map appends a repeat instance and populates its spatial
    field, using the same rendering and selection path.
*   **Active Question Swatch Coloring**: sample points are colored according to
    the **currently focused question** inside the repeat (defaulting to the
    first question in the repeat when the plot opens):
    *   **Not relevant (`!fieldState.isRelevant`)**: when a conditional child
        question is focused (e.g. `forest_type` with
        `relevant = "../land_cover = 'tree'"`), sample points where the
        question is not relevant are dimmed and non-selectable.
    *   **Unanswered (`fieldState.isEmpty`)**: rendered in the neutral
        unanswered style.
    *   **Answered Choice (`SELECT_ONE` / `SELECT_MULTIPLE`)**: colored using
        `ResolvedChoiceOption.properties["color"]` (populated from
        `ChoiceItem.properties["color"]` or the `color` column of an
        `<itemset>` secondary instance).
    *   **Answered Input (`CONTROL_INPUT` / `CONTROL_RANGE`)**: colored using a
        single answered accent swatch.

### Sample-to-Plot Aggregation

Each interpreter's pass over a plot is stored as a single `SubmissionRecord`
(`entity_id = plot.entity_id`) containing plot-level fields at the root of
`RecordInstance.data` (`confidence`, `flagged`, `flagged_reason`, and any
plot-level questions) and sample-level answers in
`fields["sample"].repeat_value.nodes`:

*   **In-Form XPath Rollups**: real-time percentages, cross-question validation
    rules (e.g. sum-to-100% or incompatible plot/sample answers), and
    single-interpreter `entity_saveto` bindings use standard XPath 1.0 over
    `/data/sample` (e.g.
    `round(100 * count(/data/sample[land_cover = 'tree']) div /data/sample_count, 2)`).
*   **Schema-Driven Plot Summary Export**: for plot-level CSV exports without
    requiring per-option calculated fields in `FormDef`, the aggregator walks
    the controls inside `RepeatDef`:
    *   `SELECT_ONE` / `SELECT_MULTIPLE`: emits `<question>:<option>` columns
        with `100 × matchCount ÷ sampleCount`.
    *   Numeric inputs (`TYPE_INT32`, `TYPE_DOUBLE`): emits plot summary
        statistics (`mean`, `sum`, `min`, `max`).
    *   Text inputs (`TYPE_STRING`): joins distinct non-empty answers with `;`.
*   **Multi-Interpreter Consensus (`K > 1` submissions per plot)**: groups
    repeat nodes across non-quarantined `SubmissionRecord`s for the plot by
    `sample_id`, computes the modal answer per sample question (or `null` on a
    tie) and disagreement rate, and rolls the consensus sample labels up to the
    plot's canonical summary and `EntityRecord.properties["status"]`.

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
