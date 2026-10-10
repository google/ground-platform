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

# Impact Events and Survey Outcomes

Ground records when data collected with it is used ("data was used" signals)
so impact can be measured without asking organizers to fill in reports (see
[Impact Measurement](../../../product/impact-measurement.md)). Two records
carry these signals:

*   **`ImpactEvent`** (`impact_event.proto`): an append-only log entry for an
    export, a partner push, a receipt, a DOI, or a survey closing.
*   **`SurveyOutcome`** (`survey_def.proto`): the organizer's one-tap answer to
    "What happened with this data?" when a survey is closed or archived.

## Impact Events

```protobuf
message ImpactEvent {
  string event_id = 1;
  ImpactEventType type = 2;
  string survey_id = 3;
  string organization_id = 4;
  repeated string purpose_ids = 5;
  optional string export_profile_id = 6;
  int64 feature_count = 7;
  double area_ha = 8;
  google.protobuf.Timestamp occurred_at = 9;
  string actor_user_id = 10;
}

enum ImpactEventType {
  IMPACT_EVENT_TYPE_UNSPECIFIED = 0;
  EXPORT = 1;
  PARTNER_PUSH = 2;
  RECEIPT_GENERATED = 3;
  RECEIPT_SHARED = 4;
  DOI_MINTED = 5;
  SURVEY_CLOSED = 6;
}
```

<!-- mdformat off -->

| Type | Recorded when | Coverage (`feature_count`, `area_ha`) |
| :--- | :--- | :--- |
| `EXPORT` | Data is downloaded from the web console (CSV, GeoJSON, or an export profile such as EUDR GeoJSON). `export_profile_id` is set when a profile was used. | The exported map features |
| `PARTNER_PUSH` | Data is sent to a partner system (FERM, Whisp, a national registry) | The features sent |
| `RECEIPT_GENERATED` | A PDF receipt or report is generated for a map feature or submission | The feature the receipt is for |
| `RECEIPT_SHARED` | A generated receipt is shared or downloaded | The feature the receipt is for |
| `DOI_MINTED` | A DOI is minted for an open dataset | The published features |
| `SURVEY_CLOSED` | The survey moves from `PUBLISHED` to `CLOSED` or `ARCHIVED` | All of the survey's features |

<!-- mdformat on -->

*   **Aggregate only**: events carry counts and areas. They never contain
    geometry, property values, or personal data other than `actor_user_id`.
*   **Deduplicated coverage**: map features that share a GeoID (the same plot
    mapped more than once) count once in `feature_count` and `area_ha`.
*   **Context at the time**: `organization_id` and `purpose_ids` are copied
    from the survey when the event is recorded, so later changes to the
    survey's purposes don't rewrite history.
*   **Append-only and offline-first**: clients generate `event_id` and store
    events locally first. Events recorded offline (for example, receipts on a
    mobile device) upload with the other pending changes, and the server
    ignores duplicate IDs.

## Survey Outcomes

```protobuf
message SurveyDef {
  // ... fields 1–21 ...
  SurveyOutcome outcome = 22;
}

message SurveyOutcome {
  enum Outcome {
    OUTCOME_UNSPECIFIED = 0;
    SUBMITTED_EUDR_DDS = 1;
    REPORTED_FERM = 2;
    LAND_TITLING = 3;
    SHARED_WITH_BUYERS = 4;
    TRAINED_OR_VALIDATED_MODEL = 5;
    PROTECTED_AREA_MANAGEMENT = 6;
    NOT_YET = 7;
  }

  enum EffortComparison {
    EFFORT_COMPARISON_UNSPECIFIED = 0;
    LESS = 1;
    SAME = 2;
    MORE = 3;
  }

  repeated Outcome outcomes = 1;
  EffortComparison effort_comparison = 2;
  google.protobuf.Timestamp answered_at = 3;
  string answered_by = 4;
}
```

*   **When it's asked**: on the `PUBLISHED → CLOSED / ARCHIVED` transition,
    as a non-blocking card with multi-select outcomes and the optional
    "Compared with your previous method, this took less, the same, or more
    time and cost." Skipping is always possible, and closing a survey never
    waits for an answer.
*   **`NOT_YET`** excludes the other outcomes. When the answer is `NOT_YET`,
    or the card was skipped, the survey list shows a gentle reminder 90 days
    after the survey closed that reopens the card.
*   Recording the answer doesn't create an `ImpactEvent`; the close itself is
    the `SURVEY_CLOSED` event.

## Export Profiles

Exports through an export profile (`ExportProfileDef`; see
[Templates and Purpose Packs](../library/02-templates-and-purpose-packs.md#export-profiles-exportprofiledef))
locate their output fields through concept links: each `field_concepts` entry
names a concept, the survey's `FormConceptLinks` map it to a question, and the
question's `save_to` property (which inherits the concept as
`EntityPropertyDefinition.concept_ref`) supplies the value. Concepts with a
geometry data type map to the feature geometry. Output fields with no linked
property are listed as a warning before export; the export still runs.

The first global profile, **EUDR GeoJSON**, writes a GeoJSON
`FeatureCollection` in WGS84 with coordinates rounded to 6 decimal places:
plot boundaries as polygons, and points for plots mapped as points (with a
warning for point plots over 4 ha, which EUDR expects as polygons).
