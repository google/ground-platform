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
@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package org.openforis.collectone.v2.balloon

import com.squareup.wire.ofEpochSecond
import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.data.MutateDataResponse
import openforis.collectone.v1.data.SubmissionFieldEntryDef
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.ChoiceItem
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PreloadType
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.model.FormState
import org.openforis.collectone.v2.core.forms.temporal.formatIsoInstant
import org.openforis.collectone.v2.core.forms.temporal.formatIsoTime
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.forms.ui.FormWizardStep
import org.openforis.collectone.v2.core.forms.ui.formatFieldValueForDisplay
import org.openforis.collectone.v2.core.geo.io.GoogleEarthDeepLinks
import org.openforis.collectone.v2.core.ids.RecordIds

/** Wire format of one embedded form inside a KML `<BalloonStyle>` configuration payload. */
@Serializable
data class EncodedBalloonForm(
  val id: String,
  val title: String = "",
  val version: String = "1",
  val protoBase64: String,
  val openInGoogleEarth: Boolean = true,
)

/** Decoded form entry available inside the KML balloon runner. */
data class BalloonFormEntry(
  val id: String,
  val title: String,
  val version: String,
  val formDef: FormDef,
)

/** Verified viewer session obtained in memory via the top-level `/balloon-auth.html` handoff. */
data class BalloonViewerSession(
  val authToken: String? = null,
  val devRole: String? = null,
  val devUserId: String = "",
  val devEmail: String = "",
  val devName: String = "",
  val collectorName: String = "",
  val collectorEmail: String = "",
) {
  val isAuthenticated: Boolean
    get() = !authToken.isNullOrBlank() || !devRole.isNullOrBlank() || devUserId.isNotBlank()

  fun devHeadersJson(): String {
    val entries = buildMap {
      devRole?.takeIf { it.isNotBlank() }?.let { put("x-dev-role", it) }
      if (devUserId.isNotBlank()) put("x-dev-user-id", devUserId)
      if (devEmail.isNotBlank()) put("x-dev-user-email", devEmail)
      if (devName.isNotBlank()) put("x-dev-user-name", devName)
    }
    if (entries.isEmpty()) return ""
    return buildJsonObject { entries.forEach { (k, v) -> put(k, v) } }.toString()
  }
}

/** Ephemeral RFC 7636 PKCE (`S256`) session generated inside a sandboxed KML balloon iframe. */
data class BalloonPkceSession(
  val sessionId: String,
  val codeVerifier: String,
  val codeChallenge: String,
)

/** Result of polling `POST /balloon/auth/claim` for an active [BalloonPkceSession]. */
sealed interface BalloonClaimPollResult {
  data object Pending : BalloonClaimPollResult

  data class Authenticated(val session: BalloonViewerSession) : BalloonClaimPollResult

  data class Failed(val message: String) : BalloonClaimPollResult
}

/**
 * Live entity status and optional in-progress form draft returned by `GET
 * /balloon/state/:surveyId/:entityId`.
 */
data class BalloonEntityLiveState(
  val status: String = "",
  val markerSymbol: String = "",
  val markerColor: String = "",
  val entityLabel: String = "",
  val draftFormId: String = "",
  val draftRecordBase64: String = "",
)

/** Runtime parameters passed to the KML balloon form runner via URL hash, query, or postMessage. */
data class BalloonConfig(
  val surveyId: String = "",
  val surveyTitle: String = "",
  val datasetId: String = "",
  val datasetName: String = "",
  val entityId: String = "",
  val entityName: String = "",
  val geoId: String = "",
  val latitude: String = "",
  val longitude: String = "",
  val status: String = "",
  val markerSymbol: String = "",
  val forms: List<BalloonFormEntry> = emptyList(),
  val serviceUrl: String = "",
  val authToken: String? = null,
  val devRole: String? = null,
  val collectorName: String = "",
  val collectorEmail: String = "",
) {
  /** Whether this balloon targets a live Collect One survey backend (requiring viewer sign-in). */
  val canSyncWithViewerAuth: Boolean
    get() = serviceUrl.isNotBlank() && surveyId.isNotBlank()

  /** Whether live submission sync to the Collect One backend can be attempted immediately. */
  val canSyncToServer: Boolean
    get() = canSyncWithViewerAuth && (!authToken.isNullOrBlank() || !devRole.isNullOrBlank())

  /** Initial viewer session if legacy parameters supplied one, or an unauthenticated session. */
  fun initialViewerSession(): BalloonViewerSession =
    BalloonViewerSession(
      authToken = authToken,
      devRole = devRole,
      collectorName = collectorName,
      collectorEmail = collectorEmail,
    )

  /** Overlays a verified [BalloonViewerSession] onto this [BalloonConfig]. */
  fun withViewerSession(session: BalloonViewerSession): BalloonConfig =
    copy(
      authToken = session.authToken,
      devRole = session.devRole,
      collectorName = session.collectorName.ifBlank { collectorName },
      collectorEmail = session.collectorEmail.ifBlank { collectorEmail },
    )

  /** Overlays live entity status and symbol fetched from `/balloon/state/:surveyId/:entityId`. */
  fun withLiveEntityState(liveState: BalloonEntityLiveState): BalloonConfig =
    copy(
      status = liveState.status.ifBlank { status },
      markerSymbol = liveState.markerSymbol.ifBlank { markerSymbol },
      entityName = entityName.ifBlank { liveState.entityLabel },
    )
}

