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
package org.openforis.collectone.v2.server.web

import java.util.concurrent.ConcurrentHashMap

/** Ephemeral in-progress form draft for a map entity inside a Google Earth KML balloon. */
data class StoredBalloonDraft(
  val formId: String,
  val recordBase64: String,
  val updatedAtMillis: Long,
  val expiresAtMillis: Long,
)

/**
 * In-memory draft store for sandboxed Google Earth KML balloon iframes (`Origin: null`,
 * `credentialless`), where browser `localStorage` and `sessionStorage` are blocked or wiped across
 * `<NetworkLink>` refreshes.
 */
class BalloonDraftStore(private val clockMillis: () -> Long = System::currentTimeMillis) {
  private val drafts = ConcurrentHashMap<String, StoredBalloonDraft>()

  fun saveDraft(
    surveyId: String,
    entityId: String,
    formId: String,
    recordBase64: String,
  ): Boolean {
    val cleanSurveyId = surveyId.trim()
    val cleanEntityId = entityId.trim()
    val cleanFormId = formId.trim()
    val cleanRecord = recordBase64.trim()
    if (!isValidId(cleanSurveyId) || !isValidId(cleanEntityId)) return false
    val key = keyOf(cleanSurveyId, cleanEntityId)
    return when {
      cleanRecord.isEmpty() -> {
        drafts.remove(key)
        true
      }
      !isValidId(cleanFormId) || cleanRecord.length > MAX_DRAFT_BASE64_LENGTH -> false
      else -> {
        storeDraft(key, cleanFormId, cleanRecord)
        true
      }
    }
  }

  fun getDraft(surveyId: String, entityId: String): StoredBalloonDraft? {
    val cleanSurveyId = surveyId.trim()
    val cleanEntityId = entityId.trim()
    if (cleanSurveyId.isEmpty() || cleanEntityId.isEmpty()) return null
    val now = clockMillis()
    evictExpired(now)
    val key = keyOf(cleanSurveyId, cleanEntityId)
    val entry = drafts[key]
    return when {
      entry == null -> null
      entry.expiresAtMillis <= now -> {
        drafts.remove(key, entry)
        null
      }
      else -> entry
    }
  }

  private fun isValidId(value: String): Boolean =
    value.isNotEmpty() && value.length <= MAX_ID_LENGTH

  private fun storeDraft(key: String, formId: String, recordBase64: String) {
    val now = clockMillis()
    evictExpired(now)
    if (drafts.size >= MAX_DRAFTS && !drafts.containsKey(key)) {
      drafts.keys.firstOrNull()?.let(drafts::remove)
    }
    drafts[key] =
      StoredBalloonDraft(
        formId = formId,
        recordBase64 = recordBase64,
        updatedAtMillis = now,
        expiresAtMillis = now + DRAFT_TTL_MILLIS,
      )
  }

  fun clearDraft(surveyId: String, entityId: String) {
    val cleanSurveyId = surveyId.trim()
    val cleanEntityId = entityId.trim()
    if (cleanSurveyId.isEmpty() || cleanEntityId.isEmpty()) return
    drafts.remove(keyOf(cleanSurveyId, cleanEntityId))
  }

  private fun keyOf(surveyId: String, entityId: String): String = "$surveyId::$entityId"

  private fun evictExpired(now: Long) {
    drafts.entries.removeIf { (_, v) -> v.expiresAtMillis <= now }
  }

  companion object {
    const val DRAFT_TTL_MILLIS: Long = 2 * 60 * 60 * 1000L
    private const val MAX_DRAFTS = 4096
    private const val MAX_ID_LENGTH = 128
    private const val MAX_DRAFT_BASE64_LENGTH = 262_144
  }
}
