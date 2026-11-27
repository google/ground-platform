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

# Personas and Critical User Journeys

Authors: [Gino Miceli](https://github.com/gino-m) \
Last modified: 2026-11-21

**TL;DR**: Collect One serves ten personas across three surfaces (Web Console,
Mobile App, and Google Earth via KML balloons). They act through three survey
roles, two organization roles, and a platform administrator status. This
document names each persona, maps it to the access model, and lists the 38
Critical User Journeys (CUJs) the product has to get right, each with its
steps, success criteria, and how much of it exists in the code today.

## How to Read This Document

*   **Personas** describe people and their goals. **Roles** are what the access
    model enforces (`acl.proto`, `organization.proto`). One person often holds
    several roles, and one role serves several personas: there is no separate
    interpreter role, for example, because `DATA_COLLECTOR` covers both field
    and desk collection.
*   **CUJ IDs** are stable (`ORG-3`, `FLD-2`, ...). Refer to them in issues,
    pull requests, and test names. Add new journeys at the end of their group
    rather than renumbering.
*   **Status** says how much of the journey the code supports:

    | Status | Meaning |
    | :--- | :--- |
    | **Built** | The screens, logic, and backend calls for every step exist. |
    | **Partial** | The main path exists; the gaps are listed. |
    | **Planned** | Described in the [PRD](prd.md) or [CEO integration](ceo-integration.md) but not found in the client code. |

    Status comes from reading the source, UI strings, and existing docs at
    commit `8e5527880`. It was not confirmed by walking each journey in a
    running build, so treat **Built** as "implemented", not "verified end to
    end". The [End-to-End Coverage](#end-to-end-coverage) section lists the
    journeys that automated tests do exercise.

## Access Model

Personas act through these roles. The effective survey role is the strongest
one that applies (see
[Access Control Lists and Quotas](../technical/model/survey/04-access-control-and-quotas.md)
and `SurveyAccess.kt`).

<!-- mdformat off -->

| Scope | Role | How it is granted | What it allows |
| :--- | :--- | :--- | :--- |
| Survey | Owner | Creating the survey | Everything a Survey organizer can do; always a Survey organizer |
| Survey | `SURVEY_ORGANIZER` | Accepted invite, or inherited by Managers of the survey's organization | Edit forms, layers, and tables; manage sharing; publish; close, archive, reopen, and delete; export; see contributor identities |
| Survey | `DATA_COLLECTOR` | Accepted invite; organization membership under the `ORGANIZATION` policy; anyone under `ANYONE_WITH_LINK` or `PUBLIC` | Open the survey, submit forms, add map features where the dataset allows it |
| Survey | `VIEWER` | Accepted invite | Browse the map, map features, and submissions; collection and editing actions are hidden |
| Organization | `MANAGER` | Creating the organization, or invited or promoted by a Manager | Edit the organization, members, imagery sources, and library; Survey organizer on every survey in it |
| Organization | `MEMBER` | Accepted invite or approved join request | See the organization's surveys, create surveys in it, collect on those shared with the organization |
| Platform | Administrator | Active Manager of the synthetic "All users" organization, or named by the deployment (`ADMIN_EMAILS`) | Edit the global library and global imagery sources, see platform-wide impact, set quotas and cost weights, create surveys without the per-owner limit |

<!-- mdformat on -->

Two settings change what a role sees rather than what it may do:
`PeerDataVisibility` (collectors see everyone's data or only their own) and
contributor identity hiding (only Survey organizers see who submitted what).

## Personas

<!-- mdformat off -->

| ID | Persona | Who they are | Surface | Typical role | Top goals |
| :--- | :--- | :--- | :--- | :--- | :--- |
| P1 | **Survey organizer** | Project manager, conservation lead, or GIS lead at an NGO, cooperative, agency, or research group. Comfortable with spreadsheets; not necessarily with GIS or XLSForm | Web Console | Owner or `SURVEY_ORGANIZER` | Launch a survey quickly, get the right people collecting, see progress, export data others accept |
| P2 | **Field data collector** | Enumerator, community ranger, or extension agent. May be a first-time smartphone user; works in sunlight, under canopy, and without a connection | Mobile App | `DATA_COLLECTOR` | Find the right site, capture it correctly once, never lose data, know it was uploaded |
| P3 | **Desk interpreter** | Remote-sensing analyst who classifies sample plots from imagery, often migrating from Collect Earth or Collect Earth Online | Web Console; Google Earth | `DATA_COLLECTOR` | Work through plots fast, compare imagery over time, record confidence, hand off what can't be decided from above |
| P4 | **QA/QC supervisor** | Senior analyst or field supervisor who checks the quality of others' work | Web Console | `SURVEY_ORGANIZER` | See what was collected and by whom, spot problems, send plots to the field, correct boundaries |
| P5 | **Organization manager** | Program or data lead who runs many surveys for an institution (the CEO "institution admin") | Web Console | Organization `MANAGER` | Standardize forms and vocabulary, manage people, report what the organization's work adds up to |
| P6 | **Organization member** | Staff member or partner affiliated with an organization | Web Console; Mobile App | Organization `MEMBER` | Find the organization's surveys, start new ones under it |
| P7 | **Viewer** | Donor, auditor, buyer, or government partner who needs to see the data but not change it | Web Console; Mobile App | `VIEWER` | Inspect the map and records with confidence that nothing can be changed by accident |
| P8 | **Public contributor** | Volunteer or community member joining an open survey from a link, QR code, or the directory | Mobile App; Web Console | `DATA_COLLECTOR` (by policy) | Start contributing in minutes, understand what becomes public |
| P9 | **Platform administrator** | Steward of a Collect One deployment (FAO or SIG staff on the hosted instance) | Web Console | Platform administrator | Curate the global library, keep usage within quota and budget, report platform-wide impact |
| P10 | **Deployment operator** | Engineer who hosts, configures, or develops Collect One | Server, debug UIs, launchpad | None (infrastructure access) | Run the service, control who may sign in, diagnose sync and email problems |

<!-- mdformat on -->

**Indirect stakeholders** never sign in but shape the journeys: the **farmer or
land holder** whose plot is mapped (receives a receipt, gives consent), and
**downstream systems and their users** (EUDR due diligence tools, Open Foris
Whisp, the AgStack Asset Registry, FAO FERM) that consume exports and GeoIDs.

One person commonly wears several hats: at a small cooperative the same person
is P1, P4, and P5, and on a pilot the organizer is also the first collector.
Journeys are written per hat.

## Critical User Journeys

### Onboarding and Access

Shared by every persona; a failure here blocks everything else.

<!-- mdformat off -->

| ID | Journey | Personas | Surface | Status |
| :--- | :--- | :--- | :--- | :--- |
| ACC-1 | Sign in and accept the terms | All | Web, Mobile | Built |
| ACC-2 | Accept an emailed survey invitation | P2, P3, P4, P7 | Web, Mobile | Built |
| ACC-3 | Join an open survey and give informed consent | P8 | Mobile, Web | Partial |
| ACC-4 | Join an organization | P6 | Web | Built |

<!-- mdformat on -->

**ACC-1 Sign in and accept the terms**

1.  Open the Web Console or the Mobile App and choose **Sign in with Google**.
2.  Read the Terms of Service and accept them (**Decline** signs out).
3.  Land on the surveys the account can open: the Surveys page on web, the
    Download survey screen on mobile.

*Success*: A new user reaches their surveys in under a minute. A deep link
opened while signed out opens after sign-in. An account outside the
deployment's passlist is refused with a clear reason. On relaunch, mobile
reopens the survey that was open, with or without a connection.

**ACC-2 Accept an emailed survey invitation**

1.  An organizer invites the person by email with a role (ORG-7); the server
    sends an invitation email.
2.  The invitee opens the link (`/#join/<surveyId>/<token>`), signs in, and
    accepts the terms.
3.  They confirm their name and profile photo; the survey dashboard opens.
4.  On mobile, the survey now appears on the Download survey screen.

*Success*: The invitation survives sign-in and terms acceptance. The organizer
sees the entry change from **Invited** to **Joined** with the person's name.
The invitee has exactly the invited role.

**ACC-3 Join an open survey and give informed consent**

1.  The contributor finds the survey under **Public surveys** in the mobile
    Download survey screen, or opens a shared link.
2.  They download or open the survey.
3.  Before the first submission, a consent notice explains what is public (map
    features, coordinates, answers, photos), that their identity is hidden from
    other participants, and how to protect others' privacy. They accept.
4.  While collecting, a banner keeps reminding them the data is public.

*Success*: No invitation or organizer action is needed. The contributor cannot
submit before accepting. Other participants see "Participant (identity
hidden)", never a name or email.

*Gaps*: The sharing setting promises access by "link or QR code", and map
features can show a QR code, but no in-app QR scanner for joining a survey was
found; mobile joins go through the directory or a link.

**ACC-4 Join an organization**

1.  Open **Manage organizations** from the profile menu.
2.  Either accept a Manager's invite link (`/#org-join/<organizationId>/<token>`),
    or find a listed organization and **Request to join**.
3.  Once a Manager approves (MGR-1), the organization's surveys appear grouped
    under it on the Surveys page.

*Success*: Only `ACTIVE` memberships grant access. Unlisted organizations are
reachable by invite only. Members can leave; the last Manager cannot.

### Survey Organizer

<!-- mdformat off -->

| ID | Journey | Personas | Surface | Status |
| :--- | :--- | :--- | :--- | :--- |
| ORG-1 | Launch a simple survey from a purpose | P1 | Web | Built |
| ORG-2 | Set up a survey with the guided wizard | P1 | Web | Built |
| ORG-3 | Design, preview, and publish a form | P1 | Web | Built |
| ORG-4 | Bring in an existing form or project | P1, P3 | Web | Partial |
| ORG-5 | Preload and curate map features | P1, P4 | Web | Partial |
| ORG-6 | Create a sample design and plot review workflow | P1, P3 | Web | Built |
| ORG-7 | Share a survey and control what collectors see | P1 | Web | Built |
| ORG-8 | Monitor progress and review collected data | P1, P4 | Web | Built |
| ORG-9 | Export data for analysis and compliance | P1 | Web | Partial |
| ORG-10 | Screen plots for deforestation risk | P1 | Web | Partial |
| ORG-11 | Update a survey that is already collecting | P1 | Web | Partial |
| ORG-12 | Close, archive, reopen, or delete a survey | P1 | Web | Partial |

<!-- mdformat on -->

**ORG-1 Launch a simple survey from a purpose**

1.  On the Surveys page, choose **Create survey**; enter a title and,
    optionally, an organization.
2.  Answer **What will this data be used for?** by picking one or more purposes
    (and programs such as EUDR), or **Skip** for an empty survey.
3.  The survey opens in the Survey editor, seeded with each purpose's form
    templates and a linked map layer or data table.
4.  **Publish changes**, then share it (ORG-7).

*Success*: A point-collection survey is ready for collectors in a few clicks,
with no form or table configured by hand. Questions seeded from a purpose are
already linked to standard fields, so impact numbers and export profiles work
without further setup. This is the "preserve simplicity" bar from the
[concept brief](concept-brief.md).

**ORG-2 Set up a survey with the guided wizard**

1.  Open the Survey Creation Wizard (`/surveys/new`).
2.  **Overview**: title, organization, languages, and how to start (new, copy an
    existing survey, or import a file).
3.  **Purpose**: collection mode (field survey, visual classification, or both),
    purpose, starting template, and reporting programs.
4.  **Survey area**: search for a place, draw a boundary, or upload GeoJSON or
    KML.
5.  **Map features**: free-form field collection, a statistical sample plot
    grid, or imported features.
6.  **Forms**: review the questionnaires and their standard field links.
7.  **Sharing**: review impact and export readiness, optionally apply CARE and
    FPIC data sovereignty defaults, and invite collaborators.

*Success*: Each step explains itself; required fields are flagged where they
are. An unfinished draft is offered again on return. The wizard refuses to
exceed the owner's quota and says why. The finished survey needs no further
editing to start collecting.

**ORG-3 Design, preview, and publish a form**

1.  In the Survey editor, add a form (empty, from a template, or imported).
2.  Add, reorder, duplicate, and delete questions on the flow canvas; set type,
    label, hint, required, choices, and display logic.
3.  Add a Point, Line, or Polygon question and choose **GPS only** or **GPS or
    draw on map**; add photo, video, or audio questions and choose **Capture
    only** or **Capture or upload**.
4.  Accept suggested standard fields as labels are typed.
5.  Choose where the form is available (mobile, web, or both) and, under
    **Advanced**, whether a submission adds a map feature or updates an existing
    one.
6.  Run the **Mobile** and **Web** previews; check the paths the display logic
    produces.
7.  Resolve flagged issues and **Publish changes**.

*Success*: No XLSForm or XPath knowledge is needed for common logic. Each form
keeps its linked map layer or data table in step with its questions. Blocking
issues (for example a GPS-only question on a web-available form) are explained
and fixable in one action. The generated XForms parses with the form engine.

**ORG-4 Bring in an existing form or project**

1.  Import an ODK XForms XML file as a new form, or start the wizard from a
    Collect Earth project (`.cep`) or by copying an existing survey.
2.  Review the imported questions; accept the offered matches to standard
    fields.
3.  Publish.

*Success*: Logic, choices, and translations survive the import. Constructs the
editor doesn't model are preserved rather than dropped. Copying a survey
carries forms, layer schemas, area, and purposes, never data.

*Gaps*: XLSForm (`.xlsx`) import and export, and migrating a Collect Earth
Online project with its collected labels, are planned.

**ORG-5 Preload and curate map features**

1.  Add a map layer or data table in the Survey editor.
2.  Import features from GeoJSON, KML, or a delimited file, or draw points,
    lines, and polygons on the layer map.
3.  Edit attributes in the table; reshape a feature by dragging vertices and
    midpoint handles.
4.  Set the layer's color, stroke, fill, and default visibility, and whether
    collectors may add features in the field.
5.  Publish.

*Success*: Import problems are reported per feature in plain language
(unsupported type, self-crossing outline, ambiguous dates). Table and map
selection stay in sync. Saving keeps each feature's submissions. Quota limits
on preloaded features and polygon vertices are enforced with a clear message.

*Gaps*: Shapefile import is planned.

**ORG-6 Create a sample design and plot review workflow**

1.  Define the survey area (ORG-2).
2.  On a map layer, choose a sample method (systematic grid, simple random,
    stratified random, or cluster), spacing or count, plot shape and size, and
    optional sub-plot points; upload strata if needed.
3.  Generate the plots and review them on the map.
4.  Under **Plot review**, turn on **Check plots on imagery (web)** to add a
    web-only desk form with confidence and flagging questions.
5.  Optionally turn on **Verify plots in the field (mobile)** with a confidence
    threshold, random spot checks, and whether crews see the desk answers.
6.  Publish.

*Success*: Generation shows progress and explains failures ("No sample plots
fit in the area"). The survey opens for collection only after its plots are
saved. Plots that already have submissions block regeneration. The desk and
field forms are created and wired to plot status without hand-written logic.

**ORG-7 Share a survey and control what collectors see**

1.  Open **Sharing** in the Survey editor.
2.  Invite people by email as Viewer, Data collector, or Survey organizer; copy
    or renew an invite link.
3.  Set **Who can collect data** (invited people, the organization, or anyone)
    and **Who can view data**; optionally hide the survey from the directory.
4.  For community or tenure data, apply the CARE defaults (restricted access,
    own submissions only, consent terms).
5.  Publish.

*Success*: Opening collection to anyone defaults data visibility to private, so
making data public is always a separate, explicit choice. Organization Managers
appear as inherited, read-only entries. The ACL size quota is enforced.
Invitees receive an email and can complete ACC-2.

**ORG-8 Monitor progress and review collected data**

1.  Open the survey's dashboard.
2.  Scan the map and the feature list; marker symbols and colors show each
    feature's status.
3.  Select a feature to see its properties, then its **History** of submissions
    grouped by form; open one in the document view.
4.  Open the data table for a layer; sort, group columns, and jump between row
    and map.
5.  Switch to the **Impact** tab to see what the data adds up to.

*Success*: Synced submissions appear without a reload. Organizers see contributor
identities; others do not. Every submission renders with the form version it
was collected with. The dashboard stays responsive on large surveys through
clustering and a virtualized table.

**ORG-9 Export data for analysis and compliance**

1.  In the dashboard's data table, open the export menu.
2.  Choose **Download CSV** or **Download GeoJSON**, a purpose's export profile
    (such as **EUDR GeoJSON**), or **Download KML (Google Earth)**.
3.  If a profile needs fields that aren't linked, review the warning and either
    fix the links or **Export anyway**.
4.  For a single feature or submission, download or share its PDF.

*Success*: Profile exports meet the target format without post-processing
(EUDR: WGS84, six decimals, standard codes). Missing links are named before the
file is written, not discovered by the recipient. Each export is recorded as an
impact event.

*Gaps*: Shapefile export, bulk media ZIPs, webhooks, and partner pushes (FERM,
Arena) are planned.

**ORG-10 Screen plots for deforestation risk**

1.  Collect or import plot boundaries (FLD-2, ORG-5).
2.  After sync, the server registers each geometry for a GeoID and requests
    Open Foris Whisp indicators.
3.  In the dashboard, open a plot's **Risk analysis** tab for the screening
    summary and charts, or expand the Whisp columns in the table.
4.  Include the risk section in the plot's PDF.

*Success*: Enrichment needs no action from the organizer and never blocks
collection. Plots that share a GeoID are recognized as the same plot. Results
are clearly attributed to Whisp.

*Gaps*: Depends on deployment configuration (a Whisp API key); without it the
tab has nothing to show.

**ORG-11 Update a survey that is already collecting**

1.  Open the Survey editor of a published survey.
2.  Change questions, layers, or sharing; the editor holds a working copy.
3.  **Publish changes**, or **Discard**.
4.  Collectors receive the update on their next sync.

*Success*: Edits made elsewhere don't overwrite in-progress work. Existing
submissions stay readable in their original form version. Field devices pick up
the change without re-downloading the survey.

*Gaps*: A visual diff before publishing and soft-deleted questions in exports
are planned.

**ORG-12 Close, archive, reopen, or delete a survey**

1.  Choose **Close survey** from the editor's menu and confirm.
2.  Answer **What happened with this data?** or skip it; a reminder badge
    returns after 90 days if unanswered.
3.  Later, **Archive** or **Reopen** it.
4.  To remove it, use **Delete survey** in **Survey details** > **Danger zone**.

*Success*: A closed survey is read-only everywhere: no **Collect data** on web,
no `+` on mobile. Closing never waits on the outcome question. Deleting frees
the owner's survey quota immediately.

*Gaps*: Restoring a deleted survey from a 30-day trash is planned; the server
keeps trashed surveys but no restore screen was found.

### Field Data Collector

<!-- mdformat off -->

| ID | Journey | Personas | Surface | Status |
| :--- | :--- | :--- | :--- | :--- |
| FLD-1 | Prepare for the field | P2 | Mobile | Partial |
| FLD-2 | Register a new site | P2, P8 | Mobile | Partial |
| FLD-3 | Find and revisit an existing site | P2 | Mobile | Built |
| FLD-4 | Sync and confirm uploads | P2 | Mobile | Built |
| FLD-5 | Hand over a receipt | P2 | Mobile | Built |
| FLD-6 | Manage the device | P2 | Mobile | Built |
| FLD-7 | Correct a mistake | P2 | Mobile | Planned |

<!-- mdformat on -->

**FLD-1 Prepare for the field**

1.  While connected, sign in (ACC-1) and pick a survey on the Download survey
    screen; search by name, location, or organization.
2.  Open **Offline maps** and **Save an area**: frame the area, choose
    satellite, terrain, or both, and check the size against free storage.
3.  Wait for the download, or hide it and let it finish in the background.
4.  Go offline.

*Success*: Downloaded surveys are marked. The app says before downloading how
much space is needed and refuses areas that are too large or won't fit.
Interrupted downloads resume. Everything needed to collect is on the device.

*Gaps*: Saving map areas is simulated in the browser build, and the native
Android and iOS offline tile clients have not landed. iOS targets are paused.

**FLD-2 Register a new site**

1.  On the survey map, tap `+` and choose a form.
2.  Accept the consent terms if the survey requires them.
3.  Answer one question per screen. For a location, capture the GPS fix; for a
    boundary, **Start walking** the perimeter or draw it where the organizer
    allows.
4.  Take photos where asked.
5.  Review and submit.
6.  The new site appears on the map with its status marker and GeoID.

*Success*: Works with no connection from start to finish. A submitted form is
stored on the device at once and uploaded later (FLD-4). GPS-only questions
cannot be answered by panning the map, and a required accuracy is shown and
enforced. Per-collector quotas are explained when reached.

*Gaps*: Saving a half-filled form so that a dead battery loses nothing, live
area and perimeter while walking, rejecting mock locations, haptic feedback,
blocking self-crossing polygons during capture, and importing a geometry by
scanning a QR code are in the PRD but were not found in the code.

**FLD-3 Find and revisit an existing site**

1.  Search the list or pan the map; clusters split on zoom.
2.  Tap a site to see its status, properties, and past submissions.
3.  Tap **Navigate** and follow the bearing and straight-line distance.
4.  On site, launch the follow-up form from the site's card and submit.
5.  The site's marker advances (for example `○` → `◐` → `✓`).

*Success*: The map shows sites only, never duplicate pins per submission.
Navigation works offline and off-road. A collector sees peers' data only if the
organizer allows it. A form already completed for a site is marked as such.

**FLD-4 Sync and confirm uploads**

1.  Return to coverage; uploads start on their own.
2.  Open **Uploads** to see what is pending, in progress, uploaded, or failed,
    for the survey or for one site.
3.  **Retry failed uploads** or **Sync all** if needed.

*Success*: Queued changes are sent within seconds of reconnecting, without the
collector opening anything. Nothing is ever dropped silently: a rejected change
stays visible with its reason. "All changes saved" is trustworthy enough to
hand the device to someone else.

**FLD-5 Hand over a receipt**

1.  Open the site or submission just recorded.
2.  **Preview PDF**, then **Share PDF** to a messaging app or printer; or show
    the site's **QR code** carrying its geometry and GeoID.

*Success*: The PDF is created on the device with no connection and includes
the map snapshot, GeoID, area, and answers. The land holder leaves with proof
of what was recorded about their plot.

**FLD-6 Manage the device**

1.  Open the drawer to switch between downloaded surveys or remove one.
2.  In **Settings**, change units and language, and review storage.
3.  Free space by clearing media that has already been uploaded, or by removing
    saved map areas.

*Success*: The app is usable in each of the nine shipped languages. Removing
anything states how much space it frees and never deletes data that hasn't
been uploaded. Downloads pause before storage runs out.

**FLD-7 Correct a mistake**

1.  Open a site or submission the collector created.
2.  Edit its answers or delete it.
3.  The correction syncs like any other change.

*Success*: Collectors can fix their own records, and only their own, without
calling a supervisor. The original is kept in the audit trail.

*Gaps*: The sync layer models submission edits and deletions, but no collector
screen for them was found.

### Desk Interpreter and QA/QC Supervisor

<!-- mdformat off -->

| ID | Journey | Personas | Surface | Status |
| :--- | :--- | :--- | :--- | :--- |
| DSK-1 | Classify sample plots on imagery | P3 | Web | Partial |
| DSK-2 | Send an uncertain plot to the field and close the loop | P3, P2, P4 | Web, Mobile | Built |
| DSK-3 | Collect in Google Earth | P3 | Google Earth | Built |
| DSK-4 | Enter data on the web | P3, P1 | Web | Built |
| DSK-5 | Reach consensus across interpreters | P4 | Web | Planned |
| DSK-6 | Audit and correct records | P4 | Web | Planned |

<!-- mdformat on -->

**DSK-1 Classify sample plots on imagery**

1.  Open the survey dashboard and select a sample plot.
2.  Switch basemaps and the organization's imagery sources (XYZ tiles or Earth
    Engine layers such as Sentinel-2 composites, NDVI, and Dynamic World).
3.  Choose **Analyze imagery** to open the time series: pick Landsat,
    Sentinel-2, or Sentinel-1 and a band or index, and pin a scene to the map.
4.  Open the desk form from the plot's card, record the class and confidence,
    and submit.
5.  Move to the next plot.

*Success*: Imagery and charts load fast enough to keep a rhythm. Each plot
shows whether it has been classified. Earth Engine errors are explained and
retryable. The form and the imagery are visible side by side.

*Gaps*: Keyboard-driven plot navigation, side-by-side or swipe date comparison,
and logging the imagery used per label are planned. The server can lock a plot
for one collector, but no client use of the lock was found.

**DSK-2 Send an uncertain plot to the field and close the loop**

1.  The interpreter records a low confidence or flags the plot in the desk
    form.
2.  The plot's marker changes to `!`; random spot checks are added per the
    workflow (ORG-6).
3.  A field collector syncs, sees the flagged plots, and reads why each was
    flagged (and the desk answers, unless the check is blind).
4.  They navigate to the plot (FLD-3) and submit the field verification form.
5.  The marker changes to verified; the supervisor compares desk and field
    answers on the dashboard.

*Success*: No export, import, or message is needed between desk and field. The
field crew is never asked to visit plots that don't need it. Desk and field
records stay attached to the same plot.

**DSK-3 Collect in Google Earth**

1.  An organizer downloads the survey's KML (ORG-9) and shares it.
2.  The interpreter opens it in Google Earth; each layer is a folder of
    placemarks.
3.  Clicking a placemark opens its balloon with the survey's web form.
4.  They sign in once from the balloon, answer, and submit; a confirmation
    shows.

*Success*: Teams used to Collect Earth keep their imagery tool. Submissions
land in the same survey as web and mobile ones. A draft survives closing the
balloon.

**DSK-4 Enter data on the web**

1.  On the dashboard, choose **Collect data** in the toolbar or on a feature's
    card.
2.  If the form needs a target, pick the feature on the map or in the list.
3.  Answer the stacked question cards; for a geometry, **Draw on map**; for
    media, capture or upload as the organizer allows.
4.  **Submit**.

*Success*: The same validation as mobile applies. Only forms marked available
on web are offered. Geometry answers stay visible on the map while the form is
open.

**DSK-5 Reach consensus across interpreters**

1.  Assign each plot to several interpreters.
2.  Review agreement on a disagreement dashboard.
3.  Adjudicate disputed plots or send them to the field.

*Success*: Interpreters don't see each other's labels before submitting.
Excluding an uncalibrated contributor keeps their records.

**DSK-6 Audit and correct records**

1.  Open a record's revision history and see who changed what, and when.
2.  Correct a boundary or answer, or revert a change.
3.  Resolve conflicting offline edits in a review queue.

*Success*: Every change is attributable and reversible.

*Gaps*: The server records audit entries and exposes them, and organizers can
reshape preloaded features in the editor (ORG-5), but no history, conflict
queue, or revert screens were found.

### Organization Manager and Member

<!-- mdformat off -->

| ID | Journey | Personas | Surface | Status |
| :--- | :--- | :--- | :--- | :--- |
| MGR-1 | Create an organization and bring people in | P5 | Web | Built |
| MGR-2 | Standardize forms and vocabulary | P5 | Web | Built |
| MGR-3 | Configure imagery for the organization | P5 | Web | Built |
| MGR-4 | Report what the organization's data adds up to | P5, P6 | Web | Built |

<!-- mdformat on -->

**MGR-1 Create an organization and bring people in**

1.  **Create organization** with a name, description, type, country, and
    whether it is listed.
2.  On **Members**, invite people by email as Member or Manager; approve or
    decline requests to join.
3.  Change roles or remove people as the team changes.
4.  Move existing surveys into the organization from their **Survey details**.

*Success*: Managers get Survey organizer access to every survey in the
organization without being added to each one. The last Manager can't be
removed, and the page says why. Deleting the organization deletes no survey
data.

**MGR-2 Standardize forms and vocabulary**

1.  On **Dictionary**, add the organization's own concepts (type, unit, list
    values, aggregation, privacy).
