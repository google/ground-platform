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

# Submission

The `SubmissionConfig` message inside `ModelDef` instructs the client runtime
how to handle and dispatch finalized form records.

> **Note on Client Compatibility:** Clients and form editors are expected to
> preserve all submission metadata, including `base64_rsa_public_key`, for
> round-trip compatibility with XForms. Collect One does not encrypt submission
> payloads; it relies on secure wire transport (TLS/HTTPS or gRPC).

## `SubmissionConfig` Message Definition

```protobuf
message SubmissionConfig {
  // Optional custom endpoint URL or gRPC service address to send submissions to.
  string action_url = 1;

  // Transmission method (e.g., "POST", "GRPC").
  string method = 2;

  // XForms base64RsaPublicKey attribute, preserved for round-trip fidelity only.
  string base64_rsa_public_key = 3;

  // If true, finalized records are queued and transmitted automatically upon network connectivity.
  bool auto_send = 4;

  // If true, records and media attachments are immediately removed from client storage after successful submission.
  bool auto_delete = 5;

  // If true, clients allow data collectors to reopen and edit finalized records before dispatch.
  bool client_editable = 6;
}
```

## Submission Configuration Fields

| Field                   | XForms XML Equivalent | Description                |
| ----------------------- | --------------------- | -------------------------- |
| `action_url`            | `action`              | Custom URL or gRPC service |
:                         :                       : destination for            :
:                         :                       : submissions.               :
| `method`                | `method`              | HTTP method (`POST`) or    |
:                         :                       : protocol identifier        :
:                         :                       : (`GRPC`).                  :
| `base64_rsa_public_key` | `base64RsaPublicKey`  | Base64 RSA public key.     |
:                         :                       : Preserved for round-trip   :
:                         :                       : fidelity; Collect One does :
:                         :                       : not encrypt submissions.   :
| `auto_send`             | `orx:auto-send`       | Boolean flag to            |
:                         :                       : automatically send         :
:                         :                       : finalized records when an  :
:                         :                       : internet connection is     :
:                         :                       : established.               :
| `auto_delete`           | `orx:auto-delete`     | Boolean flag to delete     |
:                         :                       : local records and          :
:                         :                       : attachments upon verified  :
:                         :                       : submission.                :
| `client_editable`       | `odk:client-editable` | Boolean flag allowing or   |
:                         :                       : forbidding                 :
:                         :                       : post-finalization record   :
:                         :                       : editing.                   :

## Submission Record Format (`RecordInstance`)

Finalized form submissions are represented using the code-generated
`openforis.collectone.v1.forms.RecordInstance` message. Using `RecordInstance` enables
clients and servers to read, validate, and serialize any form's hierarchical
data structure (`RecordNode`, `FieldValue`, and `TypedValue`) using standard
generated protobuf code without relying on protobuf reflection or dynamic schema
compilation.

```protobuf
message RecordInstance {
  string form_id = 1;
  string form_version = 2;
  RecordMetadata metadata = 3;
  RecordNode data = 4;
  AuditLog audit_log = 5;
}
```

## Example

```textproto
model {
  submission {
    action_url: "https://forms.example.org/api/v1/submissions"
    method: "POST"
    base64_rsa_public_key: "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."
    auto_send: true
    auto_delete: false
    client_editable: true
  }
}
```

