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
package org.groundplatform.v2.devtools.prototypeapp.ui.surveyeditor

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
import org.groundplatform.v2.devtools.prototypeapp.domain.model.geometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.hasGeometry
import org.groundplatform.v2.devtools.prototypeapp.ui.state.SurveyEditorSection
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.surveyEditorViewModel
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.ui

class SurveyEditorTest {

  @Test
  fun samples_areValid() {
    val state = surveyEditorViewModel()
    assertEquals(2, state.ui.mapLayers.size)
    assertEquals(2, state.ui.dataTables.size)
    state.ui.datasets.forEach { assertEquals(emptyList(), state.ui.datasetIssues(it), it.id) }
    assertTrue(state.ui.forms.all { state.formEditor(it.key).ui.issues.isEmpty() })
  }

  @Test
  fun layerStyle_pinIconIsKeptInTheDraft() {
    val state = surveyEditorViewModel()
    val plots = state.ui.mapLayers.first { it.geometryKind == GeometryKind.POINT }
    state.updateDataset(plots.key) { it.copy(style = it.style.copy(iconName = "flag")) }

    assertTrue(state.ui.hasUnpublishedChanges)
    val draft = state.ui.draft
    assertEquals("flag", draft.datasets.first { it.key == plots.key }.style.iconName)
    assertEquals(draft, surveyEditorViewModel(draft).ui.draft)

    state.updateDataset(plots.key) { it.copy(style = it.style.copy(iconName = null)) }
    assertNull(state.ui.draft.datasets.first { it.key == plots.key }.style.iconName)
  }

  @Test
  fun addAndDeleteForm_updatesSelection() {
    val state = surveyEditorViewModel()
    state.addForm()
    val added = assertNotNull(state.ui.selectedForm)
    assertEquals("New form", state.formEditor(added.key).ui.form.title)
    assertTrue(state.formEditor(added.key).ui.form.formId.startsWith(FormIds.PREFIX))
    state.addForm()
    val second = state.formEditor(state.ui.selectedForm!!.key).ui.form.formId
    assertTrue(second.startsWith(FormIds.PREFIX))
    assertNotEquals(state.formEditor(added.key).ui.form.formId, second)

    state.deleteForm(state.ui.selectedForm!!.key)
    assertEquals(added.key, state.ui.selectedForm?.key)
  }

  @Test
  fun addDataset_createsKindSpecificEntry() {
    val state = surveyEditorViewModel()
    state.addDataset(DatasetKind.MAP_LAYER)
    val layer = assertNotNull(state.ui.selectedDataset)
    assertEquals(DatasetKind.MAP_LAYER, layer.kind)
    assertEquals(3, state.ui.mapLayers.size)
    state.addDataset(DatasetKind.DATA_TABLE)
    assertEquals(3, state.ui.dataTables.size)

    state.deleteDataset(state.ui.selectedDataset!!.key)
    // Selection falls back to a sibling of the same kind.
    assertEquals(DatasetKind.DATA_TABLE, state.ui.selectedDataset?.kind)
  }

  @Test
  fun deletingLastOfKind_returnsToDetails() {
    val state = surveyEditorViewModel()
    state.ui.dataTables.map { it.key }.forEach { state.deleteDataset(it) }
    assertIs<SurveyEditorSection.Details>(state.ui.section)
  }

  @Test
  fun renamingProperty_carriesValuesAndKey() {
    val state = surveyEditorViewModel()
    val farmers = state.ui.dataTables.first { it.id == "farmers" }
    val index = farmers.properties.indexOfFirst { it.name == "farmer_id" }
    state.updateProperty(farmers.key, index, farmers.properties[index].copy(name = "member_id"))
    val updated = state.ui.datasets.first { it.key == farmers.key }
    assertEquals("member_id", updated.keyProperty)
    assertEquals("F-001", updated.rows.first().values["member_id"])
    assertNull(updated.rows.first().values["farmer_id"])
    assertEquals(emptyList(), state.ui.datasetIssues(updated))
  }

