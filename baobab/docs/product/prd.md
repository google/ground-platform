---
# Copyright 2026 The Ground Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
onedoc_gdoc_url: https://docs.google.com/document/d/1aYEZItFSCmn1lziASTUM5YakNeaswvL65IuFDbzDgqY
onedoc_md_file_id: 5d4e7780-eb8c-4308-a9ed-558718181447
onedoc_tab_id: t.0
onedoc_tab_title: Product Requirements
onedoc_title: Product Requirements
---

# Product Requirements

**[SHARED EXTERNALLY]**

Authors: [@gmiceli](http://who/gmiceli)… \
Contributors: … \
Last modified: [2026-09-24](google-date:2026-09-24T12:00:00Z)

## Overview

### Mission & Strategic Context

The mission of **Open Foris Ground** is to democratize geospatial data collection for social and environmental impact. By removing technical barriers, centering workflows on the map, and interoperating natively with industry standards, Ground empowers local communities, smallholder cooperatives, researchers, and public institutions to advance sustainable land management, ecosystem restoration, and deforestation-free supply chains.

Following its transition from a Google-incubated initiative to a community-governed open-source platform under Open Foris in version 1.0, **Ground 2.0 represents the platform's institutional maturation**. It evolves Ground from a lightweight field mapping tool into a comprehensive, enterprise-ready survey and monitoring platform capable of supporting national inventories and strict regulatory compliance regimes such as the European Union Deforestation Regulation (EUDR), FSC certification, and carbon verification standards:

*   **True Cross-Platform Reach (Web, Android, & iOS)**: Ground 2.0 introduces full feature parity across Android and iOS alongside an expanded Web Console—unlocking regions like Viet Nam, where over 70% of forest rangers and cooperative partners rely on iOS devices.
*   **End-to-End Desk-to-Field Workflow**: By rolling **Collect Earth Online (CEO)** visual satellite interpretation directly into the Web Console, Ground 2.0 unites remote sensing assessment and offline-first ground verification within a single survey lifecycle.
*   **Industry-Standard Forms & Longitudinal Monitoring**: Ground 2.0 adopts the industry-standard **XLSForm / XForms** model, adding support for hierarchical questionnaires, skip logic, repeating loops, and persistent **Map features** that can be revisited and updated across multiple monitoring waves.
*   **Institutional Trust & Traceability**: End-to-end versioning across surveys, forms, and records—backed by immutable audit trails and offline farmer receipt PDFs—ensures every observation and boundary adjustment is verifiable.

### Foundational References

*   **[XForms Integration & Entity-First Map Architecture](xforms-integration.md)**: Architectural specification for Ground 2.0's Entity-only map model, automatic 1-Form-to-1-Entity provisioning, `save_to` schema synchronization, and XLSForm/ODK Entities mappings.
*   **[Global Impact & Deployments](?tab=t.li0p596b998q)**: Case studies, field metrics, and partner pipelines across Ghana, Burundi, Kenya, and Viet Nam.
*   **[Open Foris Ground Community Charter [SHARED EXTERNALLY]](https://docs.google.com/document/d/1pjllfws_HMuHrDDbGokPZCATgok_8vxufkSmyVM4p_Q/edit?tab=t.0#heading=h.14535shxk14t)**: Governance model, steering committee bylaws, and institutional partnership charter.
*   **[Ground 1.0 PRD [SHARED EXTERNALLY]](https://docs.google.com/document/u/0/d/1-ARlIjK4VImSrWju_5D_wZiMMCl4vAGMm4Bozdnwj30/edit)**: Baseline Ground 1.0 workflows and task model.
*   **[Ground 2026 Strategic Priorities [SHARED EXTERNALLY]](https://docs.google.com/document/u/0/d/18PKj2Qu3wRLwVS2Y8y3pdf7Zse1egIfu9JjvBX5wwR4/edit)**: Strategic roadmap for multiplatform parity, XLSForm alignment, and institutional scale.

### Guiding Product Principles

1.  **Proportional Complexity (Simple by Default, Powerful on Demand)**:
    *   *Zero Ceremony for Simple Tasks*: Launching a basic survey to drop points or walk plot boundaries takes only a few clicks with sensible defaults. Users never pay a configuration tax for advanced features they do not use.
    *   *Progressive Disclosure*: Advanced capabilities—such as nested repeat loops, cascading choice filters, custom validation rules, or multi-wave site updates—are tucked behind progressive controls until needed.
    *   *Task-Proportional Field UX*: Minimal surveys open directly into map capture in a single tap, while complex socio-economic interviews offer structured, non-linear navigation.
2.  **Built for Challenging Environments (Offline-First Resilience)**:
    *   *100% Autonomous Field Execution*: Network connectivity is only required for initial survey download and final data synchronization. Once synced, all map rendering, GPS boundary tracking, form logic, table lookups, and PDF receipt generation run completely offline.
    *   *Field Hardiness*: Designed for harsh environments, intermittent connectivity, and sudden battery loss—featuring instant local auto-save, fault-tolerant background sync, storage exhaustion safeguards, and high-contrast sunlight legibility.
3.  **Built for Our Actual Users (Real-World Digital Literacy)**:
    *   *No GIS Jargon for Organizers*: Project managers and conservation leads can design sophisticated forms visually, manage sites in spreadsheet-like tables, and review map data without writing code or mastering desktop GIS software.
    *   *Zero-Assumption Mobile Design*: Built for frontline collectors and community rangers who may be first-time smartphone users—prioritizing large touch targets, clear visual cues, haptic feedback, and forgiving on-device error correction.

---

## Platform Experience: Web Console vs. Mobile App

Ground 2.0 pairs a browser-based **Web Console** for survey design, visual satellite interpretation, and data management with a rugged, offline-first **Mobile App** (Android & iOS) for field execution:

<!-- mdformat off(b/556740108: multiline tables break in GFM and OneDoc) -->

| Capability | Web Console (Browser) | Mobile App (Android & iOS) |
| :--- | :--- | :--- |
| **Primary Personas** | Survey organizers, project supervisors, remote-sensing interpreters, desktop data clerks | Field enumerators, community rangers, agricultural extension agents, field supervisors |
| **Connectivity** | Online / hybrid desktop workspace (Chrome, Firefox, Safari, Edge) | 100% offline-first execution on phones and tablets with background auto-sync |
| **Survey & Form Design** | Visual WYSIWYG Form Designer, live Mobile & Web dual previews, starter/org templates, form copy/paste, XLSForm import/export, and `Draft` / `Active` / `Closed` lifecycle controls | Executes published forms offline; automatically adapts UI complexity to the survey |
| **Visual Classification (CEO)** | Integrated satellite photo-interpretation: probabilistic sample design, multi-temporal imagery comparison (Planet NICFI, Sentinel-2, Earth Engine), spectral time series, and multi-interpreter QA/QC | Not supported; satellite visual interpretation is a desktop workflow (flagged plots can be verified in the field) |
| **Data Collection & Spatial Capture** | In-browser form entry and interactive point, line, and polygon digitizing over satellite imagery | Walk-or-draw perimeter tracking, unified GPS/reticle pin drop, QR code geometry scanning, and organizer-enforced hardware GPS/accuracy rules |
| **Wayfinding & Real-Time Feedback** | Coordinate display, automated area/perimeter calculation, and vertex snapping | Wayfinding compass (live distance & bearing to target plot), real-time HUD (area in ha/acres, perimeter, GPS accuracy, GeoID), and haptic feedback |
| **Data Review, QA/QC, & Curation** | Synchronized table and map views (up to 1M features), vertex-level boundary editing, photo EXIF lightbox, conflict resolution queue, revision history diffs, and 30-day recovery trash | On-device submission history and field curation (edit/delete field-created sites and submissions) rendered in the collection-time form version |
| **Exports, Receipts, & Sharing** | Harmonized CSV, GeoJSON, and Shapefile exports; bulk media ZIPs; summary PDF reports; webhooks & partner APIs (FERM, Arena); Google Drive-style sharing (`Restricted`, `Link/QR`, `Public`) | 100% offline on-device summary PDF receipt generation (for WhatsApp, Bluetooth, or portable printing) and on-screen QR code geometry sharing |
| **Impact Dashboards (MAP)** | Live organization, survey, and public anonymized **MAP (Mitigation, Adaptation, Protection)** dashboards plus automated monthly sponsor digests | Automated local area, perimeter, and GeoID calculations that feed MAP impact aggregations upon sync |

<!-- mdformat on -->

---

## Core Concepts & User Mental Model

### Sites on the Map, Submissions in the Timeline

To support both single-visit surveys and multi-year monitoring campaigns (such as tracking tree survival in Kenya or annual coffee audits in Burundi) without cluttering the user experience, Ground 2.0 renders **only Map features (Entities) on the map**, while preserving **Submissions as an immutable chronological timeline** attached to each site:

1.  **Map features (Entities on the Map)**:
    *   Every spatial feature rendered on the map represents a real-world subject—such as a farm plot, forest monitoring site, or water point—with a permanent identity and current attributes.
    *   Sites may be preloaded by the organizer before fieldwork or created on the fly by field collectors.
    *   **Automatic Provisioning by Default**: In the Survey Designer, creating a new form automatically creates a backing **Map feature table** (`EntityDatasetDef`) and configures the form to append a new site on each submission while keeping the site schema synchronized as questions are added. Organizers never have to configure entity tables manually for simple surveys.
2.  **Forms & Submissions (Encounter Logs in the Site Timeline)**:
    *   Forms represent questionnaires filled out at a specific point in time. Every completed submission is preserved as a time-stamped historical record (`SubmissionRecord`) linked to its target site.
    *   Additional forms in a survey can be linked to an existing site layer so tapping a site on the map opens its current status card, its full chronological submission timeline, and one-tap follow-up actions (`[ + Inspect Site ]`, `[ + Update Info ]`).
3.  **User-Defined Workflow Progression via `save_to`**:
    *   As forms are submitted against a map feature over time (`1:N` longitudinal history), the feature's marker and boundary styling update dynamically via standard `simplestyle-spec` properties (`marker-symbol`, `marker-color`, `stroke`, `fill`) and custom status fields managed by form `save_to` actions.
    *   *Configurable Template Defaults*: While Ground 2.0 does not hardcode an internal enum of workflow states, starter templates provide intuitive defaults:
        *   *Field Surveys*: `○` (Pending) $\rightarrow$ `◐` (In Progress) $\rightarrow$ `✓` (Completed).
        *   *Desk-to-Field Verification*: `○` (Unanalyzed) $\rightarrow$ `✓` (Consensus Reached) $\rightarrow$ `!` (Flagged for Field Validation) $\rightarrow$ `?` (Disputed / Needs SME Review).

> **Cross-Platform Terminology & Parity**: See **[Terminology & Cross-Platform Parity](terminology.md)** for the complete master lexicon and parity matrix mapping Ground 2.0 across Collect Earth Online (CEO), ODK Central/Collect, KoboToolbox, XLSForm, ArcGIS Survey123, and Open Foris Arena.

---

## Product Requirements

### 1. Survey Design & Authoring (Web Console)

*   **Visual WYSIWYG Form Designer**:
    *   Build and reorder questions, nested groups, and repeating loops visually with contextual guidance cards—no coding or spreadsheet editing required.
    *   **Automatic Entity Sync**: Creating or editing a form automatically provisions and updates its backing Map feature schema (`save_to` bindings) behind the scenes, while allowing multi-form surveys to target shared site layers.
    *   **Quick-Start & Organizational Templates**: Launch simple point-drop or boundary-walk surveys in seconds using global starter templates, or save custom organizational templates to standardize surveys across teams.
    *   **Cross-Survey Form Copy/Paste**: Duplicate entire forms (preserving logic, choices, and styling) within a survey or across different surveys.
    *   **Custom Map Layer Styling**: Configure layer order, default visibility, fill/stroke colors, and marker symbols for site layers.
*   **Live Interactive Dual Previews (Mobile & Web)**:
    *   Test forms side by side in simulated **Mobile** and **Web** viewports before publishing.
    *   Interactively verify skip logic, validation constraints, calculated fields, and multi-language translations (e.g., English, French, Vietnamese, Spanish) in real time.
*   **Expressive Form Logic (XLSForm-Compliant)**:
    *   Supports conditional visibility (**Skip Logic**), input validation with localized error messages (**Constraints**), **Cascading Selects** (e.g., filtering villages by district), **Conditional Requirements**, **Background Calculations**, **Dynamic Repeat Counts**, **Variable Substitution** in labels (e.g., *"How long has ${farmer_name} managed this plot?"*), **Repeat Aggregations** (`sum`, `count`, `min`, `max`), and offline **Table Lookups** (`pulldata`).
*   **Harmonized Spatial Question Types**:
    *   **Drop a pin (Point)**: Combines GPS sensor capture with manual reticle placement.
    *   **Trace path (Line)**: Walked GPS path tracking or manual line drawing with live length calculation.
    *   **Draw or walk perimeter (Polygon)**: Walked boundary tracking or manual vertex drawing with live enclosed area (hectares/acres) and perimeter calculations.
    *   **Anti-Spoofing & Quality Policies**: Organizers can disable manual map drawing on any spatial question to require physical hardware GPS fixes on the ground, reject mock GPS locations, and enforce minimum GPS accuracy thresholds (e.g., `<= 5m`).

### 2. Field Data Collection (Mobile App — Android & iOS)

*   **Guided Offline Preparation & Storage Guardrails**:
    *   **Take Offline Workflow**: Collectors select target map areas before leaving connectivity to pre-cache multi-zoom vector and satellite basemaps alongside survey forms and site tables.
    *   **Storage Protection**: The app monitors available device storage, warns before space is exhausted, and lets users safely clear local copies of photos that have already synced to the cloud.
*   **Site-First Map Navigation & Wayfinding**:
    *   **Unified Entity-Only Map**: The map exclusively renders **Map features** (spatial entities with live workflow status badges `○` / `◐` / `✓`), eliminating clutter or duplicate pins from raw form submissions.
    *   **Line-of-Sight Wayfinding**: Selecting a target site displays a live compass heading, bearing, and "as-the-crow-flies" distance to guide collectors under dense canopy where road routing fails.
    *   **Direct Action Launch & Submission Timeline**: Tapping a site marker opens its current attributes, chronological submission history, and one-tap buttons to launch linked follow-up forms or register a new site on the spot.
*   **Real-Time Spatial HUD & Capture Guardrails**:
    *   Displays live walked perimeter, enclosed area (in metric or imperial units), horizontal GPS accuracy, and elevation during capture.
    *   Provides tactile **haptic vibration feedback** when adding points/vertices, actively prevents **self-intersecting ("bowtie") polygons**, and displays the plot's unique, deterministically computed **GeoID** immediately upon save.
*   **Offline QR Code Geometry Exchange**:
    *   **Scan to Import**: Collectors can scan a QR code from a paper land certificate or partner app (such as WHIMO) to instantly populate, validate, and render a plot polygon or point into the active question without re-walking the boundary.
    *   **Display to Share**: Any captured geometry can be displayed on-screen as a high-contrast QR code for instant, 100% offline peer-to-peer handoff.
*   **Field Usability & On-Device Curation**:
    *   **Conversational Flexibility**: Jump non-linearly between questions during interviews, switch UI and form languages on the fly, and use split-screen landscape layouts on tablets.
    *   **On-Device Self-Correction**: Collectors can edit or delete their own field-created sites and submissions directly on-device to fix mistakes immediately.
    *   **Read-Only Viewer Mode**: Supervisors or guests with `Viewer` access can browse the map, sites, and submissions on mobile without accidentally modifying data.

### 3. Visual Classification & Photo-Interpretation (Web Console)

Ground 2.0 integrates **Collect Earth Online (CEO)** directly into the Web Console so institutions can run remote satellite interpretation and ground verification in a single project:

*   **Sample Design Generation**:
    *   Built-in statistical sampling engine supporting systematic grid, simple random, stratified random, and cluster designs within uploaded regions of interest (Shapefile/GeoJSON), plus support for configurable sub-plot sample point grids (e.g., 3x3 points per plot) or importing externally generated sample designs.
*   **Multi-Temporal Imagery & Time Series**:
    *   Configure project-specific imagery layers—including high-resolution basemaps, Planet NICFI mosaics, Sentinel-2 composites, Google Earth Engine layers, and WMS/WMTS/XYZ services—with side-by-side/swipe date comparison and per-plot spectral time-series charts.
    *   Automatically logs the exact imagery source and date used for every interpreted label.
*   **High-Throughput Interpretation & Consensus QA/QC**:
    *   Keyboard-driven plot navigation, embedded reference guides, and plot flagging/skipping for ambiguous sites (which can then be routed to mobile field teams for ground-truthing).
    *   Assign plots to multiple interpreters, track inter-interpreter agreement on a **Disagreement Dashboard**, resolve conflicts, and import legacy CEO projects without losing completed work.

### 4. Data Review, Traceability, & Compliance

*   **Desktop Data Entry, QA/QC, & Interactive GIS Editing**:
    *   **Synchronized Table & Map Workspace**: Filter, sort (including by last updated date), search across attributes, and link map features to table rows via matching numerical badges.
    *   **Vertex-Level Boundary Refinement**: Drag, insert, or delete polygon vertices in the browser to correct GPS canopy drift against clear satellite imagery without re-surveying.
    *   **Media Inspection & Bulk Download**: Inspect photos in a high-resolution lightbox displaying embedded camera EXIF metadata (timestamp, device model, GPS coordinates) and bulk-download filtered media as ZIP archives.
    *   **Offline Conflict Resolution Queue**: When multiple offline collectors update the same site concurrently, all submission logs are preserved and conflicting site attribute updates are routed to a side-by-side review queue for supervisor resolution.
*   **End-to-End Versioning, Soft Delete, & Audit Trails**:
    *   **Survey Lifecycle States**: Manage surveys across **`Draft`** (design/testing, hidden from field apps), **`Active`** (open for collection), and **`Closed`** (read-only archive) states, with visual diffs before publishing form updates.
    *   **Historical Rendering**: Every submission is always viewed using the exact form version active when it was collected, preventing broken labels or mismatched answers when forms evolve.
    *   **Non-Destructive Soft Delete & 30-Day Trash**: Deleting a question retires it from future collection while preserving all historical answers. Deleted surveys, forms, and submissions move to a 30-day recovery trash before permanent removal.
    *   **Immutable Audit Log**: Every schema change, submission edit, and geometry refinement is recorded in an exportable audit log with user identity, UTC timestamp, and before/after field diffs—allowing supervisors to inspect or revert any change.

### 5. Exports, Farmer Receipts, & Ecosystem Integrations

*   **Schema-Harmonized Dataset Exports**:
    *   Export data as **CSV, GeoJSON, or Shapefile** with custom filenames and dedicated GPS accuracy and altitude columns.
    *   **Automatic Version Harmonization**: Exports automatically align submissions from older form versions to the latest published schema, with a one-click toggle to include or exclude soft-deleted questions.
*   **Standardized Summary PDFs & Offline Farmer Receipts**:
    *   Generate clean, publication-ready summary PDFs for any site or submission—featuring a high-contrast map snapshot, GeoID, calculated area/perimeter, and formatted responses.
    *   Available as individual or bulk PDF/ZIP downloads in the Web Console, and generated **100% offline on the Mobile App** so enumerators can share digital receipts via WhatsApp/Bluetooth or print physical receipts on portable Bluetooth printers at the farm gate.
*   **Partner Platform APIs & Webhooks**:
    *   Native bi-directional integration with **FAO FERM** (Framework for Ecosystem Restoration Monitoring) and **Open Foris Arena**, plus configurable real-time **Webhooks** with custom auth headers to stream incoming records into external national registries or enterprise pipelines.

### 6. Governance, Sharing, & MAP Impact Dashboards

*   **Multi-Tenant Organizations & Flexible Sharing**:
    *   Users and surveys belong to **Organizations** with pooled resource quotas.
    *   **Google Drive-Style Access Controls**: Share surveys as **`Restricted`** (explicit email ACL with bulk paste and automated email invites), **`Accessible via Link / QR Code`** (instant onboarding by scanning a QR code in the field), or **`Public`** (open directory for citizen science).
    *   **Peer Visibility & Consent**: Organizers control whether collectors can see peers' submissions on the map or only their own, and can embed custom informed-consent questions directly inside forms without rigid app-level onboarding hurdles.
*   **Built-In Impact Measurement (MAP Framework)**:
    *   Organizers tag surveys and bind spatial metrics or calculated fields to the **MAP pillars**:
        *   **Mitigation**: Hectares under restoration/afforestation, multi-wave tree survival rates, canopy density, and carbon/biomass indicators.
        *   **Adaptation**: Smallholder farmers and cooperatives registered, climate-resilient agroforestry adoption, and market-access readiness.
        *   **Protection**: Intact forest and buffer hectares monitored, deforestation-free plot boundaries verified (e.g., EUDR), disturbance alerts ground-truthed, and community tenure mapped.
    *   Powers live **Organization & Survey MAP Dashboards** in the Web Console, a **Public Anonymized MAP Dashboard**, and **Automated Monthly Sponsor Digests**.

---

## Scale, Quotas, & Service Guardrails

### Platform Scale Targets

*   **1 Million Features & Submissions**: Both the Web Console and Mobile App are engineered to smoothly render and cluster up to **1,000,000 map features** (points, lines, and polygons) and ingest up to **1,000,000 submissions** per campaign.

### Tiered Quota Limits

Usage is governed at the organization and user level across two tiers *(see [Future Work](?tab=t.9nlxzx2c7bzm#paid-quota-expansion--foundation-payment-model) for foundation-supported quota expansion)*:

<!-- mdformat off(b/556740108: multiline tables break in GFM and OneDoc) -->

| Administrative Scope (Survey Organizers) | Basic Tier (Default) | Sponsored Tier |
| :--- | :--- | :--- |
| **Maximum surveys per owner** | 5 | 100 |
| **Maximum forms per survey** | 5 | 10 |
| **Maximum questions per form** | 10 | 50 |
| **Maximum photo questions per form** | 1 | 10 |
| **Maximum preloaded sites (entities)** | 1,000 | 10,000 |
| **Maximum collaborators in survey ACL** | 10 | 50 |

| Field Collection Scope (Per Data Collector) | Basic Tier (Default) | Sponsored Tier |
| :--- | :--- | :--- |
| **Maximum active surveys per user** | 5 | 100 |
| **Maximum field-created sites per user** | 100 | 200 |
| **Maximum submissions per preloaded site per user** | 10 | 50 |

<!-- mdformat on -->

### Universal Design & Input Guardrails

*   **Text Length Limits**: Survey/form titles up to **100 characters**; descriptions, choice labels, and site properties up to **255 characters**; instruction notes up to **1,024 characters**; open text responses up to **100 characters**.
*   **Geometry & Media Limits**: Up to **50 vertices per polygon**; photos automatically scaled up to **48 megapixels** while preserving full camera EXIF and GPS metadata.

---

## Not in Scope & Future Work

To keep Ground 2.0 focused on cross-platform reliability, XLSForm compatibility, and visual classification parity, the following items are **out of scope for 2.0** and deferred to **[Future Work](?tab=t.9nlxzx2c7bzm)**:

*   **Direct Database Streaming (P3)**: Live two-way sync with Google Sheets, BigQuery, or Earth Engine feature tables (use CSV/GeoJSON/Shapefile exports, webhooks, or REST APIs instead).
*   **Custom Mobile Tile Services & Offline Drone Rasters (P3)**: Custom WMS/XYZ layers are supported in the Web Console for visual classification, while Mobile App offline basemaps standardize on built-in vector and satellite tiles (no sideloaded MBTiles, COGs, or UAV orthomosaics on mobile).
*   **Dynamic On-Map Heatmaps & Custom BI Charts (P3)**: Beyond the built-in **MAP Impact Dashboards**, **Per-Plot Spectral Time Series**, and **Visual Classification QA/QC Dashboards**, ad-hoc statistical charting is left to external GIS/BI tools.
*   **Refreshed Public Website**: See the **[Website Outline](?tab=t.2eof4y51gq64)** tab for the proposed `groundplatform.org` structure and copy strategy.
