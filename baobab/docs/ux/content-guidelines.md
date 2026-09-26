<!--
  Copyright 2026 The Ground Authors.

  Licensed under the Apache License, Version 2.0 (the 'License');
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an 'AS IS' BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Ground 2.0 UX Writing & Content Guidelines (`docs/ux/content-guidelines.md`)

These guidelines govern all user-facing copy, terminology, voice, tone, and content standards across the Ground 2.0 Organizer Web Console, Mobile App (Android & iOS), notifications, and user documentation.

This guide is for UX writers, product designers, technical writers, and engineers writing strings, labels, instructions, dialogs, and messages.

---

## 1. Voice & Tone

Ground connects non-technical survey organizers, conservation managers, and frontline community collectors in remote and challenging environments. Our copy should feel human, dependable, and easy to understand at a glance under bright sunlight or in difficult field conditions.

### Brand Voice
Our voice is consistent across all surfaces:
* **Empowering & Respectful**: Treat data collectors and community members as skilled field partners, not passive data entry workers. Respect their time and context.
* **Direct & Plainspoken**: Use everyday, familiar words. Prefer short, active sentences over bureaucratic, academic, or technical phrasing.
* **Clear & Concise**: Get straight to the point. Eliminate filler words and extraneous background information. Every word must serve a purpose.
* **Supportive & Blameless**: When errors happen or connectivity drops, reassure the user, explain clearly what occurred, and provide a direct path forward without assigning blame.

### Tone Adapts to the Situation
While our voice never changes, our tone shifts depending on the user's emotional and operational context:
* **Onboarding & Feature Guidance**: Encouraging, warm, and clear. Help users feel confident getting started.
* **Day-to-day Data Entry & Form Building**: Efficient, neutral, and invisible. Keep labels and instructions out of the way of work.
* **Errors, Offline Warnings & Destructive Confirmations**: Calm, serious, reassuring, and explicit. Avoid alarming jargon, exclamation marks, or ambiguous choices.

---

## 2. Domain Terminology & Glossary

Ground 2.0 aligns with the modern XForms / XLSForm architecture and ODK Entities mental model. Always use standard Ground 2.0 terminology across user interfaces, user guides, button copy, and tooltips.

### Core Terminology

| Term | What It Means to Users | When to Use in Copy | Avoid |
| :--- | :--- | :--- | :--- |
| **Survey** | The overarching project containing forms, data tables, map layers, and user permissions. | Use when referring to the entire data collection campaign or project container (e.g., *"Open survey"*, *"Survey settings"*). | Project, Workspace, Container |
| **Form** | A questionnaire or data collection protocol completed by collectors in the field. | Use when referring to a questionnaire that collectors fill out (e.g., *"Add a form"*, *"Edit form questions"*, *"Start form"*). | Job, Questionnaire, Inspection sheet |
| **Submission** | A completed record of answers submitted from a form, capturing field inputs, location, and timestamps. | Use when referring to collected data records or entries (e.g., *"3 submissions waiting to sync"*, *"Export submissions"*). | Record, Response, Log, Event, Transaction |
| **Data table** | A structured dataset or registry of master data (such as farmer directories, species lists, or equipment inventories). | Use when referring to non-spatial master data or lookup tables (e.g., *"Upload data table"*, *"Farmer registry table"*). | Table (without "Data"), Master data, Entity dataset, Entity list, Sheet |
| **Map layer** | A spatial dataset shown on the map (such as sample plots, parcel boundaries, or water points). | Use when referring to geographic features organized on the map (e.g., *"Show map layers"*, *"Toggle boundary layer"*). | Layer (without "Map"), Geospatial entity list, Feature class, Spatial dataset |
| **Map feature** | An individual real-world geographic object displayed on the map (a point, line, or area). | Use **"Map feature"** as the general term, or use the specific real-world object name when known: **"Plot"**, **"Parcel"**, **"Tree"**, **"Water point"**. | Site, Data collection site, LOI, Spatial primitive, Geometry |
| **Question** | An individual data entry field in a form (text, number, select, photo, location, etc.). | Use when referring to a form field or prompt (e.g., *"Add a question"*, *"Required question"*). | Task, Field binding, Data element |
| **Group** | A section or set of related questions within a form. | Use when referring to question sections or clusters (e.g., *"Add question group"*, *"Group: Soil measurements"*). | Card, Fieldset, Block |
| **Repeat** | A question or group that can be answered multiple times (such as measuring multiple trees in one plot). | Use when referring to repeating sections or items (e.g., *"Add another tree"*, *"Repeat group"*). | Loop, Array, Sub-form |
| **Note** | Form-level read-only guidance, instructions, or contextual prompts for field collectors. | Use when referring to on-screen guidance or instructions inside a form (e.g., *"Add a note"*, *"Instruction note"*). | Instructions, Prompt, Help label |

### Deprecated Ground 1.0 Terminology
Never use deprecated Ground 1.0 terms in new user interfaces or documentation:

