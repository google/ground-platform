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

# Templates and Purpose Packs

Form templates and Purpose Packs let organizers start a survey from a purpose
instead of a blank form. Both live in organization libraries and resolve the
same way as concepts (see [Introduction](00-introduction.md#resolution)).

## Form Templates (`FormTemplateDef`)

A form template wraps a portable `FormDef` (whose questions may carry
`concept_ref` links) together with the defaults applied when it is added to a
survey.

```protobuf
message FormTemplateDef {
  // Global: "<name>"; organization: "org.<organization_id>.<name>".
  string id = 1;
  string organization_id = 2;

  groundplatform.v2.forms.LocalizedString title = 3;
  groundplatform.v2.forms.LocalizedString description = 4;

  // The form, including concept links and translations.
  groundplatform.v2.forms.FormDef form = 5;

  // Default backing map layer or data table created with the form.
  groundplatform.v2.survey.EntityDatasetDef default_dataset = 6;

  LibraryStatus status = 7;
  groundplatform.v2.data.AuditInfo audit_info = 8;
}
```

*   **Adding a form**: in the Survey editor, "+" offers **Blank form** or
    **From template**, listing templates from the survey's resolved library.
    The template is copied into the survey; later template edits do not change
    existing surveys.
*   **Save as template**: organization Managers can save any form, with its
    concept links, to their organization's library. "All users" Managers can
    also save to the global library.

## Purpose Packs (`PurposePackDef`)

A Purpose Pack is one choice in the survey-creation step **"What will this data
be used for?"**

```protobuf
message PurposePackDef {
  // Global: "<name>" (e.g., "eudr_due_diligence");
  // organization: "org.<organization_id>.<name>".
  string id = 1;
  string organization_id = 2;

  groundplatform.v2.forms.LocalizedString title = 3;
  groundplatform.v2.forms.LocalizedString description = 4;
  string icon = 5;

  // Templates added to a new survey that selects this pack.
  repeated string form_template_ids = 6;

  // Export profiles enabled by default for surveys with this purpose.
  repeated string export_profile_ids = 7;

  // Programs offered as an optional follow-up (e.g., "eudr", "uk_frc").
  repeated string program_ids = 8;

  // Goal mapping and MAP pillar. For organization packs these are
  // suggestions, reported separately in the global dashboard.
  repeated string goals = 9;
  Pillar pillar = 10;

  LibraryStatus status = 11;
  groundplatform.v2.data.AuditInfo audit_info = 12;
}
```

*   **Survey creation**: the dialog lists the organization's packs first, then
    global packs, and refreshes when the selected organization changes. The step
    is skippable. Selecting one or more packs seeds the survey draft with their
    templates.
*   **Validation presets**: a pack's templates carry their own constraints (for
    example, requiring a polygon for EUDR plots over 4 ha), so no separate
    preset object is needed.

### Survey Purposes

`SurveyDef` records the selected purposes and programs. Both remain editable in
Survey details.

```protobuf
message SurveyDef {
  // ... fields 1–18 ...
  repeated string purpose_ids = 19;
  repeated string program_ids = 20;
}
```

## Export Profiles (`ExportProfileDef`)

An export profile is a named export with a fixed output schema. Using a profile
records an impact event for the survey.

```protobuf
message ExportProfileDef {
  string id = 1;
  string organization_id = 2;
  groundplatform.v2.forms.LocalizedString title = 3;

  // Output format (e.g., "geojson", "csv", "shapefile").
  string format = 4;

  // Output field name to concept ID. Questions are located through their
  // concept links, so the profile works across differently worded forms.
  map<string, string> field_concepts = 5;

  LibraryStatus status = 6;
}
```

The first global profile is **EUDR GeoJSON**, producing geolocation files
suitable for EU due diligence statements.

## Hiding Global Entries

Organizations can hide global form templates, and the Purpose Packs built on
them, that they do not use. Hidden IDs are stored in the organization's library
settings:

```protobuf
message OrganizationLibrarySettings {
  string organization_id = 1;

  // Global template and Purpose Pack IDs hidden from this organization's
  // survey-creation and "From template" pickers.
  repeated string hidden_global_entry_ids = 2;
}
```

*   Hidden entries disappear from the organization's pickers. Surveys already
    created from them are unaffected.
*   Global concepts cannot be hidden, so standard fields always remain
    linkable.
*   In the organization editor, the **Templates** and **Purposes** tabs list
    global entries with a **Hide** toggle alongside the organization's own
    entries.
