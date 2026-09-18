# Events and Actions

The client runtime emits events during form initialization, record loading,
value edits, and repeat creation. Declarative actions subscribe to these events,
enabling automated timestamps, location capture, dynamic default assignment, and
background audio recording.

## Events

The following event types are defined in `groundplatform.v2.forms.EventType`:

| Event Enum                  | String Identifier         | Description        |
| --------------------------- | ------------------------- | ------------------ |
| `EVENT_INSTANCE_FIRST_LOAD` | `odk-instance-first-load` | Emitted once when  |
:                             :                           : a blank record     :
:                             :                           : instance is first  :
:                             :                           : initialized.       :
| `EVENT_INSTANCE_LOAD`       | `odk-instance-load`       | Emitted whenever a |
:                             :                           : record instance is :
:                             :                           : opened or resumed  :
:                             :                           : from storage.      :
| `EVENT_VALUE_CHANGED`       | `xforms-value-changed`    | Emitted whenever   |
:                             :                           : the answer of the  :
:                             :                           : bound field is     :
:                             :                           : modified.          :
| `EVENT_NEW_REPEAT`          | `odk-new-repeat`          | Emitted whenever a |
:                             :                           : new repeat         :
:                             :                           : iteration is       :
:                             :                           : appended to a      :
:                             :                           : repeated group.    :

```protobuf
enum EventType {
  EVENT_TYPE_UNSPECIFIED = 0;
  EVENT_INSTANCE_FIRST_LOAD = 1;
  EVENT_INSTANCE_LOAD = 2;
  EVENT_VALUE_CHANGED = 3;
  EVENT_NEW_REPEAT = 4;
}
```

## Actions

Actions specify operations to execute when triggered by one or more events. In
ProtoForms, actions are modeled as strongly typed `ActionDef` messages:

```protobuf
message ActionDef {
  // One or more triggering events.
  repeated EventType events = 1;

  // Target field path within the record (e.g. "member.years_active", "location").
  string target_ref = 2;

  // The concrete action to perform.
  oneof action {
    SetValueAction set_value = 3;
    SetGeopointAction set_geopoint = 4;
    RecordAudioAction record_audio = 5;
  }
}

message SetValueAction {
  // Dynamic expression evaluated at runtime (e.g. "now()", "base_years + 2").
  string value_expression = 1;

  // Optional literal constant value if no expression is used.
  TypedValue literal_value = 2;
}

message SetGeopointAction {
  // Optional accuracy threshold in meters.
  double accuracy_threshold_meters = 1;
}

message RecordAudioAction {
  // Quality profile (e.g. "low", "voice_only", "high").
  string quality = 1;
}
```

Actions bound to startup events (`EVENT_INSTANCE_FIRST_LOAD`,
`EVENT_INSTANCE_LOAD`) are defined in `ModelDef.actions`. Actions bound to
field-level edits (`EVENT_VALUE_CHANGED`) or repeat insertion
(`EVENT_NEW_REPEAT`) can be placed inside `ComponentDef.actions` or
`ModelDef.actions`.

## The `EVENT_NEW_REPEAT` Event

The `EVENT_NEW_REPEAT` event fires whenever a new repeat iteration is appended
to a repeated message field, prior to recalculating dependent expressions and
validation rules.

The snippet below assigns a calculated default value and coordinates to newly
added repeat items based on a preceding field:

```textproto
# Model definition with fields
model {
  primary_instance {
    record_schema {
      fields { name: "my_age" type: TYPE_INT32 }
      fields {
        name: "person"
        type: TYPE_MESSAGE
        is_repeated: true
        fields { name: "age" type: TYPE_INT32 }
        fields { name: "location" type: TYPE_GEOPOINT }
      }
    }
  }
}

# View definition with repeat and nested actions
view {
  components {
    control {
      field_ref: "my_age"
      type: CONTROL_INPUT
      label { text: "Your age" }
    }
  }
  components {
    repeat {
      field_ref: "person"
      actions {
        events: [EVENT_NEW_REPEAT]
        target_ref: "person.age"
        set_value {
          value_expression: "my_age + 2"
        }
      }
      actions {
        events: [EVENT_NEW_REPEAT]
        target_ref: "person.location"
        set_geopoint {}
      }
      components {
        control {
          field_ref: "person.age"
          type: CONTROL_INPUT
          label { text: "Person's age" }
        }
      }
    }
  }
}
```

## Setting Dynamic Values on Form Load

Initialization actions can capture timestamps or current GPS location when the
form is opened:

```textproto
model {
  actions {
    events: [EVENT_INSTANCE_FIRST_LOAD]
    target_ref: "start_time"
    set_value {
      value_expression: "now()"
    }
  }
  actions {
    events: [EVENT_INSTANCE_FIRST_LOAD]
    target_ref: "initial_location"
    set_geopoint {}
  }
}
```

## Setting Values on Value Change

To update audit or dependent fields when a specific input changes:

```textproto
view {
  components {
    control {
      field_ref: "my_text"
      type: CONTROL_INPUT
      label { text: "Enter note" }
      actions {
        events: [EVENT_VALUE_CHANGED]
        target_ref: "my_text_changed"
        set_value {
          literal_value { string_value: "Value changed!" }
        }
      }
      actions {
        events: [EVENT_VALUE_CHANGED]
        target_ref: "my_current_location"
        set_geopoint {}
      }
    }
  }
}
```
