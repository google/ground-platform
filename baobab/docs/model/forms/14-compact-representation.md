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

# Compact Record Representations

ProtoForms provides two complementary compact representation mechanisms for
resource-constrained, offline, and remote telemetry environments:

1.  **Protocol Buffer Binary Wire Format** (default for IP network transport and
    storage)
2.  **Compact SMS / Delimited Text Representation** (for cellular SMS, satellite
    messaging, or USSD channels)

> **Note on Client Compatibility:** Clients and form editors are expected to
> preserve all compact representation metadata (`sms_prefix`, `sms_delimiter`,
> and `sms_tag` bindings) for round-trip compatibility with ODK Forms, even if
> the client runtime does not support SMS or low-bandwidth text serialization.

## 1. Protocol Buffer Binary Wire Format

Unlike verbose XML or JSON representations, Protocol Buffers serialize records
directly into a compact binary wire format using:

-   **Varints**: Variable-length integers (1-10 bytes depending on magnitude).
-   **Zero Overhead for Default/Empty Fields**: Unpopulated or irrelevant fields
    are omitted entirely from the binary stream.
-   **Packed Repeated Fields**: Repeated numeric and enum values are tightly
    packed without per-element tag overhead.
-   **Native Data Types**: Booleans consume 1 byte; integers, floats,
    timestamps, and coordinates are encoded numerically rather than as UTF-8
    string numerals.

This binary encoding typically reduces transmission sizes by **75% to 90%**
compared to standard ODK XForms XML documents without needing compression.

## 2. SMS and Low-Bandwidth Text Representation

For environments where only SMS (140 bytes per message) or satellite text bursts
are available, ProtoForms defines a deterministic text serialization configured
via `RecordSchema` and `FieldBinding`.

### Configuration in Schema and Bindings

```protobuf
message RecordSchema {
  string name = 1;
  string title = 2;
  repeated FieldDefinition fields = 3;

  // Optional SMS command prefix (e.g. "hh" for household).
  string sms_prefix = 4;

  // Delimiter separating tags and values (defaults to single space ' ' if empty).
  string sms_delimiter = 5;
}

message FieldBinding {
  string field_path = 1;

  // Short alphanumeric tag for compact text representation (e.g. "fn", "ln").
  string sms_tag = 10;
}
```

### Example Form Definition

```textproto
model {
  primary_instance {
    record_schema {
      name: "household"
      sms_prefix: "hh"
      sms_delimiter: "+"
      fields { name: "instance_id" type: TYPE_STRING }
      fields { name: "first_name" type: TYPE_STRING }
      fields { name: "last_name" type: TYPE_STRING }
      fields { name: "age" type: TYPE_INT32 }
    }
  }
  bindings { field_path: "first_name" sms_tag: "fn" }
  bindings { field_path: "last_name" sms_tag: "ln" }
  bindings { field_path: "age" sms_tag: "age" }
}
```

### Text Serialization Rules

1.  **Prefix**: Every serialized record string begins with the configured
    `sms_prefix`.
2.  **Tagged Fields**: Only fields that have an `sms_tag` configured and contain
    a non-empty, valid value are serialized. Fields without `sms_tag` are
    omitted.
3.  **Delimiter**: Components (`prefix`, `tag`, `value`) are separated by
    `sms_delimiter`.
4.  **Escaping**: If the delimiter character appears inside a question value, it
    is escaped with a preceding backslash (`\+`).
5.  **Relevancy**: Non-relevant or empty fields are skipped entirely.

### Examples

Record with only family name:

```
hh+ln+Rivera
```

Record with given and family name:

```
hh+fn+Elena+ln+Rivera
```
