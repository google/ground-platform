# Submission

The `SubmissionConfig` message inside `ModelDef` instructs the client runtime
how to handle, encrypt, and dispatch finalized form records.

> **Note on Client Compatibility:** Clients and form editors are expected to
> preserve all submission metadata—including `base64_rsa_public_key`—for
> round-trip compatibility with ODK Forms, even if the client relies on secure
> wire transport (TLS/HTTPS or gRPC) rather than payload-level encryption.

## `SubmissionConfig` Message Definition

```protobuf
message SubmissionConfig {
  // Optional custom endpoint URL or gRPC service address to send submissions to.
  string action_url = 1;

  // Transmission method (e.g., "POST", "GRPC").
  string method = 2;

  // Base64-encoded RSA public key required to enable asymmetric payload encryption.
  string base64_rsa_public_key = 3;

  // If true, finalized records are queued and transmitted automatically upon network connectivity.
  bool auto_send = 4;

  // If true, records and media attachments are immediately removed from client storage after successful submission.
  bool auto_delete = 5;

  // If true, clients allow enumerators to reopen and edit finalized records before dispatch.
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
| `base64_rsa_public_key` | `base64RsaPublicKey`  | Base64 RSA public key for  |
:                         :                       : asymmetric key             :
:                         :                       : encapsulation. Enabling    :
:                         :                       : this encrypts all records  :
:                         :                       : and media.                 :
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
`groundplatform.v2.forms.RecordInstance` message. Using `RecordInstance` enables
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

## Encryption

ProtoForms supports end-to-end asymmetric encryption to ensure records remain
confidential in transit and at rest on intermediary servers. When
`base64_rsa_public_key` is configured, the client generates a unique symmetric
key (AES-256) per submission, encrypts the serialized protobuf record and
attached media files, and encapsulates the symmetric key using the public RSA
key in an `EncryptedSubmissionManifest`.

Full details on encryption protocols and manifest schemas can be found in the
[Encryption](#encryption) section.