* ❌ **Never say "Site" or "Data collection site"**: Geographic objects are **Map features** (or specific domain names like *plots*, *parcels*, or *assets*).
* ❌ **Never say "Job"**: Questionnaires are **Forms**.
* ❌ **Never say "Task"**: Form entry fields are **Questions**.
* ❌ **Never say "LOI" (Location of Interest)**: Use **Map feature** or specific feature types.

---

## 3. Plain Language & De-Jargonizing

Frontline collectors and community rangers work under intense sun, intermittent connectivity, and diverse linguistic backgrounds. Avoid GIS jargon, technical database terminology, and internal abstractions.

| Instead of (Jargon) | Use (Plain Language) | Example Context |
| :--- | :--- | :--- |
| *Capture point coordinate* / *Collect GPS* | **Drop a pin** / **Save location** | *"Drop a pin at your current location"* |
| *Digitize polygon boundary* | **Draw perimeter** / **Walk perimeter** | *"Walk the perimeter of the field to map its boundary"* |
| *Collect polyline geometry* | **Trace path** / **Record track** | *"Trace the river path"* |
| *Spatial primitive* / *Entity record* | **Map feature** / **[Object name]** | *"Select a map feature to view details"* |
| *Geospatial entity dataset* | **Map layer** | *"Turn on the Forest plots layer"* |
| *Relational master data collection* | **Data table** | *"Import farmer roster table"* |
| *Provision offline cache* / *Download tiles* | **Save for offline** / **Take offline** | *"Save map and forms for offline use"* |
| *Push transactions* / *Replicate mutations* | **Sync** / **Syncing** | *"Syncing 4 submissions..."* |
| *XLSForm syntax error* / *Validation schema failure* | **Check form questions** | *"There is a problem in row 12 of your form"* |
| *Authentication credentials expired* | **Please sign in again** | *"Your session expired. Please sign in again."* |

---

## 4. Component & Pattern Copy Guidelines

### Buttons & Interactive Controls
* **Lead with a strong, specific action verb**: Tell the user exactly what will happen.
  * ✅ *"Save changes"*, *"Add question"*, *"Submit form"*, *"Take offline"*, *"Export CSV"*
  * ❌ *"OK"*, *"Continue"*, *"Proceed"*, *"Yes"*, *"Click here"*
* **Sentence case**: Always use sentence case for buttons and interactive controls (e.g., *"Add a note"*, not *"Add A Note"*).
* **Destructive buttons**: Name the destructive action explicitly.
  * ✅ *"Delete form"*, *"Discard changes"*, *"Remove question"*
  * ❌ *"OK"*, *"Delete"*, *"Yes"* (when ambiguous)

### Page Titles & Section Headers
* **Descriptive and scannable**: State the purpose of the screen or section in sentence case.
  * ✅ *"Survey details"*, *"Form questions"*, *"Map layers"*, *"Offline settings"*
  * ❌ *"Survey Details & Configuration Options"*, *"Entities And Datasets Management"*
* **No trailing periods**: Never put periods at the end of titles or headers.

### Form Field Labels & Helper Text
* **Labels**: Keep labels short, direct, and focused on the noun or question being asked.
  * ✅ *"Survey name"*, *"Plot ID"*, *"Tree height (meters)"*
  * ❌ *"Please enter the name of the survey here"*
* **Helper Text**: Provide necessary context, formatting guidance, or instructions without repeating the label.
  * ✅ Label: *"Plot identifier"*, Helper text: *"Use the 3-letter code followed by a 4-digit number (e.g., FOR-0102)."*
  * ❌ Label: *"Plot identifier"*, Helper text: *"Enter plot identifier."*
* **Required vs. Optional**: Clearly communicate requirements. If most fields are required, label optional fields with *(optional)*.

### In-Form Notes & Field Prompts
Notes provide instructions to collectors while filling out forms in the field. When writing guidance notes:
* **Use direct, imperative sentences**:
  * ✅ *"Stand at the center of the plot before measuring tree canopy cover."*
  * ❌ *"The user should make sure they are situated in the middle of the plot."*
* **Include objective criteria**: Give clear visual or procedural benchmarks for field accuracy.

### Confirmation & Warning Dialogs
Dialogs interrupt the user to prevent data loss or confirm high-impact actions. Use a three-part structure:
1. **Title**: State the exact action or risk clearly as a question or statement.
   * ✅ *"Discard unsaved changes?"*
   * ❌ *"Warning"* or *"Are you sure?"*
2. **Body**: Explain the specific consequence in 1–2 plain sentences.
   * ✅ *"If you leave now, the answers you entered for this submission will be lost."*
   * ❌ *"Unsaved data will not be persisted to the server."*
3. **Buttons**: Pair an explicit action verb with a clear dismissal.
   * ✅ `[ Discard ]` and `[ Keep editing ]`
   * ❌ `[ OK ]` and `[ Cancel ]`

### Error Messages
Error messages must be calm, blameless, and actionable:
* **What happened**: State the issue in plain words without system error codes or stack traces.
* **Why it happened (if helpful)**: Provide immediate context.
* **How to fix it**: Give the user a clear, concrete next step.

