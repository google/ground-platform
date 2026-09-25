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

# Survey Structure

The top-level `SurveyDef` message (defined in `survey_def.proto`) is the root
organizational container in Ground 2.0. It encapsulates one or more ProtoForms
form definitions (`groundplatform.v2.forms.FormDef`), lookup tables, map
configurations, and sharing policies.

## Lifecycle States (`SurveyState`)

A survey transitions through five lifecycle states:

| State       | Description                                                                                     |
| ----------- | ----------------------------------------------------------------------------------------------- |
| `DRAFT`     | Staged in draft mode for creation, refinement, and testing without affecting operations.        |
| `PUBLISHED` | Published release active for mobile synchronization and field/desk data collection.             |
| `CLOSED`    | Closed survey: collection ended; submissions and entities are read-only.                        |
| `ARCHIVED`  | Retired survey no longer active for new field data collection.                                  |
| `DELETED`   | Deleted survey, removed from user interfaces.                                                   |

## Sharing Policies (`SharingPolicy`) and Visibility (`PeerDataVisibility`)

Survey organizers control general access via `SharingPolicy` (mirroring the
Google Drive sharing model) and enumerator peer visibility via
`PeerDataVisibility`:

*   **`SharingPolicy`**:
    *   `RESTRICTED`: Only users explicitly invited in `SurveyAcl` can access
        the survey.
    *   `ANYONE_WITH_LINK`: Anyone possessing the survey link or QR code can
        open the survey and collect data.
    *   `PUBLIC`: Discoverable in the public directory; anyone can collect data.
*   **`PeerDataVisibility`**:
    *   `ALL`: Field collectors can view entities and submissions collected by
        all peers on the map to coordinate coverage and avoid duplicate
        registrations.
    *   `OWN_ONLY`: Field collectors can only view their own collected
        submissions and entities to preserve respondent confidentiality.

## Form Launch & Multilingual CTA Configuration (`FormLaunchConfig`)

While XForms (`FormDef`) defines a single static `<h:title>` and has no
native concept of a data-collection launch button, Ground 2.0 layers
survey-specific form entry points and multilingual Call-to-Action (CTA) labels
onto `SurveyDef` via `FormLaunchConfig` and `SurveyDef.translations`:

```protobuf
message FormLaunchConfig {
  // Target form identifier (`FormDef.form_id`).
  string form_id = 1;

  // Multilingual label for the CTA button used to start collecting data
  // (e.g., "Register Plot", "Record Harvest"). References `SurveyDef.translations`
  // via `LabelDef.text_id`, or falls back to `LabelDef.text` / `FormDef.title`.
  groundplatform.v2.forms.LabelDef cta_label = 2;

  // Optional Material Symbols or vector icon identifier for the CTA button.
  string cta_icon = 3;

  // Optional: Binds this CTA to the bottom sheet of entities in the specified
  // Entity Dataset (`EntityDatasetDef.id`), pre-linking new submissions.
  string target_entity_dataset_id = 4;
}
```

## Complete `SurveyDef` Example

Below is an example of a complete `SurveyDef` in Protocol Buffer text format
(`textproto`):

```textproto
survey_id: "550e8400-e29b-41d4-a716-446655440000"
title: "Western Kenya Agroforestry & Shade Tree Census"
description: "Field census of smallholder agroforestry plots and shade tree survival."
organization_id: "org-kenya-forestry-01"
owner_id: "uid_987654321"
state: PUBLISHED
version: "2026091401"
sharing_policy: RESTRICTED
peer_data_visibility: ALL
default_language: "en"
supported_languages: "en"
supported_languages: "sw"

translations {
  languages {
    language: "en"
    is_default: true
    strings {
      key: "register_plot_cta"
      value { value: "Register New Plot" }
    }
  }
  languages {
    language: "sw"
    strings {
      key: "register_plot_cta"
      value { value: "Sajili Shamba Jipya" }
    }
  }
}

forms {
  form_id: "plot_registration"
  title: "Plot Registration"
  version: "2026091401"
  model {
    primary_instance {
      record_schema {
        name: "plot"
        fields { name: "farmer_id" type: TYPE_STRING }
        fields { name: "boundary" type: TYPE_GEOSHAPE }
      }
    }
  }
}

form_launch_configs {
  form_id: "plot_registration"
  cta_label {
    text_id: "register_plot_cta"
    text: "Register New Plot"
  }
  cta_icon: "add_location_alt"
}

entity_datasets {
  id: "cooperatives"
  display_name: "Farmer Cooperatives"
  type: TABULAR
  key_property: "coop_id"
  label_property: "coop_name"
}

map_config {
  initial_center { latitude: -0.2827 longitude: 34.7519 }
  initial_zoom: 12.5
}

audit_info {
  created_by: "uid_987654321"
  create_time { seconds: 1789344000 }
}
```
