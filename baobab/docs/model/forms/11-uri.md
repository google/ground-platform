# URIs

Throughout ProtoForms, URIs refer to media, external tables, and virtual state
within a client's sandboxed environment. ProtoForms retains the `jr://`
(JavaRosa / ODK compatible) URI prefix to address these sandboxed assets.

## File Endpoints

File endpoint URIs point to packaged or synchronized assets on the local device
or runtime sandbox:

| URI Format                               | Description                      |
| ---------------------------------------- | -------------------------------- |
| `jr://images/<asset_path>.png`           | Image resource (PNG, JPEG, SVG,  |
:                                          : WebP)                            :
| `jr://audio/<asset_path>.mp3`            | Audio clip or voice prompt (MP3, |
:                                          : AAC, OGG, WAV)                   :
| `jr://video/<asset_path>.mp4`            | Video resource (MP4, WebM)       |
| `jr://file-csv/<asset_path>.csv`         | Delimited CSV table resource     |
| `jr://file-geojson/<asset_path>.geojson` | GeoJSON feature collection for   |
:                                          : spatial queries                  :
| `jr://file/<asset_path>.xml`             | Legacy XML resource (supported   |
:                                          : for backward compatibility)      :

In hosted, database-backed survey runtimes (such as Ground), dataset URIs like
`jr://file-csv/<id>.csv` and `jr://file-geojson/<id>.geojson` act as logical
serialization bindings for XForms/ODK round-tripping; at runtime, expressions
referencing `instance('<id>')` resolve directly against the local indexed ODK
Entity Dataset (`EntityDatasetDef` / `EntityRecord`) without reading static
files from disk.

## Virtual Endpoints

Virtual endpoints represent dynamic or computed data structures maintained by
the form runtime rather than static files on disk:

| URI Format                 | Description                                    |
| -------------------------- | ---------------------------------------------- |
| `jr://instance/last-saved` | Resolves to the most recently finalized or     |
:                            : saved record instance for this form. This is   :
:                            : commonly referenced in initial calculations or :
:                            : `EVENT_INSTANCE_FIRST_LOAD` actions to         :
:                            : auto-fill repetitive fields across consecutive :
:                            : form entries.                                  :
