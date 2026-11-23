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
package org.openforis.collectone.v2.app.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openforis.collectone.v2.app.data.datasource.device.AuthDataSource
import org.openforis.collectone.v2.app.data.datasource.local.session.InMemorySignedInSessionStore
import org.openforis.collectone.v2.app.data.datasource.local.session.SignedInSessionStore
import org.openforis.collectone.v2.app.data.datasource.local.session.StoredSignIn
import org.openforis.collectone.v2.app.data.error.domainResultOf
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.AuthProfile
import org.openforis.collectone.v2.app.domain.model.AuthSession
import org.openforis.collectone.v2.app.domain.model.SignInMode
import org.openforis.collectone.v2.app.domain.model.SimulatedRole
import org.openforis.collectone.v2.app.domain.repository.AuthRepository

/**
 * [AuthRepository] backed by an [AuthDataSource] (the auth client) and a [SignedInSessionStore]
 * (the account signed in on this device, kept between launches).
 *
 * The client is the authority on who is signed in. The store only fills in at startup when the
 * client has no session of its own to restore ([restoreSession]): offline, or with a client that
 * keeps none (unless the store doesn't [SignedInSessionStore.restoresAccount]). From then on the
 * store follows the client: it keeps the signed-in account and the Terms of Service acceptance, and
 * forgets both on signing out (including when the auth service ends the session once it's reachable
 * again).
 *
 * The acceptance belongs to the account that accepted: it's kept with that account's email, and
 * another account (signed in after a role switch, or restored by the auth service in place of the
 * stored one) must accept again.
 */
class AuthRepositoryImpl(
  private val dataSource: AuthDataSource = AuthDataSource(),
  private val sessionStore: SignedInSessionStore = InMemorySignedInSessionStore(),
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) : AuthRepository {
  /** The email of the account that accepted the Terms of Service since signing in, if any. */
  private val termsAcceptedEmail = MutableStateFlow<String?>(null)
  private val isRestored = MutableStateFlow(false)
  private var isRestoreStarted = false

  /** Serializes writes to [sessionStore]. */
  private val storeWrites = Mutex()

  private val session: StateFlow<AuthSession> =
    combine(dataSource.session, termsAcceptedEmail, ::sessionOf)
      .stateIn(scope, SharingStarted.Eagerly, sessionOf(dataSource.session.value, null))

  private val restoredSession: StateFlow<AuthSession?> =
    combine(session, isRestored) { current, restored -> current.takeIf { restored } }
      .stateIn(scope, SharingStarted.Eagerly, null)

  override fun observeSession(): Flow<AuthSession> = session

  override suspend fun getSession(): AuthSession = session.value

  override suspend fun restoreSession() {
    if (isRestoreStarted) return
    isRestoreStarted = true
    dataSource.awaitInitialState()
    val stored = sessionStore.load()
    val live = dataSource.session.value
    termsAcceptedEmail.value =
      when {
        // Applies only if the client's account is the one that accepted (sessionOf).
        live.isSignedIn -> stored?.termsAcceptedEmail
        stored != null &&
          sessionStore.restoresAccount &&
          dataSource.restoreSession(stored.profile()) -> stored.termsAcceptedEmail
        else -> null
      }
    // From now on the store follows the signed-in account.
    dataSource.session.onEach(::store).launchIn(scope)
    isRestored.value = true
  }

  override fun observeRestoredSession(): StateFlow<AuthSession?> = restoredSession

  override suspend fun acceptTerms() {
    val current = dataSource.session.value
    if (!current.isSignedIn) return
    termsAcceptedEmail.value = current.profile.email
    store(current)
  }

  override suspend fun signInWithGoogle(role: SimulatedRole?): DomainResult<AuthProfile> =
    domainResultOf {
      dataSource.signInWithGoogle(role)
    }

  override fun signInMode(): SignInMode = dataSource.signInMode()

  override fun setSimulatedRole(role: SimulatedRole) = dataSource.setSimulatedRole(role)

  override suspend fun idToken(forceRefresh: Boolean): String? = dataSource.idToken(forceRefresh)

  override fun requestHeaders(): Map<String, String> = dataSource.requestHeaders()

  override suspend fun signOut() = dataSource.signOut()

  /** Keeps [current] in the store, or forgets the stored account (and terms) when signed out. */
  private suspend fun store(current: AuthSession) = storeWrites.withLock {
    if (current.isSignedIn) {
      sessionStore.save(current.profile.toStored(termsAcceptedEmail.value))
    } else {
      termsAcceptedEmail.value = null
      sessionStore.clear()
    }
  }

  private fun sessionOf(current: AuthSession, termsAcceptedEmail: String?) =
    current.copy(
      hasAcceptedTerms = current.isSignedIn && termsAcceptedEmail == current.profile.email
    )

  private fun StoredSignIn.profile() =
    AuthProfile(
      displayName = displayName,
      email = email,
      organizationName = organization,
      photoUrl = photoUrl,
    )

  private fun AuthProfile.toStored(termsAcceptedEmail: String?) =
    StoredSignIn(
      email = email,
      displayName = displayName,
      organization = organizationName,
      photoUrl = photoUrl,
      termsAcceptedEmail = termsAcceptedEmail,
    )
}
