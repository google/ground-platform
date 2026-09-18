# Future Roadmap

The ProtoForms specification is designed to bridge mobile data collection with
modern distributed cloud infrastructure. Key areas of ongoing development and
future standardization include:

1.  **gRPC Streaming Submission Protocol**: Bi-directional streaming for
    real-time field data synchronization, selective chunked media upload with
    resume capability, and push notifications for updated server-side entity
    lists.
2.  **Common Expression Language (CEL)**: Introducing CEL as a standardized,
    fast, type-safe alternative expression engine alongside XPath, allowing
    expressions to operate directly on in-memory protobuf objects without string
    conversion.
3.  **Automated Bidirectional Converters**: Open-source compiler toolchains
    translating between XLSForm / ODK XForms XML and ProtoForms definitions,
    enabling existing forms to immediately leverage proto-based clients while
    maintaining full round-trip compatibility.
4.  **Direct BigQuery & Columnar Ingestion**: Zero-ETL streaming of ProtoForms
    binary submissions directly into analytical warehouses (e.g. BigQuery,
    ClickHouse) preserving schema types and repeated structures without JSON
    flattening.
5.  **WebAssembly Form Engine**: A single high-performance C++/Rust ProtoForms
    runtime compiled to WebAssembly for web browsers and native mobile
    applications (Android / iOS).
