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
package org.openforis.collectone.v2.app.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.AuthProfile
import org.openforis.collectone.v2.app.domain.model.AuthSession
import org.openforis.collectone.v2.app.domain.model.SignInMode
import org.openforis.collectone.v2.app.domain.model.SimulatedRole

/** The signed-in account. Backed by the platform's auth client through a device data source. */
interface AuthRepository {
  /** Emits the current session and every change to it. */
  fun observeSession(): Flow<AuthSession>

  suspend fun getSession(): AuthSession

  /**
   * Restores the session at startup: the auth client's own session, or else the account last signed
   * in on this device (with its Terms of Service acceptance), so the app can relaunch offline
   * straight into the app. Afterwards the device keeps the signed-in account until signing out.
   * Runs once; later calls return right away.
   */
  suspend fun restoreSession() {}

  /** The current session once [restoreSession] has finished, or null until then. */
  fun observeRestoredSession(): StateFlow<AuthSession?>

  /** Records that the signed-in user accepted the Terms of Service, until they sign out. */
  suspend fun acceptTerms() {}

  /**
   * Signs in with Google and returns the authenticated profile, simulating [role] in development
   * builds ([SignInMode.simulatesRoles]). Fails with [DomainError.Cancelled] when the user closes
   * the sign-in window, or with why sign-in failed.
   */
  suspend fun signInWithGoogle(role: SimulatedRole? = null): DomainResult<AuthProfile>

  /** How this build signs in: whether it simulates roles, and with which kind of account. */
  fun signInMode(): SignInMode

  /** Sets the simulated [role] in development builds ([SignInMode.simulatesRoles]). */
  fun setSimulatedRole(role: SimulatedRole) {}

  /**
   * Bearer ID token of the currently signed-in account, or `null` when signed out or using fake
   * development sign-in.
   */
  suspend fun idToken(forceRefresh: Boolean = false): String? = null

  /** Extra request headers sent to the backend (such as `x-dev-role` in development builds). */
  fun requestHeaders(): Map<String, String> = emptyMap()

  suspend fun signOut()
}
