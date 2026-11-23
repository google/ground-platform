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
package org.openforis.collectone.v2.app.data.repository

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.auth.AuthException
import org.openforis.collectone.v2.app.client.auth.AuthenticatedCollectorProfile
import org.openforis.collectone.v2.app.client.auth.FakeAuthClient
import org.openforis.collectone.v2.app.client.auth.ScriptedAuthClient
import org.openforis.collectone.v2.app.client.auth.ScriptedAuthClient.Companion.MAYA
import org.openforis.collectone.v2.app.data.datasource.device.AuthDataSource
import org.openforis.collectone.v2.app.data.datasource.local.session.InMemorySignedInSessionStore
import org.openforis.collectone.v2.app.data.datasource.local.session.SignedInSessionStore
import org.openforis.collectone.v2.app.data.datasource.local.session.StoredSignIn
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.error.getOrNull

/**
 * The session survives relaunches through the device's store: each "launch" is a new repository
 * over a new auth client and the same [sessionStore].
 */
class AuthRepositoryImplTest {
  private val sessionStore = InMemorySignedInSessionStore()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

  @AfterTest fun cancelScope() = scope.cancel()

  /** A store like the web console's: it keeps the terms across reloads but signs nobody in. */
  private val termsOnlyStore =
    object : SignedInSessionStore by InMemorySignedInSessionStore() {
      override val restoresAccount = false
    }

  private suspend fun launch(
    client: AuthClient,
    store: SignedInSessionStore = sessionStore,
  ): AuthRepositoryImpl =
    AuthRepositoryImpl(AuthDataSource(client, scope), store, scope).apply { restoreSession() }

  @Test
  fun firstLaunch_isSignedOut() = runTest {
    val repository = launch(FakeAuthClient())

    assertFalse(repository.getSession().isSignedIn)
    assertNull(sessionStore.load())
  }

  @Test
  fun restoredSession_isNullUntilRestored() = runTest {
    val repository =
      AuthRepositoryImpl(AuthDataSource(FakeAuthClient(), scope), sessionStore, scope)
    assertNull(repository.observeRestoredSession().value)

    repository.restoreSession()

    assertEquals(repository.getSession(), repository.observeRestoredSession().value)
  }

  @Test
  fun relaunch_withAClientThatKeepsNoSession_signsBackInWithTheTermsAccepted() = runTest {
    val first = launch(FakeAuthClient())
    first.signInWithGoogle()
    first.acceptTerms()

    val session = launch(FakeAuthClient()).getSession()

    assertTrue(session.isSignedIn)
    assertTrue(session.hasAcceptedTerms)
    assertEquals(FakeAuthClient.DEFAULT_COLLECTOR_PROFILE.email, session.profile.email)
  }

  @Test
  fun signIn_returnsTheProfile() = runTest {
    val result = launch(FakeAuthClient()).signInWithGoogle()

    assertEquals(FakeAuthClient.DEFAULT_COLLECTOR_PROFILE.email, result.getOrNull()?.email)
  }

  @Test
  fun signIn_offline_failsWithANetworkError() = runTest {
    val repository = launch(FailingAuthClient(AuthException("auth/network-request-failed: x")))

    val result = repository.signInWithGoogle()

    assertEquals(DomainResult.Failure(DomainError.Network), result)
    assertFalse(repository.getSession().isSignedIn)
  }

  @Test
  fun signIn_closedByTheUser_failsAsCancelled() = runTest {
    val repository =
      launch(FailingAuthClient(AuthException("auth/popup-closed-by-user", cancelled = true)))

    assertEquals(DomainResult.Failure(DomainError.Cancelled), repository.signInWithGoogle())
  }

  @Test
  fun signIn_failingForAnotherReason_keepsTheCause() = runTest {
    val error = AuthException("auth/popup-blocked")

    val result = launch(FailingAuthClient(error)).signInWithGoogle()

    assertEquals(DomainResult.Failure(DomainError.Unknown(error)), result)
  }

  @Test
  fun relaunch_beforeAcceptingTheTerms_isSignedInWithoutThem() = runTest {
    launch(FakeAuthClient()).signInWithGoogle()

    val session = launch(FakeAuthClient()).getSession()

    assertTrue(session.isSignedIn)
    assertFalse(session.hasAcceptedTerms)
  }

