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

# Instances and Data Model

A form model specifies a single **primary instance** alongside zero or more
**secondary instances**. The primary instance defines the typed schema and
default state for submission records produced by the form, whereas secondary
instances supply read-only reference tables for choice lists, lookups, and
cascading filters.

## Primary Instance

The *primary instance* defines the schema, field hierarchy, and default values
of the record using `RecordSchema` and `RecordNode` within `PrimaryInstance`. To
ensure clients and servers never need to rely on Protocol Buffer reflection or
per-form code generation, all submitted form data is represented by the standard
code-generated `groundplatform.v2.forms.RecordInstance` message.

The following example shows a primary instance definition alongside its
populated `RecordInstance` payload:

```textproto
# Schema and Default Values inside FormDef.model.primary_instance

primary_instance {
  record_schema {
    name: "household"
    fields {
      name: "member"
      type: TYPE_MESSAGE
      fields {
        name: "given_name"
        type: TYPE_STRING
      }
      fields {
        name: "family_name"
        type: TYPE_STRING
      }
      fields {
        name: "years_active"
        type: TYPE_INT32
      }
    }
  }
  default_values {
    fields {
      key: "member"
      value {
        node_value {
          fields {
            key: "years_active"
            value {
              scalar_value {
                int32_value: 1
              }
            }
          }
        }
      }
    }
  }
}
```

When a user completes and submits the form, the client runtime serializes the
captured answers into a `groundplatform.v2.forms.RecordInstance` message:

```textproto
form_id: "household"
form_version: "2026091301"
metadata {
  instance_id: "uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
}
data {
  fields {
    key: "member"
    value {
      node_value {
        fields {
          key: "given_name"
          value { scalar_value { string_value: "Ada" } }
        }
        fields {
          key: "family_name"
          value { scalar_value { string_value: "Lovelace" } }
        }
        fields {
          key: "years_active"
          value { scalar_value { int32_value: 12 } }
        }
      }
    }
  }
}
```

