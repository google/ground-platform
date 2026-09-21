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

# Introduction

The ProtoForms specification defines a standard for representing
[ODK XForms](https://getodk.github.io/xforms-spec/) as strongly typed,
well-defined [Protocol Buffers](https://protobuf.dev/) (proto3). It maintains
full semantic parity with forms and capabilities across the Open Data Kit
([ODK](https://getodk.org)) platform while replacing legacy XHTML/XML document
structures and untyped XML instances with modern, efficient, type-safe protocol
buffer definitions.

This model provides a unified, strongly-typed form definition and submission
standard that enables modern mobile, web, and server platforms to build robust,
high-performance data collection tools. Using Protocol Buffers for form
definitions and submissions offers significant advantages:

1.  **Strong Typing and Schema Safety**: Form schemas, bindings, constraints,
    and submission payloads are strictly typed, eliminating XML parsing
    ambiguities, string casting bugs, and invalid markup.
1.  **High Performance and Compact Payloads**: Protobuf binary serialization
    reduces payload size and memory overhead compared to verbose XML, saving
    bandwidth on low-connectivity mobile networks and speeding up form parsing.
1.  **Multi-Language Code Generation**: Native client libraries and data classes
    are automatically generated across Java, Kotlin, Swift, TypeScript, Python,
    Go, C++, and Rust.
1.  **Built-in Schema Evolution**: Field numbers and protobuf compatibility
    rules provide seamless forward and backward compatibility as forms and
    questions evolve over time.
1.  **Dual Binary & JSON Support**: Forms and submissions can be represented
    either as compact binary protobufs or formatted via the canonical `proto3`
    JSON mapping for web and API interoperability.

This specification targets software engineers who implement form engines, mobile
data-collection clients, schema converters, or backend storage services. Form
authors can continue designing surveys with [XLSForm](https://xlsform.org/) or
visual builders that compile directly into ProtoForms.

This standard builds on the foundations of ODK XForms, OpenRosa, and JavaRosa,
and assumes familiarity with Protocol Buffers (`proto3`).

## Round-Trip Compatibility and Metadata Preservation

ProtoForms is designed to maintain lossless round-trip compatibility with ODK
Forms (XForms XML and XLSForm). When converting between ODK Forms and
ProtoForms, or when loading, editing, and saving form definitions and records:

*   **Clients are expected to preserve all metadata even if they do not support
    all features of ODK Forms.** For example, a client that relies on secure
    wire transport (TLS/HTTPS or gRPC) rather than payload encryption, or a
    client that does not support SMS submission, must still preserve
    `SubmissionConfig.base64_rsa_public_key`, SMS prefixes/delimiters/tags,
    preload parameters, and custom extension payloads (`custom_extensions`) when
    reading and writing form definitions.
*   Unrecognized fields and extension payloads (`google.protobuf.Any`) should be
    retained intact across round trips so downstream tools and ODK-compatible
    services continue to function without data loss.
