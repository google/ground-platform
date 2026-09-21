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

# Form Controls and View

The `view` (`ViewDef`) message defines how questions and groups are rendered in
the client UI, specifying widget types, appearance styles, labels, hints, and
selection choices.

```textproto
view {
  components {
    control {
      field_path: "given_name"
      control_type: INPUT
      label {
        text: "Enter given name:"
      }
    }
  }
  components {
    control {
      field_path: "family_name"
      control_type: INPUT
      label {
        text: "Enter family name:"
      }
    }
  }
  components {
    control {
      field_path: "years_active"
      control_type: INPUT
      label {
        text: "Years active:"
      }
    }
  }
}
```

## Form Controls

The `control_type` enum specifies the interactive widget used to gather input:

| `ControlType`     | Description                                           |
| ----------------- | ----------------------------------------------------- |
| `INPUT`           | Text, integer, decimal, date, time, and geo entry     |
:                   : fields.                                               :
| `SELECT_ONE`      | Single-select choice list (radio buttons, dropdown,   |
:                   : quick select).                                        :
| `SELECT_MULTIPLE` | Multi-select choice list (checkboxes).                |
| `UPLOAD`          | Media attachment capture (image, audio, video, file). |
| `TRIGGER`         | Confirmation button or checkbox (records `"OK"` when  |
:                   : confirmed).                                           :
| `RANGE`           | Numeric slider widget with bounds (`start`, `end`,    |
:                   : `step`).                                              :
| `RANK`            | Ordered ranking widget for sorting choices.           |

## UI Container Elements

UI components are organized recursively via `ComponentDef`:

| Element   | Message      | Description                                     |
| --------- | ------------ | ----------------------------------------------- |
| `control` | `ControlDef` | Individual input widget tied to a `field_path`. |
| `group`   | `GroupDef`   | Visual container grouping related controls or   |
:           :              : sub-groups (see [Groups](#groups)).             :
| `repeat`  | `RepeatDef`  | Dynamic repeating section (see                  |
:           :              : [Repeats](#repeats)).                           :

## Control Fields

Within each `ControlDef`:

| Field           | Type                  | Description                      |
| --------------- | --------------------- | -------------------------------- |
| `field_path`    | `string`              | Target field path within the     |
:                 :                       : record schema.                   :
| `control_type`  | `ControlType`         | Widget type (`INPUT`,            |
:                 :                       : `SELECT_ONE`, etc.).             :
| `label`         | `LocalizedString`     | Primary prompt text, optionally  |
:                 :                       : localized.                       :
| `hint`          | `LocalizedString`     | Secondary guidance text,         |
:                 :                       : optionally localized.            :
| `appearance`    | `string`              | Space-separated widget styling   |
:                 :                       : cues (e.g., `"quick"`,           :
:                 :                       : `"likert"`, `"image-map"`).      :
| `items`         | repeated `SelectItem` | Inline list of choices for       |
:                 :                       : selection or ranking.            :
| `itemset`       | `ItemSetDef`          | Dynamic selection choices        |
:                 :                       : populated via a [Secondary       :
:                 :                       : Instance](#secondary-instances). :
| `range_config`  | `RangeConfig`         | Bounds and tick configuration    |
:                 :                       : for `RANGE` controls.            :
| `upload_config` | `UploadConfig`        | Media type and file filter rules |
:                 :                       : for `UPLOAD` controls.           :
| `geo_config`    | `GeoConfig`           | Auto-acceptance accuracy         |
:                 :                       : thresholds for geopoint          :
:                 :                       : questions.                       :
| `rows`          | `int32`               | Minimum text rows displayed for  |
:                 :                       : multi-line string inputs.        :
| `autoplay`      | `bool`                | Automatically plays associated   |
:                 :                       : audio or video media on display. :

The following snippet illustrates a group containing a ranking widget populated
from a dynamic itemset:

```textproto
components {
  group {
    field_path: "region"
    label {
      text: "Stations"
    }
    children {
      components {
        control {
          field_path: "region/stations"
          control_type: RANK
          label {
            text: "Order stations by priority"
          }
          hint {
            text: "Arrange stations from highest to lowest priority."
            outputs {
              placeholder: "{region_code}"
              expr: "/data/region/code"
            }
          }
          itemset {
            dataset_query: "randomize(instance('stations')/item[region = /data/region/code])"
            value_ref: "code"
            label_ref: "station_name"
          }
        }
      }
    }
  }
}
```

### Range Configuration

For `RANGE` controls, `RangeConfig` defines the slider behavior:

```protobuf
message RangeConfig {
  double start = 1;  // Lower bound [required]
  double end = 2;  // Upper bound [required]
  double step = 3;  // Value increment [required]
  double tick_interval = 4;  // Spacing between slider tick marks
  double placeholder = 5;  // Position when answer is blank
  repeated SelectItem tick_labels = 6;  // Optional labeled ticks
}
```

### Geospatial Configuration

For geospatial capture controls (`GEOPOINT`, `GEOTRACE`, `GEOSHAPE`),
`GeoConfig` defines sensor auto-acceptance accuracy, UI uncertainty warning
thresholds, and mock provider settings corresponding to ODK XForms attributes:

```protobuf
message GeoConfig {
  // Target horizontal accuracy threshold in meters for auto-acceptance
  // (ODK `accuracyThreshold` attribute, XLSForm `capture-accuracy` / `body::accuracyThreshold`).
  double accuracy_threshold_meters = 1;

  // Horizontal accuracy threshold in meters above which the UI displays an
  // uncertainty warning (ODK `unacceptableAccuracyThreshold` attribute, XLSForm
  // `warning-accuracy` / `body::unacceptableAccuracyThreshold`).
  double warning_threshold_meters = 2;

  // Whether mock location provider readings are accepted (XLSForm
  // `allow-mock-accuracy=true`).
  bool allow_mock_accuracy = 3;
}
```

```textproto
components {
  control {
    field_ref: "plot/boundary_point"
    type: CONTROL_INPUT
    label {
      text: "Record parcel boundary vertex"
    }
    geo_config {
      accuracy_threshold_meters: 5.0
      warning_threshold_meters: 20.0
      allow_mock_accuracy: false
    }
  }
}
```

#### Enforcing Hard Constraints, Proximity, and Hardware GPS

While `GeoConfig` governs the interactive client capture dialog (auto-acceptance
and warnings), hard validation gates and proximity rules are enforced using
standard ODK XForms bindings and appearances:

*   **Hard GPS Accuracy Guardrail**: Configured in
    `FieldBinding.constraint_expression` using the XPath `selected-at()`
    function: `selected-at(., 3) <= 5.0` (with a user-friendly
    `constraint_message`).
*   **Proximity Guardrail**: Configured in `FieldBinding.constraint_expression`
    using the ODK XPath `distance()` function: `distance(.,
    /data/target_entity_location) <= 15.0`.
*   **Hardware GPS Requirement**: Controlled via `ControlDef.appearance` across
    `GEOPOINT`, `GEOTRACE`, and `GEOSHAPE`. Omitting `placement-map` forces the
    client to use hardware GNSS sensor readings exclusively (requiring live GPS
    fixes for points and walked GNSS tracking for lines/polygons), disabling
    manual map reticle and vertex placement.

## Appearances

The visual styling and layout of form controls and groups are customized using
the `appearance` string. Standard XLSForm appearance keywords (such as
`compact`, `quick`, `minimal`, `table-list`, `bearing`, `image-map`) are fully
supported. Appearance attributes can also invoke
[external applications](#declaring-external-application).
