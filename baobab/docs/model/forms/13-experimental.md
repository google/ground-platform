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