2.  In a survey's Form editor, **Save as template**; it appears under
    **Templates**.
3.  On **Templates** and **Purposes**, rename or delete the organization's
    entries and **Hide** global ones the organization doesn't use.

*Success*: Organizers in the organization see its templates and concepts first.
A concept in use can't be changed in a way that breaks the data already
collected. Answers to linked questions add up across surveys and languages.

**MGR-3 Configure imagery for the organization**

1.  On **Imagery sources**, add an XYZ tile URL or an Earth Engine preset.
2.  Decide whether collectors may download it for offline use.

*Success*: The source appears in the layers control of the organization's
surveys on web and mobile. An invalid URL template is caught before saving.

**MGR-4 Report what the organization's data adds up to**

1.  Open the organization's **Impact** tab; filter by survey or country.
2.  Review hectares and plots mapped, producers registered, goal indicators,
    and how the data was used.
3.  **Download PDF summary**.
4.  Optionally turn on **Keep our data out of platform totals**.

*Success*: Plots mapped in more than one survey count once. Fields that
couldn't be counted are explained. Opting out removes the organization from
platform totals without changing its own numbers.

### Viewer

<!-- mdformat off -->

| ID | Journey | Personas | Surface | Status |
| :--- | :--- | :--- | :--- | :--- |
| VWR-1 | Review a survey without changing it | P7 | Web, Mobile | Built |

