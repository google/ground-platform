# Access Control Lists and Quotas

Access control lists (`SurveyAcl`) and resource quotas (`QuotaLimits`) are
defined as standalone resources in `acl.proto` so permissions and capacity tiers
can be managed independently of the `SurveyDef` schema.

## Roles (`Role`) and Collaborators (`SurveyAcl`)

All users are identified by immutable unique user IDs (e.g., Firebase Auth UIDs)
rather than email addresses. Each collaborator is assigned a `Role`:

| Role               | Permissions                                            |
| ------------------ | ------------------------------------------------------ |
| `VIEWER`           | Read-only access enabling map navigation, entity       |
:                    : inspection, and submission viewing while               :
:                    : hiding/disabling collection and editing actions.       :
| `DATA_COLLECTOR`   | Field enumerator authorized to collect submissions and |
:                    : register ad-hoc geospatial entities.                   :
| `SURVEY_ORGANIZER` | Project supervisor/manager authorized to edit forms,   |
:                    : manage ACLs, publish schemas, review conflicts, and    :
:                    : export data.                                           :

```protobuf
message AclEntry {
  string user_id = 1;
  Role role = 2;
  enum InvitationStatus {
    INVITATION_STATUS_UNSPECIFIED = 0;
    PENDING = 1;
    ACCEPTED = 2;
  }
  InvitationStatus invitation_status = 3;
}

message SurveyAcl {
  string survey_id = 1;
  repeated AclEntry entries = 2;
}
```

## Example: `SurveyAcl`

```textproto
survey_id: "550e8400-e29b-41d4-a716-446655440000"
entries {
  user_id: "uid_987654321"
  role: SURVEY_ORGANIZER
  invitation_status: ACCEPTED
}
entries {
  user_id: "uid_112233445"
  role: DATA_COLLECTOR
  invitation_status: ACCEPTED
}
entries {
  user_id: "uid_556677889"
  role: VIEWER
  invitation_status: PENDING
}
```

## Tiered Resource Quotas (`QuotaTier` and `QuotaLimits`)

Ground 2.0 enforces administrative and operational field collection limits
across two tiers (`BASIC` for community usage, `SPONSORED` for verified partner
institutions):

Quota Limit                           | Basic Tier | Sponsored Tier
------------------------------------- | ---------- | --------------
`max_surveys_per_owner`               | 5          | 100
`max_forms_per_survey`                | 5          | 10
`max_tasks_per_form`                  | 10         | 50
`max_photo_tasks_per_form`            | 1          | 10
`max_predefined_entities`             | 1,000      | 10,000
`max_acl_users`                       | 10         | 50
`max_surveys_per_user`                | 5          | 100
`max_adhoc_entities_per_user`         | 100        | 200
`max_submissions_per_entity_per_user` | 10         | 50