Any value specified in `default_values` acts as the initial default value. When
rendered, the corresponding form control displays this default value to the
user. Default values for binary media fields hold
[file endpoint URIs](#file-endpoints).

### Form-Level Attributes

In legacy XForms, attributes on the primary instance root node served multiple
purposes. In ProtoForms, these are first-class fields on `FormDef`:

| Attribute / Field   | Description                                           |
| ------------------- | ----------------------------------------------------- |
| `form_id`           | Unique ID identifying the form definition on the      |
:                     : server. [required]                                    :
| `version`           | Form version string (e.g., `"2024090101"`), used for  |
:                     : tracking revisions and submissions.                   :
| `compact_prefix`    | Optional string prefix included in compact text       |
:                     : representations (e.g., SMS).                          :
| `compact_delimiter` | Character separating field tags and values in compact |
:                     : text encoding (defaults to `" "`).                    :

### Metadata

Every primary instance includes a metadata block for tracking:

*   `instance_id`: Globally unique identifier (UUID) for this submission record.
*   `instance_name`: Computed human-readable label for the record.
*   `audit`: Client audit log reference.
*   `entity`: Entity create/update declarations (see [Entities](#entities)).

--------------------------------------------------------------------------------

## Secondary Instances

Secondary instances supply immutable lookup tables accessible from XPath
expressions via `instance('id')` (for example,
`instance('regions')/item[country='ca']`).

### Internal Secondary Instances

An internal secondary instance is embedded directly within
`FormDef.model.secondary_instances`. Each entry stores key-value row maps and
may reference [translated strings](#languages) or [media](#media) identifiers.

The snippet below defines two embedded lookup tables (`regions` and `districts`)
in Protocol Buffer text format:

```textproto
secondary_instances {
  id: "regions"
  inline_data {
    rows {
      values { key: "code" value: "on" }
      values { key: "country" value: "ca" }
      values { key: "text_id" value: "static_instance-regions-0" }
    }
    rows {
      values { key: "code" value: "qc" }
      values { key: "country" value: "ca" }
      values { key: "text_id" value: "static_instance-regions-1" }
    }
    rows {
      values { key: "code" value: "by" }
      values { key: "country" value: "de" }
      values { key: "text_id" value: "static_instance-regions-2" }
    }
    rows {
      values { key: "code" value: "sn" }
      values { key: "country" value: "de" }
      values { key: "text_id" value: "static_instance-regions-3" }
    }
  }
}

secondary_instances {
  id: "districts"
  inline_data {
    rows {
      values { key: "code" value: "ottawa" }
      values { key: "region" value: "on" }
      values { key: "country" value: "ca" }
    }
    rows {
      values { key: "code" value: "montreal" }
      values { key: "region" value: "qc" }
      values { key: "country" value: "ca" }
    }
    rows {
      values { key: "code" value: "munich" }
      values { key: "region" value: "by" }
      values { key: "country" value: "de" }
    }
    rows {
      values { key: "code" value: "leipzig" }
      values { key: "region" value: "sn" }
      values { key: "country" value: "de" }
    }
  }
}
```

### External Secondary Instances

An external secondary instance references an out-of-band dataset via `uri`:

```textproto
secondary_instances {
  id: "countries"
  uri: "jr://file-csv/countries.csv"
}
```

Supported external formats include:

1.  **CSV files** (`jr://file-csv/...`).
2.  **GeoJSON files** (`jr://file/...` or `jr://file-geojson/...` with
    `.geojson` extension).
3.  **XML files** (`jr://file/...` with `.xml` extension, for legacy XForms
    compatibility).

#### Hosted Entity Datasets and Database-Backed Runtimes

In hosted, database-backed survey platforms (such as Ground), secondary
instances are **not stored as static CSV or GeoJSON files**. Instead, external
datasets are managed at the survey level as structured **Entity Datasets**
(`EntityDatasetDef` populated with versioned `EntityRecord` rows).

*   **Implicit Runtime Binding**: When evaluating XPath expressions such as
    `instance('stations')/root/item[code='CH-ZRH']/station_name` or
    `pulldata('stations', 'station_name', 'code', 'CH-ZRH')`, the client runtime
    resolves `SecondaryInstance.id` (`"stations"`) directly against its local
    indexed entity database (e.g., SQLite or IndexedDB) without materializing a
    CSV file on disk.
*   **XForms / XLSForm Round-Tripping**: The `uri` field (e.g.,
    `jr://file-csv/stations.csv`) acts as a logical binding and serialization
    descriptor. When importing an XLSForm (`select_one_from_file stations.csv`),
    the platform ingests the attachment into a managed Lookup Table; when
    exporting to XForms, the platform emits `<instance id="stations"
    src="jr://file-csv/stations.csv"/>` and dynamically serializes the Lookup
    Table records into a companion CSV or GeoJSON attachment.

#### CSV Secondary Instances

External CSV datasets are referenced via the `jr://file-csv/` URI prefix:

```textproto
secondary_instances {
  id: "stations"
  uri: "jr://file-csv/stations.csv"
}
```

Processors parse CSV files with comma, semicolon, or tab delimiters into
structured rows where each column header becomes a field name. For example:

```csv
code,station_name,elevation_m,location
CH-ZRH,Zurich Central,408,47.3779 8.5402 408 0
CH-GVA,Geneva Cornavin,375,46.2102 6.1424 375 0
```

This CSV document maps directly to the following structured dataset:

```textproto
rows {
  values { key: "code" value: "CH-ZRH" }
  values { key: "station_name" value: "Zurich Central" }
  values { key: "elevation_m" value: "408" }
  values { key: "location" value: "47.3779 8.5402 408 0" }
}
rows {
  values { key: "code" value: "CH-GVA" }
  values { key: "station_name" value: "Geneva Cornavin" }
  values { key: "elevation_m" value: "375" }
  values { key: "location" value: "46.2102 6.1424 375 0" }
}
```

#### GeoJSON Secondary Instances

GeoJSON feature collections are referenced using `jr://file/` or
`jr://file-geojson/` URIs ending in `.geojson`:

```textproto
secondary_instances {
  id: "parcels"
  uri: "jr://file/parcels.geojson"
}
```

GeoJSON `Feature` entries are parsed into structured rows with their
`properties` and typed geometry (`Point` mapped to `GeoPoint`, `LineString` to
`GeoTrace`, `Polygon` to `GeoShape`).
