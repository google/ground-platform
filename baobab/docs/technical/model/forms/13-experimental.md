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

# Experimental Features

Tooling authors and runtime engines sometimes experiment with custom
capabilities prior to formal standardization. In ProtoForms, such capabilities
should be defined through standard Protocol Buffer extensibility mechanisms
rather than ad-hoc string prefixes:

1.  **Protobuf Extension Ranges**: Fields within the extension range `50000` to
    `99999` are reserved for experimental, vendor-specific, or local deployment
    extensions.
2.  **Experimental Field Naming**: If experimental fields are introduced into
    draft `.proto` files, they must use the `experimental_` prefix (e.g.
    `experimental_offline_biometrics`).
3.  **`google.protobuf.Any` Custom Metadata**: Messages such as `FormDef`,
    `ControlDef`, and `FieldBinding` include a repeated `google.protobuf.Any
    custom_extensions` field to allow passing arbitrary typed proto payloads
    without modifying the core schema.
4.  **Graduation to Core**: Once an experimental feature has demonstrated
    interoperability and stability across at least two independent
    implementations, it can be proposed for inclusion as a core field in
    `groundplatform.v2.forms` or a subsequent major version.
