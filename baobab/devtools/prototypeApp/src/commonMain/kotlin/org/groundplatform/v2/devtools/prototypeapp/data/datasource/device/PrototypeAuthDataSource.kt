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
package org.groundplatform.v2.devtools.prototypeapp.data.datasource.device

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.groundplatform.v2.devtools.prototypeapp.client.auth.AuthenticatedCollectorProfile
import org.groundplatform.v2.devtools.prototypeapp.client.auth.PrototypeAuthClient
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AuthProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AuthSession

/**
 * Device data source wrapping [PrototypeAuthClient]: the only code that talks to the auth client.
 * Maps the client's profile type to the domain's [AuthProfile] and republishes the client's session
 * as a [StateFlow] so repositories can observe it.
 */
class PrototypeAuthDataSource(private val client: PrototypeAuthClient = PrototypeAuthClient()) {
  private val _session = MutableStateFlow(readSession())

  val session: StateFlow<AuthSession> = _session.asStateFlow()

  fun signInWithGoogle(): AuthProfile {
    val profile = client.signInWithGoogle().toDomain()
    _session.value = AuthSession(isSignedIn = true, profile = profile)
    return profile
  }

  fun signOut() {
    client.signOut()
    _session.value = readSession()
  }

  private fun readSession() =
    AuthSession(isSignedIn = client.isSignedIn(), profile = client.currentProfile().toDomain())

  private fun AuthenticatedCollectorProfile.toDomain() =
    AuthProfile(displayName = displayName, email = email, organizationName = organization)
}
