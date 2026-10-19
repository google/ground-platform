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

# Open Foris Collect One Concept Brief

Last modified: 2026-10-14

## Executive Summary

Most Earth observation pipelines are one-way: cloud models guess from orbit,
while field teams collect disconnected surveys on the ground. **Open Foris
Collect One** (referred to as **Collect One** for short) is the natural
evolution and convergence of **Open Foris Ground** and **Collect Earth Online
(CEO)**—and the modern cloud- and mobile-native successor to legacy desktop
**Collect Earth**. By uniting **Collect Earth Online** (desk-based statistical
sample design and satellite visual classification), **Ground** (offline-first
mobile field verification), and **cloud + on-device AI** on a single data model,
Open Foris Collect One creates a continuous **ground-to-cloud feedback loop**
co-maintained by the **Food and Agriculture Organization of the United Nations
(FAO)** and **Spatial Informatics Group (SIG)** with engineering, cloud
infrastructure, and geospatial AI support from **Google**. Cloud intelligence
scans landscapes and pinpoints where satellite models are uncertain; remote
interpreters triage those plots from imagery; and field collectors—assisted by
on-device AI—verify what cannot be seen through the canopy, returning instant
proof to local communities while continuously recalibrating global models.

## Objective

Open Foris Collect One is the **enterprise maturation and unification** of the
Open Foris Ground and Collect Earth Online platforms: the release that merges
two proven, community-governed geospatial tools into one dependable,
interoperable, and scalable platform built to serve as national and
institutional infrastructure.

Ground 1.0 proved the field mission — democratizing geospatial data collection
by removing technical complexity from map-centric fieldwork. It also proved the
demand: 21,000+ cocoa polygons in Ghana, 30,000+ coffee plots in Burundi, 8,000+
plots in Kenya (a third of them mapped by cooperatives running their own
campaigns), and active EUDR pilots in Viet Nam. Meanwhile, Collect Earth Online
proved the remote-sensing mission across hundreds of institutions and 60+
UNFCCC REDD+ reporting countries.

What Ground 1.0 and standalone CEO could not do separately was carry that
combined demand forward. Ground 1.0's flat task model could not express the
hierarchical forms institutions actually use; its three parallel codebases could
not absorb a fourth (iOS) — a hard blocker in markets like Viet Nam, where over
70% of forest rangers and cooperative partners are on iPhones; its
client-enforced security and lack of versioning could not satisfy the
traceability regimes (EUDR, FSC, carbon standards) that increasingly govern the
data being collected; and maintaining separate desk (CEO) and field (Ground)
platforms forced organizations to manually export and reconcile disconnected
schemas.

**Open Foris Collect One therefore has three objectives:**

1. **Meet the standard** — Adopt the XForms/XLSForm data model wholesale so
   Collect One interoperates natively with ODK, KoboToolbox, Survey123,
   SurveyCTO, and Open Foris Arena instead of translating to and from bespoke
   schemas.
2. **Consolidate the platform** — Merge desk interpretation and mobile field
   collection onto one shared Kotlin Multiplatform core (Android, iOS, and Web)
   with a centralized, authoritative backend, cutting maintenance by >40% and
   eliminating cross-platform and desk-to-field drift.
3. **Earn institutional trust** — End-to-end versioning, immutable audit trails,
   server-enforced access control and quotas, and scale to 1M features and 1M
   submissions.

All of this stays subordinate to the product's guiding principle of
**Proportional Complexity**: dropping pins on a map must still take a few clicks,
and the full power of XLSForm and statistical sample design should appear only
when asked for.

## Why FAO, SIG, and Google Are Uniting Ground and Collect Earth Online

### A Natural Evolution of Two Complementary Platforms

Satellite observation from above and field verification on the ground are two
halves of a single monitoring workflow. **Collect Earth Online (CEO)**
established the global standard for browser-based statistical sample design,
multi-temporal satellite photo-interpretation (`5 × 5` sub-plot grids, historical
imagery sliders, spectral indices), and blind multi-interpreter QA/QC. **Open
Foris Ground** pioneered low-ceremony, map-first, offline-capable mobile field
data collection, GPS boundary walks, and longitudinal site revisits. Evolving
both into **Open Foris Collect One** turns two adjacent tools into a single
end-to-end system.

### Why FAO and SIG Are Joining Forces

