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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class KeyValueSignedInSessionStoreTest {
  private val storage = MapKeyValueStorage()
  private val store = KeyValueSignedInSessionStore(storage)

  @Test fun nothingStored_loadsNull() = runTest { assertNull(store.load()) }

  @Test
  fun savedSignIn_loadsBack() = runTest {
    store.save(MAYA)

    assertEquals(MAYA, store.load())
  }

  @Test
  fun savedSignIn_withoutOptionalFields_loadsBack() = runTest {
    val signIn = MAYA.copy(photoUrl = null, termsAcceptedEmail = null)

    store.save(signIn)

    assertEquals(signIn, store.load())
  }

  @Test
  fun aNewStoreOverTheSameStorage_loadsTheSavedSignIn() = runTest {
    store.save(MAYA)

    assertEquals(MAYA, KeyValueSignedInSessionStore(storage).load())
  }

  @Test
  fun clearing_forgetsTheSignIn() = runTest {
    store.save(MAYA)

    store.clear()

    assertNull(store.load())
    assertTrue(storage.values.isEmpty())
  }

  @Test
  fun saving_replacesTheStoredSignIn() = runTest {
    store.save(MAYA)
    val other = MAYA.copy(email = "robin@example.org", termsAcceptedEmail = null)

    store.save(other)

    assertEquals(other, store.load())
  }

  @Test
  fun keepsTheSignInUnderItsKey() = runTest {
    val keyed = KeyValueSignedInSessionStore(storage, key = "custom")

    keyed.save(MAYA)

    assertEquals(setOf("custom"), storage.values.keys)
    assertNull(store.load())
  }

  @Test
  fun anUnreadableValue_countsAsNothingStored() = runTest {
    listOf("not json", "[]", "{}", """{"email":""}""", """{"email":{"nested":1}}""").forEach {
      storage.set(KeyValueSignedInSessionStore.DEFAULT_KEY, it)

      assertNull(store.load(), it)
    }
  }

  @Test
  fun aValueWithUnknownFields_stillLoads() = runTest {
    storage.set(
      KeyValueSignedInSessionStore.DEFAULT_KEY,
      """{"email":"maya@example.org","displayName":"Maya","future":true}""",
    )

    assertEquals(
      StoredSignIn(email = "maya@example.org", displayName = "Maya", organization = ""),
      store.load(),
    )
  }

  @Test
  fun keepsNoCredentials() = runTest {
    store.save(MAYA)

    val json = storage.values.getValue(KeyValueSignedInSessionStore.DEFAULT_KEY)
    assertFalse(json.contains("token", ignoreCase = true))
  }

  @Test
  fun restoresTheAccount_unlessToldNotTo() {
    assertTrue(KeyValueSignedInSessionStore(storage).restoresAccount)
    assertFalse(KeyValueSignedInSessionStore(storage, restoresAccount = false).restoresAccount)
  }

  private class MapKeyValueStorage : KeyValueStorage {
    val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun set(key: String, value: String) {
      values[key] = value
    }

    override fun remove(key: String) {
      values.remove(key)
    }
  }

  private companion object {
    val MAYA =
      StoredSignIn(
        email = "maya@example.org",
        displayName = "Maya Lin",
        organization = "Example Org",
        photoUrl = "https://example.org/maya.png",
        termsAcceptedEmail = "maya@example.org",
      )
  }
}
