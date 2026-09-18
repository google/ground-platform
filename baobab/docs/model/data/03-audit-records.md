# Audit Records and Provenance

Ground 2.0 maintains two levels of audit tracking defined in
`audit_record.proto`:

1.  **`AuditInfo`**: Resource-level creation and modification timestamps and
    actor UIDs attached to `SurveyDef`, `EntityRecord`, and `SubmissionRecord`.
2.  **`AuditRecord`**: Platform-wide append-only audit log entries recording
    mutations (`CREATE`, `UPDATE`, `SOFT_DELETE`, `RESTORE`, `PUBLISH`) along
    with attribute-level before-and-after snapshots (`FieldDelta`).

## Schema Definitions

```protobuf
message AuditInfo {
  string created_by = 1;
  google.protobuf.Timestamp create_time = 2;
  string last_modified_by = 3;
  google.protobuf.Timestamp last_modify_time = 4;
}

message FieldDelta {
  string old_value = 1;
  string new_value = 2;
}

message AuditRecord {
  string audit_id = 1;
  string survey_id = 2;
  string target_type = 3;
  string target_id = 4;
  google.protobuf.Timestamp timestamp = 5;
  string actor_id = 6;
  groundplatform.v2.survey.Role actor_role = 7;

  enum AuditAction {
    AUDIT_ACTION_UNSPECIFIED = 0;
    CREATE = 1;
    UPDATE = 2;
    SOFT_DELETE = 3;
    RESTORE = 4;
    PUBLISH = 5;
  }
  AuditAction action = 8;

  map<string, FieldDelta> field_deltas = 9;
  string client_platform = 10;
  string client_version = 11;
}
```

## Example: `AuditRecord` for a Submission Edit

```textproto
audit_id: "aud-99887766-5544-3322-1100"
survey_id: "550e8400-e29b-41d4-a716-446655440000"
target_type: "SUBMISSION"
target_id: "uuid:5b9cf8d1-106f-4004-844f-c072d76762ed"
timestamp { seconds: 1789352000 }
actor_id: "uid_987654321"
actor_role: SURVEY_ORGANIZER
action: UPDATE
field_deltas {
  key: "shade_tree_count"
  value {
    old_value: "28"
    new_value: "30"
  }
}
client_platform: "WEB_CONSOLE"
client_version: "2.4.0"
```
