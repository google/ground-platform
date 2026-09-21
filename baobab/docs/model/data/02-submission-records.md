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

# Submission Records

`SubmissionRecord` (defined in `submission_record.proto`) represents a
finalized, versioned form submission transaction in Ground 2.0. It links a form
submission to its parent `SurveyDef`, specific `FormDef` version, and optional
target `EntityRecord`.

## Payload Variants (`oneof payload`)

To support both reflection-free runtime inspection and end-to-end encrypted ODK
workflows, `SubmissionRecord` defines a `oneof payload` supporting three
representations:

1.  **`groundplatform.v2.forms.RecordInstance record`**: A code-generated
    hierarchical data tree (`RecordNode`, `FieldValue`, `TypedValue`) that
    mobile, web, and server runtimes can traverse without protobuf reflection.
2.  **`bytes raw_payload`**: Raw unencrypted protobuf primary record bytes.
3.  **`groundplatform.v2.forms.EncryptedSubmissionManifest
    encrypted_manifest`**: An asymmetrically encrypted submission manifest
    envelope.

```protobuf
message SubmissionRecord {
  string submission_id = 1;
  string survey_id = 2;
  string form_id = 3;
  string form_version = 4;
  string entity_id = 5;
  int64 revision = 6;
  groundplatform.v2.forms.RecordMetadata metadata = 7;

  oneof payload {
    groundplatform.v2.forms.RecordInstance record = 8;
    bytes raw_payload = 9;
    groundplatform.v2.forms.EncryptedSubmissionManifest encrypted_manifest = 10;
  }

  AuditInfo audit_info = 11;
  bool is_deleted = 12;
}
```

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
