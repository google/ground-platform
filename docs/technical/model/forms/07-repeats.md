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

# Repeats

Repeats represent sections or question sequences that can be collected multiple
times in a form.

In Protocol Buffers, repeats correspond directly to `repeated` message fields in
the generated data model, providing native list semantics and type safety
without XML cloning workarounds:

```protobuf
syntax = "proto3";

message FormRecord {
  repeated Person person = 1;
  RecordMetadata meta = 2;

  message Person {
    string name = 1;
    string relationship = 2;
  }
}
```

In the `view` definition, a repeat is configured with a `RepeatDef` component:

```textproto
# FormDef excerpt

model {
  primary_instance {
    fields {
      name: "person"
      type: REPEAT
      nested_fields {
        fields {
          name: "name"
          type: STRING
        }
        fields {
          name: "relationship"
          type: STRING
          default_value: "spouse"
        }
      }
    }
  }
}

view {
  components {
    repeat {
      field_path: "person"
      label {
        text: "Person"
      }
      children {
        components {
          control {
            field_path: "name"
            control_type: INPUT
            label {
              text: "Enter name"
            }
          }
        }
        components {
          control {
            field_path: "relationship"
            control_type: INPUT
            label {
              text: "Enter relationship"
            }
          }
        }
      }
    }
  }
}
```

When rendered compactly in mobile navigation (e.g., collapsed repeat lists),
clients display the label of the repeat followed by the index or summary label
of the item.

## Creation and Removal of Repeats

By default, users can freely add or remove repeat instances in the UI. Form
engines support several controls:

1.  **Disable User Addition/Removal**: Set `no_add_remove: true` on `RepeatDef`
    to prevent users from adding or deleting instances manually.
2.  **Dynamic Repeat Count**: Set `count_expr` on `RepeatDef` (e.g.,
    `count_expr: "/data/hh_size"`). The form engine dynamically resizes the list
    of repeats to match the evaluated integer value.
3.  **Pre-populated Repeats**: The form definition can include initial default
    repeat instances in `primary_instance.default_repeats`.

```textproto
view {
  components {
    repeat {
      field_path: "person"
      label {
        text: "Person"
      }
      count_expr: "hh_size"
      no_add_remove: true
      children {
        components {
          control {
            field_path: "name"
            control_type: INPUT
            label {
              text: "Enter name"
            }
          }
        }
        components {
          control {
            field_path: "relationship"
            control_type: INPUT
            label {
              text: "Enter relationship"
            }
          }
        }
      }
    }
  }
}
```

## Default Values and Templates

In ProtoForms, default values for newly added repeat instances are specified
directly within the repeat field definitions in `primary_instance`:

```textproto
fields {
  name: "person"
  type: REPEAT
  nested_fields {
    fields {
      name: "name"
      type: STRING
    }
    fields {
      name: "relationship"
      type: STRING
      default_value: "spouse"
    }
  }
}
```

Whenever a user or expression adds a new `Person` instance, `relationship`
initializes to `"spouse"` and `name` initializes empty. Actions triggered by
`EVENT_NEW_REPEAT` can also dynamically calculate initial values (see
[Events and Actions](#events-and-actions)).