/** Outcome of decoding a binary `application/grpc-web+proto` response payload. */
sealed interface GrpcWebCallOutcome<out T> {
  data class Success<T>(val response: T) : GrpcWebCallOutcome<T>

  data class GrpcFailure(val grpcStatus: Int, val grpcMessage: String?) :
    GrpcWebCallOutcome<Nothing>
}

private val balloonJson = Json { ignoreUnknownKeys = true }

private val ENTITY_REF_PATHS =
  setOf(
    "/data/target_entity",
    "target_entity",
    "/data/sample_plot_entity",
    "sample_plot_entity",
    "/data/past_individual_id",
    "past_individual_id",
    "/data/primary_respondent_id",
    "primary_respondent_id",
  )

/** Encodes [bytes] as unpadded URL-safe Base64. */
fun encodeBase64Url(bytes: ByteArray): String =
  Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)

/** Generates a single-use RFC 7636 PKCE (`S256`) session for top-level tab auth handoff. */
fun createBalloonPkceSession(
  sessionId: String = RecordIds.newId(),
  codeVerifier: String = "${RecordIds.newId()}${RecordIds.newId()}".replace("-", ""),
): BalloonPkceSession {
  val digest = RecordIds.sha256(codeVerifier.encodeToByteArray())
  return BalloonPkceSession(
    sessionId = sessionId,
    codeVerifier = codeVerifier,
    codeChallenge = encodeBase64Url(digest),
  )
}

/**
 * Builds the top-level `/balloon-auth.html#...` URL (using `#` fragment so the PKCE session ID and
 * challenge are never leaked via HTTP `Referer` headers).
 */
fun buildBalloonAuthBridgeUrl(
  authPageUrl: String,
  pkce: BalloonPkceSession,
  config: BalloonConfig,
): String {
  val base = authPageUrl.substringBefore('#').ifBlank { "/balloon-auth.html" }
  val parts = buildList {
    add("session" to pkce.sessionId)
    add("challenge" to pkce.codeChallenge)
    if (config.serviceUrl.isNotBlank()) add("serviceUrl" to config.serviceUrl)
    if (config.surveyTitle.isNotBlank()) add("surveyTitle" to config.surveyTitle)
    if (config.entityName.isNotBlank()) add("entityLabel" to config.entityName)
  }
  val hash =
    parts.joinToString("&") { (k, v) ->
      "${GoogleEarthDeepLinks.encodeUriComponent(k)}=${GoogleEarthDeepLinks.encodeUriComponent(v)}"
    }
  return "$base#$hash"
}

/** Parses an HTTP response from `POST /balloon/auth/claim`. */
fun parseBalloonClaimResponse(
  httpStatus: Int,
  responseBody: String,
  networkError: String = "",
): BalloonClaimPollResult {
  if (networkError.isNotBlank() || httpStatus == 0) {
    return BalloonClaimPollResult.Pending
  }
  if (httpStatus == 202) return BalloonClaimPollResult.Pending
  val obj = runCatching { balloonJson.parseToJsonElement(responseBody) as? JsonObject }.getOrNull()
  val statusField = obj?.get("status")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
  if (httpStatus == 200 && statusField == "authenticated" && obj != null) {
    fun field(name: String): String =
      obj[name]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()
    val session =
      BalloonViewerSession(
        authToken = field("token").takeIf { it.isNotBlank() },
        devRole = field("devRole").takeIf { it.isNotBlank() },
        devUserId = field("devUserId"),
        devEmail = field("devEmail"),
        devName = field("devName"),
        collectorName = field("collectorName"),
        collectorEmail = field("collectorEmail"),
      )
    return if (session.isAuthenticated) {
      BalloonClaimPollResult.Authenticated(session)
    } else {
      BalloonClaimPollResult.Failed("Received empty credentials from Collect One sign-in.")
    }
  }
  if (httpStatus == 403 || statusField == "invalid_verifier") {
    return BalloonClaimPollResult.Failed("Verification session expired. Click Sign in to retry.")
  }
  return BalloonClaimPollResult.Pending
}

