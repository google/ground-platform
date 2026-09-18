# Client Audit Logs

## Introduction

This specification defines the audit log telemetry recorded by ProtoForms
clients when `AuditConfig` is enabled in `RecordMetadata`. Audit logs track
enumerator interactions, question timing, edits, and location breadcrumbs for
quality assurance and verification.

## Audit Log Protobuf Schema

In ProtoForms, audit trails are represented natively as structured `AuditLog`
protocol buffers:

```protobuf
syntax = "proto3";

package groundplatform.v2.forms;

import "google/protobuf/timestamp.proto";

message AuditLog {
  repeated AuditEvent events = 1;
}

message AuditEvent {
  // Event identifier (e.g. "form_start", "question", "group", "form_exit", "form_save", "form_finalize").
  string event = 1;

  // Target field path in the primary instance (e.g. "household.person[0].age").
  string field_path = 2;

  // Timestamp when interaction began.
  google.protobuf.Timestamp start_time = 3;

  // Timestamp when interaction finished or focus changed.
  google.protobuf.Timestamp end_time = 4;

  // Geospatial coordinate recorded during this event (if location tracking is enabled).
  GeoPoint location = 5;

  // Value before edit (if track_changes is enabled in AuditConfig).
  string old_value = 6;

  // Value after edit (if track_changes is enabled in AuditConfig).
  string new_value = 7;
}
```

## CSV Export Format

For compatibility with tabular analysis tools and legacy workflows, clients can
export `AuditLog` messages to CSV according to the following conventions:

-   **Encoding**: UTF-8
-   **Delimiter**: Comma (`,`) with Unix line endings (`\n`)
-   **Quoting**: Fields containing commas, quotes, or newlines are enclosed in
    double quotes (`"`) with embedded quotes escaped as `""`.

### Header Columns

| Column Name | Protobuf Field                        | Description            |
| ----------- | ------------------------------------- | ---------------------- |
| `event`     | `AuditEvent.event`                    | Standard event name    |
:             :                                       : (e.g., `form start`,   :
:             :                                       : `question`, `group`).  :
| `node`      | `AuditEvent.field_path`               | Dot-separated field    |
:             :                                       : path within the        :
:             :                                       : record.                :
| `start`     | `AuditEvent.start_time`               | Milliseconds since     |
:             :                                       : Unix epoch UTC.        :
| `end`       | `AuditEvent.end_time`                 | Milliseconds since     |
:             :                                       : Unix epoch UTC.        :
| `latitude`  | `AuditEvent.location.latitude`        | Latitude in decimal    |
:             :                                       : degrees (when location :
:             :                                       : tracking enabled).     :
| `longitude` | `AuditEvent.location.longitude`       | Longitude in decimal   |
:             :                                       : degrees (when location :
:             :                                       : tracking enabled).     :
| `accuracy`  | `AuditEvent.location.accuracy_meters` | Estimated accuracy in  |
:             :                                       : meters (when location  :
:             :                                       : tracking enabled).     :
| `old-value` | `AuditEvent.old_value`                | Field value at start   |
:             :                                       : of interaction (when   :
:             :                                       : change tracking        :
:             :                                       : enabled).              :
| `new-value` | `AuditEvent.new_value`                | Field value at end of  |
:             :                                       : interaction (when      :
:             :                                       : change tracking        :
:             :                                       : enabled).              :