<!-- mdformat on -->

**VWR-1 Review a survey without changing it**

1.  Accept an invitation as Viewer (ACC-2).
2.  Browse the map, the feature list, properties, and submission history.

*Success*: No collect, edit, manage, or share action is offered on either
surface, and the server refuses them regardless of the client. Contributor
identities stay hidden.

### Platform Administrator and Operator

<!-- mdformat off -->

| ID | Journey | Personas | Surface | Status |
| :--- | :--- | :--- | :--- | :--- |
| ADM-1 | Curate the global library | P9 | Web | Built |
| ADM-2 | Govern usage, quotas, and cost | P9 | Web | Built |
| ADM-3 | Report platform-wide impact | P9 | Web | Partial |
| OPS-1 | Run and diagnose a deployment | P10 | Server | Built |

<!-- mdformat on -->

**ADM-1 Curate the global library**

1.  Open the admin panel (`/admin`), which is the "All users" organization.
2.  Add or edit global concepts, form templates, and purposes; add global
    imagery sources.
3.  Add or remove platform administrators.

*Success*: Changes reach every organization's pickers, except where an
organization has hidden an entry. Non-administrators who open `/admin` are
sent to their surveys with a notice.

**ADM-2 Govern usage, quotas, and cost**

1.  On **Cost controls**, choose a quota tier or set individual limits, a
    budget, and unit cost weights.