*   **Eliminating the Desk-to-Field Divide**: Previously, **FAO** (steward of
    the Open Foris suite—Collect, Collect Earth, Arena, and Whisp—and UN
    custodian agency for the Framework for Ecosystem Restoration Monitoring
    [FERM] and Kunming-Montreal Global Biodiversity Framework Target 2 across
    130+ country offices) and **SIG** (lead developer and operator of Collect
    Earth Online in partnership with NASA/USGS SERVIR) maintained separate
    platforms for satellite interpretation and mobile field verification.
    National forest inventories, ministries, and NGOs had to manually export,
    convert, and stitch together disconnected questionnaires, plot registries,
    and user permissions whenever remote analysts needed field crews to verify
    ambiguous or cloud- and canopy-occluded plots.
*   **Shared Standards, Co-Maintenance, and Legacy Migration**: Both platforms
    needed to modernize around the industry-standard **XForms / XLSForm** and
    **ODK Entities** data model, institutional **Organization** hierarchies, and
    longitudinal monitoring. Rather than duplicating engineering across parallel
    web consoles, sample designers, and form engines, FAO and SIG are merging
    Ground and Collect Earth Online into one unified platform co-maintained by
    both organizations under the Open Foris governance charter. This convergence
    also provides a seamless, cloud-native migration path for over 60 countries
    transitioning off legacy desktop **Collect Earth** ahead of the June 2027
    Google Earth Pro deprecation.

### Why Google Is Well-Positioned to Help

*   **Incubation Heritage, Cross-Platform Engineering, and Low-Ceremony UX**:
    Having originally incubated Ground and transitioned it to Open Foris
    governance, Google brings deep software engineering investment in a unified
    **Kotlin Multiplatform (KMP)** and **Compose Multiplatform** architecture
    spanning Android, iOS, and Web. Google's Material 3 **"Proportional
    Complexity"** UX design makes the platform accessible to first-time
    smartphone users and GIS specialists alike, backed by offline-first
    vector/raster caching, schema-versioned audit logs, and on-device offline
    PDF/QR compliance receipts.
*   **Bridging Planetary Cloud Infrastructure & Geospatial AI with Ground Truth**:
    Google operates **Google Earth Engine**, **BigQuery**, and **AlphaEarth
    Foundations** (multi-modal geospatial embeddings and classification models).
    Because offline mobile field collection and multi-interpreter campaign
    management lie outside the product scope of standalone Google Earth and
    Earth Engine, Google is uniquely positioned to connect orbital cloud AI with
    Open Foris Collect One—routing scarce human desk and field attention to
    high-uncertainty plots (such as sub-canopy degradation or shade-grown
    agroforestry) and feeding verified ground truth back into global climate,
    biodiversity, and deforestation-free supply-chain models.

## Key Improvements Over Ground 1.0

<!-- mdformat off -->

| # | Dimension | Ground 1.0 | Open Foris Collect One |
| - | --------- | ---------- | ---------------------- |
| 1 | **Form model** | Ordered flat list of "tasks"; no groups, no repeats, no logic | Hierarchical XForms tree (Question / Group / Repeat / Note) with offline XPath `relevant`, `constraint`, `choice_filter`, `calculate`, `repeat_count`, `pulldata()`, and aggregate functions |
| 2 | **Platform coverage** | Separate Android, Web, and backend codebases; no iOS | One shared KMP core (`shared/core`, `shared/ui`, `shared/mobile`) driving Android, iOS, and web; thin platform wrappers only |
| 3 | **Data model** | Site-level submissions; no persistent subject identity | **Data tables / Map layers** (tabular and geospatial shared entities) vs. **Forms** (immutable event records), with 1:N longitudinal linking, `save_to` state progression, and on-the-fly field entity creation |
| 4 | **Traceability** | Overwrite-in-place edits; no history | Immutable survey/form/data revisions, Draft→Active→Closed publishing states, universal soft delete with 30-day trash, and an append-only audit log with field-level deltas |
| 5 | **Authoring** | Schema-oriented task editor | Visual WYSIWYG Form Designer with live dual (mobile + web) previews, interactive logic testing, translation preview, visual version diffs, templates, and form copy/paste |
| 6 | **Web console** | Survey design and read-only review | A full desktop workspace: in-browser data entry, vertex-level geometry editing, synchronized table/map views, photo lightbox with EXIF, bulk media export, conflict resolution queue, and 1M-feature rendering |
| 7 | **Backend** | Client-enforced rules, direct client writes | Centralized service with authoritative ACLs, quota enforcement, schema-version binding, server-side conflict detection, export harmonization, and high-throughput ingestion workers |
| 8 | **Interoperability** | Bespoke schema; manual export | Zero-config XLSForm/XForms round-trip plus first-class APIs and webhooks for FERM and Open Foris Arena |
| 9 | **Governance** | Per-survey ACLs only | Multi-tenant Organizations with pooled quotas, Drive-style general access (Restricted / Link & QR / Public), peer visibility controls, and email invitations |
| 10 | **Field capability** | GPS capture and basic tasks | Unified pin capture, walk-or-draw perimeters and traces, live HUD (area, perimeter, accuracy, GeoID), line-of-sight waypoint navigation (live bearing and distance to target), organizer-enforced accuracy and hardware-GNSS policies, QR geometry exchange, on-device curation, and offline PDF receipts |
| 11 | **Visual classification** | Field collection only | Collect Earth Online merged with Ground into Collect One: web-based photo-interpretation, sample design, and QA/QC in the Web Console |
| 12 | **Impact measurement & dashboards** | Manual, post-export reporting | Built-in **MAP (Mitigation, Adaptation, and Protection)** impact framework: survey/indicator tagging and live organization & public dashboards |

