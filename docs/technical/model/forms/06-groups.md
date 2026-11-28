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

# Groups

A `GroupDef` organizes form controls and sub-groups into structured sections.

*   **Presentation Group**: When a group defines a `label`, it renders as a
    visual container (such as a card, section header, or screen page).
*   **Logical Group**: When a group defines a `field_path`, it maps to a nested
    message or field namespace in the primary record schema. Descendant controls
    resolve relative field paths against the group's `field_path`.
*   A single `GroupDef` can serve simultaneously as a logical namespace and a
    visual container.
*   Group containers support arbitrary nesting depth.

Besides visual layout, a logical group can attach a `FieldBinding` with a
`relevant_expression`. When that expression evaluates to `false`, the entire group
(along with all child controls) is hidden in the UI and excluded from the
submitted record.

The example below illustrates both a logical group (`applicant`) and a combined
logical + presentation group (`district_info`):

```textproto
# FormDef model and view excerpt

model {
  primary_instance {
    fields {
      name: "applicant"
      type: GROUP
      nested_fields {
        fields {
          name: "given_name"
          type: STRING
        }
        fields {
          name: "family_name"
          type: STRING
        }
        fields {
          name: "years_active"
          type: INT32
        }
      }
    }
    fields {
      name: "district_info"
      type: GROUP
      nested_fields {
        fields {
          name: "coordinates"
          type: STRING
        }
        fields {
          name: "municipality"
          type: STRING
        }
        fields {
          name: "resident_count"
          type: INT32
        }
      }
    }
  }

  bindings {
    field_path: "district_info"
    relevant_expression: "string-length(applicant/given_name) > 0 && string-length(applicant/family_name) > 0"
  }
}

view {
  components {
    group {
      field_ref: "applicant"
      components {
        control {
          field_ref: "given_name"
          type: CONTROL_INPUT
          label {
            text: "Enter given name:"
          }
        }
      }
      components {
        control {
          field_ref: "family_name"
          type: CONTROL_INPUT
          label {
            text: "Enter family name:"
          }
        }
      }
      components {
        control {
          field_ref: "years_active"
          type: CONTROL_INPUT
          label {
            text: "Years active:"
          }
        }
      }
    }
  }
  components {
    group {
      field_ref: "district_info"
      label {
        text: "District Details"
      }
      components {
        control {
          field_ref: "coordinates"
          type: CONTROL_INPUT
          label {
            text: "Capture site coordinates"
          }
        }
      }
      components {
        control {
          field_ref: "municipality"
          type: CONTROL_INPUT
          label {
            text: "Municipality name"
          }
        }
      }
      components {
        control {
          field_ref: "resident_count"
          type: CONTROL_INPUT
          label {
            text: "Estimated resident count"
          }
        }
      }
    }
  }
}
```

## Sections in the Form Editor

The Form editor's **sections** are groups whose display logic applies to all
their questions at once. A singleton section becomes a `GroupDef` with the
`field-list` appearance; a repeated section becomes a `RepeatDef` (see
[Repeats](07-repeats.md)). Either way the section's instance node is named after
the section ID, and its display logic becomes one `relevant_expression` binding on that
node, so the questions inside inherit it (W3C XForms 1.0 section 6.1.4) rather
than each repeating the same condition:

```textproto
bindings {
  field_path: "trees"
  relevant_expression: "/data/has_trees = 'yes'"
}
bindings {
  field_path: "trees/tree_notes"
  relevant_expression: "/data/trees/tree_kind = 'yes'"
}
```

*   **Root-relative paths**: Bindings and body references use paths relative to
    the primary instance root (`trees/tree_notes`); the XForms serializer adds
    `/data/`. Expressions refer to questions by absolute path
    (`/data/trees/tree_kind`).
*   **What a section can depend on**: Questions asked before the section's first
    question, outside the section itself and outside repeated sections.
*   **Editor checks**: The editor reports display logic that depends on a
    question inside the section, a later or deleted question, or a question in a
    repeated section; and question names that clash with a section ID.
