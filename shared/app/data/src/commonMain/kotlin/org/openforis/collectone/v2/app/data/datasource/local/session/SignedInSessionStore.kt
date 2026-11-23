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
package org.openforis.collectone.v2.app.data.datasource.local.session

/**
 * The account last signed in on this device, kept so the app can relaunch offline straight into the
 * app as that account. Only what the app shows and needs to identify the account: no tokens or
 * other credentials.
 */
data class StoredSignIn(
  val email: String,
  val displayName: String,
  val organization: String,
  val photoUrl: String? = null,
  /**
   * The email of the account that accepted the Terms of Service, or `null` when nobody did since
   * signing in. The acceptance only applies to that account: another one must accept again.
   */
  val termsAcceptedEmail: String? = null,
)

/** Keeps the [StoredSignIn] between launches. */
interface SignedInSessionStore {
  /**
   * Whether a launch without a session of the auth client's own signs back in as the stored account
   * (so the mobile app relaunches offline straight into the app). False for stores that only keep
   * the Terms of Service acceptance of the auth client's own session across page reloads (the web
   * console, which is always online): there, the auth client alone decides who is signed in.
   */
  val restoresAccount: Boolean
    get() = true

  /** The stored sign-in, or null when nobody is signed in on this device. */
  suspend fun load(): StoredSignIn?

  /** Replaces the stored sign-in with [signIn]. */
  suspend fun save(signIn: StoredSignIn)

  /** Forgets the stored sign-in (signing out). */
  suspend fun clear()
}

/**
 * [SignedInSessionStore] that keeps the sign-in only while the app runs, for hosts without a local
 * database (the web console, tests).
 */
class InMemorySignedInSessionStore(private var stored: StoredSignIn? = null) :
  SignedInSessionStore {
  override suspend fun load(): StoredSignIn? = stored

  override suspend fun save(signIn: StoredSignIn) {
    stored = signIn
  }

  override suspend fun clear() {
    stored = null
  }
}