  @Test
  fun addRow_onMapLayerGetsGeometryAtLocation() {
    val state = surveyEditorViewModel()
    val plots = state.ui.mapLayers.first { it.geometryKind == GeometryKind.POINT }
    val rowKey = state.addRow(plots.key, LatLng(-0.5, 37.0))
    val row = state.ui.datasets.first { it.key == plots.key }.rows.first { it.key == rowKey }
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
    val state = surveyEditorViewModel()
    val before = state.ui.sharing.collaborators.size
    assertNotNull(state.inviteCollaborator("not-an-email", CollaboratorRole.VIEWER))
    assertNotNull(state.inviteCollaborator("organizer@example.org", CollaboratorRole.VIEWER))
    assertNull(state.inviteCollaborator("New.Person@Example.org", CollaboratorRole.VIEWER))
    assertEquals(before + 1, state.ui.sharing.collaborators.size)
    assertNull(state.inviteCollaborator("new.person@example.org", CollaboratorRole.DATA_COLLECTOR))
    assertEquals(before + 1, state.ui.sharing.collaborators.size)
    assertEquals(
      CollaboratorRole.DATA_COLLECTOR,
      state.ui.sharing.collaborators.first { it.email == "new.person@example.org" }.role,
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
    val state = surveyEditorViewModel()
    assertNull(state.inviteCollaborator("wanjiku.mwangi@example.org", CollaboratorRole.VIEWER))
    val invited = state.ui.sharing.collaborators.first { it.email == "wanjiku.mwangi@example.org" }
    assertEquals(InvitationStatus.PENDING, invited.status)
    val token = assertNotNull(invited.inviteToken)
    assertTrue(Regex("^[a-z0-9]{4}-[a-z0-9]{4}$").matches(token))
    assertEquals("wanjiku.mwangi@example.org", invited.displayName)

    state.resetInviteLink(invited.email)
    val reset = state.ui.sharing.collaborators.first { it.email == invited.email }.inviteToken
    assertNotNull(reset)

    assertEquals("Enter a name.", state.acceptInvite(invited.email, "  ", null))
    assertNull(state.acceptInvite(invited.email, "Wanjiku Mwangi", "avatar:2", "2026-09-27"))
    val joined = state.ui.sharing.collaborators.first { it.email == invited.email }
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
      state.ui.sharing.collaborators.first { it.email == invited.email }.displayName,
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
    val state = surveyEditorViewModel()
    state.addForm()
    val keys = state.ui.forms.map { it.key }
    state.moveForm(keys[2], 0)
    assertEquals(listOf(keys[2], keys[0], keys[1]), state.ui.forms.map { it.key })
  }

  @Test
  fun moveDataset_reordersWithinKindOnly() {
    val state = surveyEditorViewModel()
    state.addDataset(DatasetKind.MAP_LAYER)
    val layers = state.ui.mapLayers.map { it.key }
    val tables = state.ui.dataTables.map { it.key }
    val kinds = state.ui.datasets.map { it.kind }

    state.moveDataset(layers.last(), 0)

    assertEquals(listOf(layers[2], layers[0], layers[1]), state.ui.mapLayers.map { it.key })
    assertEquals(tables, state.ui.dataTables.map { it.key })
    assertEquals(kinds, state.ui.datasets.map { it.kind })
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
    val state = surveyEditorViewModel()
    assertEquals(listOf("en", "sw"), state.ui.details.supportedLanguages)
    assertEquals("en", state.ui.details.defaultLanguage)

    // Add a new supported language
    state.addSupportedLanguage("fra")
    assertEquals(listOf("en", "sw", "fra"), state.ui.details.supportedLanguages)
    assertEquals("en", state.ui.details.defaultLanguage)

    // Set new default language
    state.setDefaultLanguage("sw")
    assertEquals("sw", state.ui.details.defaultLanguage)

    // Set default language to a language not yet in supported languages
    state.setDefaultLanguage("deu")
    assertEquals("deu", state.ui.details.defaultLanguage)
    assertTrue(state.ui.details.supportedLanguages.contains("deu"))

    // Remove the current default language; falls back to first remaining
    state.removeSupportedLanguage("deu")
    assertFalse(state.ui.details.supportedLanguages.contains("deu"))
    assertEquals("en", state.ui.details.defaultLanguage)

    // Adding existing language doesn't duplicate
    state.addSupportedLanguage("en")
    assertEquals(listOf("en", "sw", "fra"), state.ui.details.supportedLanguages)
  }

  @Test
  fun surveyArea_setAndClear() {
    val state = surveyEditorViewModel()
    val initialArea = assertNotNull(state.ui.details.surveyArea)
    assertEquals("Othaya Sub-County, Nyeri", initialArea.name)
    assertEquals(4, initialArea.vertexCount)

    // Clear survey area
    state.setSurveyArea(null)
    assertNull(state.ui.details.surveyArea)

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
    val updated = assertNotNull(state.ui.details.surveyArea)
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
    val state = surveyEditorViewModel()
    val initialTablesCount = state.ui.dataTables.size
    state.addForm()

    // A blank form has no Location question, so it adds table rows.
    assertEquals(initialTablesCount + 1, state.ui.dataTables.size)
    val form = assertNotNull(state.ui.selectedForm)
    val linkedLayer =
      assertNotNull(state.ui.dataTables.firstOrNull { it.linkedFormKey == form.key })
    assertEquals("New form", linkedLayer.displayName)
    assertTrue(linkedLayer.isLinkedToForm)
    assertEquals(form.key, linkedLayer.linkedFormKey)
    assertTrue(linkedLayer.properties.any { it.name == "id" })
  }

  @Test
  fun createDatasetForForm_createsAndLinksDataset() {
    val state = surveyEditorViewModel()
    val form = state.ui.forms.first()
    val initialLayers = state.ui.mapLayers.size

    state.createDatasetForForm(form.key, DatasetKind.MAP_LAYER)
    assertEquals(initialLayers + 1, state.ui.mapLayers.size)
    val dataset = assertNotNull(state.ui.selectedDataset)
    assertEquals(form.key, dataset.linkedFormKey)
    assertTrue(dataset.isLinkedToForm)
    // Check that form questions were mapped to dataset properties
    assertTrue(dataset.properties.any { it.name == "visit_date" })
    assertTrue(dataset.properties.any { it.name == "farm_location" })
  }

  @Test
  fun createFormForDataset_createsAndLinksForm() {
    val state = surveyEditorViewModel()
    val parcels = state.ui.mapLayers.first { it.id == "coffee_parcels" }
    assertFalse(parcels.isLinkedToForm)

    state.createFormForDataset(parcels.key)
    val form = assertNotNull(state.ui.selectedForm)
    val updatedParcels = state.ui.datasets.first { it.key == parcels.key }
    assertTrue(updatedParcels.isLinkedToForm)
    assertEquals(form.key, updatedParcels.linkedFormKey)
    val formEditorState = state.formEditor(form.key).ui
    assertEquals("coffee_parcels", formEditorState.saveTarget?.id)
    assertTrue(formEditorState.saveTarget?.isLinkedToThisForm == true)
    // Map layer form includes location question
    assertTrue(formEditorState.form.questions.any { it.name == "location" })
    // Map layer form includes parcel properties
    assertTrue(formEditorState.form.questions.any { it.name == "parcel_id" })
    assertTrue(formEditorState.form.questions.any { it.name == "parcel_name" })
  }

  @Test
  fun syncDatasetsLinkedToForm_updatesDatasetProperties() {
    val state = surveyEditorViewModel()
    state.addForm()
    val formEntry = state.ui.selectedForm!!
    val linkedLayer = state.ui.datasets.first { it.linkedFormKey == formEntry.key }

    // Add a question to the form
    state
      .formEditor(formEntry.key)
      .addQuestion(
        org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType.INTEGER
      )
    val addedQuestion = state.formEditor(formEntry.key).ui.form.questions.last()
    state.syncDatasetsLinkedToForm(formEntry.key)

    val updatedLayer = state.ui.datasets.first { it.key == linkedLayer.key }
    assertTrue(updatedLayer.properties.any { it.name == addedQuestion.name })
  }

  @Test
  fun unlinkDataset_clearsFormLink() {
    val state = surveyEditorViewModel()
    state.addForm()
    val formEntry = state.ui.selectedForm!!
    val linkedLayer = state.ui.datasets.first { it.linkedFormKey == formEntry.key }
    assertTrue(linkedLayer.isLinkedToForm)

    state.unlinkDataset(linkedLayer.key)
    val unlinkedLayer = state.ui.datasets.first { it.key == linkedLayer.key }
    assertFalse(unlinkedLayer.isLinkedToForm)
    assertNull(unlinkedLayer.linkedFormKey)
  }

  @Test
  fun deleteForm_unlinksAssociatedDatasets() {
    val state = surveyEditorViewModel()
    state.addForm()
    val formEntry = state.ui.selectedForm!!
    val linkedLayer = state.ui.datasets.first { it.linkedFormKey == formEntry.key }

    state.deleteForm(formEntry.key)
    val remainingLayer = state.ui.datasets.first { it.key == linkedLayer.key }
    assertFalse(remainingLayer.isLinkedToForm)
    assertNull(remainingLayer.linkedFormKey)
  }

  @Test
  fun newState_hasNoUnpublishedChanges() {
    val state = surveyEditorViewModel()
    assertFalse(state.ui.hasUnpublishedChanges)
  }

  @Test
  fun edit_marksDraftChanged_andPublishClearsIt() {
    val state = surveyEditorViewModel()
    state.updateDetails { it.copy(title = "Renamed survey") }
    assertTrue(state.ui.hasUnpublishedChanges)

    state.publish()
    assertFalse(state.ui.hasUnpublishedChanges)
    assertEquals("Renamed survey", state.ui.details.title)
  }

  @Test
  fun canPublish_requiresChanges() {
    val state = surveyEditorViewModel()
    assertEquals(0, state.ui.issueCount)
    assertFalse(state.ui.canPublish)

    state.updateDetails { it.copy(title = "Renamed survey") }
    assertTrue(state.ui.canPublish)
  }

  @Test
  fun formIssue_blocksPublish_untilFixed() {
    val state = surveyEditorViewModel()
    val editor = state.formEditor(state.ui.forms.first().key)
    val question = editor.ui.form.questions.first()

    editor.updateQuestion(question.key) { it.copy(label = "") }
    assertTrue(state.ui.hasUnpublishedChanges)
    assertEquals(1, state.ui.issueCount)
    assertFalse(state.ui.canPublish)

    editor.updateQuestion(question.key) { it.copy(label = "Fixed label") }
    assertEquals(0, state.ui.issueCount)
    assertTrue(state.ui.canPublish)
  }

  @Test
  fun datasetIssue_blocksPublish() {
    val state = surveyEditorViewModel()
    val dataset = state.ui.datasets.first()

    state.updateDataset(dataset.key) { it.copy(displayName = "") }
    assertTrue(state.ui.hasUnpublishedChanges)
    assertTrue(state.ui.issueCount > 0)
    assertFalse(state.ui.canPublish)
  }

  @Test
  fun revertingAnEdit_leavesNoUnpublishedChanges() {
    val state = surveyEditorViewModel()
    state.addForm()
    assertTrue(state.ui.hasUnpublishedChanges)

    val added = state.ui.selectedForm!!
    state.deleteForm(added.key)
    state.deleteDataset(state.ui.datasets.first { it.displayName == added.form.title }.key)
    assertFalse(state.ui.hasUnpublishedChanges)
  }

  @Test
  fun discardChanges_restoresPublishedSurvey() {
    val published = SurveyEditorSamples.draft()
    val state = surveyEditorViewModel(published)
    state.updateDetails { it.copy(title = "Renamed survey") }
    state.addForm()
    val addedFormSection = state.ui.section

    state.discardChanges()

    assertFalse(state.ui.hasUnpublishedChanges)
    assertEquals(published.details, state.ui.details)
    assertEquals(published.forms.map { it.key }, state.ui.forms.map { it.key })
    assertEquals(published.datasets, state.ui.datasets)
    assertNotEquals(addedFormSection, state.ui.section)
    assertEquals(SurveyEditorSection.Details, state.ui.section)
  }

  @Test
  fun discardChanges_afterPublish_keepsPublishedEdits() {
    val state = surveyEditorViewModel()
    state.updateDetails { it.copy(title = "Published title") }
    state.publish()
    state.updateDetails { it.copy(title = "Unpublished title") }

    state.discardChanges()

    assertEquals("Published title", state.ui.details.title)
    assertFalse(state.ui.hasUnpublishedChanges)
  }

  @Test
  fun linkedDatasetKind_followsFormGeometry() {
    val state = surveyEditorViewModel()
    state.addForm()
    val entry = state.ui.selectedForm!!
    state.formEditor(entry.key).addQuestion(EditorQuestionType.LOCATION)
    state.syncDatasetsLinkedToForm(entry.key)
    assertEquals(
      DatasetKind.MAP_LAYER,
      state.ui.datasets.first { it.linkedFormKey == entry.key }.kind,
    )
  }

  @Test
  fun createDatasetForForm_defaultsKindFromGeometry() {
    val state = surveyEditorViewModel()
    val withLocation = state.ui.forms.first { state.formEditor(it.key).ui.form.hasGeometry }
    state.createDatasetForForm(withLocation.key)
    assertEquals(DatasetKind.MAP_LAYER, state.ui.selectedDataset?.kind)
  }

  @Test
  fun linkedLayerGeometryKind_followsPrimaryGeometryQuestionType() {
    val state = surveyEditorViewModel()
    state.addForm()
    val entry = state.ui.selectedForm!!
    state.formEditor(entry.key).addQuestion(EditorQuestionType.POLYGON)
    state.syncDatasetsLinkedToForm(entry.key)
    val linked = state.ui.datasets.first { it.linkedFormKey == entry.key }
    assertEquals(DatasetKind.MAP_LAYER, linked.kind)
    assertEquals(GeometryKind.POLYGON, linked.geometryKind)

    // A new layer created for the form takes the same kind; a line form makes a line layer.
    state.createDatasetForForm(entry.key)
    assertEquals(GeometryKind.POLYGON, state.ui.selectedDataset?.geometryKind)
    val polygonKey = state.formEditor(entry.key).ui.form.primaryGeometryQuestion!!.key
    state.formEditor(entry.key).changeType(polygonKey, EditorQuestionType.LINE)
    state.syncDatasetsLinkedToForm(entry.key)
    assertTrue(
      state.ui.datasets
        .filter { it.linkedFormKey == entry.key }
        .all {
          it.geometryKind == GeometryKind.LINE
        }
    )
  }

  @Test
  fun createFormForDataset_geometryQuestionMatchesLayerKind() {
    val state = surveyEditorViewModel()
    val polygons = state.ui.mapLayers.first { it.geometryKind == GeometryKind.POLYGON }
    state.createFormForDataset(polygons.key)
    val form = state.formEditor(state.ui.selectedForm!!.key).ui.form
    assertEquals(EditorQuestionType.POLYGON, form.primaryGeometryQuestion?.type)
  }

  @Test
  fun createFormForDataset_withOpenFalse_linksDatasetWithoutLeavingCurrentForm() {
    val state = surveyEditorViewModel()
    val currentFormKey = state.ui.forms.first().key
    state.select(SurveyEditorSection.Form(currentFormKey))
    val unlinkedTable = state.ui.dataTables.first { it.linkedFormKey == null }

    state.createFormForDataset(unlinkedTable.key, open = false)

    assertEquals(SurveyEditorSection.Form(currentFormKey), state.ui.section)
    val updatedTable = state.ui.datasets.first { it.key == unlinkedTable.key }
    assertNotNull(updatedTable.linkedFormKey)
    val catalogEntry = state.ui.datasetCatalog(currentFormKey).first { it.key == unlinkedTable.key }
    assertTrue(catalogEntry.hasCreationForm)
    assertNotNull(catalogEntry.linkedFormTitle)
  }

  @Test
  fun canPublish_blockedByGpsOnlyGeometryOnWeb() {
    val state = surveyEditorViewModel()
    val entry = state.ui.forms.first { state.formEditor(it.key).ui.form.hasGeometry }
    state.publish()
    state
      .formEditor(entry.key)
      .updateAvailability(state.formEditor(entry.key).ui.form.availability.withWeb(true))
    assertTrue(state.formEditor(entry.key).ui.issues.isNotEmpty())
    assertTrue(state.ui.hasUnpublishedChanges)
    assertFalse(state.ui.canPublish)

    state.formEditor(entry.key).makeGeometryQuestionsWebCompatible()
    assertTrue(state.formEditor(entry.key).ui.issues.isEmpty())
    assertTrue(state.ui.canPublish)
  }

  @Test
  fun setFormSaveToMode_update_deletesEmptyLinkedDataset() {
    val state = surveyEditorViewModel()
    state.addForm()
    val entry = state.ui.selectedForm!!
    val linked = state.ui.datasets.first { it.linkedFormKey == entry.key }

    state.setFormSaveToMode(entry.key, SaveToMode.UPDATE)

    assertTrue(state.ui.datasets.none { it.key == linked.key })
    val saveTo = state.formEditor(entry.key).ui.form.saveTo
    assertEquals(SaveToMode.UPDATE, saveTo.mode)
    assertEquals("coffee_parcels", saveTo.targetDatasetId)
    assertEquals(SurveyEditorSection.Form(entry.key), state.ui.section)
  }

  @Test
  fun setFormSaveToMode_update_unlinksDatasetWithFeatures() {
    val state = surveyEditorViewModel()
    state.addForm()
    val entry = state.ui.selectedForm!!
    val linked = state.ui.datasets.first { it.linkedFormKey == entry.key }
    state.addRow(linked.key)

    state.setFormSaveToMode(entry.key, SaveToMode.UPDATE)

    val kept = state.ui.datasets.first { it.key == linked.key }
    assertNull(kept.linkedFormKey)
    assertNotEquals(kept.id, state.formEditor(entry.key).ui.form.saveTo.targetDatasetId)
  }

  @Test
  fun setFormSaveToMode_create_relinksNewDatasetWithoutLeavingForm() {
    val state = surveyEditorViewModel()
    state.addForm()
    val entry = state.ui.selectedForm!!
    state.setFormSaveToMode(entry.key, SaveToMode.UPDATE)
    assertTrue(state.ui.datasets.none { it.linkedFormKey == entry.key })

    state.setFormSaveToMode(entry.key, SaveToMode.CREATE)

    assertEquals(SaveToMode.CREATE, state.formEditor(entry.key).ui.form.saveTo.mode)
    assertEquals(1, state.ui.datasets.count { it.linkedFormKey == entry.key })
    assertEquals(SurveyEditorSection.Form(entry.key), state.ui.section)
  }

  @Test
  fun renamingTargetDatasetAndProperty_keepsUpdateFormPointedAtIt() {
    val state = surveyEditorViewModel()
    state.addForm()
    val entry = state.ui.selectedForm!!
    state.formEditor(entry.key).addQuestion(EditorQuestionType.TEXT)
    val question = state.formEditor(entry.key).ui.form.questions.last()
    state.setFormSaveToMode(entry.key, SaveToMode.UPDATE)
    state.formEditor(entry.key).setMapping(question.key, "status")
    val parcels = state.ui.datasets.first { it.id == "coffee_parcels" }
    assertEquals(listOf(entry.key), state.ui.formsUpdating(parcels).map { it.key })

    state.updateDataset(parcels.key) { it.copy(id = "parcels") }
    assertEquals("parcels", state.formEditor(entry.key).ui.form.saveTo.targetDatasetId)

    val statusIndex = parcels.properties.indexOfFirst { it.name == "status" }
    state.updateProperty(
      parcels.key,
      statusIndex,
      parcels.properties[statusIndex].copy(name = "state"),
    )
    assertEquals("state", state.formEditor(entry.key).ui.form.saveTo.propertyFor(question.key))
  }

  @Test
  fun publishedFormXml_includesSaveToLogic() {
    val state = surveyEditorViewModel()
    state.addForm()
    val entry = state.ui.selectedForm!!
    val createXml =
      state.ui.draft.publishedFormXml(
        SurveyEditorForm(entry.key, state.formEditor(entry.key).ui.form)
      )
    assertTrue(createXml.contains("create=\"1\""))

    state.setFormSaveToMode(entry.key, SaveToMode.UPDATE)
    val updateXml =
      state.ui.draft.publishedFormXml(
        SurveyEditorForm(entry.key, state.formEditor(entry.key).ui.form)
      )
    assertTrue(updateXml.contains("update=\"1\""))
    assertTrue(updateXml.contains("<instance id=\"coffee_parcels\""))
    assertTrue(updateXml.contains("<item>"))
  }

  // Organizations -----------------------------------------------------------------------------

  @Test
  fun organizationPolicy_withoutOrganization_isAnIssueThatBlocksPublishing() {
    val state = surveyEditorViewModel()
    state.setOrganization(null)
    state.updateSharing { it.copy(policy = SharingPolicy.ORGANIZATION) }

    assertEquals(1, state.ui.sharingIssues.size)
    assertEquals(1, state.ui.issueCount)
    assertTrue(state.ui.hasUnpublishedChanges)
    assertFalse(state.ui.canPublish)

    state.setOrganization("org-1")
    assertEquals(emptyList(), state.ui.sharingIssues)
    assertTrue(state.ui.canPublish)
  }

  @Test
  fun clearingOrganization_downgradesOrganizationPolicy_andExplainsWhy() {
    val state = surveyEditorViewModel()
    state.updateSharing { it.copy(policy = SharingPolicy.ORGANIZATION) }
    assertNull(state.ui.organizationNotice)

    state.setOrganization("")

    assertNull(state.ui.details.organizationId)
    assertEquals(SharingPolicy.RESTRICTED, state.ui.sharing.policy)
    assertNotNull(state.ui.organizationNotice)
    state.dismissOrganizationNotice()
    assertNull(state.ui.organizationNotice)
  }

  @Test
  fun clearingOrganization_keepsOtherPolicies() {
    val state = surveyEditorViewModel()
    state.updateSharing { it.copy(policy = SharingPolicy.PUBLIC) }
    state.setOrganization(null)
    assertEquals(SharingPolicy.PUBLIC, state.ui.sharing.policy)
    assertNull(state.ui.organizationNotice)
  }

  @Test
  fun discardChanges_restoresOrganizationAndClearsNotice() {
    val state = surveyEditorViewModel()
    val original = state.ui.details.organizationId
    state.updateSharing { it.copy(policy = SharingPolicy.ORGANIZATION) }
    state.setOrganization(null)
    state.discardChanges()
    assertEquals(original, state.ui.details.organizationId)
    assertEquals(SharingPolicy.RESTRICTED, state.ui.sharing.policy)
    assertNull(state.ui.organizationNotice)
    assertFalse(state.ui.hasUnpublishedChanges)
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
    val state = surveyEditorViewModel(draft)
    assertEquals(SharingPolicy.ORGANIZATION, state.ui.sharing.policy)
    assertNotNull(state.ui.details.organizationId)
    assertEquals(0, state.ui.issueCount)
  }
}