/** Parses an HTTP JSON response from `GET /balloon/state/:surveyId/:entityId`. */
fun parseBalloonEntityStateResponse(
  httpStatus: Int,
  responseBody: String,
  networkError: String = "",
): BalloonEntityLiveState? {
  if (networkError.isNotBlank() || httpStatus != 200 || responseBody.isBlank()) return null
  val obj =
    runCatching { balloonJson.parseToJsonElement(responseBody) as? JsonObject }.getOrNull()
      ?: return null
  fun field(name: String): String =
    obj[name]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()
  return BalloonEntityLiveState(
    status = field("status"),
    markerSymbol = field("markerSymbol"),
    markerColor = field("markerColor"),
    entityLabel = field("entityLabel"),
    draftFormId = field("draftFormId"),
    draftRecordBase64 = field("draftRecordBase64"),
  )
}

/** Encodes an in-progress [RecordInstance] as URL-safe Base64 for draft persistence. */
fun encodeDraftRecordBase64(record: RecordInstance): String =
  encodeBase64Url(RecordInstance.ADAPTER.encode(record))

/** Decodes an in-progress [RecordInstance] from URL-safe Base64 draft storage. */
fun decodeDraftRecordBase64(encoded: String): RecordInstance? {
  val trimmed = encoded.trim()
  if (trimmed.isEmpty()) return null
  return runCatching {
    val bytes = decodeFlexibleBase64(trimmed)
    if (bytes.isEmpty()) null else RecordInstance.ADAPTER.decode(bytes)
  }
    .getOrNull()
}

/** Decodes either standard or URL-safe Base64 (padded or unpadded). */
fun decodeFlexibleBase64(encoded: String): ByteArray {
  val normalized = encoded.trim().replace('+', '-').replace('/', '_').trimEnd('=')
  if (normalized.isEmpty()) return ByteArray(0)
  return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(normalized)
}

/** Parses URL query/fragment text (`a=1&b=2` or `#a=1&b=2`) without URL-decoding assumptions. */
fun parseUrlParams(raw: String): Map<String, String> {
  val clean = raw.trim().trimStart('#', '?')
  if (clean.isEmpty()) return emptyMap()
  return buildMap {
    for (pair in clean.split('&')) {
      if (pair.isEmpty()) continue
      val eq = pair.indexOf('=')
      val rawKey = if (eq >= 0) pair.substring(0, eq) else pair
      val rawVal = if (eq >= 0) pair.substring(eq + 1) else ""
      val key = percentDecodeUrlComponent(rawKey)
      if (key.isNotEmpty()) {
        put(key, percentDecodeUrlComponent(rawVal))
      }
    }
  }
}

internal fun percentDecodeUrlComponent(value: String): String {
  val withSpaces = value.replace('+', ' ')
  if ('%' !in withSpaces) return withSpaces
  val bytes = ArrayList<Byte>(withSpaces.length)
  var i = 0
  while (i < withSpaces.length) {
    val hex =
      if (withSpaces[i] == '%' && i + 2 < withSpaces.length) {
        withSpaces.substring(i + 1, i + 3).toIntOrNull(16)
      } else {
        null
      }
    if (hex != null) {
      bytes += hex.toByte()
      i += 3
    } else {
      bytes += withSpaces[i].toString().encodeToByteArray().toList()
      i++
    }
  }
  return bytes.toByteArray().decodeToString()
}

/**
 * Resolves a [BalloonConfig] from key-value [params] (merged from query string, URL hash, and any
 * `postMessage` initialization payload). Falls back to [defaultDemoBalloonForm] when opened
 * standalone without an embedded form payload.
 */
