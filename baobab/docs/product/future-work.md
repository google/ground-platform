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

# Future Work

While Ground 2.0 establishes a robust foundation centered on XLSForm parity, a
unified application core, and offline-first field mapping, several strategic
initiatives may be considered for subsequent milestones:

## Custom Offline Basemaps

While Ground 2.0 exclusively supports downloading Mapbox vector and raster
tiles, future milestones will expand mobile offline capabilities to support
custom basemap sources and user-supplied offline imagery:

*   **Third-Party Imagery & Tile Services**: Support for configuring and
    downloading offline tile packages from arbitrary tile servers (XYZ, TMS,
    WMS, WMTS), including Planet NICFI monthly mosaics and Sentinel-2
    composites.
*   **User-Supplied Offline Rasters & Drone Imagery**: Direct ingestion and
    offline caching of custom high-resolution aerial imagery, drone/UAV
    orthomosaics, and georeferenced rasters packaged as MBTiles archives or
    Cloud-Optimized GeoTIFFs (COGs).
*   **Offline Vector Packages & Sideloading**: Direct device sideloading and
    local rendering of custom vector tile packages (e.g., OpenMapTiles or custom
    MBTiles styles) to support field operations in areas with restricted
    bandwidth or specialized cartographic requirements.

## AI Integration

Harnessing Large Language Models (LLMs) and multimodal AI to reduce authoring
overhead, optimize field resource allocation, and accelerate post-collection
analysis:

*   **LLM-Powered Survey & Form Generation**: Conversational and prompt-driven
    form authoring in the Web Console (e.g., *"Create an agroforestry monitoring
    protocol for smallholder cocoa farms compliant with EUDR and Cocoa & Forests
    Initiative"*), automatically generating complete, valid XLSForm-compliant
    schemas with nested groups, skip logic (`relevant`), constraints, repeat
    loops, and multilingual translations.
*   **Intelligent Sample Plot & Anomaly Selection**: AI-driven prioritization
    and recommendation of potential sample plots or high-priority field
    inspection targets, combining historical satellite trends, real-time
    deforestation and canopy disturbance alerts (e.g., WHISP alerts), and
    existing submissions to identify ambiguous, anomalous, or high-risk areas
    for targeted ground-truthing.
*   **Automated Results Analysis & Synthesis**: Post-campaign data intelligence
    leveraging LLMs to analyze incoming submission data, detect statistical and
    spatial anomalies or reporting outliers, synthesize qualitative notes and
    open-ended field observations, and auto-generate executive summaries, donor
    reports, and regulatory compliance dossiers.

## Survey Analytics, Reports, and Dashboards

To empower survey organizers, M&E coordinators, and institutional sponsors with
actionable operational intelligence without requiring external BI tools:

*   **Interactive Operational Dashboards**: Real-time dashboards embedded in the
    Web Console visualizing campaign progress, enumerator productivity,
    daily/weekly submission velocity, and target completion rates against
    preloaded baseline entity registries (e.g., % of cooperative plots mapped).
*   **Geospatial Coverage & Heatmap Analysis**: Map-centric analytics surfacing
    spatial coverage densities, collection progress across administrative
    districts, unvisited target entities, and real-time field-team cluster
    distribution.
*   **Automated Statistical Reports & Aggregations**: Built-in reporting engine
    generating instant cross-tabulations, frequency distributions, and summary
    metrics (e.g., total mapped area in hectares, mean canopy density, crop type
    breakdowns) aggregated by form, date range, or administrative hierarchy.
*   **Quality Assurance & Anomaly Dashboards**: Operational QA/QC views flagging
    potential data anomalies, such as extreme survey completion durations,
    elevated GNSS horizontal dilution of precision (HDOP), self-intersecting or
    aberrant plot perimeters, and duplicate entity registrations.
*   **Exportable Executive & Compliance Dossiers**: One-click generation of
    templated, print-ready PDF and HTML executive summaries, donor impact
    reports, and regulatory audit dossiers (e.g., EUDR compliance summaries)
    complete with embedded map figures, charts, and key performance indicators.

## QA/QC Controls for Entities and Submissions

While Ground 2.0 establishes core data review capabilities, historical
versioning, and immutable audit logging, operationalizing large-scale
verification for compliance frameworks (such as EUDR, FSC certifications, and
carbon accounting) requires formal Quality Assurance and Quality Control (QA/QC)
mechanisms. Future milestones will introduce dedicated review lifecycles,
verification states, and field-correction feedback loops for both entities and
submissions:

*   **Submission Review & Approval Lifecycle**: Structured status transitions
    for incoming submissions (`Pending Review`, `Approved`, `Flagged / Needs
    Revision`, and `Rejected`). Project supervisors and designated reviewers can
    filter, bulk-triage, and validate submissions in the Web Console before they
    are certified or marked final.
*   **Field-Return & Re-Collection Feedback Loop**: The ability for supervisors
    to reject or flag questionable submissions and route them back to the
    originating field enumerator's mobile queue with targeted reviewer notes.
    The Mobile App notifies the collector and provides an actionable "Needs
    Revision" inbox to re-verify responses, re-photograph subjects, or re-walk
    errant boundary perimeters without creating duplicate submissions.
*   **Entity Verification, Deduplication, & Reconciliation**: Formal lifecycle
    management for persistent geospatial entities (`Draft / Unverified`,
    `Verified`, `Disputed`, and `Archived`). Includes automated duplicate
    detection based on spatial overlap thresholds, boundary proximity, and fuzzy
    attribute matching (e.g., matching farmer names or national IDs), with
    guided split-and-merge tools in the Web Console to resolve duplicate field
    registrations into canonical records.
*   **Automated Quality Rules & Anomaly Scoring**: Configurable rule engines
    evaluating submissions and entities upon synchronization. Automatically
    flags speed/duration anomalies (e.g., completing an extensive socio-economic
    form in under two minutes), poor GNSS dilution of precision (HDOP),
    impossible attribute ranges, and geometric discrepancies (e.g.,
    self-intersections or extreme perimeter-to-area ratios), assigning a
    composite data-quality confidence score to prioritize supervisory review.
*   **Granular Attribute-Level Flagging & Reviewer Annotations**: Question- and
    vertex-level annotations allowing reviewers to tag specific responses, media
    attachments, or spatial coordinates with queries or discrepancy warnings
    without overwriting raw surveyor inputs. All annotations, reviewer comments,
    and status updates are recorded in the immutable audit log with full actor
    attribution.
*   **Downstream Pipeline & Export Gating**: Configurable policy controls
    governing data dissemination based on verification status. Survey managers
    can restrict external synchronizations (e.g., Google Sheets streaming, Earth
    Engine feature tables, GeoJSON/Shapefile downloads, and partner webhooks) to
    certified `Verified` entities and `Approved` submissions, keeping unverified
    or disputed field records quarantined in staging.

## One- or Two-Way Sync with Google Sheets

Providing seamless, low-friction spreadsheet interoperability for project
stakeholders, non-technical coordinators, and automated operational pipelines:

*   **One-Way Streaming Sync (Ground to Sheets)**: Automated, near-real-time
    streaming of incoming field submissions, entity state transitions, and audit
    logs directly into designated Google Sheets workbooks. Enables instant
    stakeholder visibility, collaborative field tracking, and live reporting
    dashboards (via Looker Studio or native Sheets charts) without manual CSV or
    GeoJSON exports.
*   **Two-Way Synchronization (Collaborative Curation & Registry Management)**:
    Bi-directional synchronization allowing survey supervisors to review, clean,
    and update collected submission data directly within Google Sheets, with
    modifications propagating back to the Ground backend under strict schema
    validation and immutable audit logging. Also enables managing tabular entity
    registries and lookup tables (e.g., farmer rosters, plot IDs, cooperative
    registries) in Google Sheets with automatic, versioned synchronization to
    mobile field devices.

## Paid Quota Expansion & Foundation Payment Model

To bridge the gap between the free Basic Tier and fully sponsored institutional
deployments, future iterations will explore mechanisms allowing non-profits,
civil society organizations, and research institutions to self-fund additional
resource quota, underpinned by a sustainable foundation-based financial model:

*   **Self-Service Paid Quota Expansion**: Enable non-profit organizations that
    exceed Basic Tier thresholds to purchase additional capacity on demand
    (e.g., higher caps on predefined geospatial entities beyond the 1,000 limit,
    expanded survey limits, larger team Access Control Lists, and increased
    photo storage/bandwidth) at non-profit-friendly, cost-recovery rates,
    without needing to apply for formal institutional sponsorship.
*   **Foundation Model for Payment Processing**: Leverage an open-source or
    non-profit foundation structure (such as an Open Foris legal entity, a
    501(c)(3) fiscal sponsor, or an established open-source software foundation
    like Open Collective or the Linux Foundation) to provide the formal legal
    and banking rails necessary for the project to accept credit card payments,
    invoices, wire transfers, and purchase orders from international
    non-profits.
*   **Cost Recovery & Project Self-Sustainability**: Establish a transparent
    financial mechanism where payment revenues directly offset variable backend
    infrastructure costs—such as cloud compute, managed database storage, media
    storage, and third-party satellite tile egress—while creating an ongoing
    funding stream for dedicated open-source maintenance, security audits, and
    community support.
*   **Console Billing & Quota Administration**: An administrative billing and
    usage portal within the Web Console where survey managers can view real-time
    consumption metrics, purchase modular quota add-on packages, manage
    renewals, and download tax-compliant receipts and invoices for donor
    reporting.

## Automated Task Assignment, Management, and Gamified Crowdsourcing

Building upon Ground 2.0's preloaded geospatial entity registries, hierarchical
submission models, and XLSForm-compliant engine, future iterations will
introduce advanced task assignment, team routing, and gamification layers to
support automated alerts and crowdsourced campaigns:

### Automated and Manual Task Assignment & Management

Integrating dynamic external triggers—such as deforestation alerts (e.g.,
WHISP), areas of low model certainty, and plots requiring visual classification
validation—into structured field workflows:

1.  **Dynamic Entity Ingestion & Triggers**: Automated backend pipelines and
    webhooks that ingest raster-derived vector polygons or point alerts as
    dynamic Geospatial Entities carrying custom attributes (e.g.,
    `alert_source`, `certainty_score`, `validation_status`).
2.  **Team, Group, and Proximity-Based Routing**: Enhanced Access Control Lists
    (ACLs) and geospatial proximity filters allowing survey organizers to route
    tasks to specific user accounts, regional sub-teams, or cooperatives, or
    dynamically surface tasks within a field collector's immediate operational
    footprint or S2 cell bounds.
3.  **Entity-Linked Follow-Up Submissions**: Configuring validation
    surveys as follow-up encounters linked to preloaded alert entities,
    allowing mobile users to tap flagged map markers to launch pre-linked forms
    that dynamically pull in model certainty scores via `pulldata()` and update
    entity `marker-symbol` / `marker-color` (`○` -> `◐` -> `✓`) via `save_to`.
4.  **Web Console Triage & Mobile Task Queues**: Dedicated task management
    dashboards in the Web Console for batch assignment, visual status badging
    (*Pending*, *Assigned*, *Verified*), and conflict review, coupled with a
    dedicated "Assigned Tasks" layer and wayfinding HUD in the Mobile App.

### Gamified Crowdsourcing & Bounty Workflows

Motivating and incentivizing citizen scientists and volunteer data collectors
through engagement layers that integrate with task distribution:

1.  **Points and Scoring Systems**: Automated scoring based on task completion
    difficulty, data accuracy, and successful verification of deforestation
    alerts or low-certainty pixels.
2.  **Achievement Badges & Public Leaderboards**: Digital milestone badges and
    community rankings segmented by daily, weekly, or regional contributions.
3.  **Themed Bounties and Quests**: Repurposing unassigned public verification
    tasks into time-bound community challenges or opt-in bounty queues that
    users can claim based on proximity, turning traditional assignment into a
    participatory experience.

## Autonomous Task Assignment & Collective Active Inference

Ground V2 will evolve beyond a passive data collection utility into an
intelligent, distributed sensor network. The next major architectural phase will
introduce dynamic crowdsourcing driven by Active Inference models. By treating
field users as a sensory periphery and the platform as a centralized generative
model, Ground will autonomously optimize data collection to minimize spatial and
environmental uncertainty (Epistemic Foraging).

1.  **Epistemic Foraging & Uncertainty Mapping**
    *   **Objective**: Shift from manual task creation to autonomous,
        algorithmically driven data sampling.
    *   **Implementation**: The system will continuously calculate the Expected
        Free Energy (EFE) across the spatial environment. It will identify
        geographic coordinates or environmental strata with the highest
        statistical variance or epistemic uncertainty, generating heatmaps of
        where "ground truth" data is most needed.
    *   **Technical Bridge**: Requires an architectural layer to pass Ground’s
        spatial state to external cognitive inference engines (e.g.,
        Python-based `pymdp` or PyMCP environments) to perform the uncertainty
        analysis.
2.  **Dynamic Crowdsourced Task Assignment (The "Policy" Layer)**
    *   **Objective**: Route human data collectors to high-uncertainty zones
        efficiently.
    *   **Implementation**: Once the inference engine identifies high-value
        sampling locations, the Ground V2 Task Assignment Engine will
        autonomously generate and dispatch data-collection policies (tasks) to
        mobile clients.
    *   **Mechanics**: Tasks will be dynamically queued based on user proximity,
        historical reliability, and the calculated epistemic value of the
        observation, essentially executing "collective active inference" through
        human-in-the-loop actuators.
3.  **State-Space Integration & Belief Updating**
    *   **Objective**: Feed physical-world observations back into the central
        generative model in near real-time.
    *   **Implementation**: Establish a translation pipeline that converts
        standard Ground V2 ingestion payloads (GeoJSON, offline map syncs) into
        the specific Observation Space matrices ($O$) required by discrete-state
        or continuous POMDP (Partially Observable Markov Decision Process)
        models.
    *   **Mechanics**: As crowdsourced users complete tasks and upload
        observations, the data acts as the sensory input that updates the
        central system's probabilistic belief state (world model), closing the
        inference loop.
4.  **Data Schema & Engine Interoperability**
    *   **Objective**: Ensure Ground's geospatial data architecture is natively
        compatible with frontier cognitive AI frameworks.
    *   **Implementation**: Define standard adapter patterns for exporting
        Ground datasets into tensor-ready formats.
    *   **Design Decision Required**: Determine the optimal spatial resolution
        mapping—whether to discretize Ground's continuous vector/coordinate data
        into grid-based bounding boxes (optimizing for discrete-state frameworks
        like `pymdp`) or to support continuous spatial vectors directly.

### Epistemic Foraging & EU Deforestation Regulation (EUDR) Compliance

Ground V2 will evolve from a passive geospatial utility into an active,
algorithmic sensing network. By integrating Active Inference, the platform will
autonomously direct crowdsourced field data collection to resolve spatial
uncertainty. This architecture is specifically designed to defend local
communities against algorithmic exclusion ahead of the upcoming EUDR enforcement
deadlines.

#### Core Architecture & Feature Matrix

<!-- mdformat off -->

| Feature / Architecture Layer | Core Product Functionality | Active Inference Mechanic | Civic & Economic Value Proposition |
|---|---|---|---|
| **Epistemic Foraging Engine** | Identifies physical areas (e.g., shaded agroforestry) where satellite classification is ambiguous. | Calculates Expected Free Energy (EFE) to map "algorithmic blindspots" requiring ground truth. | **Resource Optimization**: Directs NGO and regulatory focus strictly to high-variance, undocumented regions. |
| **Dynamic Task Dispatch** | Autonomously routes data-collection tasks to mobile users nearest to high-uncertainty zones. | Executes "Policies" by using human field users as a distributed sensory periphery. | **Supply Chain Inclusion**: Empowers smallholder farmers to actively prove compliance and avoid market exclusion. |
| **GeoJSON Compliance Adapter** | Compiles verified field data into legally binding, timestamped polygon boundaries. | Translates Observation Space ($O$) inputs into strict regulatory reporting schemas. | **Market Premium**: Generates instant EUDR verification tokens, allowing cooperatives to guarantee zero deforestation. |
| **Civic Intelligence Dashboards** | Surfaces predictive risk heatmaps, anomaly alerts, and regional compliance readiness on-device. | Visualizes the updated Generative Model state and flags high-urgency prediction errors ("surprise"). | **Land Sovereignty**: Equips local defenders to spot illegal incursions immediately via immutable evidence logging. |

<!-- mdformat on -->