Examples:
* ✅ *"Can't connect right now. Your submission is saved on your device and will sync automatically when you're back online."*
  * ❌ *"Error 503: Network unreachable. Mutation queue persistence active."*
* ✅ *"Photo is too large. Please select a photo under 10 MB."*
  * ❌ *"Invalid payload size: File exceeds maxUploadLimit."*
* ✅ *"Enter a valid email address (e.g., name@example.com)."*
  * ❌ *"Malformed email string."*

### Empty States
When a list, map layer, or data table contains no data, empty states should inform and orient the user:
* **Explain what belongs here**: State clearly what the view is for.
* **Explain why it's empty**: Clarify that no items exist yet.
* **Provide a primary action**: Offer a direct button to create or add the first item.
* **Hide unconfigured optional sections (progressive disclosure)**: When a list or section corresponds to an optional feature that the survey organizer hasn't used (for example, a survey with no data tables or no map layers), hide that section of the form or screen entirely rather than showing an empty state, keeping the UI clean and simple.

Example:
* **Title**: *"No forms yet"*
* **Body**: *"Create your first form to start collecting data in the field."*
* **Action**: `[ + Create form ]`

### Status Badges & Connectivity Messaging
Use standard, short status descriptors:
* **Connectivity & Sync**: *"Online"*, *"Offline"*, *"Saved on device"*, *"Syncing 2 submissions..."*, *"All changes synced"*, *"Sync paused"*.
* **Survey & Form State**: *"Draft"*, *"Published"*, *"Archived"*.
* **Feature Status**: *"Pending"*, *"In progress"*, *"Completed"*, *"Needs review"*.

---

## 5. Style, Mechanics & Formatting

### Capitalization
* **Sentence case everywhere**: Use sentence case for all UI copy—including page titles, dialog headers, table headers, menu items, buttons, form labels, and radio options.
  * ✅ *"Survey settings"*, *"Add new question"*, *"Save to device"*
  * ❌ *"Survey Settings"*, *"Add New Question"*, *"Save To Device"*
* **Title Case**: Reserved strictly for proper product names:
  * ✅ *Ground*, *Google Sans Flex*, *Collect Earth Online*, *ODK Central*

### Punctuation
* **No trailing periods**: Do not use periods on:
  * Button labels (`[ Save changes ]`)
  * Page titles, modal titles, and section headers
  * Single-sentence tooltips and badge labels
  * Form field labels and placeholder text
* **Use periods on complete sentences**:
  * Multi-sentence dialog descriptions
  * Error message bodies explaining next steps
  * Instructional notes with two or more sentences
* **Avoid exclamation points**: Exclamation points can feel alarming or overly cheerful. Deliver messages with calm professionalism.
  * ✅ *"Welcome to Ground."* / *"All submissions synced."*
  * ❌ *"Welcome to Ground!"* / *"Sync completed successfully!"*

### Contractions
* Use natural, common contractions (*"can't"*, *"don't"*, *"you're"*, *"it's"*) in explanatory text, dialogs, helper text, and empty states to maintain a friendly, approachable tone.
* Avoid contractions in high-stakes warnings where misunderstanding could lead to accidental data loss.

### Numbers, Measurements & Units
* **Spell out numbers zero through nine** in body prose (*"Select three plots"*), but **use numerals (0, 1, 2...)** for counts, metrics, coordinates, and measurements (*"3 submissions waiting"*, *"5 meters"*, *"12 MB"*).
* Always specify units explicitly (*"meters"*, *"km"*, *"ha"*, *"MB"*) and format units consistently.

---

## 6. Accessibility & Global Inclusivity

Ground is deployed globally across diverse cultures, languages, and technical backgrounds.

* **Write for Translation (Internationalization)**:
  * Keep sentence structures straightforward (Subject-Verb-Object).
  * Avoid colloquialisms, regional slang, cultural idioms, or sports metaphors (*"hit the ground running"*, *"ballpark figure"*, *"touch base"*).
  * Use consistent words for consistent concepts. Do not alternate between *"Survey"*, *"Project"*, and *"Campaign"* in different screens.
* **Screen Reader Accessibility**:
  * Ensure button and link text makes sense out of context. Avoid *"Click here"*, *"More"*, or *"Learn more"* without descriptive context.
  * Provide descriptive text for visual icons and map status indicators (e.g., provide accessibility label *"Completed plot"* rather than just showing a green checkmark).
* **Gender-Neutral & Inclusive Language**:
  * Use second-person (*"you"*, *"your"*) or gender-neutral third-person (*"they"*, *"their"*).
  * Never use gendered pronouns (*"he/she"*, *"his/her"*).

---

## 7. Cross-Platform Parity Reference

For a comprehensive cross-platform mapping of Ground 2.0 terminology to Collect Earth Online (CEO), ODK Central/Collect, KoboToolbox, XLSForm, ArcGIS Survey123, and Open Foris Arena, refer to:
* **[`docs/product/terminology.md`](../product/terminology.md)**: Master cross-platform terminology matrix and concept crosswalk.
* **[`docs/product/prd.md`](../product/prd.md)**: Product requirements and platform specifications.
