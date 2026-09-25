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
`relevant_expr`. When that expression evaluates to `false`, the entire group
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
    relevant_expr: "string-length(applicant/given_name) > 0 && string-length(applicant/family_name) > 0"
  }
}

view {
  components {
    group {
      field_path: "applicant"
      children {
        components {
          control {
            field_path: "given_name"
            control_type: INPUT
            label {
              text: "Enter given name:"
            }
          }
        }
        components {
          control {
            field_path: "family_name"
            control_type: INPUT
            label {
              text: "Enter family name:"
            }
          }
        }
        components {
          control {
            field_path: "years_active"
            control_type: INPUT
            label {
              text: "Years active:"
            }
          }
        }
      }
    }
  }
  components {
    group {
      field_path: "district_info"
      label {
        text: "District Details"
      }
      children {
        components {
          control {
            field_path: "coordinates"
            control_type: INPUT
            label {
              text: "Capture site coordinates"
            }
          }
        }
        components {
          control {
            field_path: "municipality"
            control_type: INPUT
            label {
              text: "Municipality name"
            }
          }
        }
        components {
          control {
            field_path: "resident_count"
            control_type: INPUT
            label {
              text: "Estimated resident count"
            }
          }
        }
      }
    }
  }
}
```
