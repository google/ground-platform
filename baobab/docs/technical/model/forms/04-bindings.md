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

# Bindings and Field Rules

A `FieldBinding` message specifies the validation rules, data types,
calculations, and behavior associated with a field in the primary record. A
binding connects a field path in the record with its operational logic and
(optionally) its presentation in the [View](#view).

The following snippet illustrates `FieldBinding` definitions in Protocol Buffer
text format:

```textproto
bindings {
  field_path: "intro_banner"
  type: STRING
  readonly_expr: "true()"
}
bindings {
  field_path: "text_fields/short_note"
  type: STRING
}
bindings {
  field_path: "text_fields/detailed_notes"
  type: STRING
}
bindings {
  field_path: "numeric_fields/quantity"
  type: INT32
  constraint_expr: ". < 100"
  constraint_message {
    text: "Quantity must be under 100"
  }
}
bindings {
  field_path: "numeric_fields/measurement"
  type: DECIMAL
  constraint_expr: ". > 1.50 && . < 9.50"
  constraint_message {
    text: "Measurement must fall between 1.50 and 9.50"
  }
}
bindings {
  field_path: "schedule/appointment_date"
  type: DATE
  constraint_expr: ". >= today()"
  constraint_message {
    text: "Appointment date cannot be in the past"
  }
}
bindings {
  field_path: "schedule/appointment_time"
  type: TIME
}
bindings {
  field_path: "schedule/timestamp"
  type: DATETIME
}
bindings {
  field_path: "choices/category"
  type: STRING
  constraint_expr: "not(selected(., 'opt_a') && selected(., 'opt_b'))"
  constraint_message {
    text: "Options A and B are mutually exclusive"
  }
}
bindings {
  field_path: "geo/station_point"
  type: GEOPOINT
  constraint_expr: "selected-at(., 3) <= 5.0 && distance(., /data/target_site) <= 15.0"
  constraint_message {
    text: "GPS precision must be within 5 meters and 15 meters of the site"
  }
}
bindings {
  field_path: "geo/route_path"
  type: GEOTRACE
}
bindings {
  field_path: "geo/parcel_boundary"
  type: GEOSHAPE
}
bindings {
  field_path: "media/photo_attachment"
  type: BINARY
  max_pixels: 1024
}
bindings {
  field_path: "media/voice_memo"
  type: BINARY
}
bindings {
  field_path: "media/clip_video"
  type: BINARY
}
bindings {
  field_path: "media/scanned_code"
  type: BARCODE
}
bindings {
  field_path: "display/confirm_ack"
  type: STRING
  required_expr: "true()"
}
```

## Field Binding Attributes

The following fields are supported on `FieldBinding`:

| Field                | Type              | Description                 |
| -------------------- | ----------------- | --------------------------- |
| `field_path`         | `string`          | Relative or absolute path   |
:                      :                   : to the field within the     :
:                      :                   : record schema (e.g.,        :
:                      :                   : `"given_name"`,             :
:                      :                   : `"member/name"`,            :
:                      :                   : `"/household/member/age"`). :
:                      :                   : [required]                  :
| `type`               | `DataType`        | Assigned field data type.   |
:                      :                   : See [Data                   :
:                      :                   : Types](#data-types).        :
| `readonly_expr`      | `string`          | Boolean expression          |
:                      :                   : indicating whether user     :
:                      :                   : input is disabled. Defaults :
:                      :                   : to `false`.                 :
| `required_expr`      | `string`          | Boolean expression          |
:                      :                   : indicating whether a        :
:                      :                   : non-empty value is          :
:                      :                   : required. Defaults to       :
:                      :                   : `false`.                    :
| `relevant_expr`      | `string`          | Relevancy (skip logic)      |
:                      :                   : boolean expression. When    :
:                      :                   : `false`, the field/group is :
:                      :                   : hidden and omitted from     :
:                      :                   : submission.                 :
| `constraint_expr`    | `string`          | Validation expression       |
:                      :                   : evaluating acceptable       :
:                      :                   : inputs. Evaluated only when :
:                      :                   : the field is non-empty.     :
| `calculate_expr`     | `string`          | Expression to automatically |
:                      :                   : compute the value of the    :
:                      :                   : field.                      :
| `save_incomplete`    | `bool`            | Persists an intermediate    |
:                      :                   : draft whenever navigation   :
:                      :                   : enters this field.          :
| `required_message`   | `LocalizedString` | Custom error message when   |
:                      :                   : the `required_expr` check   :
:                      :                   : fails.                      :
| `constraint_message` | `LocalizedString` | Custom error message when   |
:                      :                   : `constraint_expr` evaluates :
:                      :                   : to false.                   :
| `preload`            | `PreloadDef`      | Preloader configuration for |
:                      :                   : automated metadata          :
:                      :                   : extraction. See             :
:                      :                   : [Preloaders](#preloaders).  :
| `max_pixels`         | `int32`           | Maximum long-edge pixel     |
:                      :                   : resolution for image        :
:                      :                   : attachments; images are     :
:                      :                   : resized proportionally on   :
:                      :                   : upload.                     :
| `entity_save_to`     | `string`          | Target Entity property name |
:                      :                   : when publishing to an       :
:                      :                   : Entity Dataset (see         :
:                      :                   : [Entities](#entities)).     :

## Data Types

ProtoForms defines strongly typed representations for all data types:

ProtoForms `DataType` | Protocol Buffer Representation | Description
--------------------- | ------------------------------ | -----------
`STRING`              | `string`                       | UTF-8 encoded text.
`INT32`               | `int32` / `sint32`             | 32-bit signed integer.
`INT64`               | `int64` / `sint64`             | 64-bit signed integer.
`DECIMAL`             | `double` / `float`             | Double-precision floating point number.
`BOOLEAN`             | `bool`                         | Native boolean (`true` / `false`).
`DATE`                | `google.type.Date`             | Calendar date (year, month, day) without timezone.
`TIME`                | `google.type.TimeOfDay`        | Time of day (hours, minutes, seconds, nanos).
`DATETIME`            | `google.protobuf.Timestamp`    | Point in time (seconds and nanos since Unix epoch), with optional timezone offset metadata.
`GEOPOINT`            | `GeoPoint` message             | Latitude, longitude, altitude (meters), and accuracy (meters).
`GEOTRACE`            | `GeoTrace` message             | Sequence of 2 or more GeoPoints forming an open polyline.
`GEOSHAPE`            | `GeoShape` message             | Closed polygon of 3 or more GeoPoints where first point equals last point.
`BINARY`              | `string` / `FileReference`     | Filename or URI pointing to media attachment (image, audio, video).
`BARCODE`             | `string`                       | Barcode or QR code payload.
`INTENT`              | `string`                       | Action descriptor targeting [external applications](#declaring-external-application).

## XPath Expressions and Path Addressing

ProtoForms evaluates XPath expressions against the typed record hierarchy and
secondary instances.

### Path Resolution

Field paths address scalar and composite nodes within the primary record or a
secondary lookup table. Engines evaluate both absolute paths (rooted at the top
level of the record) and relative paths (resolved against the active evaluation
context node). The shorthand steps `.` (current context node) and `..`
(enclosing parent message) can appear anywhere in a path expression:

*   `.` — Current field or group node
*   `..` — Enclosing parent group or repeat node
*   `/` — Root record container
*   `field_name` — Direct child field
*   `/household/member/given_name` — Rooted absolute path
*   `../sibling_field` — Sibling field relative to parent
*   `./nested_group/value` — Explicit relative path

### Operators and Type Coercion

ProtoForms supports the full set of standard XPath 1.0 operators:

*   **Union**: `|`
*   **Logical**: `and`, `or`
*   **Arithmetic**: `+`, `-`, `*`, `div`, `mod`
*   **Comparison**: `=`, `!=`, `<`, `<=`, `>`, `>=`

Because ProtoForms stores fields in native Protocol Buffer types (`int32`,
`double`, `bool`, `Date`, `Timestamp`), arithmetic and comparisons execute
natively without string-coercion ambiguity. Date and timestamp values can also
be converted to numeric epoch days via [`number()`](#number-functions) for
arithmetic comparisons.

### Predicates and Axes

Bracketed filter predicates (`[expr]`) narrow node-sets according to boolean or
positional criteria. Path traversal supports three XPath axes:

*   `self` (`.`): Current context node
*   `parent` (`..`): Enclosing message/repeat node
*   `child`: Immediate member fields of a message or repeat

## Expression Function Reference

In legacy XML-based form engines, every field value was stored as a raw string,
leading to subtle bugs (for example, `boolean("false")` evaluating to `true`).
ProtoForms expressions operate directly on strongly typed Protocol Buffer values
while providing full compatibility with standard XPath and XForms functions.

In the function signatures below:

*   `arg?` indicates an optional parameter.
*   `arg*` indicates zero or more repeated parameters.
*   `A | B` indicates alternative accepted parameter types.

### String Functions

| Function                    | Return Type | Behavior                         |
| --------------------------- | ----------- | -------------------------------- |
| `string(* arg)`             | `string`    | Converts the argument to its     |
:                             :             : canonical UTF-8 text             :
:                             :             : representation.                  :
| `concat(string\|node-set    | `string`    | Concatenates all provided        |
: arg*)`                      :             : strings and node-set text values :
:                             :             : into a single string.            :
| `join(string sep, node-set  | `string`    | Concatenates node values         |
: nodes*)`                    :             : separated by the delimiter       :
:                             :             : string `sep`.                    :
| `substr(string val, number  | `string`    | Extracts a substring starting at |
: start, number end?)`        :             : 0-based index `start` up to (but :
:                             :             : excluding) `end`.                :
| `substring-before(string    | `string`    | Returns the portion of `target`  |
: target, string prefix)`     :             : preceding the first occurrence   :
:                             :             : of `prefix`.                     :
| `substring-after(string     | `string`    | Returns the portion of `target`  |
: target, string suffix)`     :             : following the first occurrence   :
:                             :             : of `suffix`.                     :
| `translate(string src,      | `string`    | Replaces characters in `src`     |
: string from_chars, string   :             : found in `from_chars` with       :
: to_chars)`                  :             : corresponding characters from    :
:                             :             : `to_chars`.                      :
| `string-length(string arg)` | `number`    | Returns the character length of  |
:                             :             : `arg` (note\: `arg` is           :
:                             :             : mandatory).                      :
| `normalize-space(string     | `string`    | Trims leading/trailing           |
: arg?)`                      :             : whitespace and collapses         :
:                             :             : internal whitespace runs to a    :
:                             :             : single space.                    :
| `contains(string text,      | `boolean`   | Evaluates to `true` if `text`    |
: string pattern)`            :             : contains the substring           :
:                             :             : `pattern`.                       :
| `starts-with(string text,   | `boolean`   | Evaluates to `true` if `text`    |
: string prefix)`             :             : begins with `prefix`.            :
| `ends-with(string text,     | `boolean`   | Evaluates to `true` if `text`    |
: string suffix)`             :             : ends with `suffix`.              :
| `uuid(number len?)`         | `string`    | Generates an RFC 4122 v4 UUID    |
:                             :             : when called without arguments,   :
:                             :             : or a random alphanumeric token   :
:                             :             : of `len` characters.             :
| `digest(string payload,     | `string`    | Computes a cryptographic digest  |
: string algo, string         :             : (`MD5`, `SHA-1`, `SHA-256`,      :
: encode?)`                   :             : `SHA-384`, `SHA-512`) formatted  :
:                             :             : as `hex` or `base64`.            :
| `pulldata(string            | `string`    | Looks up a row in secondary      |
: instance_id, string         :             : instance `instance_id` where     :
: return_field, string        :             : `key_field == key_val` and       :
: key_field, string key_val)` :             : returns `return_field`.          :
| `base64-decode(base64Binary | `string`    | Decodes a Base64-encoded binary  |
: input)`                     :             : sequence into its UTF-8 string   :
:                             :             : representation.                  :

### Boolean Functions

| Function                    | Return Type | Behavior                         |
| --------------------------- | ----------- | -------------------------------- |
| `if(boolean cond, *         | `*`         | Evaluates `cond`; returns        |
: when_true, * when_false)`   :             : `when_true` if true, or          :
:                             :             : `when_false` otherwise           :
:                             :             : (preserving typed values).       :
| `coalesce(string first,     | `string`    | Returns the first non-empty      |
: string second)`             :             : argument, or an empty string if  :
:                             :             : both are empty.                  :
| `once(* expr)`              | `*`         | Evaluates and assigns `expr`     |
:                             :             : only when the target field is    :
:                             :             : currently empty; otherwise       :
:                             :             : retains the existing value.      :
| `true()`                    | `boolean`   | Returns boolean constant `true`. |
| `false()`                   | `boolean`   | Returns boolean constant         |
:                             :             : `false`.                         :
| `boolean(* arg)`            | `boolean`   | Coerces `arg` to a boolean value |
:                             :             : according to XPath truthiness    :
:                             :             : rules.                           :
| `boolean-from-string(string | `boolean`   | Returns `true` strictly when     |
: arg)`                       :             : `arg` is `"true"` or `"1"`;      :
:                             :             : returns `false` for all other    :
:                             :             : inputs.                          :
| `not(boolean arg)`          | `boolean`   | Negates the boolean value of     |
:                             :             : `arg`.                           :
| `regex(string target,       | `boolean`   | Returns `true` if `target`       |
: string pattern)`            :             : matches the regular expression   :
:                             :             : `pattern`.                       :
| `checklist(number min,      | `boolean`   | Verifies that the count of       |
: number max, string val*)`   :             : truthy answers lies between      :
:                             :             : `min` and `max` inclusive (`-1`  :
:                             :             : disables a bound).               :
| `weighted-checklist(number  | `boolean`   | Computes the sum of weights for  |
: min, number max, [string    :             : all truthy `val` arguments and   :
: val, number weight]*)`      :             : checks that the total lies       :
:                             :             : within `[min, max]`.             :

### Number Functions

| Function                    | Return Type | Behavior                         |
| --------------------------- | ----------- | -------------------------------- |
| `number(* arg)`             | `number`    | Converts `arg` to a numeric      |
:                             :             : value; dates and timestamps      :
:                             :             : convert to fractional days since :
:                             :             : the Unix epoch (1970-01-01 UTC). :
| `random()`                  | `number`    | Returns a pseudo-random          |
:                             :             : floating-point number in `[0.0,  :
:                             :             : 1.0)`.                           :
| `int(number arg)`           | `number`    | Truncates the fractional portion |
:                             :             : of `arg` toward zero to yield an :
:                             :             : integer.                         :
| `sum(node-set nodes)`       | `number`    | Sums numeric values across       |
:                             :             : `nodes`, treating empty or       :
:                             :             : missing nodes as `0`.            :
| `max(node-set nodes*)`      | `number`    | Returns the maximum numeric      |
:                             :             : value across all provided nodes  :
:                             :             : (`NaN` if any node is            :
:                             :             : non-numeric).                    :
| `min(node-set nodes*)`      | `number`    | Returns the minimum numeric      |
:                             :             : value across all provided nodes  :
:                             :             : (`NaN` if any node is            :
:                             :             : non-numeric).                    :
| `round(number val, number   | `number`    | Rounds `val` to the specified    |
: places?)`                   :             : number of decimal `places`       :
:                             :             : (defaults to `0`).               :
| `pow(number base, number    | `number`    | Raises `base` to the power of    |
: exp)`                       :             : `exp`.                           :
| `log(number arg)`           | `number`    | Natural logarithm ($\ln$) of     |
:                             :             : `arg`.                           :
| `log10(number arg)`         | `number`    | Base-10 logarithm of `arg`.      |
| `abs(number arg)`           | `number`    | Absolute value of `arg`.         |
| `sin(number arg)`           | `number`    | Sine of `arg` (in radians).      |
| `cos(number arg)`           | `number`    | Cosine of `arg` (in radians).    |
| `tan(number arg)`           | `number`    | Tangent of `arg` (in radians).   |
| `asin(number arg)`          | `number`    | Arc sine of `arg` in radians.    |
| `acos(number arg)`          | `number`    | Arc cosine of `arg` in radians.  |
| `atan(number arg)`          | `number`    | Arc tangent of `arg` in radians. |
| `atan2(number y, number x)` | `number`    | Two-argument arc tangent of `y / |
:                             :             : x` in radians.                   :
| `sqrt(number arg)`          | `number`    | Principal square root of `arg`.  |
| `exp(number arg)`           | `number`    | Natural exponential              |
:                             :             : $e^{\text{arg}}$.                :
| `exp10(number arg)`         | `number`    | Base-10 exponential              |
:                             :             : $10^{\text{arg}}$.               :
| `pi()`                      | `number`    | Mathematical constant $\pi       |
:                             :             : \approx 3.141592653589793$.      :

### Node-set Functions

| Function                  | Return Type | Behavior                           |
| ------------------------- | ----------- | ---------------------------------- |
| `count(node-set nodes)`   | `number`    | Total number of elements in        |
:                           :             : `nodes`.                           :
| `count-non-empty(node-set | `number`    | Number of elements in `nodes` that |
: nodes)`                   :             : contain a non-empty value.         :
| `position(node target?)`  | `number`    | Returns the 1-based repeat index   |
:                           :             : of the current context node, or of :
:                           :             : the single node `target` among its :
:                           :             : identically named siblings.        :
| `instance(string id)`     | `node-set`  | Resolves the root of secondary     |
:                           :             : instance `id`. Absolute paths      :
:                           :             : inside predicates continue to      :
:                           :             : resolve against the primary        :
:                           :             : record.                            :
| `current()`               | `node-set`  | Returns the active question's      |
:                           :             : evaluation context node when       :
:                           :             : referenced inside an               :
:                           :             : `instance(...)` filter predicate.  :
| `randomize(node-set       | `node-set`  | Randomly permutes `nodes` via the  |
: nodes, number seed?)`     :             : inside-out Fisher-Yates shuffle.   :
:                           :             : Passing `seed` produces a          :
:                           :             : deterministic permutation using a  :
:                           :             : Park-Miller PRNG (non-numeric      :
:                           :             : seeds hash via SHA-256 to a 64-bit :
:                           :             : signed big-endian integer).        :

### Date and Time Functions

| Function                    | Return Type | Behavior                |
| --------------------------- | ----------- | ----------------------- |
| `today()`                   | `string`    | Current local calendar  |
:                             :             : date formatted as       :
:                             :             : `YYYY-MM-DD`.           :
| `now()`                     | `string`    | Current timestamp in    |
:                             :             : the device's local      :
:                             :             : timezone offset (ISO    :
:                             :             : 8601).                  :
| `format-date(date val,      | `string`    | Formats `val` using     |
: string pattern)`            :             : pattern tokens (`%Y`    :
:                             :             : 4-digit year, `%y`      :
:                             :             : 2-digit year, `%m`      :
:                             :             : 0-padded month, `%n`    :
:                             :             : numeric month, `%b`     :
:                             :             : short month name, `%d`  :
:                             :             : 0-padded day, `%e`      :
:                             :             : numeric day, `%a` short :
:                             :             : weekday name) localized :
:                             :             : to the active form or   :
:                             :             : device locale.          :
| `format-date-time(dateTime  | `string`    | Formats `val`           |
: val, string pattern)`       :             : supporting all          :
:                             :             : `format-date` tokens    :
:                             :             : plus `%H` (24-hr        :
:                             :             : 0-padded hour), `%h`    :
:                             :             : (24-hr hour), `%M`      :
:                             :             : (0-padded minute), `%S` :
:                             :             : (0-padded second), and  :
:                             :             : `%3` (0-padded          :
:                             :             : milliseconds).          :
| `date(* val)`               | `string`    | Converts a numeric      |
:                             :             : epoch day count or      :
:                             :             : string into an ISO      :
:                             :             : calendar date           :
:                             :             : (`YYYY-MM-DD`).         :
| `decimal-date-time(dateTime | `number`    | Converts a timestamp to |
: val)`                       :             : fractional days elapsed :
:                             :             : since                   :
:                             :             : 1970-01-01T00\:00\:00Z. :
| `decimal-time(time val)`    | `number`    | Converts a time-of-day  |
:                             :             : value to a fractional   :
:                             :             : day in `[0.0, 1.0)`     :
:                             :             : (for example, `12\:00`  :
:                             :             : maps to `0.5`).         :

### Select, Translation, Repeat, and Geographic Functions

| Function                               | Return    | Behavior                 |
:                                        : Type      :                          :
| -------------------------------------- | --------- | ------------------------ |
| `selected(string list, string choice)` | `boolean` | Returns `true` if        |
:                                        :           : `choice` appears as a    :
:                                        :           : token in space-delimited :
:                                        :           : `list`.                  :
| `selected-at(string list, number idx)` | `string`  | Returns the token at     |
:                                        :           : 0-based index `idx` in   :
:                                        :           : space-delimited `list`   :
:                                        :           : (or `""` if out of       :
:                                        :           : bounds).                 :
| `count-selected(node target)`          | `number`  | Computes the count of    |
:                                        :           : selected choice tokens   :
:                                        :           : stored in `target`.      :
| `jr:choice-name(node target, string    | `string`  | Resolves the localized   |
: choice_val)`                           :           : display label for choice :
:                                        :           : token `choice_val` on    :
:                                        :           : question `target`.       :
| `jr:itext(string key)`                 | `string`  | Looks up the localized   |
:                                        :           : string for `key` in the  :
:                                        :           : active language          :
:                                        :           : dictionary.              :
| `indexed-repeat(node-set target,       | `string`  | Selects a specific       |
: node-set repeat1, number idx1, ...)`   :           : repeat iteration up to   :
:                                        :           : three nesting levels     :
:                                        :           : deep (e.g.               :
:                                        :           : `repeat1[idx1]/target`). :
| `area(node-set\|geoshape shape)`       | `number`  | Computes the enclosed    |
:                                        :           : surface area in square   :
:                                        :           : meters for a polygon     :
:                                        :           : `shape`.                 :
| `distance(node-set\|geoshape\|geotrace | `number`  | Computes the total       |
: path)`                                 :           : geodesic length or       :
:                                        :           : perimeter in meters      :
:                                        :           : across the coordinates   :
:                                        :           : of `path`.               :
| `geofence(geopoint pt, geoshape        | `boolean` | Evaluates                |
: polygon)`                              :           : point-in-polygon         :
:                                        :           : inclusion using          :
:                                        :           : ray-casting (`true` if   :
:                                        :           : `pt` lies inside         :
:                                        :           : `polygon`).              :
| `intersects(geoshape\|geotrace geom)`  | `boolean` | Returns `true` if any    |
:                                        :           : segments of `geom` cross :
:                                        :           : or self-intersect.       :

## Record Metadata

This section covers submission-level metadata attached to each captured record
instance. Form-level attributes (`form_id`, `version`, etc.) are documented in
[Instances and Data Model](#instances-and-data-model).

In ProtoForms, record metadata is represented as a structured `RecordMetadata`
message within the record:

```protobuf
message RecordMetadata {
  string instance_id = 1;  // Unique submission UUID
  string instance_name = 2;  // Human-readable submission title
  google.protobuf.Timestamp time_start = 3;
  google.protobuf.Timestamp time_end = 4;
  google.type.Date date_today = 5;
  string user_id = 6;
  string device_id = 7;
  string deprecated_id = 8;  // Prior instance_id if this is an edit
  string email = 9;
  string phone_number = 10;
  AuditConfig audit = 11;
}
```

### Preload Attributes

Preloaders populate metadata fields according to predetermined system hooks:

`PreloadType`       | Parameter       | Value Populated                         | Trigger Event
------------------- | --------------- | --------------------------------------- | -------------
`PRELOAD_UID`       | *(none)*        | `uuid:...`                              | `EVENT_INSTANCE_FIRST_LOAD`
`PRELOAD_TIMESTAMP` | `"start"`       | Form start timestamp (`Timestamp`)      | `EVENT_INSTANCE_FIRST_LOAD`
`PRELOAD_TIMESTAMP` | `"end"`         | Form completion timestamp (`Timestamp`) | Revalidation / Save
`PRELOAD_DATE`      | `"today"`       | Date of form entry (`Date`)             | `EVENT_INSTANCE_FIRST_LOAD`
`PRELOAD_PROPERTY`  | `"deviceid"`    | Unique device install ID                | `EVENT_INSTANCE_FIRST_LOAD`
`PRELOAD_PROPERTY`  | `"email"`       | User email address                      | `EVENT_INSTANCE_FIRST_LOAD`
`PRELOAD_PROPERTY`  | `"username"`    | Authenticated username                  | `EVENT_INSTANCE_FIRST_LOAD`
`PRELOAD_PROPERTY`  | `"phonenumber"` | Device phone number                     | `EVENT_INSTANCE_FIRST_LOAD`

### Audit Configuration

Client audit logging configuration is strongly typed via `AuditConfig`:

```protobuf
message AuditConfig {
  enum LocationPriority {
    LOCATION_PRIORITY_UNSPECIFIED = 0;
    NO_POWER = 1;
    LOW_POWER = 2;
    BALANCED = 3;
    HIGH_ACCURACY = 4;
  }

  LocationPriority location_priority = 1;
  int32 location_min_interval_seconds = 2;
  int32 location_max_age_seconds = 3;
  bool track_changes = 4;
}
```

*   `location_priority`: Location tracking accuracy mode.
*   `location_min_interval_seconds`: Minimum time interval between background
    location samples.
*   `location_max_age_seconds`: Maximum acceptable cache age for location
    readings.
*   `track_changes`: When `true`, all answer edits record `old_value` and
    `new_value` in the audit trail. (See
    [Client Audit Logs](#client-audit-logs)).
