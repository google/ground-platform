---
onedoc_gdoc_url: https://docs.google.com/document/d/1aYEZItFSCmn1lziASTUM5YakNeaswvL65IuFDbzDgqY
onedoc_md_file_id: 5d4e7780-eb8c-4308-a9ed-558718181447
onedoc_tab_id: t.0
onedoc_tab_title: Ground 2.0 PRD
onedoc_title: Ground 2.0 PRD
---

# Ground 2.0 PRD

**[SHARED EXTERNALLY]**

Authors: [@gmiceli](http://who/gmiceli)… \
Contributors: … \
Last modified: [2026-09-15](google-date:2026-09-15T12:00:00Z)

## Overview

### Mission

The core mission of the Open Foris Ground platform is democratizing geospatial
data collection to drive social and environmental impact. By reducing technical
complexity, offering map-centric workflows, and ensuring deep interoperability
with industry-standard tools, Ground empowers local communities, land-use
planners, researchers, and public benefit organizations to collect and leverage
geospatial data for sustainable land management, climate change mitigation, and
traceable supply chains.

### Background

The transition of Ground from a volunteer-led Google initiative to a
community-governed open-source project under the Open Foris initiative was
successfully completed during the Ground 1.0 release lifecycle, governed by a
multi-stakeholder steering committee to ensure long-term sustainability and
community-driven stewardship. Ground 2.0 represents the **enterprise maturation
phase** designed to productionize, stabilize, and scale the established
community-led model. The focus of Ground 2.0 is addressing the technical debt
and architectural bottlenecks of Ground 1.0 through logic deduplication, robust
server-enforced security, and extensive data standard integrations.

### Global Impact & Deployments

For detailed case studies, deployment metrics, capacity building statistics, and
institutional pipeline expansion across Ghana, Burundi, Kenya, and Viet Nam, see
the **[Global Impact & Deployments](?tab=t.li0p596b998q)** tab.

### Foundational References & Strategic Context

Ground 2.0 builds directly upon foundational strategic agreements and prior
architectural milestones:

*   **[Open Foris Ground Community Charter [SHARED EXTERNALLY]](https://docs.google.com/document/d/1pjllfws_HMuHrDDbGokPZCATgok_8vxufkSmyVM4p_Q/edit?tab=t.0#heading=h.14535shxk14t)**:
    Establishes the governance structure, steering committee bylaws, and
    collaborative charter between Google, the FAO, and partner institutions.
*   **[Ground 1.0 PRD [SHARED EXTERNALLY]](https://docs.google.com/document/u/0/d/1-ARlIjK4VImSrWju_5D_wZiMMCl4vAGMm4Bozdnwj30/edit)**:
    Documents the baseline features, initial task abstractions, and mobile-web
    architecture of Ground 1.0.
*   **[Ground 2026 Strategic Priorities [SHARED EXTERNALLY]](https://docs.google.com/document/u/0/d/18PKj2Qu3wRLwVS2Y8y3pdf7Zse1egIfu9JjvBX5wwR4/edit)**:
    Defines the roadmap priorities for Ground 2.0, emphasizing multiplatform
    parity (Android & iOS), XLSForm schema alignment, enterprise scalability,
    and developer maintenance reduction.

### Technical and Strategic Context

Ground 1.0 operated under severe scaling and architectural constraints:
maintaining separate codebases across Android, Web, and backend services, and
soon facing an additional codebase with the introduction of an iOS client.
Ground 2.0 consolidates this ecosystem using a shared multiplatform core,
ensuring continuous feature parity across Android and iOS devices. This
modernization is critical for regions like Viet Nam, where over 70% of forest
rangers and cooperative partners use iOS devices, making iOS support a
non-negotiable blocker for scaling up impact.

Ground 2.0 preserves this established separation of roles between its two
purpose-built environments while expanding their capabilities: the browser-based
**Web Console** adds desktop data collection and in-browser GIS geometry editing
alongside survey design and administration, while the rugged, offline-first
**Mobile App** extends field data collection, GNSS tracking, and spatial capture
across both Android and iOS.

In addition, Ground 2.0 transitions the data model from flat, disconnected tasks
into a hierarchical structure that mirrors the industry-standard XLSForm
specification (such as those used by ODK and KoboToolbox). This allows survey
organizers to deploy advanced skip logic, nested groups, repeated question
loops, and preloaded registries offline, facilitating compliance with emerging
global regulations like the European Union Deforestation Regulation (EUDR). To
ensure rigorous traceability, compliance auditability, and longitudinal data
integrity, Ground 2.0 incorporates end-to-end versioning across surveys, forms,
and collected data, backed by immutable audit logs for all edits.

### Client Platforms: Web Console vs. Mobile App

Ground 2.0 continues to emphasize the clear separation of concerns between its
desktop browser interface (**Web Console**) and its field-deployed native mobile
clients (**Mobile App**), providing specialized tools tailored to the
operational environments of survey organizers and field enumerators:

<!-- mdformat off(b/556740108: multiline tables break in GFM and OneDoc) -->

| Dimension / Capability | Web Console (Browser) | Mobile App (Android & iOS) |
| ---------------------- | --------------------- | -------------------------- |
| **Primary Personas** | Survey organizers, GIS managers, project supervisors, desktop data clerks | Field enumerators, community rangers, agricultural extension agents, field inspectors (viewer mode) |
| **Operating Environment** | Desktop and laptop web browsers (Chrome, Firefox, Safari, Edge) | Handheld mobile phones and ruggedized field tablets (Android & iOS) |
| **Connectivity Requirement** | Online-connected / hybrid desktop workspace | 100% offline-first field execution with local storage and auto-sync |
| **Form Authoring & Testing** | Visual WYSIWYG Form Designer with live, interactive dual previews (Mobile & Web), operational publish states (Draft, Active, Closed), draft/publish versioning, global and org templates, intra- and cross-survey form copy/paste, and question soft-deletion | Offline execution via standard XForms-compliant forms engine |
| **Data Collection & Entry** | In-browser form completion for desktop data entry, QA/QC reviews, and office intake | Native mobile forms with unified GPS/reticle pin capture (or organizer-enforced hardware GPS capture), photo/audio attachments, and offline caching |
| **Data Review & Editing** | Direct viewing, editing, multi-attribute search, sorting by last updated date; photo inspection lightbox with EXIF; bulk media ZIP download; historical rendering via collection-time form version; full audit log inspection; 30-day auto-emptying trash | Local submission history and baseline entity viewing, rendered with collection-time form version; on-device curation (edit and delete) of field-created entities and submissions |
| **Geometry Creation & Editing** | Interactive web map drawing and vertex manipulation for points, lines, and polygons; map-to-panel numerical badges; rendering up to 1M features | Walk-or-draw perimeter tracking, unified pin placement, tracks capture with haptic feedback, self-intersection guardrails, and QR code geometry capture |
| **Spatial Feedback & HUD** | Coordinate display, polygon area calculation, vertex snapping, and satellite layers | Real-time on-screen HUD (live walked perimeter, area, GPS accuracy, local GeoID confirmation, organizer accuracy threshold & hardware GPS enforcement) |
| **Basemaps & Caching** | High-resolution streaming cloud basemaps | Pre-cached Mapbox vector and raster tile packages with offline map caching, storage exhaustion guardrails, and uploaded media cache eviction |
| **Data Export & Sharing** | Multi-format export (CSV, GeoJSON, Shapefile) with altitude/accuracy metadata; entity and submission summary PDF generation and download; real-time webhooks and partner APIs (CEO, FERM, Arena); general access sharing (Restricted, Link/QR code, Public); peer visibility controls; email invites | Local cache diagnostics; on-device entity and submission summary PDF generation, download, and native system share sheet integration |

<!-- mdformat on -->

### Guiding Product Principles

#### Proportional Complexity (Simple by Default, Powerful on Demand)

Ground 2.0 is designed around the principle of **Proportional Complexity**
(embodying Alan Kay's dictum: *"Simple things should be simple, complex things
should be possible"* and the elimination of *accidental complexity*):

*   **Zero Accidental Complexity**: A task should only be as complex as it
    inherently is, and no more. Users must never be forced to pay an
    administrative, conceptual, or UI ceremony tax for advanced features they do
    not need. Setting up a survey to capture simple spatial data (such as
    dropping point locations on a map) must require only a few clicks and
    sensible defaults out of the box, without forcing organizers to navigate
    intricate schema definitions, question groups, or validation logic.
*   **Progressive Disclosure**: As survey requirements grow—incorporating nested
    repeat loops, cascading choice filters, dynamic XPath validation, or
    longitudinal entity linking—the system progressively surfaces advanced
    controls. The full expressive power of the XLSForm specification is readily
    accessible on demand, but remains unobtrusive during basic workflows.
*   **Task-Proportional Field Experience**: On mobile clients, the field
    interface automatically streamlines interactions to match the scope of the
    survey. Simple point-mapping or boundary-tracking tasks open directly into
    spatial capture with minimal taps, while multi-section socio-economic
    questionnaires provide robust, non-linear navigation.

#### Built for Challenging, Real-World Environments (Offline-First Resilience)

Ground 2.0 is engineered from the ground up to operate reliably in the remote,
rugged, and infrastructure-constrained settings where conservation, agriculture,
and forestry work actually happens:

*   **Offline as a First-Class Citizen**: Connectivity is treated as a transient
    convenience rather than an operational dependency. Mobile clients require a
    network connection for only two discrete touchpoints: initial survey setup
    (downloading survey configurations, form schemas, and pre-cached offline
    basemaps) and final synchronization (uploading completed submissions,
    spatial geometries, and media attachments).
*   **Fully Autonomous Field Execution**: Once a survey is synced to a mobile
    device, all core data collection workflows execute 100% offline with zero
    network latency. Field teams can record GNSS spatial geometries (points,
    walked perimeters, and tracks), evaluate complex XPath expressions, execute
    skip logic, validate constraints, capture photos and audio, and review
    collected submissions on-device without ever requiring cellular or Wi-Fi
    reception.
*   **Operational Hardiness**: Field hardware is routinely exposed to harsh
    conditions, intermittent packet loss, depleted batteries, and unexpected
    power loss. The mobile architecture enforces atomic local persistence
    (preventing data loss during sudden app termination or device shutdown),
    fault-tolerant background synchronization that gracefully recovers from
    dropped connections without duplicating submissions, defensive local cache
    eviction guardrails, and high-contrast UI affordances legible under direct
    field sunlight.

#### Built for Our Actual Users (Designing for Real-World Digital Literacy)

Ground 2.0 is built for frontline community practitioners, local rangers, and
public benefit teams, not GIS specialists or tech-industry power users. The
platform meets users where they are, accommodating diverse technical backgrounds
and levels of digital literacy across its primary personas:

*   **Pragmatic Simplicity for Survey Organizers**: Survey organizers are domain
    experts—conservation officers, project managers, and community leaders—who
    possess deep operational knowledge but typically have only standard computer
    literacy, primarily familiar with basic office productivity software (such
    as spreadsheets and web forms). The Web Console eliminates GIS jargon,
    abstract schema code, and configuration ceremony, offering an intuitive
    visual WYSIWYG Form Designer, direct table-like data inspection, and sane
    out-of-the-box defaults so organizers can deploy sophisticated surveys
    without writing code or mastering desktop GIS suites.
*   **Zero-Assumption Design for Field Enumerators**: For frontline data
    collectors, community rangers, and agricultural extension workers, Ground
    may represent their very first interaction with a smartphone or computing
    device of any kind. The Mobile App assumes zero prior familiarity with
    digital conventions, mobile operating system paradigms, or abstract file
    systems. It prioritizes radical simplicity: large, obvious touch targets,
    prominent visual iconography, minimal reliance on text input, spoken and
    haptic feedback, automated sensor capture, and structured step-by-step
    guidance that makes field collection immediately self-evident.
*   **Defensive and Forgiving Interactions**: Across both web and mobile, the
    interface emphasizes error prevention over post-hoc correction. Destructive
    actions require clear confirmation, validation constraints provide localized
    natural-language guidance instead of technical errors, and on-device
    curation gives field collectors the agency to rectify data entry mistakes
    directly without fearing system corruption or data loss.

## Product Requirements

### Functional Requirements

#### Shared Functional Requirements

##### Hierarchical Form Schema and Data Model

Ground 1.0 stored survey tasks as an ordered flat list, which was incapable of
representing nested repeats or multi-level groups. Ground 2.0 overhauls the
internal survey schema to utilize a nested, tree-based logical structure.

A Form is represented as a hierarchical tree of nodes where elements can be
nested dynamically as Questions, Groups, or Repeats. Submission records mirror
this tree structure, preserving parent-child relationships and repeat-loop
indices (e.g., `parent_group/repeat_loop[2]/child_question`) to align precisely
with the industry-standard hierarchical schemas supported by ODK and
KoboToolbox.

##### Advanced XPath Skip, Validation, & Custom Form Logic

Form logic is executed offline on mobile devices and within the web browser via
a standard **XForms-compliant expression evaluator** embedded directly into the
shared multiplatform core.

This engine interprets XPath expressions compiled during XLSForm import,
enabling rich expression- and UI-driven custom form logic:

*   **Skip Logic (`relevant`)**: Dynamically hides or displays questions or
    entire groups based on prior responses (e.g., `${shading_pct} > 30` or
    `${crop_type} = 'cocoa'`).
*   **Validation Logic (`constraint`)**: Restricts inputs to valid ranges,
    displaying custom, localized error messages when constraints are violated
    (e.g., `. <= 100` for percentage inputs).
*   **Cascading Selects (`choice_filter`)**: Dynamically filters select options
    based on previous selections (e.g., filtering cooperatives based on the
    selected county).
*   **Conditional Requirement (`required`)**: Evaluates dynamic expressions to
    determine if a question is mandatory based on context (e.g., `required =
    "${has_shade_trees} = 'yes'"`).
*   **Dynamic Calculations (`calculate`)**: Computes derived indicators and
    intermediate mathematical or string values in the background without user
    intervention (e.g., calculating plot yield per hectare, estimated canopy
    biomass, or combined indices).
*   **Calculated Repeat Counts (`repeat_count`)**: Dynamically sets the number
    of iterations in a repeat loop based on an earlier numeric answer (e.g.,
    prompting for exactly `${shade_tree_count}` inspection iterations).
*   **Dynamic Questions, Labels, Hints & Notes**: Dynamically injects prior
    responses into subsequent question labels, instructions, notes, or hints
    using variable substitution syntax (e.g., *"How many years has
    ${farmer_name} managed this parcel?"*).
*   **Data Aggregation across Repeats**: Evaluates aggregate functions
    (`count()`, `sum()`, `min()`, `max()`) across repeated question loops (e.g.,
    summing tree stem counts or averaging soil pH across repeat samples).
*   **Dynamic Entity Lookups (`pulldata()`)**: Prepopulates responses, defaults,
    and choice lists by querying preloaded tabular CSV registries and lookup
    tables offline at runtime.

##### Preloaded & Field-Created Entities

Ground 2.0 supports two distinct classes of preloaded entity registries to
support longitudinal and multi-visit workflows, mapping natively to ODK
Central's "Entities" specification:

*   **Tabular Entities**: Relational, non-spatial records (e.g., registries of
    farmers, cooperative member rosters, or species taxonomies) uploaded by
    organizers as CSVs. These are queried using XLSForm `select_one_from_file`
    and offline `pulldata()` operations.
*   **Geospatial Entities**: Relational records tied to a geometry (e.g.,
    preloaded cocoa plot polygons or forest monitoring plots). These replace
    Ground 1.0's rigid "sites" and are rendered directly on the map as
    interactive vector features with searchable attribute cards.

###### Dynamic On-The-Fly Entity Creation

To support "opportunistic mapping", Ground 2.0 allows data collectors to
register a brand-new entity directly when encountering an unregistered plot or
farmer:

*   **Registration**: Prompts the collector to map its geometry (point or
    perimeter) and complete baseline attributes (e.g., farm name, owner, and
    registration ID).
*   **Local Caching & Immediate Follow-Up**: The newly registered geospatial
    entity is immediately saved to the offline entity database, appears on the
    map, and allows immediate launch of secondary survey forms linked directly
    to it.

*   **On-Device Curation (Edit and Delete) of Field-Created Entities**: Data
    collectors who register an ad-hoc entity in the field can curate their
    records directly on-device—updating attributes, refining geometry, or
    deleting erroneous entities and their initial submissions—both before and
    after synchronization to promptly rectify field mistakes.

###### Longitudinal Linking & Multi-Wave Surveys

To support complex monitoring and evaluation (M&E) campaigns—such as tracking
tree survival rates over time in Kenya's restoration tracts or conducting
multi-season coffee audits in Burundi—Ground 2.0 replaces static "site-level"
submissions with a relational, entity-linked data model. This architecture
decouples the persistent identity of a subject (the **Entity**, stored within an
**Entity Dataset**) from the discrete, time-series observations recorded against
it (the **Submissions**).

Survey organizers configure the relationship between Entities and Submissions in
the Web Console Form Editor using the **Submission Model**:

*   **Single Submission (1:1)**: Optimized for baseline registrations, property
    georeferencing, or physical asset audits where each entity must receive
    exactly one observational record.
*   **Multiple Submissions (1:N)**: Optimized for longitudinal tracking,
    repeated seasonal monitoring, and periodic inspections where field teams
    submit consecutive, chronologically linked follow-up forms against the same
    parent Entity over months or years.

##### Survey, Form, and Data Versioning with Audit Logging

To guarantee data integrity across longitudinal campaigns and satisfy strict
traceability and compliance standards (such as the EUDR, FSC certifications, and
carbon project audits), Ground 2.0 provides robust, native versioning for
surveys, forms, and collected data, coupled with tamper-evident audit logging:

###### Survey & Form Schema Versioning

*   **Survey Operational Publishing States (Draft, Active, Closed)**: Survey
    organizers can manage active data collection through three explicit
    operational states:
    *   **Draft**: The survey, forms, and preloaded entity registries are being
        configured, edited, and tested. Field data collection is disabled and
        the survey is hidden from mobile enumerators.
    *   **Active**: The survey is published and open for data collection across
        native mobile apps and desktop web entry.
    *   **Closed**: Data collection is concluded or temporarily paused. Field
        and web clients cannot create new submissions, while existing records
        remain accessible for review, synchronization, QA/QC, and export.
*   **Draft vs. Published Schema Lifecycles**: Survey organizers can create,
    refine, and test survey configurations and form schemas in draft mode
    without affecting ongoing field operations. Changes become active only when
    an organizer explicitly publishes a new version.
*   **Immutable Version Releases**: Each publication increments the survey and
    form schema version sequentially. Published versions are stored as immutable
    snapshots, ensuring that mobile and web clients execute against a fixed,
    predictable schema until an update is synchronized.
*   **Platform-Wide Soft Deletion & Auto-Emptying Trash**:
    *   **Soft Deletion of Questions**: Form questions and fields are never
        permanently/destructively deleted once a form version is published.
        Instead, deleting a question marks it as soft-deleted
        (archived/deprecated) in subsequent versions. Soft-deleted questions are
        omitted from new data collection workflows while preserving their
        definitions and historical answers in past submissions.
    *   **Universal Soft Deletion & Auto-Emptying Trash**: Soft deletion applies
        platform-wide to surveys, forms, and submissions. Accidental deletions
        of entire surveys or submission records move the data to an
        **Auto-Emptying Trash** with a 30-day retention window before permanent
        backend purging, allowing project supervisors to restore mistakenly
        removed items without data loss.

###### Data Versioning (Submissions & Entities)

*   **Immutable Submission & Entity Revisions**: Data collected in Ground 2.0 is
    strictly versioned. Any edit to an existing submission record or preloaded/
    field-created entity (such as QA/QC corrections, attribute amendments, or
    spatial boundary vertex refinements) generates a new sequential revision
    rather than overwriting the previous state in place.
*   **Historical Point-in-Time Reconstruction**: Previous revisions remain fully
    accessible in the system. Project supervisors can trace the evolution of a
    record, compare historical revisions side by side, and revert accidental or
    erroneous modifications.

###### Comprehensive Audit Logging

*   **Platform-Wide Audit Trails**: An append-only audit log captures every
    administrative action, schema change, submission edit, and entity mutation
    across the platform.
*   **Structured Audit Metadata**: Each audit log entry records:
    *   **Actor**: User ID, email, and organizational role of the person making
        the change.
    *   **Timestamp**: Precise ISO 8601 UTC timestamp.
    *   **Action Type**: The specific mutation (e.g., `CREATE`, `UPDATE`,
        `SOFT_DELETE`, `RESTORE`, `PUBLISH`).
    *   **Target Identifier**: The affected survey, form version, submission,
        entity, or question ID.
    *   **Field-Level Deltas**: Before-and-after values for all modified
        attributes, choices, and spatial geometries.
    *   **Client Context**: Client platform (Web Console vs. Mobile App), client
        version, network IP, and optional user edit rationale.
*   **Audit Inspection & Export**: Audit logs are queryable via the Web Console
    and can be exported for external compliance audits and verification reviews.

##### Interoperability & Integrations

###### Terminology & XLSForm Parity

To eliminate user confusion and align natively with industry-standard GIS and
survey tools like ODK and KoboToolbox, Ground 2.0 systematically reframes its
core concepts:

| ODK Concept    | KoboToolbox | XLSForm Sheet/Row      | ArcGIS    | Ground 1.0   | Ground 2.0 Term |
:                : Concept     :                        : Survey123 : Term         :                 :
| -------------- | ----------- | ---------------------- | --------- | ------------ | --------------- |
| **Survey**     | Project     | Workbook               | Survey    | Survey       | **Survey**      |
:                :             :                        :           :              : (Root ACL/Org   :
:                :             :                        :           :              : container)      :
| **Form**       | Form        | `survey` (sheet)       | Form      | Job          | **Form**        |
:                :             :                        :           :              : (XLSForm Schema :
:                :             :                        :           :              : sheet)          :
| **Question**   | Question    | Row                    | Question  | Task         | **Question**    |
:                :             :                        :           :              : (Individual     :
:                :             :                        :           :              : input row)      :
| **Repeat**     | Repeat      | `begin repeat`         | Repeat    | N/A          | **Repeat**      |
:                : Question    :                        :           :              : (Variable loop  :
:                :             :                        :           :              : structure)      :
| **Group**      | Group       | `begin group`          | Group /   | N/A          | **Group** (UI   |
:                :             :                        : Page      :              : section/logical :
:                :             :                        :           :              : cluster)        :
| **Note**       | Note        | `note` (type)          | Note      | Instructions | **Note**        |
:                :             :                        :           : task         : (Read-only      :
:                :             :                        :           :              : guidance block) :
| **Submission** | Submission  | Instance               | Response  | Submission   | **Submission**  |
:                : / Record    :                        :           :              : (Completed      :
:                :             :                        :           :              : transaction)    :
| **Entity       | Dynamic     | `entities` /           | N/A       | N/A          | **Entity        |
: Dataset**      : Attachments : `pulldata()`           :           :              : Dataset**       :
:                :             :                        :           :              : (Tabular entity :
:                :             :                        :           :              : list)           :
| **Entity       | Dynamic     | `entities` /           | Feature   | Data         | **Entity        |
: Dataset**      : Attachments : `select_one_from_file` : Layers    : collection   : Dataset**       :
:                :             :                        :           : site         : (Geospatial     :
:                :             :                        :           :              : entity list w/  :
:                :             :                        :           :              : `geometry`)     :

###### XLSForm Import & Export

Ground 2.0 guarantees zero-config round-tripping of surveys and form metadata
with other industry-standard data collection platforms, including ODK,
KoboToolbox, ArcGIS Survey123, and SurveyCTO:

*   **Import**: Organizers can upload standard `.xlsx` or `.xml` XLSForms
    directly to the Ground Web Console, automatically generating the form
    schema, question groupings, skip logic, and validation rules.
*   **Export**: Completed surveys can be exported as fully compliant XLSForm
    workbooks, allowing immediate migration and metadata sharing across ODK,
    KoboToolbox, Survey123, and SurveyCTO.

###### Reference XLSForm Template Specification

To facilitate seamless interoperability, Ground 2.0 maps its native geometry and
entity features directly to the industry-standard XLSForm layout. Organizers can
define surveys using standard spreadsheet software (e.g., Microsoft Excel or
Google Sheets) and upload them directly to the Web Console.

**Example XLSForm `survey` Sheet**
<!-- mdformat off(b/556740108: multiline tables break in GFM and OneDoc) -->

| type | name | label | hint | required | appearance | relevant |
| ----------------------------------------- | ------------------- | -------------------------- | ---------------------------------------------------------------------------------- | -------- | ------------------ | --------------------------- |
| **select_one_from_file cooperatives.csv** | `selected_coop` | **Select cooperative** | Choose your cooperative from the list or tap its marker on the map. | yes | `map-select` | |
| **geoshape** | `plot_perimeter` | **Draw or walk perimeter** | Map the outer boundary of the cocoa/coffee plot. Live area will display on screen. | yes | `walk-or-draw` | |
| **geotrace** | `access_path` | **Trace path** | Trace the main path leading from the road to the plot. | no | `walk-or-draw` | |
| **geopoint** | `soil_sample_point` | **Drop a pin** | Zoom and center the reticle over the physical soil sample location. | no | `manual-placement` | |
| **select_one yes_no** | `has_shade_trees` | **Are there shade trees?** | Select yes if there are visible shade trees in the plot. | yes | `minimal` | |
| **integer** | `shade_tree_count` | **Number of shade trees** | Enter the total number of shade trees within the boundary. | yes | | `${has_shade_trees} = 'yes'` |

<!-- mdformat on -->

**Example XLSForm `choices` Sheet**

list_name  | name  | label
---------- | ----- | -----
**yes_no** | `yes` | Yes
**yes_no** | `no`  | No

**Schema Type Mapping**

When an XLSForm is imported, the engine parses types and appearances to
configure client components:

*   **`select_one_from_file <dataset>.csv` (`map-select`)**: Mapped to a
    **Select an Entity** question. Loads the lookup dataset into the database
    for attribute listing and map marker selection.
*   **`geoshape` (`walk-or-draw` / `placement-map`)**: Mapped to native **Draw
    or walk perimeter** (Polygon) with automated area and perimeter calculations
    (in imperial or metric units based on user or system settings). Omitting
    `placement-map` suppresses manual vertex drawing on the map, requiring
    walked hardware GNSS tracking.
*   **`geotrace` (`walk-or-draw` / `placement-map`)**: Mapped to native **Trace
    path** (LineString) with real-time length tracking. Omitting `placement-map`
    suppresses manual vertex drawing on the map, requiring walked hardware GNSS
    tracking.
*   **`geopoint` (`placement-map` / `manual-placement`)**: Mapped to native
    **Drop a pin** (Point) with reticle centering and GPS accuracy display.
    Omitting `placement-map` forces hardware GNSS sensor capture exclusively,
    disabling manual map reticle placement by data collectors.
*   **QR Code Geometry Ingestion**: For all geometry questions, the mobile app
    additionally supports scanning a QR code to import pre-existing coordinates
    or GeoJSON features directly into the active field question.
*   **Layer Defaults**: The first geometry question in the form is displayed as
    the primary map layer by default; secondary geometries and preloaded entity
    datasets are configured as toggleable overlay layers.

###### External Partner Platform APIs (CEO, FERM, and Arena)

Ground 2.0 provides well-defined REST and programmatic APIs for reading and
writing survey configurations, form schemas, reference entities, and collected
submissions, initially established for deep integration with open science and
forestry monitoring platforms:

*   **Collect Earth Online (CEO)**: Seamless two-way integration where sample
    plots flagged in CEO as "difficult to classify" via satellite imagery are
    exported and preloaded into Ground as geospatial lookup entities, and
    high-resolution ground truth points and plot perimeters captured in Ground
    are fetched via API to validate and refine CEO’s remote-sensing
    classification models.
*   **FAO Framework for Ecosystem Restoration Monitoring (FERM)**: Ingestion and
    registry APIs allowing ecosystem restoration initiatives to exchange target
    plot geometries, tree-planting survival submissions, and biophysical
    indicators directly with national registries.
*   **Open Foris Arena**: Bi-directional schema and data synchronization
    allowing Arena national forest inventory campaigns to deploy mobile surveys
    through Ground and pull completed field submission records into Arena
    analytical pipelines.

###### Configurable Webhook & Real-Time API Forwarding

To support extensible data pipelines with external third-party systems,
enterprise repositories, and national forest registries:

*   **Real-Time Data Forwarding on Ingestion**: Organizers can configure generic
    webhook and REST endpoints per survey or job to automatically forward newly
    synchronized submission records, spatial geometries, and entity updates
    immediately upon backend arrival.
*   **Payload Customization & Header Authentication**: Supports configurable
    request payloads, custom HTTP authorization headers (e.g., API keys, Bearer
    tokens), and automatic retry policies with backoff.

##### Governance, Multi-Tenancy & Data Sharing

###### Multi-Tenant Organizations

To support scaling to millions of global submissions while maintaining strict
administrative oversight, Ground 2.0 introduces **"Organizations"** (similar to
academic institutions or enterprise accounts). All users are grouped under an
organization, and surveys are associated with these organizations. Quotas and
billing limits are managed collectively at the organization level, allowing
managers to allocate and pool resources across multiple field teams.

###### Data Sharing Terms & Simplified Consent

Ground 2.0 removes rigid, mandatory runtime data sharing agreements from the
mobile onboarding flow:

*   General platform Terms of Service (ToS) and Privacy Policies are available
    in all supported languages in the "About" screen.
*   Survey organizers have complete autonomy to embed custom consent agreements
    directly within forms as required multiple-choice questions (e.g., "Do you
    agree to share this boundary data under CC-BY 4.0? Yes/No"), ensuring
    regulatory compliance without platform-wide onboarding friction.

###### Survey Sharing, Access Workflows, & Peer Data Visibility

To balance collaborative field coordination with respondent privacy:

*   **Survey Sharing & General Access Options (Google Docs/Drive Model)**:
    Ground 2.0 adopts the general access sharing options established in Ground
    1.0, directly mirroring the familiar permissions and sharing controls
    provided by Google Docs and Google Drive:
    *   **Restricted**: Only people explicitly added to the survey Access
        Control List (ACL) via email can open and access the survey with the
        link. Uninvited users attempting to access the survey are denied entry.
    *   **Accessible via Link / QR Code (Anyone with the Link)**: Everyone with
        the survey link or QR code can access the survey and collect data
        without needing to be individually added to the ACL. Organizers can
        distribute the survey by copying the direct URL or displaying/printing a
        QR code that field enumerators can quickly scan with mobile devices for
        instant onboarding.
    *   **Public**: The survey is publicly discoverable and accessible to
        anyone. Any user can find the survey in the public directory and collect
        data for it, supporting open crowdsourcing, citizen science campaigns,
        and broad community data gathering.
*   **Collaborator Access Control List (ACL) & Automated Email Invitations**:
    Organizers can invite specific collaborators by adding individual emails or
    bulk-pasting comma- or whitespace-separated lists of email addresses into
    the sharing dialog with assigned roles (e.g., Viewer, Data Collector, Survey
    Organizer). Adding users automatically triggers email invitations with
    direct activation links.
*   **Peer Data Visibility Controls**: Organizers can explicitly configure
    whether data collectors can view submissions and entities collected by peers
    on the map, or only their own records, preventing duplicate registrations of
    geospatial entities in collaborative campaigns while preserving
    confidentiality when required.
*   **Invitation Mode & Self-Removal**: Collectors can review and accept
    invitations before surveys populate their local device library, and retain
    the ability to remove themselves from surveys they no longer participate in.

##### Entity and Submission Summary PDF Generation

To deliver verifiable documentation, regulatory compliance records, and physical
receipts across both field and office workflows, Ground 2.0 supports generating
publication-ready summary PDF reports for any Entity (preloaded or
field-created) or completed Submission record across both the **Web Console**
and the **Mobile App**:

*   **Standardized Summary Structure**: Generated PDF reports follow a unified
    layout across web and mobile, compiling:
    *   **Entity Identity & Attributes**: Unique Entity ID, farmer/owner
        details, registration timestamp, and deterministically generated GeoID.
    *   **Spatial Cartography & Metrics**: High-contrast map snapshot depicting
        the plot boundary or point location, accompanied by calculated spatial
        metrics (enclosed area in hectares/acres and boundary perimeter).
    *   **Submission Details**: Complete survey responses reconstructed and
        formatted against the collection-time form schema, complete with
        collection timestamp and enumerator identity.
*   **Farmer Receipts & Regulatory Due Diligence**: Provides tangible proof of
    land registration, cooperative record-keeping, and compliance documentation
    (such as EUDR due diligence dossiers) whether generated on the spot at the
    farm gate or exported from the office console.

##### Impact Measurement & Socialization

To demonstrate measurable social and environmental impact, maintain
institutional accountability, and support open science initiatives:

*   **Survey Strategic Purpose Tracking**: During survey creation, organizers
    categorize the primary operational purpose and thematic domain of the survey
    (e.g., EUDR compliance, forest monitoring, ecosystem restoration,
    smallholder supply chains, community land tenure, or biodiversity
    conservation). This metadata enables the steering committee and community to
    track strategic platform adoption and deployment trends across sectors.
*   **Public Anonymized Metrics Dashboard**: A public-facing web dashboard
    surfaces high-level, privacy-preserving aggregate platform metrics (such as
    active users, country deployments, total mapped polygons and area in
    hectares, and monitored commodity breakdowns), demonstrating platform
    momentum and community impact.
*   **Automated Monthly Usage & Impact Reports**: Scheduled backend reporting
    jobs compile and deliver automated monthly usage digests to organization
    administrators and institutional sponsors, detailing active survey counts,
    submission volumes, field collector activity, and spatial progress across
    their teams.

#### Web Console Functional Requirements

The Ground 2.0 Web Console serves as the unified administrative, survey design,
and data management hub for project leaders, GIS specialists, and data managers.

##### WYSIWYG Form Designer & Dual Previews (Mobile & Web)

Ground 2.0 replaces flat, schema-based editing with a visual **WYSIWYG Form
Designer**, allowing organizers to build, structure, and test complex
hierarchical forms with zero coding:

*   **Visual Form Building**: Drag, drop, reorder, and configure XLSForm
    question types, hierarchical groups, repeated loops, select choices, and
    multimedia prompts on an intuitive visual canvas.
*   **Live Interactive Dual Previews**: Real-time preview panel with two
    dedicated viewports:
    *   **Mobile Preview**: Simulates mobile screens, rendering touch
        interactions, bottom sheets, and step-by-step card navigation.
    *   **Web Preview**: Simulates desktop layouts with wide-screen inputs,
        multi-column arrangements, and keyboard-friendly data entry.
*   **Interactive Logic Testing**: Test XPath skip logic (`relevant`),
    validation constraints (`constraint`), cascading filters (`choice_filter`),
    and calculated expressions in real time before publishing.
*   **Live Multi-Language Translation Preview**: Toggle configured language
    locales (e.g., English, French, Vietnamese, Spanish) in the preview pane to
    verify translations and layout alignments across both viewports.
*   **Version Drafting & Publication Workflow**: Authors can stage schema
    updates in draft mode, compare changes against the active published version
    using a visual diff inspector, and publish updates. The designer includes
    explicit question soft-deletion controls, alerting organizers that questions
    will be retired from future collection while preserving all existing data
    and previous form versions.
*   **Question Type Guidance & Descriptions**: The component palette provides
    descriptive cards, inline summaries, and contextual tooltips explaining what
    each question type does (e.g., delineating Point vs. Line vs. Polygon
    geometries, preloaded CSV lookup tables, and nested repeat groups) to guide
    non-technical organizers.
*   **Persistent Survey Title & Breadcrumb Navigation**: The survey title,
    active form version, operational status (`Draft` / `Active` / `Closed`), and
    navigation breadcrumbs remain permanently visible in the header during all
    steps of survey creation, form design, sharing configuration, and data
    inspection.
*   **Low-Friction Quick-Start & Predefined Templates (Global &
    Organizational)**: Enforcing proportional complexity, organizers can deploy
    basic spatial surveys (e.g., point-only ground observations, boundary walks,
    or path traces) in just a few clicks using pre-configured starter templates.
    Furthermore, organizations can save and standardize custom survey templates
    across their organization, alongside global community templates.
*   **Copy/Paste Forms (Intra- and Cross-Survey)**: Organizers can copy and
    paste entire forms (including their question hierarchies, skip logic,
    validation rules, and appearance settings) both within the same survey and
    between different surveys, eliminating redundant authoring effort while
    preserving schema definitions.
*   **Custom Map Layers & Geometry Styling (`LayerDef`)**: Organizers configure
    an ordered stack of map layers (`LayerDef` inside `MapConfig`) with granular
    visual styling (`GeometryStyle`: fill color, stroke color, stroke width,
    fill opacity, and marker pin/icon styles) and default visibility
    (`visible_by_default`) for individual form geometry questions and geospatial
    entity datasets across web and mobile maps.
*   **Progressive Disclosure of Advanced XLSForm Controls**: Advanced schema
    constructs—including XPath skip logic (`relevant`), validation expressions
    (`constraint`), dynamic filters (`choice_filter`), and repeat count
    expressions—are tucked behind contextual progressive disclosure controls,
    ensuring novice organizers are never exposed to unnecessary complexity while
    advanced GIS managers retain granular control.

##### Web Data Collection, Review, & Interactive Geometry Editing

To empower desktop GIS workflows and office-based data intake:

*   **In-Browser Data Collection**: Complete new survey submissions directly
    within the browser for phone interviews, historical registry digitization,
    or office intake.
*   **Submission Data Editing**: Inspect, review, and edit existing submissions
    during QA/QC to correct typos, update missing attributes, or amend flagged
    responses, with all changes tracked in the audit log.
*   **Collection-Version Submission Rendering**: Submissions are viewed and
    inspected using the exact form version they were collected with. The Web
    Console dynamically reconstructs the historical form layout, question
    prompts, option lists, hints, and conditional visibility logic corresponding
    to the submission's collection-time form version, preventing visual
    distortion, mismatched field mappings, or missing labels caused by
    subsequent form updates.
*   **Submission Audit Trail & Revision History**: An integrated audit drawer
    displays the complete edit history for each submission, detailing who made
    each change, timestamps, and side-by-side diffs of modified attributes and
    geometries, with the ability to revert changes if necessary.
*   **Interactive Point & Polygon Drawing**: Drop, move, and snap point markers
    to satellite imagery, or digitize parcel boundaries, crop plots, and access
    roads with automatic area and perimeter calculations (supporting imperial
    and metric units, defaulting to the system setting).
*   **Advanced Vertex-Level Geometry Editing**: Modify existing field-collected
    geometries directly in the web map:
    *   **Node Manipulation**: Click and drag vertices to adjust boundaries,
        click midpoints to insert new vertices, and delete errant vertices.
    *   **Boundary Correction**: Rectify GPS drift, align jagged field
        perimeters against clear satellite basemaps, or split/merge contiguous
        parcels without requiring re-surveying.
*   **Synchronized Table/Map Views**: Split-screen table and map views for
    filtering, searching, bulk editing, and exporting datasets (CSV, GeoJSON,
    Shapefile).
*   **Comprehensive Survey & Submission Search**:
    *   **Survey Discovery**: Fast global search across survey titles,
        descriptions, operational states (`Draft` / `Active` / `Closed`), and
        organizational tags on the organizer home dashboard.
    *   **Submission Search**: Real-time multi-attribute search within the
        submission table view by respondent name, plot/entity ID, or specific
        question responses.
*   **Submission Sorting by Last Updated Date**: Multi-column sorting in the
    table view, allowing supervisors to sort submissions by **last updated /
    modification date**, original collection timestamp, or enumerator identity
    to quickly surface recent field edits and incoming sync batches.
*   **Photo Inspection Lightbox & Bulk Media Download**:
    *   **High-Resolution Lightbox**: Dedicated photo inspection viewer
        featuring zoom, rotation, and embedded EXIF metadata extraction
        (displaying camera model, hardware timestamp, orientation, and embedded
        GPS coordinates).
    *   **Bulk Media Download**: One-click bulk export of survey media
        attachments (photos and audio recordings) packaged as compressed ZIP
        archives, with flexible options to download all attachments or filter by
        form, date range, or selected entities.
*   **Map-to-Panel Numerical Reference Badges**: Geospatial entities, polygons,
    lines, and point markers rendered on the web map display numerical reference
    badges matching their corresponding entries in the entity drawer, submission
    panel, and table view, streamlining verification and inspection.

##### Submission Conflict Review & Resolution Queue

When duplicate submissions occur under a Single Submission (1:1) model:

*   **Conflict Review Queue**: Conflicting submissions intercepted by the
    backend are automatically routed to a dedicated review queue.
*   **Organizer Resolution Flow**: Organizers receive immediate conflict alerts
    and use a side-by-side comparison interface to resolve conflicts using a
    "Latest Device Timestamp Wins" policy or by manually merging tabular fields
    before finalizing dataset exports.

##### Harmonized Dataset & Summary PDF Export

The Web Console provides powerful data export and document generation
capabilities to prepare collected survey data for external analysis in GIS tools
(e.g., QGIS, ArcGIS), statistical packages (e.g., R, Python), and compliance
reporting:

*   **Latest-Version Schema Harmonization**: Exported datasets (CSV, GeoJSON,
    Shapefile) are automatically harmonized to match the **latest version of the
    form**. When surveys evolve across multiple form versions over time, the
    export engine maps all submissions—regardless of their collection-time form
    version—to the current schema's question identifiers, ordering, and
    hierarchical structure. This eliminates mismatched columns and generates a
    unified, tabular dataset ready for analysis.
*   **Entity & Submission Summary PDF Generation**: Organizers, GIS managers,
    and project supervisors can generate, preview, print, and download
    structured summary PDFs for individual entities or submissions directly from
    the entity drawer, submission inspection panel, or table view. Additionally,
    organizers can trigger bulk summary PDF exports (downloaded as a
    consolidated multi-page PDF or a ZIP archive of individual PDFs) for
    selected or filtered entities and submissions to support institutional
    reporting, compliance audits, and physical distribution.
*   **Spatial Precision & Sensor Metadata Export**: Data exports automatically
    include dedicated columns for recorded altitude and horizontal GPS accuracy
    alongside coordinate geometries.
*   **Configurable Export Filenames**: Export dialogs allow organizers to
    specify custom file naming conventions for exported tabular datasets,
    summary PDFs, and compressed archives upon download.
*   **Optional Inclusion of Soft-Deleted Questions**: Export dialogues provide
    an explicit configuration toggle: *"Include soft-deleted questions"*:
    *   **Exclude Soft-Deleted Questions (Default)**: Exports contain only the
        active questions defined in the latest form version, producing a clean,
        streamlined dataset.
    *   **Include Soft-Deleted Questions**: The export includes supplementary
        columns for any questions soft-deleted in earlier revisions. Historical
        responses collected for those questions are preserved and populated,
        while records collected after question deletion are filled with null or
        empty values.

#### Mobile App Functional Requirements

The Ground 2.0 Mobile App is purpose-built for demanding field conditions,
providing a lightweight, robust, and offline-first data collection tool across
Android and iOS.

##### Harmonized Geometry Question Types & Map Overlay Logic

Ground 2.0 provides three distinct, native geometry question types within the
form designer to guarantee standardized, harmonized datasets:

*   **Drop a pin** (Point geometry): A unified point capture question combining
    sensor-based GPS location capture with manual map reticle placement into a
    single seamless task type. Survey organizers can optionally suppress manual
    map reticle placement to require actual hardware GPS fixes exclusively.
*   **Trace path** (LineString geometry): Manual drawing or walk-and-track path
    tracing. Survey organizers can optionally disable manual map drawing to
    require walked hardware GNSS tracking exclusively.
*   **Draw or walk perimeter** (Polygon geometry): Walked perimeter tracking or
    manual vertex-by-vertex boundary drawing. Survey organizers can optionally
    disable manual vertex drawing to require walked hardware GNSS tracking
    exclusively.

Each question field requires selecting exactly one geometry type (no mixed
geometries per question). Dedicated Ground 1.0 "Map a new site" tasks are
deprecated in favor of embedding standard geometry questions or geospatial
entity registrations directly within forms.

###### Map Visualization & Overlay Logic

*   **Layer Stack & Geometry Styling (`LayerDef`)**: Each geospatial entity
    dataset (`entity_dataset_id`) and form geometry question (`form_geometry`)
    rendered on the map is configured via an ordered `LayerDef` inside
    `MapConfig.layers`, specifying its legend label, `default_style`
    (`GeometryStyle`), and default visibility (`visible_by_default`). For entity
    dataset layers, per-entity `simplestyle-spec` properties (`marker-color`,
    `stroke`, `fill`, etc. in `EntityRecord.properties`) override the layer's
    `default_style`.
*   **Toggleable Geometries**: Individual form geometry layers (e.g., an access
    path alongside a plot perimeter) and entity dataset layers can be toggled on
    or off directly within the map layer controls.
*   **Active Self-Intersection Prevention**: During manual polygon drawing, the
    mobile engine actively prohibits adding vertices that create
    self-intersecting (bowtie) segments, providing immediate on-screen guidance
    to maintain topological validity.

###### Preloaded Entities & "Select an Entity" Interaction

*   **Selection Workflow**: Collectors select preloaded entities from a
    searchable attribute list or by tapping map markers.
*   **Interactive Overlays**: Preloaded geospatial entities render as map
    overlays with visibility toggle controls.
*   **Bottom Sheet Action**: Tapping an entity reveals a bottom sheet displaying
    baseline attributes and historical submission timestamps, with an action to
    launch a new submission pre-linked to that entity.
*   **Mix-and-Match Forms**: Form designers can combine "Select an Entity"
    questions with new geometry capture questions in the same form.

###### Geometry Capture via QR Code Scanning

To support rapid, error-free geometry ingestion and seamless interoperability
with external registries, land titles, and partner field tools (such as WHIMO)
without requiring physical perimeter walking or manual map digitizing:

*   **Camera-Based Geometry Ingestion**: When collecting data for any geometry
    question (Point, LineString, or Polygon) or defining a field-created entity,
    data collectors can trigger a built-in QR code scanner.
*   **Standardized Spatial Payload Decoding**: Directly scans and decodes
    geospatial payloads (such as GeoJSON geometries or coordinate lists) encoded
    in QR codes displayed on paper certificates, partner mobile apps, or plot
    markers.
*   **Instant Spatial Rendering & HUD Validation**: Scanned geometries undergo
    immediate client-side validation (coordinate range checks, polygon closure,
    and vertex complexity guardrails), automatically render on the map viewport
    with centering, and populate real-time HUD calculations (area and
    perimeter), seamlessly binding the spatial feature to the active question.

##### Real-Time Geometry Feedback & HUD Calculations

During geometry capture, a semi-transparent on-screen card provides live spatial
feedback:

*   **Total Walked Perimeter**: Live perimeter counter updating dynamically as
    the collector walks (displayed in meters or feet).
*   **Horizontal GPS Accuracy & Elevation**: Real-time accuracy and elevation
    metadata displayed on screen (in meters or feet) to prevent saving
    poor-quality geometry under dense canopy.
*   **Haptic Capture Confirmation**: Provides tactile vibration feedback upon
    pressing "Add point" or placing a vertex, confirming registration on older
    or slower mobile hardware and preventing duplicate taps.
*   **Organizer-Enforced Proximity & Accuracy Thresholds**: When configured by
    the survey organizer, the app enforces maximum GPS accuracy thresholds
    (e.g., must be <= 5m) or distance radii before permitting point or vertex
    recording, ensuring low-accuracy fixes under dense canopy are rejected.
*   **Organizer-Enforced Hardware GPS & Mock Location Suppression**: Survey
    organizers can disable manual map reticle and vertex placement across point
    (`geopoint`), path (`geotrace`), and perimeter (`geoshape`) questions
    (omitting `placement-map` in XLSForm `appearance`) to force data collectors
    to record actual hardware GNSS sensor readings at their physical location
    rather than manually tapping or dragging vertices on the map. Organizers can
    also disallow mock location providers (`allow-mock-accuracy=false`) to
    prevent GPS spoofing.
*   **Real-Time Area Size**: Calculates and displays enclosed area (in
    **hectares** or **acres**) the instant a polygon is closed.
*   **On-Screen GeoID Confirmation & Entity Card Display**: Prominently surfaces
    the deterministically generated GeoID directly on the mobile submission
    confirmation screen upon saving, as well as on entity detail cards and
    bottom sheets, giving enumerators and farmers immediate visual verification
    of the unique spatial identifier calculated locally for any geometry.
*   **Configurable Unit System (Metric / Imperial)**: Measurement units adapt to
    user preference (metric or imperial). The operating system setting is used
    by default, while users can explicitly modify units in app settings.

##### Field Usability, Navigation, & Non-Linear Interviews

*   **Marker Prominence & Zoom Preservation**: High-contrast markers on colored
    backgrounds with automatic zoom preservation during task transitions.
*   **Wayfinding HUD**: Persistent "blue dot" with heading indicator guiding
    collectors toward pre-identified plots.
*   **Non-Linear Question Navigation**: Toggleable question list allowing
    collectors to jump freely between questions during conversational farmer
    interviews.
*   **On-the-Fly Language Switching**: In-app button to toggle UI languages
    instantly without exiting active forms.
*   **Measurement Unit Preferences**: Setting allowing users to switch between
    metric (meters, hectares) and imperial (feet, acres) units, defaulting to
    the device's system setting.
*   **Landscape Tablet Mode**: Native landscape orientation and split-screen
    layouts for cooperative offices using tablets for bulk registrations.
*   **Submission History in Collection-Time Form Version**: When reviewing
    locally cached past submissions in the field, records are displayed using
    the specific form version and schema active at the time of data collection.
*   **On-Device Curation (Edit and Delete)**: Enumerators can review, edit, and
    delete their locally cached submissions and field-registered entities
    directly on mobile, enabling in-situ corrections (e.g., updating answers,
    re-taking photos, or adjusting geometries) and removal of accidental entries
    before or after synchronization.
*   **Viewer (Read-Only) Role Field UX**: When accessed by accounts assigned a
    read-only `VIEWER` role, the mobile client runs in an exploratory mode,
    enabling full map navigation, entity inspection, and submission viewing
    while hiding and disabling collection actions (such as registering new
    geospatial entities or collecting data).
*   **Streamlined Single-Action Capture**: For minimal surveys (such as simple
    point drops or boundary tracking with no additional questions), the app
    bypasses extraneous navigation flows and lands enumerators directly in
    spatial capture mode, allowing data collection in minimal taps.

##### Offline Entity and Submission Summary PDF Generation & Field Sharing

To deliver immediate, verifiable documentation to smallholder farmers and field
supervisors in disconnected environments, the Mobile App supports generating and
sharing standardized entity and submission summary PDFs directly in the field:

*   **100% Offline On-Device Generation**: Collectors and supervisors can
    generate publication-ready summary PDFs for any selected Entity (preloaded
    or field-created) or completed Submission record directly within the mobile
    client. The rendering pipeline operates entirely locally on-device using
    native canvas/document graphics, requiring zero cellular network
    connectivity or backend round-trips.
*   **Local Download & Native System Share Sheet**:
    *   **Direct Download / Save**: Users can save or download generated PDF
        files directly into local device storage for permanent offline archival.
    *   **System Share Sheet Dispatch**: Integrates with native mobile operating
        system share sheets to transmit PDFs across external channels, including
        instant messaging (e.g., WhatsApp, Signal), Bluetooth, nearby sharing,
        or email.
    *   **External Viewers & Mobile Printing**: Opens directly in system PDF
        viewers or prints in the field via portable Bluetooth printers to hand
        physical receipts to farmers at the farm gate.

### Technical Requirements

#### Shared Technical Requirements

##### Centralized Backend Architecture & Security Governance

Ground 2.0 routes all web and mobile transactions through a consolidated
**Centralized Backend Service**:

*   **Unified Security Governance**: Authoritative backend access control, data
    validation, and administrative rule enforcement.
*   **Fine-Grained Quota Enforcement**: Intercepts sync requests to validate
    transaction volumes and media sizes against organizational quotas before
    persistence.
*   **High-Throughput Performance**: Dedicated backend workers to enqueue and
    process heavy write volumes (scaling up to 1 million submissions).
*   **Server-Side Conflict Detection**: Enforces unique constraints on
    `entity_id` for Single Submission (1:1) surveys; accepts the earliest sync
    receipt as the baseline and routes duplicate syncs to the Web Console
    Conflict Review Queue.
*   **Immutable Schema Snapshots & Version Binding**: Survey configurations and
    form schemas are persisted as immutable snapshots keyed by `survey_id` and
    `form_version_id`. Ingestion workers enforce schema validation against the
    specific form version stamped on each incoming submission at collection
    time.
*   **Append-Only Audit Logging Infrastructure**: Centralized audit logging
    service captures all mutations across surveys, forms, submissions, and
    entities in append-only storage, recording actor identity, UTC timestamps,
    action types, and structured before/after field deltas.
*   **Dynamic Export Harmonization Engine**: Background export jobs dynamically
    reconcile multi-version submissions against the latest form version schema,
    remapping attributes, projecting soft-deleted question columns on demand,
    and compiling batch entity and submission summary PDF reports.
*   **Soft Delete & Trash Lifecycle Management**: An automated backend retention
    worker manages soft-deleted surveys, forms, and submissions, maintaining a
    secure 30-day recovery window in an auto-emptying trash store before
    permanently purging records.

##### Automated Backend Data Enrichment & Hooks

<!-- TODO: Implement hooks and APIs for data enrichment. -->

*   **Event-Driven Hooks & APIs**: Extensible webhooks and API triggers allow
    external services to perform data enrichment (such as boundary registration
    or environmental analysis) asynchronously upon submission synchronization.
*   **Local GeoID Derivation**: Because spatial identifiers (such as S2 cell
    GeoIDs) can be calculated deterministically and locally from coordinates,
    clients and backends compute them on-demand for verification and regulatory
    compliance without requiring central coordination.

##### Resource Quotas & Universal Guardrails

Usage is governed by a tiered quota system and hardcoded guardrails to protect
backend resources. *(Note: Future work explores enabling non-profits to pay for
additional quota beyond the Basic Tier via a foundation-supported payment model;
see
[Paid Quota Expansion & Foundation Payment Model](#paid-quota-expansion--foundation-payment-model).)*

###### Tiered Quota Limits

**Tiered Survey Organizer Limits** (Administrative scopes):
<!-- mdformat off(b/556740108: multiline tables break in GFM and OneDoc) -->

| Metric / Constraint | Basic Tier (Default) | Sponsored Tier |
| ----------------------------------------- | -------------------- | -------------- |
| **Maximum surveys per owner** | 5 | 100 |
| **Maximum jobs/forms per survey** | 5 | 10 |
| **Maximum tasks/questions per job** | 10 | 50 |
| **Maximum photo tasks per job** | 1 | 10 |
| **Maximum predefined geospatial entities** | 1,000 | 10,000 |
| **Maximum users in Access Control Lists (ACLs)** | 10 | 50 |

<!-- mdformat on -->

**Tiered Data Collection Limits** (Operational and field submission scopes):

<!-- mdformat off(b/556740108: multiline tables break in GFM and OneDoc) -->

| Metric / Constraint | Basic Tier (Default) | Sponsored Tier |
| ------------------------------------------------ | -------------------- | -------------- |
| **Maximum surveys per user** | 5 | 100 |
| **Maximum ad hoc/field-created entities per user** | 100 | 200 |
| **Maximum submissions per predefined geospatial entity per user** | 10 | 50 |

<!-- mdformat on -->

###### Universal Hardcoded Guardrails

**Survey Design Limits** (Enforced during web design and XLSForm import):

*   **Survey name / form name**: Max 100 characters
*   **Survey description / question instructions**: Max 255 characters
*   **Instructions task**: Max 1024 characters
*   **Multiple choice option label**: Max 255 characters
*   **Geospatial entity string property**: Max 255 characters

**Data Collection Limits** (Enforced during entry and sync):

*   **Text field input**: Max 100 characters
*   **Polygon complexity**: Max 50 vertices per polygon
*   **Photo resolution & EXIF metadata**: Dynamically scaled to 48 megapixels to
    optimize storage and bandwidth while preserving device EXIF metadata (camera
    model, orientation, hardware timestamps, and embedded GPS tags) and native
    compression parameters.

##### Large-Scale Map Feature Rendering Performance

To support regional and national monitoring campaigns mapping hundreds of
thousands of plots:

*   **1 Million Map Feature Scalability**: The mapping engines across both the
    Web Console and Mobile App are optimized to support visual rendering and
    responsive spatial interaction for up to **1 million map features** (points,
    tracks, and plot boundary polygons) simultaneously.
*   **Dynamic Spatial Indexing & Viewport Caching**: Utilizes client-side
    spatial indexing (R-trees / geohashing), viewport bounding-box querying,
    vector tile clipping, automated marker clustering at coarse zoom levels, and
    level-of-detail (LOD) geometry simplification to maintain high frame rates
    without device memory exhaustion.

##### Engineering Architecture, Maintainability & Agent Verifiability

To significantly reduce ongoing maintenance overhead, accelerate development
velocity, and ensure sustainable open-source community stewardship:

*   **Logic Deduplication via Shared Multiplatform Core**: A shared
    cross-platform engine encapsulates data synchronization, offline
    persistence, form expression evaluation, coordinate transformations, summary
    PDF layout/data-binding specifications, and API coordination, reducing
    engineering maintenance by >40% and eliminating cross-platform logic drift
    between web, Android, and iOS.
*   **Separation of Concerns**: Clean modular decoupling between UI presentation
    layers, offline database adapters, geospatial rendering modules, and
    centralized backend services to facilitate targeted debugging and isolated
    unit testing.
*   **Agent-Verifiable Development Loops**: Development workflows, CLI tools,
    and integration harnesses are architected to be hermetic and
    deterministically verifiable by both AI coding agents and human developers,
    establishing rapid, automated RED-GREEN-REFACTOR development loops.
*   **Centralized Documentation & Machine-Readable Artifacts**: All
    architectural specifications, Protobuf schema definitions, and design
    documentation are co-located and maintained directly within the repository,
    ensuring code and documentation remain continuously synchronized.
*   **Shared Agent Skills & Developer Knowledge Base**: Standardizes development
    workflows, test verification recipes, and codebase conventions into reusable
    skills and instruction sets to streamline onboarding and maintain long-term
    code health.

#### Web Console Technical Requirements

##### Browser Compatibility & Operating Environment

*   **Supported Browsers**: Google Chrome, Mozilla Firefox, Apple Safari, and
    Microsoft Edge.
*   **Workspace Design**: Desktop-optimized layout supporting multi-column
    controls and responsive wide-screen data management.
*   **In-App Language Selector**: Top-level navigation includes an integrated
    locale selector to switch console languages directly without URL
    manipulation.

##### In-Browser GIS Rendering & Digitization Performance

*   Hardware-accelerated in-browser vector rendering for responsive drawing,
    vertex manipulation, and real-time spatial calculations.

##### Summary PDF Generation & Printing

*   **In-Browser & Backend PDF Rendering Pipeline**: Supports high-resolution
    vector map snapshot rendering, collection-version form schema
    reconstruction, and standardized document layout assembly to generate
    publication-ready entity and submission summary PDFs directly in the browser
    for immediate preview, printing, and download, as well as asynchronous
    backend worker generation for bulk PDF exports.

#### Mobile App Technical Requirements

##### Maintenance Efficiency & Cross-Platform Feature Parity

*   **Requirement (Maintenance Overhead & Velocity)**: Significantly reduce
    ongoing maintenance costs and accelerate development velocity across mobile
    platforms, ensuring simultaneous feature releases and strict logical parity
    between Android and iOS without duplicating implementation effort.
*   **Unified Multiplatform Core Architecture**: Standardize on a shared
    multiplatform core across mobile platforms to fulfill this requirement:
    *   **Shared Core Logic**: Cross-platform engine handling data sync, offline
        storage, form logic evaluation, coordinate transformations, unit
        conversions (metric/imperial), and API coordination.
    *   **Maintenance & Parity Impact**: Reduces mobile engineering overhead
        by >40% compared to maintaining separate platform codebases, eliminating
        cross-platform logic drift, synchronizing feature releases, and
        preventing duplicate bug fixes.

##### Offline-First Architecture & Local State Management

*   **100% Disconnected Operation**: Local offline database caching for
    versioned survey schemas, preloaded entity registries, and submission
    queues.
*   **Form Version Binding on Local Save**: Every submission record created
    offline is bound to the active form version ID, ensuring consistent local
    validation and enabling retrospective rendering against that exact schema.
*   **Embedded Form Logic Evaluator**: Standard XForms-compliant expression
    engine embedded within the shared core for fast, fully offline evaluation of
    skip logic, constraints, and calculations.
*   **Deterministic Local Submission Model Enforcement**:
    *   **Waypoint Completion UX (1:1)**: Updates local state upon submission,
        styling entity markers as completed and deactivating data collection to
        prevent offline double-entry.
    *   **Continuous Active UX (1:N)**: Retains active marker state and surfaces
        historical submission timestamps on the entity card before subsequent
        collections.

##### Offline Storage Safeguards & Media Purging

*   **Guided "Take Offline" Workflow**: Step-by-step bounding box selector for
    pre-caching target regions prior to field departure.
*   **Multi-Zoom Vector & Satellite Raster Caching**: Downloads Mapbox vector
    and raster tiles across multiple zoom levels within the selected bounding
    box. Ground 2.0 exclusively supports downloading Mapbox vector and raster
    tiles.
*   **Device Storage Exhaustion Safeguards**: Dynamic storage monitoring issues
    prominent warnings when available device storage falls below critical levels
    (e.g., <500 MB) and suspends further tile downloads to avoid crashes.
*   **Uploaded Media Cache Eviction**: Provides explicit controls to safely
    purge locally cached photos that have already successfully synchronized to
    cloud storage, freeing internal device storage while preserving thumbnail
    references and record integrity.

##### Camera-Based QR Code Geometry Ingestion

*   **Hardware-Accelerated Scanner Pipeline**: Integrated camera barcode and QR
    scanner utilizing platform-optimized optical scanning capabilities
    coordinated through the shared application core.
*   **Geospatial Payload Parsing & Schema Validation**: Robust parser supporting
    GeoJSON Feature and Geometry specifications (Point, LineString, Polygon) and
    delimited coordinate strings. Enforces strict client-side validation,
    including WGS84 coordinate boundaries, standard coordinate ordering
    (longitude, latitude), linear ring closure, and platform guardrails (maximum
    50 vertices per polygon).
*   **Reactive State Propagation**: Successfully parsed geometry immediately
    updates the active form and dispatches reactive state updates to the
    interactive map display and real-time HUD calculation engine without UI
    latency.

##### On-Device Data Sharing

*   **Offline PDF Rendering Engine**: Lightweight, cross-platform PDF layout and
    generation module running entirely on-device without cloud dependencies,
    dynamically assembling tabular attributes, hierarchical question-response
    trees, and vector geometry snapshots into standard PDF documents consistent
    with Web Console summary PDFs.
*   **Secure Sandboxed Storage**: Persists generated PDF files within secure,
    sandboxed local storage and safely surfaces them via platform-native secure
    file access controls to guarantee data privacy.
*   **Native System Sharing Integration**: Seamless integration with the mobile
    operating system's native sharing capabilities across Android and iOS,
    supporting direct file download and saving, opening in external PDF viewers,
    mobile printing, and multi-channel sharing across messaging and email apps.
*   **On-Screen Geometry QR Code Generation**: Dynamically encodes captured or
    selected feature geometries (Point, LineString, Polygon) into standardized,
    compact geospatial payloads (such as GeoJSON or coordinate strings) and
    renders high-contrast QR codes directly on the device display, enabling
    seamless, 100% offline peer-to-peer geometry sharing and rapid handoff to
    partner field tools (such as WHIMO) via camera scanning without requiring
    network connectivity or wireless pairing.

### Documentation

#### Refreshed groundplatform.org Website

A proposed sitemap, navigation structure, and page-by-page copy strategy for a
refreshed groundplatform.org can be found in the
**[Website Outline](?tab=t.2eof4y51gq64)** tab.

## Not in Scope

To maintain engineering velocity and focus on core field reliability, XLSForm
parity, and cross-platform multiplatform stabilization, the following
capabilities are explicitly designated as out of scope for the Ground 2.0
release lifecycle (and deferred to future roadmap milestones):

*   **Map-Based Visual Photo-Interpretation & Classification (P3)**:
    Remote-sensing visual interpretation, augmented sampling grids, and desktop
    labeling workflows (such as Collect Earth-style photo-interpretation) are
    excluded from Ground 2.0's field data collection scope; see
    **[Support for Visual Classification](?tab=t.9nlxzx2c7bzm#support-for-visual-classification)**.
*   **Direct Database Streaming Integrations (P3)**: Direct streaming or
    bi-directional synchronizations with Google Earth Engine feature tables,
    BigQuery datasets, or Google Sheets are not supported natively in Ground
    2.0; external integrations rely on standard webhooks, REST APIs, and tabular
    exports (CSV/GeoJSON/Shapefile); see
    **[One- or Two-Way Sync with Google Sheets](?tab=t.9nlxzx2c7bzm#one--or-two-way-sync-with-google-sheets)**.
*   **Custom User Map Overlays & Tile Services (P3)**: Uploading arbitrary
    custom map overlays or streaming non-standard web tile services (WMS, WMTS,
    TMS, XYZ) into the Web Console is excluded; Ground 2.0 standardizes on
    high-resolution cloud basemaps.
*   **Custom User-Supplied Offline Imagery & Rasters (P3)**: Mobile offline
    caching is strictly standardized on pre-cached Mapbox vector and raster
    tiles. Direct ingestion, sideloading, or rendering of user-supplied
    drone/UAV orthomosaics, COGs, or custom MBTiles is deferred; see
    **[Custom Offline Basemaps](?tab=t.9nlxzx2c7bzm#custom-offline-basemaps)**.
*   **Dynamically Computed Analytical Map Layers (P3)**: Generating on-the-fly
    computational layers (such as NDVI vegetation indices, interpolated canopy
    surfaces, or heatmaps) within the client mapping interface is out of scope.
*   **In-App Statistics & Analytical Dashboards (P3)**: Advanced in-app
    statistical cross-tabulations, data exploration widgets, and business
    intelligence analytics within web and mobile clients are deferred; analysis
    should be conducted via exported datasets in dedicated GIS or statistical
    tools; see
    **[Survey Analytics, Reports, and Dashboards](?tab=t.9nlxzx2c7bzm#survey-analytics-reports-and-dashboards)**.

## Future Work

For strategic initiatives and capabilities planned for subsequent
milestones—including visual classification, custom offline basemaps, AI
integration, survey analytics, QA/QC controls, Google Sheets synchronization,
quota expansion, automated task assignment, gamified crowdsourcing, and
collective active inference—see the **[Future Work](?tab=t.9nlxzx2c7bzm)** tab.