  @Test
  fun signingOut_forgetsTheAccountAndTheTerms() = runTest {
    val first = launch(FakeAuthClient())
    first.signInWithGoogle()
    first.acceptTerms()

    first.signOut()

    assertFalse(first.getSession().hasAcceptedTerms)
    assertNull(sessionStore.load())
    assertFalse(launch(FakeAuthClient()).getSession().isSignedIn)
  }

  @Test
  fun offlineRelaunch_restoresTheStoredAccount() = runTest {
    sessionStore.save(stored(hasAcceptedTerms = true))
    val client = ScriptedAuthClient(serviceSession = MAYA, isServiceReachable = false)

    val session = launch(client).getSession()

    assertTrue(session.isSignedIn)
    assertTrue(session.hasAcceptedTerms)
    assertEquals(MAYA.email, client.currentUser.value?.email)
  }

  @Test
  fun backOnline_withTheSessionConfirmed_staysSignedIn() = runTest {
    sessionStore.save(stored(hasAcceptedTerms = true))
    val client = ScriptedAuthClient(serviceSession = MAYA, isServiceReachable = false)
    val repository = launch(client)

    client.reconnect()

    assertTrue(repository.getSession().isSignedIn)
    assertTrue(repository.getSession().hasAcceptedTerms)
    assertEquals(stored(hasAcceptedTerms = true), sessionStore.load())
  }

  @Test
  fun backOnline_withTheSessionEnded_signsOut() = runTest {
    sessionStore.save(stored(hasAcceptedTerms = true))
    val client = ScriptedAuthClient(serviceSession = MAYA, isServiceReachable = false)
    val repository = launch(client)

    client.reconnect(session = null)

    assertFalse(repository.getSession().isSignedIn)
    assertNull(sessionStore.load())
  }

  @Test
  fun onlineRelaunch_signedOutByTheAuthService_forgetsTheStoredAccount() = runTest {
    sessionStore.save(stored(hasAcceptedTerms = true))

    val session = launch(ScriptedAuthClient(serviceSession = null)).getSession()

    assertFalse(session.isSignedIn)
    assertNull(sessionStore.load())
  }

  @Test
  fun onlineRelaunch_asAnotherAccount_doesNotCarryOverTheTerms() = runTest {
    val someoneElse = "someone.else@example.org"
    sessionStore.save(
      stored(hasAcceptedTerms = true).copy(email = someoneElse, termsAcceptedEmail = someoneElse)
    )

    val session = launch(ScriptedAuthClient(serviceSession = MAYA)).getSession()

    assertTrue(session.isSignedIn)
    assertFalse(session.hasAcceptedTerms)
    assertEquals(MAYA.email, sessionStore.load()?.email)
  }

  @Test
  fun acceptingTheTerms_storesTheAccountThatAccepted() = runTest {
    launch(ScriptedAuthClient(serviceSession = MAYA)).acceptTerms()

    assertEquals(MAYA.email, sessionStore.load()?.termsAcceptedEmail)
  }

  @Test
  fun anotherAccountSignedIn_mustAcceptTheTermsAgain() = runTest {
    val client = ScriptedAuthClient(serviceSession = MAYA)
    val repository = launch(client)
    repository.acceptTerms()

    client.reconnect(session = ROBIN)

    assertTrue(repository.getSession().isSignedIn)
    assertEquals(ROBIN.email, repository.getSession().profile.email)
    assertFalse(repository.getSession().hasAcceptedTerms)
  }

  @Test
  fun theAccountThatAccepted_keepsItsAcceptanceAfterAnotherAccountWasSignedIn() = runTest {
    val client = ScriptedAuthClient(serviceSession = MAYA)
    val repository = launch(client)
    repository.acceptTerms()
    client.reconnect(session = ROBIN)

    client.reconnect(session = MAYA)

    assertTrue(repository.getSession().hasAcceptedTerms)
  }

  @Test
  fun backOnline_asAnotherAccount_mustAcceptTheTermsAgain() = runTest {
    sessionStore.save(stored(hasAcceptedTerms = true))
    val client = ScriptedAuthClient(serviceSession = MAYA, isServiceReachable = false)
    val repository = launch(client)

    client.reconnect(session = ROBIN)

    assertTrue(repository.getSession().isSignedIn)
    assertFalse(repository.getSession().hasAcceptedTerms)
    assertEquals(ROBIN.email, sessionStore.load()?.email)
  }

