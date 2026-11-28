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

# Geometry

Collect One stores vector geometry in one canonical form, `Geometry` (defined in
`geometry.proto`): a [GeoJSON](https://datatracker.ietf.org/doc/html/rfc7946)
geometry in WGS 84 longitude and latitude, with an explicit type.

## Schema Definition

```protobuf
enum GeometryType {
  GEOMETRY_TYPE_UNSPECIFIED = 0;
  GEOMETRY_TYPE_POINT = 1;
  GEOMETRY_TYPE_LINE_STRING = 2;
  GEOMETRY_TYPE_POLYGON = 3;
  GEOMETRY_TYPE_MULTI_POINT = 4;
  GEOMETRY_TYPE_MULTI_LINE_STRING = 5;
  GEOMETRY_TYPE_MULTI_POLYGON = 6;
}

message Position {
  double longitude = 1;
  double latitude = 2;
  optional double altitude_meters = 3;
}

message Geometry {
  GeometryType type = 1;
  oneof coordinates {
    Position point = 2;
    PositionList line_string = 3;
    PolygonCoordinates polygon = 4;
    PositionList multi_point = 5;
    MultiLineStringCoordinates multi_line_string = 6;
    MultiPolygonCoordinates multi_polygon = 7;
  }
}

message BoundingBox {
  Position southwest = 1;
  Position northeast = 2;
}
```

It maps one to one onto GeoJSON:

| GeoJSON | `Geometry` |
| :--- | :--- |
| `"type": "Polygon"` | `type: GEOMETRY_TYPE_POLYGON` |
| `"coordinates": [[[lng, lat], …]]` | `polygon { rings { positions { longitude, latitude } … } }` |

*   **Positions** are longitude first, like GeoJSON, in decimal degrees.
*   **Polygon rings** are closed: the last position repeats the first
    (RFC 7946 section 3.1.6). The exterior ring comes first, then any holes.
*   **Bounding boxes** are GeoJSON `bbox`es (RFC 7946 section 5):
    `southwest` holds the lowest longitude and latitude and `northeast` the
    highest. Boxes that cross the antimeridian aren't supported yet.
*   **`type` and `coordinates` agree.** Writers set both; readers treat a
    geometry whose `type` names another case than the one set as invalid.
    `GeometryProtos.typeOf` in `shared/core` checks this.

## Where It Is Used

| Field | Holds |
| :--- | :--- |
| `EntityRecord.geometry` | The geometry of a map feature; unset for rows of a Data table |
| `LayerDef.geometry_type` | The type of the geometries a map layer draws |
| `SubmissionRecord.location` | A Point: where the collector was when recording the submission |
| `SubmissionGeometryPolygonDef.geometry` | A geometry answer recorded in a submission |
| `DataTableRow.geometry` | A Map layer row of the Survey editor, of its dataset's type (a Point row with several vertices is a MultiPoint) |
| `SurveyArea.boundary` | A MultiPolygon, one polygon per part, exterior rings only |
| `PlotRef.geometry` | The Point or Polygon a geo analysis request draws, when the stored feature has none |

Single positions use `Position` (longitude, latitude), the type of a Point's coordinates:
`SurveyArea.center`, `MapConfig.initial_center`, `PlaceRecord.position`, and
`EntityCluster.centroid`.

Bounds use `BoundingBox`: `PlaceRecord.bounds` (the geocoder's box of a place),
and the `bbox` filters of `ListEntitiesRequest` and `ListEntityClustersRequest`.

The sample seed, map layer imports (projected from the Survey editor's rows),
features created from forms (`save_to` the `geometry` property), and every
mapper write this form. `GeometryProtos` (`shared/core`) builds and reads it;
the app maps it to the map's `Geometry` with `GeometryMapper`
(`:shared:app:data`), keeping every part of a multi-part geometry: the map
draws, frames, and hit-tests all of its parts. File imports still split
multi-part geometries into one feature per part.

## Derived Display Text

Clients derive the text they show from the geometry instead of storing it:

*   **Type names** ("Point", "Line", "Polygon") come from string resources in
    every locale (`GeometryType.labelRes` in `:shared:app:ui-common`).
*   **Coordinates** are those of the geometry's representative point, as
    `0.4182°S, 36.9491°E` with the hemisphere letters of the user's language:
    `coordinatesText` (`:shared:app:ui-common`) on screen and
    `PdfReportStrings.coordinatesText` in PDF reports. `plainCoordinatesLabel`
    (`CoordinateDegrees` in `:shared:app:domain`) writes the same text with
    untranslated letters, only where a locale-neutral form is required: search
    matching, comparing displayed positions, and the name of the coordinate
    lookup result.

| Geometry | Representative point |
| :--- | :--- |
| Point | The point itself |
| LineString | The first vertex |
| Polygon | The average of the exterior ring's vertices, excluding the closing vertex |
| Multi-part | The representative point of the first part |

## Migration

Rows written before the canonical geometry are still in stored databases and
must keep decoding. Readers use the first of these that is present, and writers
store only `geometry`:

1.  **`EntityRecord.geometry`**: the canonical geometry.
2.  **`EntityRecord.point`, `trace`, or `shape`** (the `legacy_geometry`
    oneof of XForms `GeoPoint`, `GeoTrace`, `GeoShape`): converted as stored.
3.  **The legacy display label and normalized position**: older rows named
    their kind only in `geometry_type_label` (`"Point"`, `"LineString"`,
    `"Polygon"`) and placed the feature by `normalized_x` and `normalized_y`
    in its survey's sample map. The app decodes these to the shape it drew for
    them (`LegacyEntityGeometry`), so they keep their place and kind.

`LayerDef.geometry_type` likewise falls back to the layer's legacy
`geometry_type_label`.

Submissions decode like this (`LegacySubmissionGeometry`):

*   **`SubmissionRecord.location`**, else the reserved `normalized_x` (24) and
    `normalized_y` (25) projected from the survey's sample map, else the
    coordinates the reserved `coordinates_label` (23) starts with.
*   **`SubmissionGeometryPolygonDef.geometry`**, else the rectangle of the
    reserved `normalized_x`, `normalized_y`, `width_fraction`, and
    `height_fraction` (14 to 17) on the survey's sample map. An answer with
    neither is dropped.

Survey editor rows and survey areas decode like this (`SurveyGeometryMapper`):

*   **`DataTableRow.geometry`** (field 5), else the reserved `repeated
    forms.GeoPoint` vertices (field 3; the field name `geometry` moved to the
    new field).
*   **`SurveyArea.boundary`**, else the reserved `parts` (2), `forms.GeoShape`
    rings.
*   **`SurveyArea.center`** (field 7), else the reserved `forms.GeoPoint` center
    (field 4; the field name `center` moved to the new field).

Places decode like this (`ProtoRecordMapper`):

*   **`PlaceRecord.position`**, else the reserved `longitude` (9) and
    `latitude` (10) numbers. Out-of-range positions are clamped
    (`LatLng.coerced`).
*   **`PlaceRecord.bounds`**, else the reserved `bbox_min_lng`, `bbox_min_lat`,
    `bbox_max_lng`, and `bbox_max_lat` numbers (11 to 14) when all four are
    set. Edges are swapped where needed so west ≤ east and south ≤ north.
*   **`PlaceRecord.source`**, else the reserved English `source_label` (17):
    `"Places API"` is `PLACE_SOURCE_PLACES_API`; any other label leaves the
    source unknown. The UI names the source in the user's language.

`MapConfig.initial_center` replaced a `forms.GeoPoint` (field 1, reserved; the
name moved to field 6). No client reads it, so stored surveys need no legacy
decoding. `EntityCluster.centroid` (reserved 2) and the request `bbox` filters
(reserved `ListEntitiesRequest` 6 and `ListEntityClustersRequest` 3, which took
the removed `GeoBoundingBox`) are never stored, so they aren't decoded either.

`PlotRef` is only sent in requests, never stored, so its reserved `point` (2) and
`shape` (3) are not read.

The server reads only the new fields: it counts vertices for quotas from them,
and treats a stored survey without `boundary` as having no area. Server rows are
not decoded from the legacy fields because the demo database is reset to the
bundled seed at release.

The display labels `EntityRecord.geometry_type_label` (21),
`EntityRecord.coordinates_label` (24), and `LayerDef.geometry_type_label` (8),
and the positions `EntityRecord.normalized_x` (25) and `normalized_y` (26), are
reserved by number and name. Rows that still carry them decode with them kept
as unknown fields, which is where `LegacyEntityGeometry` reads the kind and
position of step 3. A row is rewritten in the canonical form the next time a
client saves it; no backfill rewrites stored rows.

The map draws, frames, and hit-tests every feature by its geometry. Where the
map session still works in its survey's normalized frame (clustering, panning,
wayfinding), it projects the geometry's representative point into it with
`SurveyMapAnchor`.

## Not Yet Converged

Every stored shape, position, and bounding box outside form answers now uses
`Geometry`, `Position`, or `BoundingBox`. What remains is deliberate:

| Type | Why it stays |
| :--- | :--- |
| `forms.GeoPoint`, `GeoTrace`, `GeoShape` | XForms geo values: form answers (`TypedValue`), audit log locations, and the `EntityRecord.legacy_geometry` oneof that old rows still carry. They follow the ODK geo types, not GeoJSON. |
| `GeoCoord` (`shared/core`) | The unvalidated coordinate the geo file readers, the sample design engine, and the impact calculations compute with. Unlike the map's `LatLng`, it holds out-of-range and NaN values, so file import can report them and engine arithmetic can step past the range edges; and `shared/core`, which the server shares, doesn't depend on the map types. Callers convert at the boundary (`LatLng.toGeoCoord()`, `GeoCoord.toLatLng()`). |
| `forms.GeoPoint` in the server's `PlotGeometry` | The Earth Engine adapter (`ImageryAnalytics`) builds its in-memory plot geometry from `forms.GeoPoint`s, converted from the canonical geometry with `GeometryProtos.toGeoPoint`. Nothing stores it; switching its factories to `Position` is a server-only cleanup. |