fun decodeBalloonConfig(
  params: Map<String, String>,
  defaultServiceUrl: String = "",
): BalloonConfig {
  val decodedForms = mutableListOf<BalloonFormEntry>()
  var hadEncodedForms = false

  params["forms"]
    ?.takeIf { it.isNotBlank() }
    ?.let { rawForms ->
      val jsonText =
        if (rawForms.trimStart().startsWith("[")) {
          rawForms
        } else {
          runCatching { decodeFlexibleBase64(rawForms).decodeToString() }.getOrNull().orEmpty()
        }
      runCatching { balloonJson.decodeFromString<List<EncodedBalloonForm>>(jsonText) }
        .getOrNull()
        ?.also { hadEncodedForms = true }
        ?.filter { it.openInGoogleEarth }
        ?.forEach { entry ->
          runCatching { FormDef.ADAPTER.decode(decodeFlexibleBase64(entry.protoBase64)) }
            .getOrNull()
            ?.let { proto ->
              decodedForms +=
                BalloonFormEntry(
                  id = entry.id.ifBlank { proto.form_id.ifBlank { "form" } },
                  title = entry.title.ifBlank { proto.title.ifBlank { "Data Collection Form" } },
                  version = entry.version.ifBlank { proto.version.ifBlank { "1" } },
                  formDef = proto,
                )
            }
        }
    }

  if (decodedForms.isEmpty() && !hadEncodedForms) {
    params["form"]
      ?.takeIf { it.isNotBlank() }
      ?.let { rawForm ->
        hadEncodedForms = true
        if (params["openInGoogleEarth"]?.trim()?.lowercase() != "false") {
          runCatching { FormDef.ADAPTER.decode(decodeFlexibleBase64(rawForm)) }
            .getOrNull()
            ?.let { proto ->
              val formId =
                params["formId"]?.takeIf { it.isNotBlank() } ?: proto.form_id.ifBlank { "form" }
              val formTitle =
                params["formTitle"]?.takeIf { it.isNotBlank() }
                  ?: proto.title.ifBlank { "Data Collection Form" }
              val formVersion =
                params["formVersion"]?.takeIf { it.isNotBlank() } ?: proto.version.ifBlank { "1" }
              decodedForms +=
                BalloonFormEntry(
                  id = formId,
                  title = formTitle,
                  version = formVersion,
                  formDef = proto,
                )
            }
        }
      }
  }

  val surveyId = params["surveyId"].orEmpty()
  val entityId = params["entityId"].orEmpty()
  val latitude = params["lat"] ?: params["latitude"].orEmpty()
  val longitude = params["lng"] ?: params["longitude"].orEmpty()

  val rawForms =
    when {
      decodedForms.isNotEmpty() -> decodedForms
      hadEncodedForms || surveyId.isNotBlank() -> emptyList()
      else -> listOf(defaultDemoBalloonForm())
    }
  val preparedForms = rawForms.map { entry ->
    entry.copy(
      formDef =
        prepareFormDefForEntity(
          formDef = entry.formDef,
          entityId = entityId,
          latitude = latitude,
          longitude = longitude,
        )
    )
  }

  val resolvedServiceUrl =
    (params["serviceUrl"]?.takeIf { it.isNotBlank() } ?: defaultServiceUrl).trimEnd('/')

  return BalloonConfig(
    surveyId = surveyId,
    surveyTitle = params["surveyTitle"].orEmpty(),
    datasetId = params["datasetId"].orEmpty(),
    datasetName = params["datasetName"].orEmpty(),
    entityId = entityId,
    entityName = params["entityLabel"] ?: params["entityName"].orEmpty(),
    geoId = params["geoId"].orEmpty(),
    latitude = latitude,
    longitude = longitude,
    status = params["status"].orEmpty(),
    markerSymbol = params["markerSymbol"].orEmpty(),
    forms = preparedForms,
    serviceUrl = resolvedServiceUrl,
    authToken = params["token"]?.takeIf { it.isNotBlank() },
    devRole = params["devRole"]?.takeIf { it.isNotBlank() },
    collectorName = params["collectorName"].orEmpty(),
    collectorEmail = params["collectorEmail"].orEmpty(),
  )
}

/**
 * Pre-populates the entity-reference defaults in [formDef] with [entityId] (so required
 * `target_entity` bindings pass automatically while hidden in the balloon), and seeds blank
 * top-level `geopoint` defaults from the placemark's [latitude]/[longitude] when present.
 */
fun prepareFormDefForEntity(
  formDef: FormDef,
  entityId: String,
  latitude: String = "",
  longitude: String = "",
): FormDef {
  val model = formDef.model ?: return formDef
  val primary = model.primary_instance ?: return formDef
  val schema = primary.record_schema ?: return formDef
  val defaults = primary.default_values ?: RecordNode()
  val updatedFields = defaults.fields.toMutableMap()

  if (entityId.isNotBlank()) {
    schema.fields
      .filter { "/data/${it.name}" in ENTITY_REF_PATHS || it.name in ENTITY_REF_PATHS }
      .forEach { field ->
        updatedFields[field.name] = FieldValue(scalar_value = TypedValue(string_value = entityId))
      }
  }

  val lat = latitude.toDoubleOrNull()
  val lng = longitude.toDoubleOrNull()
  if (lat != null && lng != null && lat.isFinite() && lng.isFinite()) {
    schema.fields
      .filter { it.type == DataType.TYPE_GEOPOINT && it.name !in updatedFields }
      .forEach { field ->
        updatedFields[field.name] =
          FieldValue(
            scalar_value = TypedValue(geopoint_value = GeoPoint(latitude = lat, longitude = lng))
          )
      }
  }

  if (updatedFields == defaults.fields) return formDef
  return formDef.copy(
    model =
      model.copy(
        primary_instance = primary.copy(default_values = defaults.copy(fields = updatedFields))
      )
  )
}

