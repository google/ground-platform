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
package org.openforis.collectone.v2.app.data.datasource.local.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Strings kept by key, such as the browser's `localStorage`. */
interface KeyValueStorage {
  /** The value kept under [key], or null when there is none (or it can't be read). */
  fun get(key: String): String?

  /** Keeps [value] under [key], replacing what was there. */
  fun set(key: String, value: String)

  /** Forgets the value kept under [key], if any. */
  fun remove(key: String)
}

/**
 * [SignedInSessionStore] that keeps the [StoredSignIn] as JSON under [key] in a [KeyValueStorage].
 * A value that can't be read (from another version, or edited by hand) counts as nothing stored.
 */
class KeyValueSignedInSessionStore(
  private val storage: KeyValueStorage,
  override val restoresAccount: Boolean = true,
  private val key: String = DEFAULT_KEY,
) : SignedInSessionStore {
  override suspend fun load(): StoredSignIn? = storage.get(key)?.let(::decode)

  override suspend fun save(signIn: StoredSignIn) = storage.set(key, encode(signIn))

  override suspend fun clear() = storage.remove(key)

  companion object {
    /** The key the sign-in is kept under by default. */
    const val DEFAULT_KEY = "collect-one.signed-in-session"

    private const val EMAIL = "email"
    private const val DISPLAY_NAME = "displayName"
    private const val ORGANIZATION = "organization"
    private const val PHOTO_URL = "photoUrl"
    private const val TERMS_ACCEPTED_EMAIL = "termsAcceptedEmail"

    internal fun encode(signIn: StoredSignIn): String = buildJsonObject {
      put(EMAIL, signIn.email)
      put(DISPLAY_NAME, signIn.displayName)
      put(ORGANIZATION, signIn.organization)
      signIn.photoUrl?.let { put(PHOTO_URL, it) }
      signIn.termsAcceptedEmail?.let { put(TERMS_ACCEPTED_EMAIL, it) }
    }
      .toString()

    internal fun decode(json: String): StoredSignIn? = runCatching {
      val fields = Json.parseToJsonElement(json).jsonObject
      StoredSignIn(
        email = fields.string(EMAIL)?.takeIf { it.isNotBlank() } ?: return null,
        displayName = fields.string(DISPLAY_NAME).orEmpty(),
        organization = fields.string(ORGANIZATION).orEmpty(),
        photoUrl = fields.string(PHOTO_URL),
        termsAcceptedEmail = fields.string(TERMS_ACCEPTED_EMAIL),
      )
    }
      .getOrNull()

    private fun JsonObject.string(name: String): String? = runCatching {
      get(name)?.jsonPrimitive?.contentOrNull
    }
      .getOrNull()
  }
}
