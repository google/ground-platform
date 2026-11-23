<!--
 Copyright 2026 The Collect One Authors.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# System & Framework Clients Layer

**TL;DR:** The `client` package houses thin platform-specific (`expect` / `actual`) wrappers and external service clients at the outermost boundary of the Collect One clients' Clean Architecture.

## Scope & Purpose

This layer isolates low-level OS APIs, browser/JVM runtime intrinsics, and third-party network or file-format protocols from the rest of the application. It contains:

- `auth/` (`FakeAuthClient`): Platform authentication bridge for Google sign-in and session token lifecycle.
- `location/` (`LocationClient`): Device GNSS/GPS location provider abstraction.
- `places/` (`PlacesGeocoder`, `SurveyThumbnailClient.kt`): Geocoding and reverse-geocoding client interface for map place search, and the HTTP client and Mapbox token used by survey map thumbnails.
- `offline/` (`OfflineRegionClient`, `SimulatedOfflineRegionClient`, `UnavailableOfflineRegionClient`): The port through which saved map areas download and remove their tile regions; each host plugs in its implementation through `platformModule(offlineRegionClient = …)` (simulated in the browser, unavailable on Android and iOS until their native clients land).
- `storage/` (`SqlDriverFactory`, `DeviceStorageClient`): The local database drivers, and the device's free and total storage.
- `places/` also holds `OfflineAreaNameClient`: the Mapbox Geocoding v6 reverse lookup (`permanent=true`) that names saved map areas, and the placeholder names of simulated saves.
- `pdf/` (`PdfExportClient`, `SimplePdfWriter`, `RecordPdfReports`, `PdfReportLayout`): Platform PDF file export delivery and standalone PDF document layout/rendering primitives.

## Role in the Overall Architecture

In the concentric Clean Architecture model (`Views → ViewModels → Use Cases → Repositories → Data Sources → System & Framework`), `client/` sits in the outermost **System & Framework** ring:

```
Repository Implementations (data/repository/*)
        ↓
Data Sources (data/datasource/*)
        ↓
System & Framework Clients (client/*)
```

## Dependency Rules

- **Inbound access**: Data-producing clients (`client/auth/`, `client/location/`, `client/places/`) are consumed exclusively by Data Sources in `data/datasource/`. Foreground file-delivery infrastructure (`client/pdf/PdfExportClient`) is invoked by `DataCollectionViewModel` under a documented UI-infrastructure exemption in `LayerDependencyArchitectureTest`.
- **Outbound dependencies**: Classes in `client/` must remain independent of application business rules and UI state. Enforced by `LayerDependencyArchitectureTest`, `client/` **never** imports from `domain/`, `data/`, `di/`, or `ui/`.