<!-- mdformat on -->

### Why these improvements compound

The improvements are not a feature list; they reinforce each other. Adopting
XForms is what makes interoperability mechanical rather than bespoke. The shared
core is what makes the same form engine run identically in the field, in the
browser, and in the designer's live preview. The entity/submission split is what
makes longitudinal monitoring, workflow state on the map, and visual
classification all expressible without new primitives.

<!-- mdformat off -->

| Foundation | What it unlocks |
| ---------- | --------------- |
| **XForms / XLSForm hierarchical model** | Native interoperability (ODK, Kobo, Survey123, Arena); one expression language across all clients |
| **Shared multiplatform core** | Android + iOS + web feature parity; the WYSIWYG designer's live preview runs the *same* engine as the field |
| **Data tables / Map layers vs. Forms (entities vs. events)** | Longitudinal M&E with 1:N submissions; map workflow state; visual classification (plots + interpretations) with no new primitives |
| **Centralized backend** | Versioning, audit trails, quota enforcement, and scale to 1M features / 1M submissions |

<!-- mdformat on -->

## Visual Classification (CEO Parity on Web)

Open Foris Collect One targets **functional parity with Collect Earth Online
(CEO) for web-based visual classification**, bringing remote
photo-interpretation into the same platform as field collection.

### Rationale

- **One Open Foris platform, two halves of the same workflow.** CEO labels what
  can be seen from above; Ground records what is verified on the ground.
  Historically these were two tools with two project models, two questionnaire
  formats, and two user accounts, bridged by an API. Uniting CEO and Ground into
  Open Foris Collect One removes the seam entirely for the many institutions —
  FAO, SIG, SERVIR, national forest inventories — that already run both.
- **The Collect One architecture already supplies the primitives.** Visual
  classification maps cleanly onto the model described above: a **sample plot**
  is a row in a geospatial **Table** (entity dataset); an **interpretation
  questionnaire** is a **Form** with XLSForm logic; a **label** is a
  **Submission** bound to its collection-time form version; **multi-interpreter
  review** is the 1:N entity-to-submission relationship the model already
  guarantees; and **disagreement review** leverages a dedicated consensus-evaluation
  workflow and QA/QC dashboard (distinct from the offline sync conflict queue).
  Parity is largely new UI and imagery plumbing over existing foundations, not a
  second data model.
- **Ground truth and remote labels become directly comparable.** Field
  submissions and interpreted labels sharing one schema, one versioning scheme,
  and one audit log makes accuracy assessment and model training data a query
  rather than a reconciliation exercise.

### In scope for parity

1. **Sample design generation** — Probabilistic designs (systematic grid,
   simple/stratified random, cluster) bounded by user-defined ROIs,
   administrative boundaries, or uploaded Shapefile/GeoJSON; plot-level and
   sub-plot sample point grids; plot CSV/GeoJSON upload for externally generated
   designs.
2. **Imagery integration** — Configurable imagery sources per project, including
   public basemaps, WMS/WMTS/XYZ/TMS tile services, Planet NICFI monthly
   mosaics, Sentinel-2 composites, and Earth Engine-served layers; multi-temporal
   time-series comparison and side-by-side imagery switching.
3. **High-throughput interpretation UI** — Keyboard-driven plot-to-plot
   navigation, sample point labeling against the project questionnaire,
   flag/skip/revisit controls, zoom and pan aids, and embedded organizer-authored
   learning materials and interpretation guidance.
4. **Time-series and indicator widgets** — Geo-Dash-equivalent panels showing
   per-plot spectral and index time series to support change and degradation
   calls.