/** True when [step] is the map-feature picker (`entityref` / `map-select`), answered by KML. */
fun isEntityReferenceStep(step: FormWizardStep.QuestionStep): Boolean {
  val control = step.control
  return control.canonicalPath in ENTITY_REF_PATHS ||
    control.controlDef.field_ref in ENTITY_REF_PATHS ||
    control.controlDef.appearance.split(Regex("\\s+")).contains("map-select")
}

/** Counts the visible questions in [formDef] (excluding map-feature entity reference steps). */
fun visibleQuestionCount(formDef: FormDef): Int =
  FormWizardController(formDef = formDef).steps.count { step ->
    step is FormWizardStep.QuestionStep && !isEntityReferenceStep(step)
  }

/** Extracts answered question summaries in display order from [formState]. */
fun extractAnsweredEntries(formState: FormState): List<SubmissionFieldEntryDef> {
  val labelsByPath = mutableMapOf<String, String>()
  fun walk(components: List<ComponentState>) {
    for (component in components) {
      when (component) {
        is ComponentState.ControlState -> {
          val text = component.label?.text?.takeIf { it.isNotBlank() }
          if (text != null) labelsByPath[component.canonicalPath] = text
        }
        is ComponentState.GroupState -> walk(component.children)
        is ComponentState.RepeatGroupState ->
          component.instances.forEach { instance -> walk(instance.children) }
      }
    }
  }
  walk(formState.rootComponents)

  val orderedPaths = (labelsByPath.keys + formState.fieldStates.keys).distinct()
  return buildList {
    for (path in orderedPaths) {
      val fs = formState.fieldStates[path] ?: continue
      val isMetaOrEntityRef =
        fs.dataType == DataType.TYPE_MESSAGE ||
          fs.relativePath == "meta/instanceID" ||
          fs.relativePath.startsWith("meta/") ||
          fs.relativePath.startsWith("__") ||
          fs.canonicalPath.endsWith("/meta/instanceID") ||
          fs.canonicalPath in ENTITY_REF_PATHS ||
          fs.relativePath in ENTITY_REF_PATHS
      if (fs.isRelevant && !fs.isEmpty && !isMetaOrEntityRef) {
        val questionName = fs.relativePath.ifBlank { fs.canonicalPath.trimStart('/') }
        val questionLabel =
          labelsByPath[fs.canonicalPath]
            ?: fs.canonicalPath.substringAfterLast('/').replace('_', ' ')
        add(
          SubmissionFieldEntryDef(
            question_name = questionName,
            question_label = questionLabel,
            answer_value = formatFieldValueForDisplay(fs.value, fs.dataType),
          )
        )
      }
    }
  }
}

/** Formats answered entries and plot metadata as a shareable JSON string for offline copy. */
fun buildSubmissionSummaryJson(
  config: BalloonConfig,
  form: BalloonFormEntry,
  entries: List<SubmissionFieldEntryDef>,
  submissionId: String,
): String = buildJsonObject {
  put("submissionId", submissionId)
  if (config.surveyId.isNotBlank()) put("surveyId", config.surveyId)
  if (config.datasetId.isNotBlank()) put("datasetId", config.datasetId)
  if (config.entityId.isNotBlank()) put("entityId", config.entityId)
  if (config.entityName.isNotBlank()) put("entityLabel", config.entityName)
  if (config.geoId.isNotBlank()) put("geoId", config.geoId)
  put("formId", form.id)
  put("formTitle", form.title)
  putJsonObject("answers") {
    entries.forEach { entry -> put(entry.question_name, entry.answer_value) }
  }
}
  .toString()

/**
 * Builds an idempotent [MutateDataRequest] recording the completed [SubmissionRecord] and (when
 * collected against a map feature) updating the target [EntityRecord]'s `save_to` properties and
 * workflow status (`○` → `◐` → `✓`).
 */
