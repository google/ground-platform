/*
 * Copyright 2026 The Collect One Authors.
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
package org.openforis.collectone.v2.balloon

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.MutateDataResponse
import openforis.collectone.v1.data.Position
import openforis.collectone.v1.data.SubmissionFieldEntryDef
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.engine.FormSession
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.ui.AppContrast
import org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm
import org.openforis.collectone.v2.core.forms.ui.earthColorScheme
import org.openforis.collectone.v2.core.geo.io.KmlBalloonWriter
import org.openforis.collectone.v2.core.mutation.DataMutations

class BalloonPayloadTest {
  @Test
  fun decodeBalloonConfigFallsBackToBuiltInDemoFormWhenNoPayloadProvided() {
    val config =
      decodeBalloonConfig(
        params = parseUrlParams("#entityId=plot-1&entityLabel=Parcel%20%231&geoId=ENT-01"),
        defaultServiceUrl = "https://example.org/",
      )

    assertEquals("plot-1", config.entityId)
    assertEquals("Parcel #1", config.entityName)
    assertEquals("ENT-01", config.geoId)
    assertEquals("https://example.org", config.serviceUrl)
    assertEquals(1, config.forms.size)
    assertEquals("Plot Land Cover & Deforestation Assessment", config.forms.single().title)
    assertFalse(config.canSyncToServer)
  }

  @Test
  fun decodeBalloonConfigDecodesMultipleEncodedFormsAndPrePopulatesTargetEntity() {
    val customForm =
      FormDef(
        form_id = "coffee_audit",
        title = "Coffee Plot Audit",
        version = "3",
        model =
          ModelDef(
            primary_instance =
              PrimaryInstance(
                record_schema =
                  RecordSchema(
                    name = "data",
                    fields =
                      listOf(
                        FieldDefinition(name = "target_entity", type = DataType.TYPE_STRING),
                        FieldDefinition(name = "plot_gps", type = DataType.TYPE_GEOPOINT),
                        FieldDefinition(name = "shade_trees", type = DataType.TYPE_INT32),
                      ),
                  )
              ),
            bindings =
              listOf(
                FieldBinding(
                  field_path = "/data/target_entity",
                  type = DataType.TYPE_STRING,
                  required_expression = "true()",
                ),
                FieldBinding(field_path = "/data/plot_gps", type = DataType.TYPE_GEOPOINT),
                FieldBinding(field_path = "/data/shade_trees", type = DataType.TYPE_INT32),
              ),
          ),
        view =
          ViewDef(
            components =
              listOf(
                ViewComponent(
                  control =
                    ControlDef(
                      field_ref = "/data/target_entity",
                      type = ControlType.CONTROL_SELECT_ONE,
                      appearance = "map-select",
                      label = LabelDef(text = "Select Plot"),
                    )
                ),
                ViewComponent(
                  control =
                    ControlDef(
                      field_ref = "/data/shade_trees",
                      type = ControlType.CONTROL_INPUT,
                      label = LabelDef(text = "Shade tree count"),
                    )
                ),
              )
          ),
      )

    val encodedList =
      Json.encodeToString(
        listOf(
          EncodedBalloonForm(
            id = "form-coffee",
            title = "Coffee Plot Audit",
            version = "3",
            protoBase64 = encodeBase64Url(FormDef.ADAPTER.encode(customForm)),
          )
        )
      )

    val config =
      decodeBalloonConfig(
        params =
          mapOf(
            "surveyId" to "survey-1",
            "datasetId" to "plots",
            "entityId" to "ent-42",
            "entityLabel" to "Finca Esperanza",
            "lat" to "8.472121",
            "lng" to "-83.579134",
            "markerSymbol" to "○",
            "token" to "demo-jwt",
            "forms" to encodeBase64Url(encodedList.encodeToByteArray()),
          ),
        defaultServiceUrl = "https://example.org",
      )

    assertTrue(config.canSyncToServer)
    val prepared = config.forms.single().formDef
    val session = FormSession(formDef = prepared)
    assertEquals(
      "ent-42",
      session.state.findFieldState("/data/target_entity")?.value?.scalar_value?.string_value,
    )
    val seededGps =
      session.state.findFieldState("/data/plot_gps")?.value?.scalar_value?.geopoint_value
    assertEquals(8.472121, seededGps?.latitude)
    assertEquals(-83.579134, seededGps?.longitude)

    val (mutateReq, nextSymbol) =
      buildBalloonMutateDataRequest(
        config = config,
        form = config.forms.single(),
        result =
          FinalizationResult.Success(
            state = session.state,
            recordInstance = session.state.recordInstance,
            entityStates = emptyList(),
          ),
        entries =
          listOf(
            SubmissionFieldEntryDef(
              question_name = "shade_trees",
              question_label = "Shade tree count",
              answer_value = "18",
            )
          ),
        submissionId = "sub-99",
        clientId = "client-1",
        baseSeq = 1L,
        nowEpochSeconds = 1_760_000_000L,
      )

    assertEquals("◐", nextSymbol)
    assertEquals("survey-1", mutateReq.survey_id)
    assertEquals(2, mutateReq.mutations.size)
    assertEquals("sub-99", mutateReq.mutations[0].submission_mutation?.submission_id)
    val entityMutation = mutateReq.mutations[1].entity_mutation
    assertEquals("ent-42", entityMutation?.entity_id)
    assertEquals("plots", entityMutation?.dataset_id)
    val entityUpsert = entityMutation?.upsert
    // Sparse property patch: upsert.entity_id must be empty so DataMutationApplier merges
    // properties onto the stored entity instead of replacing the whole EntityRecord and wiping
    // label/geometry.
    assertEquals("", entityUpsert?.entity_id)
    assertEquals("In progress", entityUpsert?.properties?.get("status")?.string_value)
    assertEquals("◐", entityUpsert?.properties?.get("marker-symbol")?.string_value)
    assertEquals("18", entityUpsert?.properties?.get("shade_trees")?.string_value)

    val existingPlot =
      EntityRecord(
        entity_id = "ent-42",
        dataset_id = "plots",
        survey_id = "survey-1",
        label = "Finca Esperanza",
        geo_id = "P000042",
        geometry = Geometry(point = Position(latitude = 8.472121, longitude = -83.579134)),
        properties = mapOf("plot_id" to TypedValue(string_value = "P000042")),
      )
    val mergedPlot = DataMutations.applyEntity(existingPlot, entityMutation?.delete, entityUpsert)
    assertEquals("Finca Esperanza", mergedPlot.label)
    assertEquals("P000042", mergedPlot.geo_id)
    assertEquals(8.472121, mergedPlot.geometry?.point?.latitude)
    assertEquals("P000042", mergedPlot.properties["plot_id"]?.string_value)
    assertEquals("In progress", mergedPlot.properties["status"]?.string_value)
    assertEquals("18", mergedPlot.properties["shade_trees"]?.string_value)
  }

  @Test
  fun grpcWebFrameAndResponseRoundTrip() {
    val responseBytes = MutateDataResponse.ADAPTER.encode(MutateDataResponse())
    val dataFrame = frameGrpcWebRequest(responseBytes)
    val trailerPayload = "grpc-status: 0\r\n".encodeToByteArray()
    val trailerFrame = ByteArray(5 + trailerPayload.size)
    trailerFrame[0] = 0x80.toByte()
    trailerFrame[4] = trailerPayload.size.toByte()
    trailerPayload.copyInto(trailerFrame, destinationOffset = 5)

    val outcome = parseMutateDataGrpcWebResponse(dataFrame + trailerFrame)
    assertIs<GrpcWebCallOutcome.Success<MutateDataResponse>>(outcome)

    val failed =
      parseMutateDataGrpcWebResponse(
        ByteArray(0),
        headerGrpcStatus = "16",
        headerGrpcMessage = "Unauthenticated%20caller",
      )
    assertIs<GrpcWebCallOutcome.GrpcFailure>(failed)
    assertEquals(16, failed.grpcStatus)
    assertEquals("Unauthenticated caller", failed.grpcMessage)

    val emptyBodyWithOkHeader =
      parseMutateDataGrpcWebResponse(
        ByteArray(0),
        headerGrpcStatus = "0",
      )
    assertIs<GrpcWebCallOutcome.GrpcFailure>(emptyBodyWithOkHeader)
  }

  @Test
  fun pkceChallengeMatchesRfc7636VectorAndBuildsFragmentBridgeUrl() {
    val sessionId = "11111111-2222-4333-8444-555555555555"
    val pkce =
      createBalloonPkceSession(
        sessionId = sessionId,
        codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
      )
    assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkce.codeChallenge)

    val config =
      BalloonConfig(
        surveyId = "survey-1",
        surveyTitle = "Kenya Coffee",
        entityName = "Parcel 02",
        serviceUrl = "https://api.example.org",
      )
    assertTrue(config.canSyncWithViewerAuth)
    assertFalse(config.canSyncToServer)

    val url = buildBalloonAuthBridgeUrl("https://app.example.org/balloon-auth.html", pkce, config)
    assertTrue(url.startsWith("https://app.example.org/balloon-auth.html#session=$sessionId"), url)
    assertFalse("?" in url, url)
    assertTrue("challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM" in url, url)

    assertIs<BalloonClaimPollResult.Pending>(
      parseBalloonClaimResponse(202, """{"status":"pending"}""")
    )
    assertIs<BalloonClaimPollResult.Failed>(
      parseBalloonClaimResponse(403, """{"status":"invalid_verifier"}""")
    )
    val claimed =
      parseBalloonClaimResponse(
        200,
        """{"status":"authenticated","token":"viewer-jwt","collectorName":"Gino","collectorEmail":"gino@example.org"}""",
      )
    assertIs<BalloonClaimPollResult.Authenticated>(claimed)
    assertEquals("viewer-jwt", claimed.session.authToken)
    assertEquals("gino@example.org", claimed.session.collectorEmail)
    assertTrue(config.withViewerSession(claimed.session).canSyncToServer)
  }

  @Test
  fun googleEarthThemeAndKmlBalloonStyleUseTerrariumTokens() {
    val light = earthColorScheme(darkTheme = false, contrast = AppContrast.STANDARD)
    assertEquals(Color(0xFF0B57D0), light.primary)
    assertEquals(Color(0xFFFFFFFF), light.surface)
    assertEquals(Color(0xFF1F1F1F), light.onSurface)
    assertEquals(Color(0xFFF0F4F9), light.surfaceContainer)

    val dark = earthColorScheme(darkTheme = true, contrast = AppContrast.STANDARD)
    assertEquals(Color(0xFFA8C7FA), dark.primary)
    assertEquals(Color(0xFF131314), dark.surface)
    assertEquals(Color(0xFFE3E3E3), dark.onSurface)

    val kml =
      KmlBalloonWriter.writeDocument(
        surveyId = "s1",
        surveyTitle = "Survey",
        documentName = "Survey",
        layers =
          listOf(
            KmlBalloonWriter.LayerFolderSpec(
              layerId = "l1",
              datasetId = "plots",
              name = "Sample plots",
            )
          ),
      )
    assertTrue("<bgColor>ffffffff</bgColor>" in kml)
    assertTrue("<textColor>ff1f1f1f</textColor>" in kml)
    assertTrue("background: #FFFFFF;" in kml)
  }

  @Test
  fun decodeBalloonConfigFiltersOutFormsWithOpenInGoogleEarthDisabled() {
    val formA =
      FormDef(
        form_id = "form-a",
        title = "Earth Enabled Form",
        version = "1",
      )
    val formB =
      FormDef(
        form_id = "form-b",
        title = "Web Only Form",
        version = "1",
      )

    val mixedEncoded =
      Json.encodeToString(
        listOf(
          EncodedBalloonForm(
            id = "form-a",
            title = "Earth Enabled Form",
            version = "1",
            protoBase64 = encodeBase64Url(FormDef.ADAPTER.encode(formA)),
            openInGoogleEarth = true,
          ),
          EncodedBalloonForm(
            id = "form-b",
            title = "Web Only Form",
            version = "1",
            protoBase64 = encodeBase64Url(FormDef.ADAPTER.encode(formB)),
            openInGoogleEarth = false,
          ),
        )
      )

    val mixedConfig =
      decodeBalloonConfig(
        params =
          mapOf(
            "surveyId" to "survey-1",
            "forms" to encodeBase64Url(mixedEncoded.encodeToByteArray()),
          )
      )
    assertEquals(listOf("form-a"), mixedConfig.forms.map { it.id })

    val allDisabledEncoded =
      Json.encodeToString(
        listOf(
          EncodedBalloonForm(
            id = "form-b",
            title = "Web Only Form",
            version = "1",
            protoBase64 = encodeBase64Url(FormDef.ADAPTER.encode(formB)),
            openInGoogleEarth = false,
          )
        )
      )
    val emptyConfig =
      decodeBalloonConfig(
        params =
          mapOf(
            "surveyId" to "survey-1",
            "forms" to encodeBase64Url(allDisabledEncoded.encodeToByteArray()),
          )
      )
    assertTrue(emptyConfig.forms.isEmpty())
  }

  @Test
  fun draftRecordBase64AndEntityStateResponseRoundTrip() {
    val form = defaultDemoBalloonForm()
    val session = FormSession(formDef = form.formDef)
    val encoded = encodeDraftRecordBase64(session.state.recordInstance)
    val decoded = decodeDraftRecordBase64(encoded)
    assertEquals(session.state.recordInstance, decoded)

    val parsed =
      parseBalloonEntityStateResponse(
        200,
        """{"surveyId":"s1","entityId":"p1","entityLabel":"Plot 01","status":"In progress","markerSymbol":"◐","markerColor":"#F9AB00","draftFormId":"plot_interpretation_form","draftRecordBase64":"$encoded"}""",
      )
    assertTrue(parsed != null)
    assertEquals("In progress", parsed.status)
    assertEquals("◐", parsed.markerSymbol)
    assertEquals("plot_interpretation_form", parsed.draftFormId)
    assertEquals(encoded, parsed.draftRecordBase64)

    val baseConfig = BalloonConfig(surveyId = "s1", entityId = "p1")
    val merged = baseConfig.withLiveEntityState(parsed)
    assertEquals("In progress", merged.status)
    assertEquals("◐", merged.markerSymbol)
    assertEquals("Plot 01", merged.entityName)
  }

  @Test
  fun googleEarthPlotInterpretationForm_hidesEntityPickerAndProducesValidSubmissionAndEntityUpdate() {
    val formDef = WorkbenchExampleForm.GOOGLE_EARTH_PLOT_INTERPRETATION.formDefinition.proto
    val encodedList =
      Json.encodeToString(
        listOf(
          EncodedBalloonForm(
            id = "form-google-earth-interpretation",
            title = formDef.title,
            version = formDef.version,
            protoBase64 = encodeBase64Url(FormDef.ADAPTER.encode(formDef)),
            openInGoogleEarth = true,
          )
        )
      )
    val formsPayload = encodeBase64Url(encodedList.encodeToByteArray())

    val config =
      decodeBalloonConfig(
        params =
          mapOf(
            "surveyId" to "00000037-7e4a-5b2c-9d18-3f8a61c4e902",
            "surveyTitle" to "Peatland & Canopy Change Interpretation (Google Earth)",
            "datasetId" to "interpretation_plots",
            "datasetName" to "Satellite Interpretation Sample Plots",
            "entityId" to "00000044-7e4a-5b2c-9d18-3f8a61c4e904",
            "entityLabel" to "PLT-GE-04 • West Blackwater Buffer",
            "geoId" to "PLT-GE-04",
            "lat" to "-2.21864",
            "lng" to "113.91738",
            "status" to "Pending",
            "markerSymbol" to "○",
            "devRole" to "admin",
            "collectorName" to "Maya Lin",
            "collectorEmail" to "maya.lin@collect-one.example.org",
            "forms" to formsPayload,
          ),
        defaultServiceUrl = "https://example.org",
      )

    assertTrue(config.canSyncToServer)
    val entry = config.forms.single()
    assertEquals("form-google-earth-interpretation", entry.id)
    // Entity picker is pre-populated and hidden inside the balloon, leaving the 7 Earth-compatible
    // interpretation questions visible.
    assertEquals(7, visibleQuestionCount(entry.formDef))

    val session = FormSession(formDef = entry.formDef)
    assertEquals(
      "00000044-7e4a-5b2c-9d18-3f8a61c4e904",
      session.state.findFieldState("/data/target_entity")?.value?.scalar_value?.string_value,
    )

    session.updateString("/data/land_use_transition", "rewetted_restoration")
    session.updateString("/data/canopy_stratum", "canopy")
    session.updateInt("/data/canopy_cover_pct", 74)
    session.updateMultiSelect("/data/disturbance_drivers", listOf("drainage_canal"))
    session.updateString("/data/deforestation_free_status", "compliant_post_2020")
    session.updateString("/data/imagery_acquisition_date", "2026-08-19")
    session.updateString(
      "/data/interpreter_notes",
      "Blocked perimeter canal; dense secondary peat-swamp regrowth.",
    )

    val finalized = assertIs<FinalizationResult.Success>(session.finalize())
    val entries = extractAnsweredEntries(finalized.state)
    assertEquals(
      listOf(
        "land_use_transition",
        "canopy_stratum",
        "canopy_cover_pct",
        "disturbance_drivers",
        "deforestation_free_status",
        "imagery_acquisition_date",
        "interpreter_notes",
      ),
      entries.map { it.question_name },
    )

    val (mutateReq, nextSymbol) =
      buildBalloonMutateDataRequest(
        config = config,
        form = entry,
        result = finalized,
        entries = entries,
        submissionId = "00000054-7e4a-5b2c-9d18-3f8a61c4e914",
        clientId = "balloon-client-1",
        baseSeq = 1L,
        nowEpochSeconds = 1_770_010_000L,
      )
    assertEquals("◐", nextSymbol)
    assertEquals(2, mutateReq.mutations.size)
    val subUpsert = mutateReq.mutations[0].submission_mutation?.upsert
    assertEquals("00000044-7e4a-5b2c-9d18-3f8a61c4e904", subUpsert?.entity_id)
    assertEquals("form-google-earth-interpretation", subUpsert?.form_id)
    val entityMutation = mutateReq.mutations[1].entity_mutation
    assertEquals("00000044-7e4a-5b2c-9d18-3f8a61c4e904", entityMutation?.entity_id)
    val entityUpsert = entityMutation?.upsert
    assertEquals("", entityUpsert?.entity_id)
    assertEquals("◐", entityUpsert?.properties?.get("marker-symbol")?.string_value)
    assertEquals(
      "rewetted_restoration",
      entityUpsert?.properties?.get("land_use_transition")?.string_value,
    )
    assertEquals("74", entityUpsert?.properties?.get("canopy_cover_pct")?.string_value)
  }

  @Test
  fun buildBalloonMutateDataRequest_unsetInitialStatusBecomesCompletedCheckmark() {
    val form = defaultDemoBalloonForm()
    val session = FormSession(formDef = form.formDef)
    val config =
      BalloonConfig(
        surveyId = "survey-unset",
        datasetId = "plots",
        entityId = "plot-1",
        entityName = "Plot 1",
        status = "",
        markerSymbol = "",
        forms = listOf(form),
        serviceUrl = "https://example.org",
        authToken = "demo-jwt",
      )

    val (mutateReq, nextSymbol) =
      buildBalloonMutateDataRequest(
        config = config,
        form = form,
        result =
          FinalizationResult.Success(
            state = session.state,
            recordInstance = session.state.recordInstance,
            entityStates = emptyList(),
          ),
        entries =
          listOf(
            SubmissionFieldEntryDef(
              question_name = "land_cover",
              question_label = "Land cover",
              answer_value = "forest",
            )
          ),
        submissionId = "sub-1",
        clientId = "client-1",
        baseSeq = 1L,
        nowEpochSeconds = 1_760_000_000L,
      )

    assertEquals("✓", nextSymbol)
    val entityProps = mutateReq.mutations[1].entity_mutation?.upsert?.properties
    assertEquals("Completed", entityProps?.get("status")?.string_value)
    assertEquals("✓", entityProps?.get("marker-symbol")?.string_value)
    assertEquals("#1E8E3E", entityProps?.get("marker-color")?.string_value)
  }
}
