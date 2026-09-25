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

# Translations

ProtoForms supports localized text for question labels, hints, and enumerator
guidance. Localized strings are organized into a strongly typed
`TranslationCatalog` within `ModelDef.translations`, referenced by persistent
`text_id` keys in the view controls.

## Translation Catalog Structure

Within `ModelDef`, localized strings are organized using the following protobuf
messages:

```protobuf
message TranslationCatalog {
  // List of language-specific translation tables.
  repeated LanguageTranslation languages = 1;
}

message LanguageTranslation {
  // Language name or BCP-47 tag (e.g., "English", "es-MX", "Français").
  string language = 1;

  // Whether this is the default language when the form is opened.
  bool is_default = 2;

  // Map of text ID to localized text and media variants.
  map<string, LocalizedString> strings = 3;
}

message LocalizedString {
  // Primary display text for question prompts or hints.
  string value = 1;

  // Optional abbreviated phrasing for compact displays, mobile cards, or summary tables.
  string short_value = 2;

  // Optional guidance hint for enumerator training or collapsible info panels.
  string guidance_value = 3;

  // Multi-modal media associated with this localized entry (audio, images, videos).
  MediaRef media = 4;
}
```

## Referencing Localized Text in Controls

Controls in `ViewDef` reference the catalog using `text_id`:

```protobuf
message LabelDef {
  // Inline literal text (used when multi-lingual catalogs are not needed).
  string text = 1;

  // Text identifier referencing strings in TranslationCatalog (replaces jr:itext()).
  string text_id = 2;

  // Direct media attachments.
  MediaRef media = 3;
}
```

### Example

Instead of hardcoded text:

```textproto
control {
  field_ref: "tenure_years"
  type: CONTROL_INPUT
  label { text: "How many years have you lived here?" }
}
```

The localized control references `text_id`:

```textproto
control {
  field_ref: "tenure_years"
  type: CONTROL_INPUT
  label {
    text_id: "tenure_years_label"
    text: "How many years have you lived here?"  # Optional fallback text
  }
  hint {
    text_id: "tenure_years_hint"
  }
}
```

With the corresponding translations defined in `ModelDef`:

```textproto
model {
  translations {
    languages {
      language: "English"
      is_default: true
      strings {
        key: "tenure_years_label"
        value {
          value: "How many years have you lived here?"
          short_value: "Tenure (yrs)"
        }
      }
      strings {
        key: "tenure_years_hint"
        value {
          value: "Enter full calendar years at this address."
          guidance_value: "Round down to the nearest completed year."
        }
      }
    }
    languages {
      language: "Spanish"
      strings {
        key: "tenure_years_label"
        value {
          value: "¿Cuántos años ha vivido aquí?"
          short_value: "Residencia (años)"
        }
      }
      strings {
        key: "tenure_years_hint"
        value {
          value: "Ingrese años completos en este domicilio."
          guidance_value: "Redondee hacia abajo al año cumplido más cercano."
        }
      }
    }
  }
}
```

## Localized String Variants

| Field            | Form Equivalent | Description                            |
| ---------------- | --------------- | -------------------------------------- |
| `value`          | *(none)*        | Default text displayed for question    |
:                  :                 : labels and hints.                      :
| `short_value`    | `short`         | Shorter label phrasing for mobile      |
:                  :                 : summary lists, review pages, or column :
:                  :                 : headers in exports.                    :
| `guidance_value` | `guidance`      | In-depth instructions or training      |
:                  :                 : prompts for enumerators, typically     :
:                  :                 : rendered behind an info/expand toggle. :

Multi-media attachments (images, audio prompts, and video clips) can also be
localized per language by attaching `MediaRef` directly inside
`LocalizedString`, as described in the [Media](#media) section.
