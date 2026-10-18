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
package org.groundplatform.v2.app.client.auth

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeAuthClientTest {
  @Test
  fun signInWithGoogle_andSignOut_togglesSessionAndReturnsProfile() = runAuthTest {
    val authClient = FakeAuthClient()
    assertFalse(authClient.isSignedIn())

    val profile = authClient.signInWithGoogle()
    assertTrue(authClient.isSignedIn())
    assertEquals("Maya Lin", profile.displayName)
    assertEquals("maya.lin@groundplatform.org", profile.email)

    assertEquals(profile, authClient.currentUser.value)
    assertNull(authClient.idToken())

    authClient.signOut()
    assertFalse(authClient.isSignedIn())
    assertNull(authClient.currentUser.value)
  }
}

class FakeAuthClientDevRoleTest {
  @Test
  fun devRoles_signInAsTheirPersonas_andNameThemInHeaders() = runAuthTest {
    val client = FakeAuthClient(supportsDevRoles = true)
    client.setDevRole(DevRole.USER)

    assertEquals("Ima User", client.signInWithGoogle().displayName)
    assertEquals(
      mapOf(
        "x-dev-user-id" to "uid-ima-user",
        "x-dev-user-email" to "ima.user@example.org",
        "x-dev-user-name" to "Ima User",
        "x-dev-role" to "user",
      ),
      client.requestHeaders(),
    )

    client.signOut()
    assertTrue(client.requestHeaders().isEmpty())
    client.setDevRole(DevRole.ADMIN)
    assertEquals("Adam Admin", client.signInWithGoogle().displayName)
    assertEquals("uid-adam-admin", client.requestHeaders()["x-dev-user-id"])
    assertEquals("admin", client.requestHeaders()["x-dev-role"])
  }

  @Test
  fun withoutDevRoles_signsInAsTheDevelopmentUser_withoutHeaders() = runAuthTest {
    val client = FakeAuthClient()

    assertEquals("Maya Lin", client.signInWithGoogle().displayName)
    assertTrue(client.requestHeaders().isEmpty())
  }
}

/** Runs a test that calls suspending auth APIs; the fake never actually suspends. */
private fun runAuthTest(block: suspend () -> Unit) {
  var failure: Throwable? = null
  block.startCoroutine(
    Continuation(EmptyCoroutineContext) { result -> failure = result.exceptionOrNull() }
  )
  failure?.let { throw it }
}