2.  On **Cost tracking**, review usage by organization, survey, and collector,
    and any budget alerts.

*Success*: Limits are enforced on the server, not only in the client. An
organizer who hits a limit is told which one.

**ADM-3 Report platform-wide impact**

1.  Open the **Impact** tab of "All users".
2.  Review totals from global standard fields, by country, with
    organization-suggested indicators shown separately.

*Success*: Opted-out organizations, sensitive concepts, and small groups
(fewer than 10 map features, or fewer than 2 organizations in an area) never
appear.

*Gaps*: A public anonymized dashboard and monthly sponsor digests are planned.

**OPS-1 Run and diagnose a deployment**

1.  Deploy the server with its auth mode, sign-in passlist, administrators,
    email provider, and optional Earth Engine and Whisp credentials.
2.  In development, use the launchpad and the development sign-in dialog to
    act as an administrator or a regular user.
3.  Diagnose with the RPC, message, and Spanner debug pages.

*Success*: A person outside the passlist cannot sign in. Debug pages and data
reset are off unless explicitly enabled, and are never on in production.

## Journeys That Cross Personas

Three end-to-end flows matter more than any single journey, because they are
what Collect One offers that its predecessors did not:

<!-- mdformat off -->

| Flow | Sequence | Why it matters |
| :--- | :--- | :--- |
| **Launch to first submission** | ORG-1 → ORG-7 → ACC-2 → FLD-1 → FLD-2 → FLD-4 → ORG-8 | The core loop. Time from "Create survey" to the first record on the organizer's dashboard is the headline measure of simplicity |
| **Desk to field and back** | ORG-6 → DSK-1 → DSK-2 → FLD-3 → FLD-4 → ORG-8 | The reason Ground and Collect Earth Online were merged |
| **Plot to proof** | FLD-2 → FLD-5 → ORG-10 → ORG-9 → MGR-4 | Takes one boundary walk through to a farmer receipt, a risk screening, a compliance export, and an impact number |

