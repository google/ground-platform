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
package org.openforis.collectone.v2.app.data.datasource.device

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.AuthenticatedCollectorProfile
import org.openforis.collectone.v2.app.client.auth.DevRole
import org.openforis.collectone.v2.app.client.auth.FakeAuthClient
import org.openforis.collectone.v2.app.domain.model.AuthProfile
import org.openforis.collectone.v2.app.domain.model.AuthSession
import org.openforis.collectone.v2.app.domain.model.SignInMode
import org.openforis.collectone.v2.app.domain.model.SimulatedRole

/**
 * Device data source wrapping the [AuthClient]: the only code that talks to it. Maps the client's
 * profile type to the domain's [AuthProfile] and republishes the client's signed-in user as an
 * [AuthSession] [StateFlow] so repositories can observe it.
 */
class AuthDataSource(
  private val client: AuthClient = FakeAuthClient(),
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) {
  /** The last signed-in profile, shown (for example on the sign-in screen) after signing out. */
  private var lastProfile: AuthProfile =
    (client as? FakeAuthClient)?.currentProfile()?.toDomain() ?: SIGNED_OUT_PROFILE

  val session: StateFlow<AuthSession> =
    client.currentUser
      .map(::sessionOf)
      .stateIn(scope, SharingStarted.Eagerly, sessionOf(client.currentUser.value))

  /**
   * Signs in, first setting the simulated [role] when the client supports development roles; throws
   * the client's [org.openforis.collectone.v2.app.client.auth.AuthException] if sign-in fails.
   */
  suspend fun signInWithGoogle(role: SimulatedRole? = null): AuthProfile {
    if (role != null && client.supportsDevRoles) client.setDevRole(role.toDevRole())
    return client.signInWithGoogle().toDomain()
  }

  /** Sets the simulated [role] when the client supports development roles. */
  fun setSimulatedRole(role: SimulatedRole) {
    if (client.supportsDevRoles) client.setDevRole(role.toDevRole())
  }

  suspend fun signOut() = client.signOut()

  /** Bearer ID token of the currently signed-in user, if any. */
  suspend fun idToken(forceRefresh: Boolean = false): String? = client.idToken(forceRefresh)

  /** Extra request headers (such as `x-dev-role`) configured by the auth client. */
  fun requestHeaders(): Map<String, String> = client.requestHeaders()

  /** Waits until [session] reflects the session the client restored at startup, if any. */
  suspend fun awaitInitialState() = client.awaitInitialState()

  /**
   * Signs back in as [profile] when the client couldn't restore a session of its own; returns
   * whether [session] is now signed in as it. See [AuthClient.restoreSession].
   */
  suspend fun restoreSession(profile: AuthProfile): Boolean =
    client.restoreSession(
      AuthenticatedCollectorProfile(
        displayName = profile.displayName,
        email = profile.email,
        organization = profile.organizationName,
        photoUrl = profile.photoUrl,
        devRole = client.devRole,
      )
    )

  fun signInMode(): SignInMode =
    SignInMode(
      simulatesRoles = client.supportsDevRoles,
      usesRealAccounts = client.usesRealAccounts,
      lastRole =
        if (client.devRole == DevRole.USER) SimulatedRole.REGULAR_USER
        else SimulatedRole.ADMINISTRATOR,
    )

  private fun SimulatedRole.toDevRole() =
    when (this) {
      SimulatedRole.ADMINISTRATOR -> DevRole.ADMIN
      SimulatedRole.REGULAR_USER -> DevRole.USER
    }

  private fun sessionOf(user: AuthenticatedCollectorProfile?): AuthSession {
    val profile = user?.toDomain()?.also { lastProfile = it } ?: lastProfile
    return AuthSession(isSignedIn = user != null, profile = profile)
  }

  private fun AuthenticatedCollectorProfile.toDomain() =
    AuthProfile(
      displayName = displayName,
      email = email,
      organizationName = organization,
      photoUrl = photoUrl,
    )

  companion object {
    private val SIGNED_OUT_PROFILE =
      AuthProfile(displayName = "", email = "", organizationName = "")
  }
}
