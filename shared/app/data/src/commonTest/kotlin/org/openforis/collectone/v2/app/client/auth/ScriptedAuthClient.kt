/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.client.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An [AuthClient] with real accounts whose auth service keeps sessions, like Firebase, and can be
 * unreachable: starting with [isServiceReachable] false, it has no session of its own and accepts a
 * restored one; [reconnect] then reports the service's session, which replaces the restored one.
 */
class ScriptedAuthClient(
  /** The session the auth service keeps for this device, or null when signed out there. */
  private var serviceSession: AuthenticatedCollectorProfile? = null,
  private var isServiceReachable: Boolean = true,
) : AuthClient {
  private val user = MutableStateFlow(serviceSession.takeIf { isServiceReachable })

  override val currentUser: StateFlow<AuthenticatedCollectorProfile?> = user.asStateFlow()

  override val usesRealAccounts: Boolean = true

  override suspend fun restoreSession(profile: AuthenticatedCollectorProfile): Boolean {
    if (isServiceReachable) return false
    user.value = profile
    return true
  }

  /** The auth service becomes reachable and reports [session] (null: the session ended). */
  fun reconnect(session: AuthenticatedCollectorProfile? = serviceSession) {
    isServiceReachable = true
    serviceSession = session
    user.value = session
  }

  override suspend fun signInWithGoogle(): AuthenticatedCollectorProfile {
    val profile = checkNotNull(serviceSession) { "No account to sign in with" }
    user.value = profile
    return profile
  }

  override suspend fun signOut() {
    serviceSession = null
    user.value = null
  }

  override suspend fun idToken(forceRefresh: Boolean): String? = null

  companion object {
    val MAYA =
      AuthenticatedCollectorProfile(
        displayName = "Maya Lin",
        email = "maya.lin@example.org",
        organization = "",
        photoUrl = "https://example.org/maya.png",
      )
  }
}