  @Test
  fun acceptingTheTerms_whileSignedOut_acceptsNothing() = runTest {
    val repository = launch(FakeAuthClient())

    repository.acceptTerms()
    repository.signInWithGoogle()

    assertFalse(repository.getSession().hasAcceptedTerms)
  }

  @Test
  fun storedAccount_keepsNoCredentials() = runTest {
    launch(ScriptedAuthClient(serviceSession = MAYA)).acceptTerms()

    assertEquals(stored(hasAcceptedTerms = true), sessionStore.load())
  }

  @Test
  fun reload_withAStoreThatDoesNotRestoreTheAccount_keepsTheTermsOfTheClientsOwnSession() =
    runTest {
      val first = launch(ScriptedAuthClient(serviceSession = MAYA), termsOnlyStore)
      first.signInWithGoogle()
      first.acceptTerms()

      val session = launch(ScriptedAuthClient(serviceSession = MAYA), termsOnlyStore).getSession()

      assertTrue(session.isSignedIn)
      assertTrue(session.hasAcceptedTerms)
    }

  @Test
  fun reload_withAStoreThatDoesNotRestoreTheAccount_beforeAcceptingTheTerms_asksForThem() =
    runTest {
      launch(ScriptedAuthClient(serviceSession = MAYA), termsOnlyStore).signInWithGoogle()

      val session = launch(ScriptedAuthClient(serviceSession = MAYA), termsOnlyStore).getSession()

      assertTrue(session.isSignedIn)
      assertFalse(session.hasAcceptedTerms)
    }

  @Test
  fun reload_withAStoreThatDoesNotRestoreTheAccount_andNoClientSession_staysSignedOut() = runTest {
    termsOnlyStore.save(stored(hasAcceptedTerms = true))
    val client = FakeAuthClient()

    val session = launch(client, termsOnlyStore).getSession()

    assertFalse(session.isSignedIn)
    assertFalse(session.hasAcceptedTerms)
    assertNull(client.currentUser.value)
    assertNull(termsOnlyStore.load())
  }

  @Test
  fun reload_withAStoreThatDoesNotRestoreTheAccount_offline_staysSignedOut() = runTest {
    termsOnlyStore.save(stored(hasAcceptedTerms = true))
    val client = ScriptedAuthClient(serviceSession = MAYA, isServiceReachable = false)

    val session = launch(client, termsOnlyStore).getSession()

    assertFalse(session.isSignedIn)
    assertNull(client.currentUser.value)
  }

  @Test
  fun signingOutAndBackIn_withAStoreThatDoesNotRestoreTheAccount_asksForTheTermsAgain() = runTest {
    val client = ScriptedAuthClient(serviceSession = MAYA)
    val repository = launch(client, termsOnlyStore)
    repository.signInWithGoogle()
    repository.acceptTerms()

    repository.signOut()
    client.reconnect(MAYA)
    val reloaded = launch(ScriptedAuthClient(serviceSession = MAYA), termsOnlyStore)

    assertTrue(repository.getSession().isSignedIn)
    assertFalse(repository.getSession().hasAcceptedTerms)
    assertFalse(reloaded.getSession().hasAcceptedTerms)
  }

  /** An [AuthClient] whose sign-in always fails with [error]. */
  private class FailingAuthClient(private val error: AuthException) : AuthClient {
    override val currentUser = MutableStateFlow<AuthenticatedCollectorProfile?>(null)

    override suspend fun signInWithGoogle(): AuthenticatedCollectorProfile = throw error

    override suspend fun signOut() = Unit

    override suspend fun idToken(forceRefresh: Boolean): String? = null
  }

  private fun stored(hasAcceptedTerms: Boolean) =
    StoredSignIn(
      email = MAYA.email,
      displayName = MAYA.displayName,
      organization = MAYA.organization,
      photoUrl = MAYA.photoUrl,
      termsAcceptedEmail = MAYA.email.takeIf { hasAcceptedTerms },
    )

  private companion object {
    val ROBIN =
      AuthenticatedCollectorProfile(
        displayName = "Robin Okafor",
        email = "robin.okafor@example.org",
        organization = "",
      )
  }
}
