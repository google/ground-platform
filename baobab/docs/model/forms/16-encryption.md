# End-to-End Encryption

## Introduction

This specification defines the cryptographic protocols and manifest structures
used by ProtoForms clients to encrypt finalized form submissions. End-to-end
encryption guarantees that data remains confidential while in transit and at
rest on intermediary collection servers, ensuring only holders of the private
RSA key can decrypt records and attachments.

> **Note on Client Compatibility:** Clients and form editors are expected to
> preserve encryption metadata (such as
> `SubmissionConfig.base64_rsa_public_key`) for round-trip compatibility with
> ODK Forms, even if the client relies on transport-layer security (TLS/HTTPS or
> gRPC) or does not support payload-level encryption.

## Encrypted Submission Manifest

When encryption is enabled (via `SubmissionConfig.base64_rsa_public_key`), the
client produces an `EncryptedSubmissionManifest` protocol buffer representing
the submission envelope:

```protobuf
syntax = "proto3";

package groundplatform.v2.forms;

message EncryptedSubmissionManifest {
  // Form identifier matching FormDef.form_id.
  string form_id = 1;

  // Form version string.
  string version = 2;

  // Unique UUID identifying this submission record.
  string instance_id = 3;

  // Ephemeral 256-bit AES symmetric key encrypted with the recipient's RSA public key.
  bytes base64_encrypted_key = 4;

  // Encrypted primary ProtoForms record payload (or reference to uploaded encrypted binary).
  EncryptedFile primary_record = 5;

  // List of encrypted media attachments (photos, audio, signatures).
  repeated EncryptedFile media_attachments = 6;

  // Cryptographic signature verifying manifest integrity and non-tampering.
  bytes base64_encrypted_signature = 7;
}

message EncryptedFile {
  // Original relative filename (e.g., "submission.pb.enc", "photo1.jpg.enc").
  string filename = 1;

  // MIME content type of the underlying unencrypted file.
  string content_type = 2;

  // MD5 or SHA-256 checksum of the unencrypted file.
  string unencrypted_checksum = 3;

  // Optional direct ciphertext bytes (if sending in a single protobuf payload).
  bytes ciphertext = 4;
}
```

## Manifest Example (Text Format)

```textproto
form_id: "myform"
version: "2026090901"
instance_id: "uuid:5b9cf8d1-106f-4004-844f-c072d76762ed"
base64_encrypted_key: "sHXUut13/res3S3uJkwgfhABOc74aXGnCTxTcRTplS9k..."
primary_record {
  filename: "submission.pb.enc"
  content_type: "application/x-protobuf"
}
media_attachments {
  filename: "myimage.jpg.enc"
  content_type: "image/jpeg"
}
media_attachments {
  filename: "myaudio.mp3.enc"
  content_type: "audio/mp3"
}
base64_encrypted_signature: "OU7rbZl0uFy7xv/HnSl1juVrdf2fQpzcfjwetgl+wse..."
```

## Cryptographic Algorithms

### 1. Payload Encryption (Symmetric)

The primary record binary (`.pb`) and all media attachments are encrypted using
an ephemeral 256-bit AES key generated at random for each submission:

-   **Legacy Compatibility Mode**: `AES/CFB/PKCS7Padding` with sequential
    initialization vectors incremented per attached file (compatible with ODK
    Aggregate / ODK Central / Briefcase).
-   **Modern Mode**: `AES-256-GCM` authenticated encryption with independent
    96-bit random nonces per attachment.

### 2. Key Encapsulation (Asymmetric)

The ephemeral 256-bit AES key is wrapped with the recipient's RSA public key via
**RSA-OAEP** with SHA-256 and MGF1 padding
(`RSA/ECB/OAEPWithSHA-256AndMGF1Padding`).

Once all payloads are encrypted and the manifest has been constructed, the
cleartext symmetric key is wiped from memory.

### 3. Integrity Signature

To verify that the encrypted submission has not been altered or truncated in
transit:

```
concatenate lines separated by newline ("\n"):
  - form_id
  - version
  - base64_encrypted_key
  - instance_id
  - for every media attachment and primary record in sequence:
      filename + "::" + hex_md5(unencrypted_bytes)
append trailing "\n"
calculate the MD5 hash of the concatenated string
encrypt the resulting MD5 digest using RSA-OAEP with the public RSA key
```
