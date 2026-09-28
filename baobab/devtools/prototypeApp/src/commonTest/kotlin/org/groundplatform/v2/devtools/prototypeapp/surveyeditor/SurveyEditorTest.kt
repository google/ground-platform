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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.PrototypeWorkbenchPage

class SurveyEditorTest {

  @Test
  fun samples_areValid() {
    val state = SurveyEditorState()
    assertEquals(2, state.mapLayers.size)
    assertEquals(2, state.dataTables.size)
    state.datasets.forEach { assertEquals(emptyList(), state.datasetIssues(it), it.id) }
    assertTrue(state.forms.all { it.editor.issues.isEmpty() })
  }

  @Test
  fun addAndDeleteForm_updatesSelection() {
    val state = SurveyEditorState()
    state.addForm()
    val added = assertNotNull(state.selectedForm)
    assertEquals("New form", added.editor.form.title)
    assertEquals("new_form", added.editor.form.formId)
    state.addForm()
    assertEquals("new_form_2", state.selectedForm!!.editor.form.formId)

    state.deleteForm(state.selectedForm!!.key)
    assertEquals(added.key, state.selectedForm?.key)
  }

  @Test
  fun addDataset_createsKindSpecificEntry() {
    val state = SurveyEditorState()
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
    val state = SurveyEditorState()
    state.dataTables.map { it.key }.forEach { state.deleteDataset(it) }
    assertIs<SurveyEditorSection.Details>(state.section)
  }

  @Test
  fun renamingProperty_carriesValuesAndKey() {
    val state = SurveyEditorState()
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
    val state = SurveyEditorState()
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
    val state = SurveyEditorState()
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
    val state = SurveyEditorState()
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
    val state = SurveyEditorState()
    state.addForm()
    val keys = state.forms.map { it.key }
    state.moveForm(keys[2], 0)
    assertEquals(listOf(keys[2], keys[0], keys[1]), state.forms.map { it.key })
  }

  @Test
  fun moveDataset_reordersWithinKindOnly() {
    val state = SurveyEditorState()
    state.addDataset(DatasetKind.MAP_LAYER)
    val layers = state.mapLayers.map { it.key }
    val tables = state.dataTables.map { it.key }
    val kinds = state.datasets.map { it.kind }

    state.moveDataset(layers.last(), 0)

    assertEquals(listOf(layers[2], layers[0], layers[1]), state.mapLayers.map { it.key })
    assertEquals(tables, state.dataTables.map { it.key })
    assertEquals(kinds, state.datasets.map { it.kind })
  }
}