fun buildBalloonMutateDataRequest(
  config: BalloonConfig,
  form: BalloonFormEntry,
  result: FinalizationResult.Success,
  entries: List<SubmissionFieldEntryDef>,
  submissionId: String = RecordIds.newId(),
  clientId: String,
  baseSeq: Long,
  nowEpochSeconds: Long,
): Pair<MutateDataRequest, String> {
  val nowInstant = ofEpochSecond(nowEpochSeconds, 0L)
  val collectorName = config.collectorName.ifBlank { "Google Earth Collector" }
  val submissionMutation =
    DataMutation(
      mutation_id = "mut-sub-$submissionId",
      client_id = clientId,
      client_seq = baseSeq,
      client_timestamp = nowInstant,
      submission_mutation =
        SubmissionMutation(
          submission_id = submissionId,
          form_id = form.id,
          base_revision = 0L,
          upsert =
            SubmissionRecord(
              submission_id = submissionId,
              survey_id = config.surveyId,
              form_id = form.id,
              form_version = form.version,
              entity_id = config.entityId,
              entity_label = config.entityName,
              form_title = form.title,
              collector_name = collectorName,
              collector_email = config.collectorEmail,
              record = result.recordInstance,
              sync_status_key = "SYNCED",
              field_entries = entries,
              client_updated_at = nowInstant,
            ),
        ),
    )

  val saveToProps = mutableMapOf<String, String>()
  result.entityStates.forEach { entityState ->
    entityState.properties.forEach { (key, value) ->
      val formatted = formatTypedSaveToValue(value)
      if (formatted.isNotBlank()) saveToProps[key] = formatted
    }
  }
  if (saveToProps.isEmpty()) {
    entries.forEach { entry -> saveToProps[entry.question_name] = entry.answer_value }
  }

  val (fallbackSymbol, fallbackColor, fallbackStatus) =
    when (config.markerSymbol) {
      "○" -> Triple("◐", "#F9AB00", "In progress")
      else -> Triple("✓", "#1E8E3E", "Completed")
    }
  val nextSymbol = saveToProps["marker-symbol"] ?: fallbackSymbol
  val nextColor = saveToProps["marker-color"] ?: fallbackColor
  val nextStatus = saveToProps["status"] ?: fallbackStatus

  val mutations = buildList {
    add(submissionMutation)
    if (config.entityId.isNotBlank() && config.datasetId.isNotBlank()) {
      val updatedProps =
        saveToProps +
          mapOf(
            "status" to nextStatus,
            "marker-symbol" to nextSymbol,
            "marker-color" to nextColor,
          )
      add(
        DataMutation(
          mutation_id = "mut-ent-$submissionId",
          client_id = clientId,
          client_seq = baseSeq + 1L,
          client_timestamp = nowInstant,
          entity_mutation =
            EntityMutation(
              dataset_id = config.datasetId,
              entity_id = config.entityId,
              base_revision = 0L,
              upsert =
                EntityRecord(
                  properties =
                    updatedProps.mapValues { (_, value) -> TypedValue(string_value = value) }
                ),
            ),
        )
      )
    }
  }

  return MutateDataRequest(survey_id = config.surveyId, mutations = mutations) to nextSymbol
}

/** Frames a serialized protobuf payload with the 5-byte uncompressed gRPC-Web header. */
fun frameGrpcWebRequest(messageBytes: ByteArray): ByteArray {
  val framed = ByteArray(5 + messageBytes.size)
  framed[0] = 0
  framed[1] = (messageBytes.size ushr 24).toByte()
  framed[2] = (messageBytes.size ushr 16).toByte()
  framed[3] = (messageBytes.size ushr 8).toByte()
  framed[4] = messageBytes.size.toByte()
  messageBytes.copyInto(framed, destinationOffset = 5)
  return framed
}