5. **QA/QC and consensus** — Assignment of plots to multiple interpreters,
   disagreement dashboards, per-interpreter statistics and productivity metrics,
   convergence-of-evidence checks against external datasets or model outputs, and
   imagery traceability (which imagery informed which label).
6. **Unified survey management** — Visual classification projects are Collect One
   surveys: same organizations, ACLs, sharing model, versioning, audit log,
   quotas, and export pipeline (CSV / GeoJSON / Shapefile).
7. **CEO project migration** — An import path for existing Collect Earth Online
   deployments covering projects, plots and sample points, survey
   questionnaires, and previously collected labels, so institutions can move
   active campaigns onto Collect One without re-interpreting completed work.

### Out of scope

- **Mobile offline custom basemaps.** Mobile offline caching is standardized on
  Mapbox vector and raster tiles; user-supplied MBTiles, COGs, and drone
  orthomosaics are not supported.
- **Visual classification on mobile.** Interpretation is a Web Console workflow.
- **Automated/AI classification.** No model inference, auto-labeling, or plot
  recommendation; the platform captures human interpretation.
- **Direct database streaming integrations** (Earth Engine feature tables,
  BigQuery, Google Sheets) and **general-purpose BI dashboards** beyond the
  visual classification QA/QC views and the **MAP impact dashboards** below.

### Delivery risk

This expansion adds a new persona (the remote interpreter), a new imagery
subsystem, and a statistically non-trivial sampling engine to a release already
carrying a data model rewrite and a multiplatform consolidation. It should be
sequenced **after** the shared core, entity model, and Web Console foundations
land, and is the most credible candidate for descoping if the release is at
risk.

## MAP Impact Measurement & Dashboarding

To demonstrate measurable climate, ecological, and social outcomes without
requiring external BI pipelines, Open Foris Collect One introduces built-in
impact measurement and dashboarding structured around the **MAP framework
(Mitigation, Adaptation, and Protection)**:

<!-- mdformat off -->

| MAP Pillar | Focus | Representative Open Foris Collect One Indicators |
| ---------- | ----- | ------------------------------------------------ |
| **Mitigation** | Reducing or sequestering greenhouse gas emissions | Hectares under restoration/afforestation, tree survival rates across multi-wave surveys, shade-tree canopy density, and calculated biomass/carbon stock indicators |
| **Adaptation** | Strengthening ecosystem and smallholder climate resilience | Smallholder farmers and cooperatives registered, climate-resilient crop and agroforestry practices adopted, soil/water conservation coverage, and verified market-access readiness |
| **Protection** | Conserving standing forests, biodiversity, and community tenure | Hectares of intact forest and buffer zones monitored, deforestation-free plot perimeters verified (e.g., EUDR compliance), disturbance alerts ground-truthed, and community tenure boundaries documented |

<!-- mdformat on -->

- **Survey & indicator alignment** — During survey setup and form authoring,
  organizers tag surveys with one or more **MAP pillars** (`Mitigation`,
  `Adaptation`, `Protection`) alongside thematic domains, and optionally bind
  spatial geometries (mapped hectares, verified entities) and form questions or
  `calculate` expressions to standardized MAP indicators.
- **Organization & survey MAP dashboards (Web Console)** — Live in-app
  dashboards aggregate spatial footprints and submission metrics across both
  field collection and visual classification, displaying real-time progress per
  survey and across an organization's portfolio by MAP pillar, geography, time
  window, and commodity.
- **Public anonymized MAP dashboard & sponsor digests** — The public platform
  dashboard and automated monthly sponsor digests roll up privacy-preserving
  aggregate impact totals across Mitigation, Adaptation, and Protection to give
  steering committee partners, ministries, and donors continuous visibility into
  verified outcomes.

## Roles & Responsibilities

> **Note (Status of This Section)**: This concept brief is a planning and
> discussion document. The division of work described below reflects the project
> team's current working assumptions about how each organization expects to
> participate. It is not binding on anyone, creates no obligations, and does not
> commit any organization to provide funding, staffing, hosting, or support.
> Approvals, sign-offs, and comments recorded on this document are directional
> input only. Roles described here are expected to evolve as planning
> progresses.

