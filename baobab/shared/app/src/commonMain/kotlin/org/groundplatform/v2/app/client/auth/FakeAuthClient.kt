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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The signed-in user's profile, as an [AuthClient] reports it. */
data class AuthenticatedCollectorProfile(
  val displayName: String,
  val email: String,
  val organization: String,
)

/**
 * [AuthClient] simulating Google Sign-In, for tests and for platforms not wired to Firebase yet
 * (`docs/technical/client/architecture.md`, sections 3 and 4). It has no ID tokens, so the user
 * data service serves its calls as its development user (`AUTH_MODE=dev`).
 */
class FakeAuthClient : AuthClient {
  private var profile: AuthenticatedCollectorProfile = DEFAULT_COLLECTOR_PROFILE
  private val user = MutableStateFlow<AuthenticatedCollectorProfile?>(null)

  override val currentUser: StateFlow<AuthenticatedCollectorProfile?> = user.asStateFlow()

  fun isSignedIn(): Boolean = user.value != null

  fun currentProfile(): AuthenticatedCollectorProfile = profile

  override suspend fun signInWithGoogle(): AuthenticatedCollectorProfile = signInAs()

  /** Signs in as the given profile (the sample organizer by default). */
  fun signInAs(
    displayName: String = DEFAULT_COLLECTOR_PROFILE.displayName,
    email: String = DEFAULT_COLLECTOR_PROFILE.email,
    organization: String = DEFAULT_COLLECTOR_PROFILE.organization,
  ): AuthenticatedCollectorProfile {
    profile =
      AuthenticatedCollectorProfile(
        displayName = displayName,
        email = email,
        organization = organization,
      )
    user.value = profile
    return profile
  }

  override suspend fun signOut() {
    user.value = null
  }

  override suspend fun idToken(forceRefresh: Boolean): String? = null

  companion object {
    val DEFAULT_COLLECTOR_PROFILE =
      AuthenticatedCollectorProfile(
        displayName = "Maya Lin",
        email = "maya.lin@groundplatform.org",
        organization = "Open Foris • East Africa Field Team",
      )
  }
}