/** Parses a binary `application/grpc-web+proto` response body for `MutateData`. */
fun parseMutateDataGrpcWebResponse(
  bodyBytes: ByteArray,
  headerGrpcStatus: String? = null,
  headerGrpcMessage: String? = null,
): GrpcWebCallOutcome<MutateDataResponse> {
  if (headerGrpcStatus != null) {
    val code = headerGrpcStatus.toIntOrNull() ?: 2
    if (code != 0) {
      return GrpcWebCallOutcome.GrpcFailure(
        code,
        headerGrpcMessage?.let(::percentDecodeUrlComponent),
      )
    }
  }

  var offset = 0
  var decodedMessage: MutateDataResponse? = null
  var trailerStatus: Int? = headerGrpcStatus?.toIntOrNull()
  var trailerMessage: String? = headerGrpcMessage?.let(::percentDecodeUrlComponent)

  while (offset + 5 <= bodyBytes.size) {
    val flags = bodyBytes[offset].toInt() and 0xFF
    val length =
      ((bodyBytes[offset + 1].toInt() and 0xFF) shl 24) or
        ((bodyBytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bodyBytes[offset + 3].toInt() and 0xFF) shl 8) or
        (bodyBytes[offset + 4].toInt() and 0xFF)
    offset += 5
    if (length < 0 || offset + length > bodyBytes.size) break
    val payload = bodyBytes.copyOfRange(offset, offset + length)
    offset += length
    if ((flags and 0x80) != 0) {
      val trailers =
        payload
          .decodeToString()
          .split("\r\n", "\n")
          .filter { ':' in it }
          .associate { line ->
            line.substringBefore(':').trim().lowercase() to line.substringAfter(':').trim()
          }
      trailerStatus = trailers["grpc-status"]?.toIntOrNull() ?: 2
      trailerMessage = trailers["grpc-message"]?.let(::percentDecodeUrlComponent)
    } else {
      decodedMessage = MutateDataResponse.ADAPTER.decode(payload)
    }
  }

  val finalStatus = trailerStatus ?: if (decodedMessage != null) 0 else 2
  if (finalStatus != 0) {
    return GrpcWebCallOutcome.GrpcFailure(finalStatus, trailerMessage)
  }
  val response =
    decodedMessage
      ?: return GrpcWebCallOutcome.GrpcFailure(2, "Empty MutateData response from server")
  return GrpcWebCallOutcome.Success(response)
}

private fun formatTypedSaveToValue(v: TypedValue): String =
  when {
    v.string_value != null -> v.string_value.orEmpty()
    v.double_value != null -> v.double_value.toString()
    v.int64_value != null -> v.int64_value.toString()
    v.int32_value != null -> v.int32_value.toString()
    v.bool_value != null -> v.bool_value.toString()
    v.date_value != null -> {
      val d = v.date_value!!
      if (d.year > 0) {
        "${d.year.toString().padStart(4, '0')}-" +
          "${d.month.toString().padStart(2, '0')}-" +
          d.day.toString().padStart(2, '0')
      } else {
        ""
      }
    }
    v.time_value != null -> formatIsoTime(v.time_value!!)
    v.timestamp_value != null -> formatIsoInstant(v.timestamp_value!!)
    v.geopoint_value != null -> "${v.geopoint_value!!.latitude} ${v.geopoint_value!!.longitude}"
    v.geoshape_value != null ->
      v.geoshape_value!!.points.joinToString("; ") { "${it.latitude} ${it.longitude}" }
    v.geotrace_value != null ->
      v.geotrace_value!!.points.joinToString("; ") { "${it.latitude} ${it.longitude}" }
    else -> ""
  }

/**
 * Built-in demo form used when `balloon-form.html` is opened directly without an embedded survey
 * `FormDef` payload.
 */
