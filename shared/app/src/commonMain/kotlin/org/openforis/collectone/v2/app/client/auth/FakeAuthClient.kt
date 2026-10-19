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
package org.openforis.collectone.v2.app.client.auth

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
 * data service (`AUTH_MODE=dev`) serves its calls as a sample user: the development user (Maya Lin,
 * an administrator) by default, or, when [supportsDevRoles], [ADMIN_PERSONA] for [DevRole.ADMIN]
 * and [USER_PERSONA] for [DevRole.USER], named by `x-dev-user-*` headers.
 */
class FakeAuthClient(
  override val supportsDevRoles: Boolean = false,
  initialDevRole: DevRole = DevRole.ADMIN,
  private val onDevRoleChanged: (DevRole) -> Unit = {},
) : AuthClient {
  private var profile: AuthenticatedCollectorProfile = DEFAULT_COLLECTOR_PROFILE
  private val user = MutableStateFlow<AuthenticatedCollectorProfile?>(null)

  override val currentUser: StateFlow<AuthenticatedCollectorProfile?> = user.asStateFlow()

  fun isSignedIn(): Boolean = user.value != null

  fun currentProfile(): AuthenticatedCollectorProfile = profile

  override var devRole: DevRole = initialDevRole
    private set

  override fun setDevRole(role: DevRole) {
    devRole = role
    onDevRoleChanged(role)
  }

  /** The sample user the current [devRole] signs in as, when [supportsDevRoles]. */
  private val persona: Persona?
    get() =
      when {
        !supportsDevRoles -> null
        devRole == DevRole.USER -> USER_PERSONA
        else -> ADMIN_PERSONA
      }

  override suspend fun signInWithGoogle(): AuthenticatedCollectorProfile {
    val p = persona?.profile ?: DEFAULT_COLLECTOR_PROFILE
    return signInAs(p.displayName, p.email, p.organization)
  }

  override fun requestHeaders(): Map<String, String> {
    val p = persona ?: return emptyMap()
    if (!isSignedIn()) return emptyMap()
    return mapOf(
      "x-dev-user-id" to p.userId,
      "x-dev-user-email" to p.profile.email,
      "x-dev-user-name" to p.profile.displayName,
      DEV_ROLE_HEADER to devRole.header,
    )
  }

  /** A sample user of development sign-in, identified to the service by [userId]. */
  data class Persona(val userId: String, val profile: AuthenticatedCollectorProfile)

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

    /** Administrator a [DevRole.ADMIN] development sign-in uses. */
    val ADMIN_PERSONA =
      Persona(
        userId = "uid-adam-admin",
        profile =
          AuthenticatedCollectorProfile(
            displayName = "Adam Admin",
            email = "adam.admin@example.org",
            organization = "Platform administrator",
          ),
      )

    /**
     * Regular user a [DevRole.USER] development sign-in uses: a data collector on the Kenya sample
     * survey and a member of the Kenya Forest Service sample organization.
     */
    val USER_PERSONA =
      Persona(
        userId = "uid-ima-user",
        profile =
          AuthenticatedCollectorProfile(
            displayName = "Ima User",
            email = "ima.user@example.org",
            organization = "Kenya Forest Service",
          ),
      )
  }
}
