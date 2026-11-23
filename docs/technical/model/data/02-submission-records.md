<!--
  Copyright 2026 The Collect One Authors.

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

# Submission Records

`SubmissionRecord` (defined in `submission_record.proto`) represents a
finalized, versioned form submission transaction in Open Foris Collect One. It links a form
submission to its parent `SurveyDef`, specific `FormDef` version, and optional
target `EntityRecord`.

## Payload Variants (`oneof payload`)

To support reflection-free runtime inspection as well as opaque transport,
`SubmissionRecord` defines a `oneof payload` supporting two representations:

1.  **`openforis.collectone.v1.forms.RecordInstance record`**: A code-generated
    hierarchical data tree (`RecordNode`, `FieldValue`, `TypedValue`) that
    mobile, web, and server runtimes can traverse without protobuf reflection.
2.  **`bytes raw_payload`**: Serialized protobuf primary record bytes.

```protobuf
message SubmissionRecord {
  string submission_id = 1;
  string survey_id = 2;
  string form_id = 3;
  string form_version = 4;
  string entity_id = 5;
  int64 revision = 6;
  openforis.collectone.v1.forms.RecordMetadata metadata = 7;

  oneof payload {
    openforis.collectone.v1.forms.RecordInstance record = 8;
    bytes raw_payload = 9;
  }

  AuditInfo audit_info = 11;
  bool is_deleted = 12;
  google.protobuf.Timestamp commit_timestamp = 13;
  // …
  google.protobuf.Timestamp client_updated_at = 28;
  google.protobuf.Timestamp server_updated_at = 29;
}
```

## Update Times

A submission records when it was last updated twice, once per clock:

| Field | Set by | Meaning |
| :--- | :--- | :--- |
| `client_updated_at` | The client, on every save | When the collector saved the submission, in the device clock |
| `server_updated_at` | The server, on every accepted `SubmissionMutation` | When the server accepted the last change, in the server clock |
| `commit_timestamp` | The store, on every write | Sync bookkeeping, not stored in the record (see [Sync Engine](../../sync-engine.md)) |

`server_updated_at` is server-owned: the server overwrites whatever a client
sends, and upsert merges ignore it.

Clients show a submission's time as `client_updated_at`, because that is when
the work happened even if the upload came hours later, and fall back to
`server_updated_at`. Rows written before either field existed have neither;
clients fall back to their `commit_timestamp`, and show no time when that is
missing too. The text is the UTC minute (`2026-09-24 08:15 UTC`), formatted on
the client by `formatSubmissionTime` in `:shared:app:domain`.

The display label these times replace, `timestamp_label` (field 20, and field
11 of `SubmissionGeometryPolygonDef`), is reserved: proto3 skips it in rows
that still carry it.

## Example: `SubmissionRecord` Embedding `RecordInstance`

```textproto
submission_id: "uuid:5b9cf8d1-106f-4004-844f-c072d76762ed"
survey_id: "550e8400-e29b-41d4-a716-446655440000"
form_id: "plot_registration"
form_version: "2026091401"
entity_id: "ent-plot-7721"
revision: 1
metadata {
  instance_id: "uuid:5b9cf8d1-106f-4004-844f-c072d76762ed"
  start_time { seconds: 1789351000 }
  end_time { seconds: 1789351420 }
  device_id: "android-install-99182"
}
record {
  form_id: "plot_registration"
  form_version: "2026091401"
  data {
    fields {
      key: "farmer_id"
      value { scalar_value { string_value: "FRM-00412" } }
    }
    fields {
      key: "shade_tree_count"
      value { scalar_value { int32_value: 28 } }
    }
  }
}
audit_info {
  created_by: "uid_112233445"
  create_time { seconds: 1789351425 }
}
is_deleted: false
```