fun defaultDemoBalloonForm(): BalloonFormEntry {
  val formDef =
    FormDef(
      form_id = "plot_interpretation_form",
      title = "Plot Land Cover & Deforestation Assessment",
      version = "1",
      model =
        ModelDef(
          primary_instance =
            PrimaryInstance(
              record_schema =
                RecordSchema(
                  name = "data",
                  title = "Plot Land Cover & Deforestation Assessment",
                  fields =
                    listOf(
                      FieldDefinition(
                        name = "meta",
                        type = DataType.TYPE_MESSAGE,
                        fields =
                          listOf(FieldDefinition(name = "instanceID", type = DataType.TYPE_STRING)),
                      ),
                      FieldDefinition(name = "land_use_class", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "canopy_cover_pct", type = DataType.TYPE_INT32),
                      FieldDefinition(name = "deforestation_free", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "disturbance_drivers", type = DataType.TYPE_STRING),
                      FieldDefinition(name = "interpreter_notes", type = DataType.TYPE_STRING),
                    ),
                )
            ),
          bindings =
            listOf(
              FieldBinding(
                field_path = "/data/meta/instanceID",
                type = DataType.TYPE_STRING,
                preload = PreloadType.PRELOAD_UID,
              ),
              FieldBinding(
                field_path = "/data/land_use_class",
                type = DataType.TYPE_STRING,
                required_expression = "true()",
              ),
              FieldBinding(
                field_path = "/data/canopy_cover_pct",
                type = DataType.TYPE_INT32,
                required_expression = "true()",
                constraint_expression = ". >= 0 and . <= 100",
                constraint_message = "Enter a canopy percentage between 0 and 100.",
              ),
              FieldBinding(
                field_path = "/data/deforestation_free",
                type = DataType.TYPE_STRING,
                required_expression = "true()",
              ),
              FieldBinding(
                field_path = "/data/disturbance_drivers",
                type = DataType.TYPE_STRING,
                relevant_expression = "/data/deforestation_free != 'yes'",
              ),
              FieldBinding(
                field_path = "/data/interpreter_notes",
                type = DataType.TYPE_STRING,
              ),
            ),
        ),
      view =
        ViewDef(
          components =
            listOf(
              ViewComponent(
                control =
                  ControlDef(
                    field_ref = "/data/land_use_class",
                    type = ControlType.CONTROL_SELECT_ONE,
                    label = LabelDef(text = "IPCC Land Use Category"),
                    hint =
                      LabelDef(
                        text = "Classify the primary land use visible inside the plot boundary."
                      ),
                    choices =
                      listOf(
                        ChoiceItem(
                          value_ = "forest",
                          label = LabelDef(text = "Forest land (≥10% canopy)"),
                          properties = mapOf("color" to "#1B5E20"),
                        ),
                        ChoiceItem(
                          value_ = "agroforestry",
                          label = LabelDef(text = "Shade agroforestry / perennial crop"),
                          properties = mapOf("color" to "#689F38"),
                        ),
                        ChoiceItem(
                          value_ = "cropland",
                          label = LabelDef(text = "Annual cropland"),
                          properties = mapOf("color" to "#FBC02D"),
                        ),
                        ChoiceItem(
                          value_ = "grassland",
                          label = LabelDef(text = "Grassland / pasture"),
                          properties = mapOf("color" to "#F37C22"),
                        ),
                        ChoiceItem(
                          value_ = "wetland",
                          label = LabelDef(text = "Wetland / riparian"),
                          properties = mapOf("color" to "#0288D1"),
                        ),
                        ChoiceItem(
                          value_ = "settlement",
                          label = LabelDef(text = "Settlement / infrastructure"),
                          properties = mapOf("color" to "#D13135"),
                        ),
                      ),
                  )
              ),
              ViewComponent(
                control =
                  ControlDef(
                    field_ref = "/data/canopy_cover_pct",
                    type = ControlType.CONTROL_INPUT,
                    label = LabelDef(text = "Tree Canopy Cover (%)"),
                    hint = LabelDef(text = "Estimated crown cover from 0 to 100%."),
                  )
              ),
              ViewComponent(
                control =
                  ControlDef(
                    field_ref = "/data/deforestation_free",
                    type = ControlType.CONTROL_SELECT_ONE,
                    label = LabelDef(text = "Deforestation-Free Since 2020 Cutoff?"),
                    choices =
                      listOf(
                        ChoiceItem(
                          value_ = "yes",
                          label = LabelDef(text = "Yes — intact or established prior to 2020"),
                          properties = mapOf("color" to "#2E7D32"),
                        ),
                        ChoiceItem(
                          value_ = "no",
                          label = LabelDef(text = "No — clearing detected after 2020"),
                          properties = mapOf("color" to "#C62828"),
                        ),
                        ChoiceItem(
                          value_ = "uncertain",
                          label = LabelDef(text = "Uncertain — cloud cover / needs field check"),
                          properties = mapOf("color" to "#F9A825"),
                        ),
                      ),
                  )
              ),
              ViewComponent(
                control =
                  ControlDef(
                    field_ref = "/data/disturbance_drivers",
                    type = ControlType.CONTROL_SELECT_MULTIPLE,
                    label = LabelDef(text = "Observed Disturbance Drivers"),
                    choices =
                      listOf(
                        ChoiceItem(
                          value_ = "agricultural_expansion",
                          label = LabelDef(text = "Agricultural expansion"),
                          properties = mapOf("color" to "#F9A825"),
                        ),
                        ChoiceItem(
                          value_ = "selective_logging",
                          label = LabelDef(text = "Selective logging"),
                          properties = mapOf("color" to "#8D6E63"),
                        ),
                        ChoiceItem(
                          value_ = "fire",
                          label = LabelDef(text = "Fire / burn scar"),
                          properties = mapOf("color" to "#D84315"),
                        ),
                        ChoiceItem(
                          value_ = "road_access",
                          label = LabelDef(text = "New road or track"),
                          properties = mapOf("color" to "#5C6BC0"),
                        ),
                      ),
                  )
              ),
              ViewComponent(
                control =
                  ControlDef(
                    field_ref = "/data/interpreter_notes",
                    type = ControlType.CONTROL_INPUT,
                    appearance = "multiline",
                    label = LabelDef(text = "Interpreter Notes"),
                    hint = LabelDef(text = "Optional imagery date, sensor, or verification notes."),
                  )
              ),
            )
        ),
    )
  return BalloonFormEntry(
    id = formDef.form_id,
    title = formDef.title,
    version = formDef.version,
    formDef = formDef,
  )
}
