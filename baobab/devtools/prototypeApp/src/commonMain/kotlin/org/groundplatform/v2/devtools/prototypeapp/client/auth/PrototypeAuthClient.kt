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
package org.groundplatform.v2.devtools.prototypeapp.client.auth

/** Authenticated collector profile returned by [PrototypeAuthClient]. */
data class AuthenticatedCollectorProfile(
  val displayName: String,
  val email: String,
  val organization: String,
)

/**
 * External service client (`client/auth/ *Client`) simulating OAuth / Google Sign-In authentication
 * for the prototype workbench per Sections 3 & 4 of `docs/technical/client/architecture.md`.
 */
class PrototypeAuthClient {
  private var signedIn: Boolean = false
  private var profile: AuthenticatedCollectorProfile = DEFAULT_COLLECTOR_PROFILE

  fun isSignedIn(): Boolean = signedIn

  fun currentProfile(): AuthenticatedCollectorProfile = profile

  fun signInWithGoogle(
    displayName: String = DEFAULT_COLLECTOR_PROFILE.displayName,
    email: String = DEFAULT_COLLECTOR_PROFILE.email,
    organization: String = DEFAULT_COLLECTOR_PROFILE.organization,
  ): AuthenticatedCollectorProfile {
    signedIn = true
    profile =
      AuthenticatedCollectorProfile(
        displayName = displayName,
        email = email,
        organization = organization,
      )
    return profile
  }

  fun signOut() {
    signedIn = false
  }

  companion object {
    val DEFAULT_COLLECTOR_PROFILE =
      AuthenticatedCollectorProfile(
        displayName = "Maya Lin",
        email = "maya.lin@groundplatform.org",
        organization = "Open Foris • East Africa Field Team",
      )
  }
}