<!-- mdformat on -->

## End-to-End Coverage

The Playwright suite in [`e2eTests/`](../../e2eTests/) drives production builds
against a local server. It covers these journeys today:

<!-- mdformat off -->

| Journey | Covered by |
| :--- | :--- |
| ACC-1 (web) | `webConsole.spec.ts`, `webConsoleNavigation.spec.ts` |
| ACC-1 (mobile) | `mobileApp.spec.ts`, `mobileAppNavigation.spec.ts` |
| ORG-6 (plots saved before the survey opens) | `webConsoleSampleDesign.spec.ts` |
| FLD-4 (reconnect, server back, offline relaunch) | `mobileAppOffline.spec.ts` |
| Switching the web console's language (part of no journey above) | `webConsoleLanguage.spec.ts` |

<!-- mdformat on -->

Every other journey relies on module-level unit, Compose UI, and golden
screenshot tests. The three cross-persona flows above have no end-to-end test
and are the most valuable ones to add.

## Related Documents

*   [Product Requirements](prd.md): what each surface must do, and scale and
    quota targets.
*   [Collect Earth Online Integration](ceo-integration.md): the desk-to-field
    journey in narrative form, and the CEO capability inventory.
*   [Terminology](terminology.md): names used here and their equivalents in
    other tools.
*   [Access Control Lists and Quotas](../technical/model/survey/04-access-control-and-quotas.md):
    the role and sharing model in full.
*   [Web Console Features](../technical/client/web-console.md): page-by-page
    behavior behind the web journeys.
*   [Future Work](future-work.md): the roadmap behind several planned
    journeys.
