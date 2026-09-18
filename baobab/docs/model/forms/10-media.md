# Media

ProtoForms provides first-class support for multi-media attachments in labels,
hints, and select choices. Instead of ad-hoc `<value form="...">` tags, media
resources are represented as strongly typed `MediaRef` messages.

## `MediaRef` Message Definition

```protobuf
message MediaRef {
  // Standard thumbnail/display image URI (JPEG, PNG, GIF, SVG, WebP).
  string image_uri = 1;

  // High-resolution image URI for pan-and-zoom modal views.
  string big_image_uri = 2;

  // Audio prompt or question readout URI (MP3, AAC, OGG, WAV).
  string audio_uri = 3;

  // Video guidance or demonstration URI (MP4, WebM).
  string video_uri = 4;
}
```

Media references can be specified:

1.  **Directly on controls or options** via `LabelDef.media` (for unilingual
    forms).
2.  **Inside localized translations** via `LocalizedString.media` in
    `TranslationCatalog` (for multi-lingual forms).

## Localized Media in `TranslationCatalog`

```textproto
model {
  translations {
    languages {
      language: "English"
      is_default: true
      strings {
        key: "bird_call_prompt"
        value {
          value: "Listen to the recorded red-tailed hawk call:"
          media {
            image_uri: "jr://images/redtailed_hawk_thumb.jpg"
            big_image_uri: "jr://images/redtailed_hawk_full.jpg"
            audio_uri: "jr://audio/redtailed_hawk.mp3"
          }
        }
      }
    }
  }
}
```

## Direct Media on View Controls and Options

```textproto
view {
  components {
    control {
      field_ref: "selected_species"
      type: CONTROL_SELECT_ONE
      label {
        text: "Select the observed bird:"
      }
      options {
        value: "golden_eagle"
        label {
          text: "Golden Eagle"
          media {
            image_uri: "jr://images/golden_eagle.jpg"
            big_image_uri: "jr://images/golden_eagle_hi_res.jpg"
          }
        }
      }
    }
  }
}
```

## Interactive Image Behavior

-   When both `image_uri` and `big_image_uri` are specified, clients render the
    standard image with an interactive zoom indicator. Tapping opens a
    fullscreen pannable and zoomable viewer.
-   When only `image_uri` is supplied, the image is rendered inline without
    modal zoom.
-   `big_image_uri` should not be provided without `image_uri`.

## Minimum Client Format Support

Implementations must support the following standard media formats:

-   **Images**: JPEG, PNG, GIF, SVG, WebP
-   **Audio**: MP3, AAC, OGG, WAV
-   **Video**: MP4, WebM
