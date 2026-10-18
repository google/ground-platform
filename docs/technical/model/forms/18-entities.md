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

# Entities

ProtoForms provides first-class support for **Entities** and **Datasets** (also
known as Entity Lists). An Entity represents a stateful, uniquely-identified
real-world subject (e.g. a patient, clinic, water point, tree, or asset) that
forms can query, create, update, or synchronize offline.

## Glossary

-   **Entity**: A uniquely-identified domain record that forms can query,
    create, or update.
-   **Dataset**: A collection of Entities sharing the same schema.
-   **Entity Property**: A named attribute belonging to an Entity.
-   **Entity Action**: An operation executed upon submission finalization
    (`CREATE`, `UPDATE`, `UPSERT`).
-   **Offline Entity Sync**: Distributed optimistic concurrency using
    `base_version`, `trunk_version`, and `branch_id`.

## Entity Declaration Schema

Entities are declared using `EntityDeclaration` messages in `ModelDef.entities`
or attached to repeating containers:

```protobuf
syntax = "proto3";

package groundplatform.v2.forms;

message EntityDeclaration {
  // Target dataset name (e.g., "clinics", "water_points").
  string dataset = 1;

  enum EntityAction {
    ENTITY_ACTION_UNSPECIFIED = 0;
    CREATE = 1;
    UPDATE = 2;
    UPSERT = 3;
  }

  // Primary action to perform on form submission.
  EntityAction action = 2;

  // Field reference or expression resolving to the RFC 4122 v4 UUID.
  string id_expression = 3;

  // Expression computing the human-readable entity label.
  string label_expression = 4;

  // Boolean condition expression determining whether the entity operation executes.
  string condition_expression = 5;

  // Offline optimistic locking and multi-branch synchronization metadata.
  EntitySyncMetadata sync_metadata = 6;

  // Explicit mapping of dataset property names to form field paths.
  repeated EntityPropertyMapping property_mappings = 7;
}

message EntitySyncMetadata {
  // Expression resolving to the revision number observed by the client.
  string base_version_expression = 1;

  // Expression resolving to the server-provided trunk version.
  string trunk_version_expression = 2;

  // Expression resolving to the client offline branch UUID.
  string branch_id_expression = 3;
}

message EntityPropertyMapping {
  // Destination property name in the dataset.
  string property_name = 1;

  // Source field path in the primary instance record.
  string source_field_path = 2;
}
```

Alternatively, field bindings in `ModelDef.bindings` can specify `entity_saveto`
directly:

```protobuf
message FieldBinding {
  string field_path = 1;
  DataType type = 2;
  bool read_only = 3;
  string relevant_expression = 4;
  string calculate_expression = 5;
  string constraint_expression = 6;
  string required_expression = 7;
  string constraint_message = 8;
  string required_message = 9;
  string sms_tag = 10;
  string entity_saveto = 11;
}
```

## Example: Entity Creation Form

The following definition creates a new entity in the `clinics` dataset whenever
a facility registration form is completed:

```textproto
form_id: "clinic_registration"
version: "2026090901"
title: "Clinic Registration"

model {
  primary_instance {
    record_schema {
      name: "clinic_registration"
      fields { name: "coordinates" type: TYPE_GEOPOINT }
      fields { name: "facility_name" type: TYPE_STRING }
      fields { name: "clinic_id" type: TYPE_STRING }
    }
  }

  # Generate UUID upon form creation
  actions {
    events: [EVENT_INSTANCE_FIRST_LOAD]
    target_ref: "clinic_id"
    set_value {
      value_expression: "uuid()"
    }
  }

  # Map field values to entity properties
  bindings { field_path: "coordinates" entity_saveto: "geometry" }
  bindings { field_path: "facility_name" entity_saveto: "facility_name" }

  # Declare entity creation
  entities {
    dataset: "clinics"
    action: CREATE
    id_expression: "clinic_id"
    label_expression: "facility_name"
    condition_expression: "true()"
  }
}
```

## Example: Entity Update Form (Offline-Capable)

Forms updating existing entities bind to a secondary dataset instance and supply
offline branching metadata:

```textproto
form_id: "clinic_capacity_update"
version: "2026090902"
title: "Clinic Bed Capacity Update"

model {
  primary_instance {
    record_schema {
      name: "clinic_capacity_update"
      fields { name: "target_clinic" type: TYPE_STRING }
      fields { name: "bed_count" type: TYPE_INT32 }
    }
  }

  # Secondary instance containing existing clinics dataset
  secondary_instances {
    id: "clinics"
    uri: "jr://file-csv/clinics.csv"
  }

  bindings {
    field_path: "bed_count"
    entity_saveto: "bed_count"
  }

  entities {
    dataset: "clinics"
    action: UPDATE
    id_expression: "target_clinic"
    label_expression: "concat(bed_count, ' beds - ', instance('clinics')/root/item[name=target_clinic]/facility_name)"
    sync_metadata {
      base_version_expression: "instance('clinics')/root/item[name=target_clinic]/__version"
      trunk_version_expression: "instance('clinics')/root/item[name=target_clinic]/__trunkVersion"
      branch_id_expression: "instance('clinics')/root/item[name=target_clinic]/__branchId"
    }
  }
}
```

## Referencing Existing Entities

Entity datasets are bound via `secondary_instances`:

-   **Hosted / Database-Backed Runtimes (e.g., Ground)**: `SecondaryInstance.id`
    binds directly to the survey's managed Entity Dataset
    (`EntityDatasetDef`). The client runtime resolves `instance('<dataset>')`
    against local indexed `EntityRecord` rows (which natively include reserved
    system properties `entity_id` / `name` / `__id`, `label`, `revision` /
    `__version`, `geometry`, and custom typed properties).
-   **File-Based XForms Runtimes**: Backends serialize the dataset as a
    CSV attachment (`jr://file-csv/<dataset>.csv`) with `name` (UUID), `label`,
    `__version`, and `geometry` (for geospatial datasets) columns, or as a
    GeoJSON attachment (`jr://file-geojson/<dataset>.geojson`).

## Entities in Repeats and Groups

ProtoForms allows `EntityDeclaration` within repeat blocks
(`RepeatDef.entities`), enabling a single submission to create or update
multiple entities (e.g., registering multiple family members or multiple field
assets within a single interview). Property mappings inside repeated containers
resolve relative to each repeat instance.
