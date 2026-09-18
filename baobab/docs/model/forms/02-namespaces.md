# Packages and Extensions

In Protocol Buffers, package declarations and typed imports replace XML
namespaces to prevent naming collisions and manage modularity.

## Package Structure

The core ProtoForms schema is defined in the `groundplatform.v2.forms` package
and organized into modular protocol buffer files corresponding to each top-level
entity:

*   `form_def.proto`: Defines `FormDef` (the root form definition message) and
    all nested schema, binding, translation, action, and UI presentation
    structures (`ModelDef`, `ViewDef`, `ControlDef`, etc.).
*   `record_instance.proto`: Defines `RecordInstance` (the root submission
    record message), `RecordMetadata`, and the reflection-free hierarchical data
    nodes (`RecordNode`, `RecordNodeList`, `FieldValue`, `TypedValueList`).
*   `encrypted_submission.proto`: Defines `EncryptedSubmissionManifest` and
    `EncryptedFile` envelopes for end-to-end encrypted submissions.
*   `audit_log.proto`: Defines `AuditLog`, `AuditEvent`, and `AuditConfig` for
    enumerator interaction telemetry and location breadcrumbs.
*   `types.proto`: Defines shared primitive data types (`DataType`), geospatial
    geometries (`GeoPoint`, `GeoTrace`, `GeoShape`), and literal value
    containers (`TypedValue`).

```protobuf
syntax = "proto3";

package groundplatform.v2.forms;
```

Standard types are imported from canonical protobuf repositories rather than
ad-hoc schema URLs:

*   `google/protobuf/timestamp.proto` for UTC timestamps and date-times.
*   `google/type/date.proto` for calendar dates.
*   `google/type/timeofday.proto` for time values without dates.

## Unification of Legacy Namespaces

In legacy XML XForms, multiple namespaces were required due to historical
evolution (`http://openrosa.org/javarosa` with prefix `jr:`,
`http://openrosa.org/xforms` with prefix `orx:`, and
`http://www.opendatakit.org/xforms` with prefix `odk:`).

In ProtoForms, these concepts are first-class, strongly typed fields within the
unified schema:

Legacy XML Attribute / Element | Legacy Namespace Prefix | ProtoForms Message & Field
------------------------------ | ----------------------- | --------------------------
`orx:version`                  | `orx`                   | `FormDef.version`
`orx:meta/instanceID`          | `orx`                   | `ModelDef.primary_instance.metadata.instance_id_field`
`jr:preload`                   | `jr`                    | `FieldBinding.preload.type`
`jr:preloadParams`             | `jr`                    | `FieldBinding.preload.param`
`jr:constraintMsg`             | `jr`                    | `FieldBinding.constraint_message`
`jr:requiredMsg`               | `jr`                    | `FieldBinding.required_message`
`jr:count`                     | `jr`                    | `RepeatDef.count_expr`
`jr:noAddRemove`               | `jr`                    | `RepeatDef.no_add_remove`
`orx:max-pixels`               | `orx`                   | `FieldBinding.max_pixels`
`orx:auto-send`                | `orx`                   | `SubmissionConfig.auto_send`
`orx:auto-delete`              | `orx`                   | `SubmissionConfig.auto_delete`
`odk:rank`                     | `odk`                   | `ControlDef.control_type = RANK`
`odk:tick-interval`            | `odk`                   | `RangeConfig.tick_interval`

## Custom Options and Extension Ranges

For proprietary or third-party features, ProtoForms recommends
[Protocol Buffer Options](https://protobuf.dev/programming-guides/proto3/#customoptions)
and extension ranges. This guarantees that custom metadata remains strictly
typed and discoverable without risking collisions with core specification
fields.
