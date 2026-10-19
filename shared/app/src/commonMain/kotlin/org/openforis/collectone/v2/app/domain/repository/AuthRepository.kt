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
   * Signs in with Google and returns the authenticated profile, simulating [role] in development
   * builds ([SignInMode.simulatesRoles]); throws
   * [org.openforis.collectone.v2.app.domain.model.SignInException] if sign-in fails or is
   * cancelled.
   */
  suspend fun signInWithGoogle(role: SimulatedRole? = null): AuthProfile

  /** How this build signs in: whether it simulates roles, and with which kind of account. */
  fun signInMode(): SignInMode

  suspend fun signOut()
}
