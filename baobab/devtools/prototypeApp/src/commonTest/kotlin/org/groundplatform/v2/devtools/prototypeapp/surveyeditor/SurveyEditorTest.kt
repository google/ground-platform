/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.devtools.prototypeapp.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.PrototypeWorkbenchPage
import org.groundplatform.v2.devtools.prototypeapp.data.seed.SurveyEditorSamples
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.InvitationStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.InviteLinks
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapThumbnailTheme
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.CollaboratorRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDatasetValidator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityRow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.FormIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SaveToMode
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingPolicy
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyArea
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.publishedFormXml

class SurveyEditorTest {

  @Test
  fun samples_areValid() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    assertEquals(2, state.mapLayers.size)
    assertEquals(2, state.dataTables.size)
    state.datasets.forEach { assertEquals(emptyList(), state.datasetIssues(it), it.id) }
    assertTrue(state.forms.all { it.editor.issues.isEmpty() })
  }

  @Test
  fun layerStyle_pinIconIsKeptInTheDraft() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val plots = state.mapLayers.first { it.geometryKind == GeometryKind.POINT }
    state.updateDataset(plots.key) { it.copy(style = it.style.copy(iconName = "flag")) }

    assertTrue(state.hasUnpublishedChanges)
    val draft = state.toDraft()
    assertEquals("flag", draft.datasets.first { it.key == plots.key }.style.iconName)
    assertEquals(draft, SurveyEditorState(draft).toDraft())

    state.updateDataset(plots.key) { it.copy(style = it.style.copy(iconName = null)) }
    assertNull(state.toDraft().datasets.first { it.key == plots.key }.style.iconName)
  }

  @Test
  fun addAndDeleteForm_updatesSelection() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    val added = assertNotNull(state.selectedForm)
    assertEquals("New form", added.editor.form.title)
    assertTrue(added.editor.form.formId.startsWith(FormIds.PREFIX))
    state.addForm()
    val second = state.selectedForm!!.editor.form.formId
    assertTrue(second.startsWith(FormIds.PREFIX))
    assertNotEquals(added.editor.form.formId, second)

    state.deleteForm(state.selectedForm!!.key)
    assertEquals(added.key, state.selectedForm?.key)
  }

  @Test
  fun addDataset_createsKindSpecificEntry() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addDataset(DatasetKind.MAP_LAYER)
    val layer = assertNotNull(state.selectedDataset)
    assertEquals(DatasetKind.MAP_LAYER, layer.kind)
    assertEquals(3, state.mapLayers.size)
    state.addDataset(DatasetKind.DATA_TABLE)
    assertEquals(3, state.dataTables.size)

    state.deleteDataset(state.selectedDataset!!.key)
    // Selection falls back to a sibling of the same kind.
    assertEquals(DatasetKind.DATA_TABLE, state.selectedDataset?.kind)
  }

  @Test
  fun deletingLastOfKind_returnsToDetails() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.dataTables.map { it.key }.forEach { state.deleteDataset(it) }
    assertIs<SurveyEditorSection.Details>(state.section)
  }

  @Test
  fun renamingProperty_carriesValuesAndKey() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val farmers = state.dataTables.first { it.id == "farmers" }
    val index = farmers.properties.indexOfFirst { it.name == "farmer_id" }
    state.updateProperty(farmers.key, index, farmers.properties[index].copy(name = "member_id"))
    val updated = state.datasets.first { it.key == farmers.key }
    assertEquals("member_id", updated.keyProperty)
    assertEquals("F-001", updated.rows.first().values["member_id"])
    assertNull(updated.rows.first().values["farmer_id"])
    assertEquals(emptyList(), state.datasetIssues(updated))
  }

  @Test
  fun addRow_onMapLayerGetsGeometryAtLocation() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val plots = state.mapLayers.first { it.geometryKind == GeometryKind.POINT }
    val rowKey = state.addRow(plots.key, LatLng(-0.5, 37.0))
    val row = state.datasets.first { it.key == plots.key }.rows.first { it.key == rowKey }
    assertEquals(listOf(LatLng(-0.5, 37.0)), row.geometry)
    assertTrue(row.values[plots.keyProperty]!!.isNotBlank())
  }

  @Test
  fun validator_flagsBadCellsAndMissingGeometry() {
    val parcels = SurveyEditorSamples.coffeeParcels()
    val broken =
      parcels.copy(
        rows =
          parcels.rows +
            EntityRow("x", mapOf("parcel_id" to "NYR-104", "area_ha" to "big"), emptyList())
      )
    val messages = EntityDatasetValidator.validate(broken).map { it.message }
    assertTrue(messages.any { "duplicated" in it })
    assertTrue(messages.any { "Area (ha) must be decimal" in it })
    assertTrue(messages.any { "at least 3 vertices" in it })
    assertTrue(messages.any { "missing Parcel name" in it })
  }

  @Test
  fun geometryText_roundTripsAndRejectsGarbage() {
    val pts = listOf(LatLng(-0.418, 36.948), LatLng(-0.42, 36.95))
    assertEquals(pts, GeometryText.parse(GeometryText.format(pts)))
    assertNull(GeometryText.parse("1, 2; oops"))
    assertNull(GeometryText.parse("95, 10"))
    assertEquals(emptyList(), GeometryText.parse(" "))
  }

  @Test
  fun inviteCollaborator_validatesAndUpserts() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val before = state.sharing.collaborators.size
    assertNotNull(state.inviteCollaborator("not-an-email", CollaboratorRole.VIEWER))
    assertNotNull(state.inviteCollaborator("organizer@example.org", CollaboratorRole.VIEWER))
    assertNull(state.inviteCollaborator("New.Person@Example.org", CollaboratorRole.VIEWER))
    assertEquals(before + 1, state.sharing.collaborators.size)
    assertNull(state.inviteCollaborator("new.person@example.org", CollaboratorRole.DATA_COLLECTOR))
    assertEquals(before + 1, state.sharing.collaborators.size)
    assertEquals(
      CollaboratorRole.DATA_COLLECTOR,
      state.sharing.collaborators.first { it.email == "new.person@example.org" }.role,
    )
  }

  @Test
  fun pageHash_supportsLegacyFormEditorLink() {
    assertEquals(
      PrototypeWorkbenchPage.SURVEY_EDITOR,
      PrototypeWorkbenchPage.fromHash("#survey-editor"),
    )
    assertEquals(
      PrototypeWorkbenchPage.SURVEY_EDITOR,
      PrototypeWorkbenchPage.fromHash("#form-editor"),
    )
    assertEquals(PrototypeWorkbenchPage.MOBILE_PROTOTYPE, PrototypeWorkbenchPage.fromHash(""))
  }

  @Test
  fun invite_issuesLinkAndAcceptCachesProfile() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    assertNull(state.inviteCollaborator("wanjiku.mwangi@example.org", CollaboratorRole.VIEWER))
    val invited = state.sharing.collaborators.first { it.email == "wanjiku.mwangi@example.org" }
    assertEquals(InvitationStatus.PENDING, invited.status)
    val token = assertNotNull(invited.inviteToken)
    assertTrue(Regex("^[a-z0-9]{4}-[a-z0-9]{4}$").matches(token))
    assertEquals("wanjiku.mwangi@example.org", invited.displayName)

    state.resetInviteLink(invited.email)
    val reset = state.sharing.collaborators.first { it.email == invited.email }.inviteToken
    assertNotNull(reset)

    assertEquals("Enter a name.", state.acceptInvite(invited.email, "  ", null))
    assertNull(state.acceptInvite(invited.email, "Wanjiku Mwangi", "avatar:2", "2026-09-27"))
    val joined = state.sharing.collaborators.first { it.email == invited.email }
    assertEquals(InvitationStatus.ACCEPTED, joined.status)
    assertNull(joined.inviteToken)
    assertNotNull(joined.userId)
    assertEquals(CachedProfile("Wanjiku Mwangi", "avatar:2", "2026-09-27"), joined.profile)
    assertEquals("Wanjiku Mwangi", joined.displayName)
    assertEquals("Already joined.", state.acceptInvite(invited.email, "Someone", null))

    // Changing the role keeps the cached profile.
    state.setCollaboratorRole(invited.email, CollaboratorRole.DATA_COLLECTOR)
    assertEquals(
      "Wanjiku Mwangi",
      state.sharing.collaborators.first { it.email == invited.email }.displayName,
    )
  }

  @Test
  fun inviteLinks_suggestNamesAndInitials() {
    assertEquals("Grace Njeri", InviteLinks.suggestedName("grace.njeri@example.org"))
    assertEquals("Collector Two", InviteLinks.suggestedName("collector_two@example.org"))
    assertEquals("GN", InviteLinks.initials("Grace Njeri"))
    assertEquals("DK", InviteLinks.initials("Daniel Arap Kiprop"))
    assertEquals("R", InviteLinks.initials("reviewer@example.org"))
    assertEquals("https://ground.example.org/join/ab12-cd34", InviteLinks.url("ab12-cd34"))
  }

  @Test
  fun samples_joinedPeopleHaveCachedProfiles() {
    val sharing = SurveyEditorSamples.sharing()
    assertNotNull(sharing.ownerProfile)
    sharing.collaborators.forEach {
      if (it.status == InvitationStatus.ACCEPTED) {
        assertNotNull(it.profile, it.email)
        assertNull(it.inviteToken)
      } else {
        assertNull(it.profile, it.email)
        assertNotNull(it.inviteToken, it.email)
      }
    }
  }

  @Test
  fun moveForm_reordersForms() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    val keys = state.forms.map { it.key }
    state.moveForm(keys[2], 0)
    assertEquals(listOf(keys[2], keys[0], keys[1]), state.forms.map { it.key })
  }

  @Test
  fun moveDataset_reordersWithinKindOnly() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addDataset(DatasetKind.MAP_LAYER)
    val layers = state.mapLayers.map { it.key }
    val tables = state.dataTables.map { it.key }
    val kinds = state.datasets.map { it.kind }

    state.moveDataset(layers.last(), 0)

    assertEquals(listOf(layers[2], layers[0], layers[1]), state.mapLayers.map { it.key })
    assertEquals(tables, state.dataTables.map { it.key })
    assertEquals(kinds, state.datasets.map { it.kind })
  }

  @Test
  fun isoLanguages_lookupAndSearch() {
    assertTrue(IsoLanguages.all.size >= 7900)

    // Lookup by 2-letter ISO 639-1 code
    val english = IsoLanguages.findByCode("en")
    assertNotNull(english)
    assertEquals("eng", english.id)
    assertEquals("en", english.part1)
    assertEquals("English", english.name)
    assertEquals("en", english.code)

    // Lookup by 3-letter ISO 639-3 code
    val swahili = IsoLanguages.findByCode("swa")
    assertNotNull(swahili)
    assertEquals("swa", swahili.id)
    assertEquals("sw", swahili.part1)
    assertEquals("sw", swahili.code)

    // Case-insensitive lookup
    assertEquals(english, IsoLanguages.findByCode("EN"))
    assertEquals(english, IsoLanguages.findByCode("EnG"))

    // Search functionality
    val swahiliMatches = IsoLanguages.search("swahili")
    assertTrue(
      swahiliMatches.any { it.id == "swa" || it.name.contains("Swahili", ignoreCase = true) }
    )

    val spanishMatches = IsoLanguages.search("spa")
    assertTrue(spanishMatches.any { it.id == "spa" })
  }

  @Test
  fun languageSelection_addRemoveAndSetDefault() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    assertEquals(listOf("en", "sw"), state.details.supportedLanguages)
    assertEquals("en", state.details.defaultLanguage)

    // Add a new supported language
    state.addSupportedLanguage("fra")
    assertEquals(listOf("en", "sw", "fra"), state.details.supportedLanguages)
    assertEquals("en", state.details.defaultLanguage)

    // Set new default language
    state.setDefaultLanguage("sw")
    assertEquals("sw", state.details.defaultLanguage)

    // Set default language to a language not yet in supported languages
    state.setDefaultLanguage("deu")
    assertEquals("deu", state.details.defaultLanguage)
    assertTrue(state.details.supportedLanguages.contains("deu"))

    // Remove the current default language; falls back to first remaining
    state.removeSupportedLanguage("deu")
    assertFalse(state.details.supportedLanguages.contains("deu"))
    assertEquals("en", state.details.defaultLanguage)

    // Adding existing language doesn't duplicate
    state.addSupportedLanguage("en")
    assertEquals(listOf("en", "sw", "fra"), state.details.supportedLanguages)
  }

  @Test
  fun surveyArea_setAndClear() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val initialArea = assertNotNull(state.details.surveyArea)
    assertEquals("Othaya Sub-County, Nyeri", initialArea.name)
    assertEquals(4, initialArea.vertexCount)

    // Clear survey area
    state.setSurveyArea(null)
    assertNull(state.details.surveyArea)

    // Set a new survey area
    val customArea =
      SurveyArea(
        name = "Chinga Dam & Reservoir",
        parts =
          listOf(
            listOf(
              LatLng(-0.4130, 36.9430),
              LatLng(-0.4130, 36.9720),
              LatLng(-0.4380, 36.9720),
              LatLng(-0.4380, 36.9430),
            )
          ),
        center = LatLng(-0.4258, 36.9574),
        zoom = 13.0,
      )
    state.setSurveyArea(customArea)
    val updated = assertNotNull(state.details.surveyArea)
    assertEquals("Chinga Dam & Reservoir", updated.name)
    assertEquals(4, updated.vertexCount)
    assertEquals(-0.4258, updated.center.lat)
    assertEquals(36.9574, updated.center.lng)
  }

  @Test
  fun surveyArea_multiPartDefaultsToBoundingBoxCenter() {
    val area =
      SurveyArea(
        name = "Islands",
        parts =
          listOf(
            listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0), LatLng(1.0, 1.0)),
            listOf(LatLng(3.0, 3.0), LatLng(3.0, 4.0), LatLng(4.0, 4.0)),
          ),
      )
    assertEquals(LatLng(2.0, 2.0), area.center)
    assertEquals(6, area.vertexCount)
    assertTrue(area.zoom in 1.0..16.0)
  }

  @Test
  fun addForm_createsLinkedDataTableByDefault() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val initialTablesCount = state.dataTables.size
    state.addForm()

    // A blank form has no Location question, so it adds table rows.
    assertEquals(initialTablesCount + 1, state.dataTables.size)
    val form = assertNotNull(state.selectedForm)
    val linkedLayer = assertNotNull(state.dataTables.firstOrNull { it.linkedFormKey == form.key })
    assertEquals("New form", linkedLayer.displayName)
    assertTrue(linkedLayer.isLinkedToForm)
    assertEquals(form.key, linkedLayer.linkedFormKey)
    assertTrue(linkedLayer.properties.any { it.name == "id" })
  }

  @Test
  fun createDatasetForForm_createsAndLinksDataset() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val form = state.forms.first()
    val initialLayers = state.mapLayers.size

    state.createDatasetForForm(form.key, DatasetKind.MAP_LAYER)
    assertEquals(initialLayers + 1, state.mapLayers.size)
    val dataset = assertNotNull(state.selectedDataset)
    assertEquals(form.key, dataset.linkedFormKey)
    assertTrue(dataset.isLinkedToForm)
    // Check that form questions were mapped to dataset properties
    assertTrue(dataset.properties.any { it.name == "visit_date" })
    assertTrue(dataset.properties.any { it.name == "farm_location" })
  }

  @Test
  fun createFormForDataset_createsAndLinksForm() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val parcels = state.mapLayers.first { it.id == "coffee_parcels" }
    assertFalse(parcels.isLinkedToForm)

    state.createFormForDataset(parcels.key)
    val form = assertNotNull(state.selectedForm)
    val updatedParcels = state.datasets.first { it.key == parcels.key }
    assertTrue(updatedParcels.isLinkedToForm)
    assertEquals(form.key, updatedParcels.linkedFormKey)
    // Map layer form includes location question
    assertTrue(form.editor.form.questions.any { it.name == "location" })
    // Map layer form includes parcel properties
    assertTrue(form.editor.form.questions.any { it.name == "parcel_id" })
    assertTrue(form.editor.form.questions.any { it.name == "parcel_name" })
  }

  @Test
  fun syncDatasetsLinkedToForm_updatesDatasetProperties() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    val formEntry = state.selectedForm!!
    val linkedLayer = state.datasets.first { it.linkedFormKey == formEntry.key }

    // Add a question to the form
    formEntry.editor.addQuestion(
      org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType.INTEGER
    )
    val addedQuestion = formEntry.editor.form.questions.last()
    state.syncDatasetsLinkedToForm(formEntry)

    val updatedLayer = state.datasets.first { it.key == linkedLayer.key }
    assertTrue(updatedLayer.properties.any { it.name == addedQuestion.name })
  }

  @Test
  fun unlinkDataset_clearsFormLink() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    val formEntry = state.selectedForm!!
    val linkedLayer = state.datasets.first { it.linkedFormKey == formEntry.key }
    assertTrue(linkedLayer.isLinkedToForm)

    state.unlinkDataset(linkedLayer.key)
    val unlinkedLayer = state.datasets.first { it.key == linkedLayer.key }
    assertFalse(unlinkedLayer.isLinkedToForm)
    assertNull(unlinkedLayer.linkedFormKey)
  }

  @Test
  fun deleteForm_unlinksAssociatedDatasets() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    val formEntry = state.selectedForm!!
    val linkedLayer = state.datasets.first { it.linkedFormKey == formEntry.key }

    state.deleteForm(formEntry.key)
    val remainingLayer = state.datasets.first { it.key == linkedLayer.key }
    assertFalse(remainingLayer.isLinkedToForm)
    assertNull(remainingLayer.linkedFormKey)
  }

  @Test
  fun newState_hasNoUnpublishedChanges() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    assertFalse(state.hasUnpublishedChanges)
  }

  @Test
  fun edit_marksDraftChanged_andPublishClearsIt() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.updateDetails { it.copy(title = "Renamed survey") }
    assertTrue(state.hasUnpublishedChanges)

    state.markPublished()
    assertFalse(state.hasUnpublishedChanges)
    assertEquals("Renamed survey", state.details.title)
  }

  @Test
  fun canPublish_requiresChanges() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    assertEquals(0, state.issueCount)
    assertFalse(state.canPublish)

    state.updateDetails { it.copy(title = "Renamed survey") }
    assertTrue(state.canPublish)
  }

  @Test
  fun formIssue_blocksPublish_untilFixed() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val editor = state.forms.first().editor
    val question = editor.form.questions.first()

    editor.updateQuestion(question.key) { it.copy(label = "") }
    assertTrue(state.hasUnpublishedChanges)
    assertEquals(1, state.issueCount)
    assertFalse(state.canPublish)

    editor.updateQuestion(question.key) { it.copy(label = "Fixed label") }
    assertEquals(0, state.issueCount)
    assertTrue(state.canPublish)
  }

  @Test
  fun datasetIssue_blocksPublish() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val dataset = state.datasets.first()

    state.updateDataset(dataset.key) { it.copy(displayName = "") }
    assertTrue(state.hasUnpublishedChanges)
    assertTrue(state.issueCount > 0)
    assertFalse(state.canPublish)
  }

  @Test
  fun revertingAnEdit_leavesNoUnpublishedChanges() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    assertTrue(state.hasUnpublishedChanges)

    val added = state.selectedForm!!
    state.deleteForm(added.key)
    state.deleteDataset(state.datasets.first { it.displayName == added.editor.form.title }.key)
    assertFalse(state.hasUnpublishedChanges)
  }

  @Test
  fun discardChanges_restoresPublishedSurvey() {
    val published = SurveyEditorSamples.draft()
    val state = SurveyEditorState(published)
    state.updateDetails { it.copy(title = "Renamed survey") }
    state.addForm()
    val addedFormSection = state.section

    state.discardChanges()

    assertFalse(state.hasUnpublishedChanges)
    assertEquals(published.details, state.details)
    assertEquals(published.forms.map { it.key }, state.forms.map { it.key })
    assertEquals(published.datasets, state.datasets)
    assertNotEquals(addedFormSection, state.section)
    assertEquals(SurveyEditorSection.Details, state.section)
  }

  @Test
  fun discardChanges_afterPublish_keepsPublishedEdits() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.updateDetails { it.copy(title = "Published title") }
    state.markPublished()
    state.updateDetails { it.copy(title = "Unpublished title") }

    state.discardChanges()

    assertEquals("Published title", state.details.title)
    assertFalse(state.hasUnpublishedChanges)
  }

  @Test
  fun linkedDatasetKind_followsFormGeometry() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    val entry = state.selectedForm!!
    entry.editor.addQuestion(EditorQuestionType.LOCATION)
    state.syncDatasetsLinkedToForm(entry)
    assertEquals(
      DatasetKind.MAP_LAYER,
      state.datasets.first { it.linkedFormKey == entry.key }.kind,
    )
  }

  @Test
  fun createDatasetForForm_defaultsKindFromGeometry() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val withLocation = state.forms.first { it.editor.form.hasGeometry }
    state.createDatasetForForm(withLocation.key)
    assertEquals(DatasetKind.MAP_LAYER, state.selectedDataset?.kind)
  }

  @Test
  fun linkedLayerGeometryKind_followsPrimaryGeometryQuestionType() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    val entry = state.selectedForm!!
    entry.editor.addQuestion(EditorQuestionType.POLYGON)
    state.syncDatasetsLinkedToForm(entry)
    val linked = state.datasets.first { it.linkedFormKey == entry.key }
    assertEquals(DatasetKind.MAP_LAYER, linked.kind)
    assertEquals(GeometryKind.POLYGON, linked.geometryKind)

    // A new layer created for the form takes the same kind; a line form makes a line layer.
    state.createDatasetForForm(entry.key)
    assertEquals(GeometryKind.POLYGON, state.selectedDataset?.geometryKind)
    val polygonKey = entry.editor.form.primaryGeometryQuestion!!.key
    entry.editor.changeType(polygonKey, EditorQuestionType.LINE)
    state.syncDatasetsLinkedToForm(entry)
    assertTrue(
      state.datasets
        .filter { it.linkedFormKey == entry.key }
        .all {
          it.geometryKind == GeometryKind.LINE
        }
    )
  }

  @Test
  fun createFormForDataset_geometryQuestionMatchesLayerKind() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val polygons = state.mapLayers.first { it.geometryKind == GeometryKind.POLYGON }
    state.createFormForDataset(polygons.key)
    val form = state.selectedForm!!.editor.form
    assertEquals(EditorQuestionType.POLYGON, form.primaryGeometryQuestion?.type)
  }

  @Test
  fun canPublish_blockedByGpsOnlyGeometryOnWeb() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val entry = state.forms.first { it.editor.form.hasGeometry }
    state.markPublished()
    entry.editor.updateAvailability(entry.editor.form.availability.withWeb(true))
    assertTrue(entry.editor.issues.isNotEmpty())
    assertTrue(state.hasUnpublishedChanges)
    assertFalse(state.canPublish)

    entry.editor.makeGeometryQuestionsWebCompatible()
    assertTrue(entry.editor.issues.isEmpty())
    assertTrue(state.canPublish)
  }

  @Test
  fun setFormSaveToMode_update_deletesEmptyLinkedDataset() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    val entry = state.selectedForm!!
    val linked = state.datasets.first { it.linkedFormKey == entry.key }

    state.setFormSaveToMode(entry.key, SaveToMode.UPDATE)

    assertTrue(state.datasets.none { it.key == linked.key })
    val saveTo = entry.editor.form.saveTo
    assertEquals(SaveToMode.UPDATE, saveTo.mode)
    assertEquals("coffee_parcels", saveTo.targetDatasetId)
    assertEquals(SurveyEditorSection.Form(entry.key), state.section)
  }

  @Test
  fun setFormSaveToMode_update_unlinksDatasetWithFeatures() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    val entry = state.selectedForm!!
    val linked = state.datasets.first { it.linkedFormKey == entry.key }
    state.addRow(linked.key)

    state.setFormSaveToMode(entry.key, SaveToMode.UPDATE)

    val kept = state.datasets.first { it.key == linked.key }
    assertNull(kept.linkedFormKey)
    assertNotEquals(kept.id, entry.editor.form.saveTo.targetDatasetId)
  }

  @Test
  fun setFormSaveToMode_create_relinksNewDatasetWithoutLeavingForm() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    val entry = state.selectedForm!!
    state.setFormSaveToMode(entry.key, SaveToMode.UPDATE)
    assertTrue(state.datasets.none { it.linkedFormKey == entry.key })

    state.setFormSaveToMode(entry.key, SaveToMode.CREATE)

    assertEquals(SaveToMode.CREATE, entry.editor.form.saveTo.mode)
    assertEquals(1, state.datasets.count { it.linkedFormKey == entry.key })
    assertEquals(SurveyEditorSection.Form(entry.key), state.section)
  }

  @Test
  fun renamingTargetDatasetAndProperty_keepsUpdateFormPointedAtIt() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    val entry = state.selectedForm!!
    entry.editor.addQuestion(EditorQuestionType.TEXT)
    val question = entry.editor.form.questions.last()
    state.setFormSaveToMode(entry.key, SaveToMode.UPDATE)
    entry.editor.setMapping(question.key, "status")
    val parcels = state.datasets.first { it.id == "coffee_parcels" }
    assertEquals(listOf(entry.key), state.formsUpdating(parcels).map { it.key })

    state.updateDataset(parcels.key) { it.copy(id = "parcels") }
    assertEquals("parcels", entry.editor.form.saveTo.targetDatasetId)

    val statusIndex = parcels.properties.indexOfFirst { it.name == "status" }
    state.updateProperty(
      parcels.key,
      statusIndex,
      parcels.properties[statusIndex].copy(name = "state"),
    )
    assertEquals("state", entry.editor.form.saveTo.propertyFor(question.key))
  }

  @Test
  fun publishedFormXml_includesSaveToLogic() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.addForm()
    val entry = state.selectedForm!!
    val createXml = state.toDraft().publishedFormXml(SurveyEditorForm(entry.key, entry.editor.form))
    assertTrue(createXml.contains("create=\"1\""))

    state.setFormSaveToMode(entry.key, SaveToMode.UPDATE)
    val updateXml = state.toDraft().publishedFormXml(SurveyEditorForm(entry.key, entry.editor.form))
    assertTrue(updateXml.contains("update=\"1\""))
    assertTrue(updateXml.contains("<instance id=\"coffee_parcels\""))
    assertTrue(updateXml.contains("<item>"))
  }

  // Organizations -----------------------------------------------------------------------------

  @Test
  fun organizationPolicy_withoutOrganization_isAnIssueThatBlocksPublishing() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.setOrganization(null)
    state.updateSharing { it.copy(policy = SharingPolicy.ORGANIZATION) }

    assertEquals(1, state.sharingIssues.size)
    assertEquals(1, state.issueCount)
    assertTrue(state.hasUnpublishedChanges)
    assertFalse(state.canPublish)

    state.setOrganization("org-1")
    assertEquals(emptyList(), state.sharingIssues)
    assertTrue(state.canPublish)
  }

  @Test
  fun clearingOrganization_downgradesOrganizationPolicy_andExplainsWhy() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.updateSharing { it.copy(policy = SharingPolicy.ORGANIZATION) }
    assertNull(state.organizationNotice)

    state.setOrganization("")

    assertNull(state.details.organizationId)
    assertEquals(SharingPolicy.RESTRICTED, state.sharing.policy)
    assertNotNull(state.organizationNotice)
    state.dismissOrganizationNotice()
    assertNull(state.organizationNotice)
  }

  @Test
  fun clearingOrganization_keepsOtherPolicies() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    state.updateSharing { it.copy(policy = SharingPolicy.PUBLIC) }
    state.setOrganization(null)
    assertEquals(SharingPolicy.PUBLIC, state.sharing.policy)
    assertNull(state.organizationNotice)
  }

  @Test
  fun discardChanges_restoresOrganizationAndClearsNotice() {
    val state = SurveyEditorState(SurveyEditorSamples.draft())
    val original = state.details.organizationId
    state.updateSharing { it.copy(policy = SharingPolicy.ORGANIZATION) }
    state.setOrganization(null)
    state.discardChanges()
    assertEquals(original, state.details.organizationId)
    assertEquals(SharingPolicy.RESTRICTED, state.sharing.policy)
    assertNull(state.organizationNotice)
    assertFalse(state.hasUnpublishedChanges)
  }

  @Test
  fun forSurvey_copiesOwnerFromSurveyListOnlyWhenDraftHasNone() {
    val survey =
      SurveyPreviewItem(
        id = "s1",
        title = "Survey",
        description = "",
        location = "",
        coordinatesLabel = "",
        offlineSizeLabel = "",
        isDownloaded = false,
        thumbnailTheme = MapThumbnailTheme.entries.first(),
        entityCount = 0,
        ownerEmail = "list.owner@example.org",
        organizationId = "org-1",
      )
    val blank = SurveyEditorDraft.forSurvey("s1", stored = null, survey = survey)
    assertEquals("list.owner@example.org", blank.sharing.ownerEmail)
    assertEquals("org-1", blank.details.organizationId)

    val stored = SurveyEditorSamples.draft()
    val kept = SurveyEditorDraft.forSurvey("s1", stored = stored, survey = survey)
    assertEquals(stored.sharing.ownerEmail, kept.sharing.ownerEmail)
  }

  @Test
  fun organizationSharedSample_isValidAndOpenToTheOrganization() {
    val draft = SurveyEditorSamples.organizationSharedDraft()
    val state = SurveyEditorState(draft)
    assertEquals(SharingPolicy.ORGANIZATION, state.sharing.policy)
    assertNotNull(state.details.organizationId)
    assertEquals(0, state.issueCount)
  }
}