Open Foris Collect One is a community-governed open-source project under the
Open Foris initiative, directed by a multi-stakeholder steering committee per
the
[Open Foris Ground Community Charter](https://docs.google.com/document/d/1pjllfws_HMuHrDDbGokPZCATgok_8vxufkSmyVM4p_Q/edit#heading=h.14535shxk14t)
and co-maintained by **FAO** and **SIG** with **Google**'s engineering and
architectural support. For planning purposes, Open Foris Collect One currently
anticipates the following division of hosting and engineering focus across the
three organizations:

<!-- mdformat off -->

| Organization | Anticipated hosting | Anticipated engineering focus | Expected nature of participation |
| ------------ | ------------------- | ----------------------------- | -------------------------------- |
| **Google** | — | Maintainer of the GitHub repositories: code review, release tagging, CI/CD, dependency and security updates, issue triage, and open-source stewardship (licensing, contribution guidelines, community process) | **Best-effort advisor and maintainer.** Architectural and product guidance, design review, and repository maintenance contributed on a best-effort basis as resources allow |
| **SIG** (Spatial Informatics Group) | **Development and staging** environments | Shared data module (shared multiplatform core, data model, sync, and persistence), sample design module, and visual classification module | Expected to contribute dedicated engineering resources, subject to confirmation and available funding |
| **FAO** | **Production** instance | Web dashboard (Web Console) and mobile experiences (Android and iOS clients) | Expected to contribute dedicated engineering resources, subject to confirmation and available funding |

<!-- mdformat on -->

### Environment promotion

Under the arrangement sketched above, code would land in Google-maintained
repositories, be exercised in SIG-hosted development and staging environments,
and be promoted to an FAO-hosted production instance. Every deployment would
therefore cross at least one organizational boundary.

### Open coordination topics

The anticipated split of work does not line up neatly with the project's
technical dependencies, so the following interfaces are worth settling
explicitly rather than assuming:

- **Shared core API compatibility (SIG → FAO).** Under the division above, SIG
  would maintain the shared multiplatform core that the web and mobile clients
  are built on. This is the single highest-traffic dependency in the project,
  and the group will need to agree on an API stability policy, semantic
  versioning, deprecation windows, and a release cadence the client teams can
  plan against.
- **Visual classification spans both areas.** The interpretation and sample
  design modules fall to SIG in the sketch above, but they surface through the
  Web Console. Which group builds the interpretation UI itself — module or
  dashboard — is still an open question.
- **Staging-to-production promotion.** Validation in staging and operation of
  production are anticipated to sit with different organizations. Release
  sign-off criteria, rollback authority, and how regressions found in production
  get handled would need to be worked out.
- **Production operations.** Incident response, on-call, uptime expectations,
  backup and restore, and data residency for a production instance are not
  addressed by the engineering focus areas above and remain to be discussed.
- **Best-effort maintainership is a planning risk.** Repository administration,
  release engineering, and security patching are anticipated on a best-effort
  footing, while both delivery groups would depend on them continuously. Sharing
  co-maintainer permissions across the participating organizations would reduce
  the risk of releases or security fixes stalling on any single organization's
  availability.

## Success Measures

| Objective | Measure |
| --------- | ------- |
| Meet the standard | XLSForm round-trip fidelity: forms authored in XLSForm, served by ODK Central, and filled in Collect/Enketo survive import/export without information loss |
| Consolidate the platform | >40% reduction in cross-platform engineering maintenance; simultaneous Android/iOS releases; zero platform-specific logic forks |
| Earn institutional trust | Every mutation attributable in the audit log; point-in-time reconstruction of any submission; 1M features rendered and 1M submissions ingested |
| Unlock iOS markets | Feature-complete iOS client enabling Viet Nam EUDR campaigns (70%+ iOS devices) |
| CEO parity | An institution can run a full sample-design → interpretation → QA/QC → export campaign in Collect One without reaching for standalone CEO |
| MAP impact visibility | Active surveys report standardized Mitigation, Adaptation, and Protection indicators into live organizational and public impact dashboards |
| Preserve simplicity | A point-collection survey remains deployable in a few clicks, unchanged from Ground 1.0 |

## Decisions

- **Sampling engine** — Statistical sample design is built natively in the
  shared core rather than wrapping an existing library, keeping the designs
  auditable and consistent across clients.
- **CEO migration** — Both parity and migration are in scope: existing CEO
  projects, plots, questionnaires, and collected labels can be imported into
  Collect One.
- **CEO APIs** — The two-way CEO integration APIs are superseded. With CEO and
  Ground unified into Collect One, no cross-platform API is needed for this
  workflow.
- **Interpreter role** — No distinct ACL role is introduced; the existing Data
  Collector role covers visual classification.

## Open Questions

1. **Imagery cost and keys** — Planet NICFI, Earth Engine, and commercial tile
   services carry per-institution credentials and quota implications that the
   current Organization quota model does not yet represent.
