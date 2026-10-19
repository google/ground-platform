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

import kotlinx.coroutines.flow.StateFlow

/**
 * External auth service client (`client/auth/ *Client`): signs the user in and issues the ID tokens
 * the user data service authenticates calls with (`Authorization: Bearer <token>`).
 *
 * [FirebaseWebAuthClient] signs in with Firebase Authentication in browsers; [FakeAuthClient]
 * simulates sign-in where Firebase isn't configured (tests, and platforms not wired to Firebase
 * yet), and its calls run as the user data service's development user.
 */
interface AuthClient {
  /** The signed-in user, or null when signed out. */
  val currentUser: StateFlow<AuthenticatedCollectorProfile?>

  /** Signs in with a Google account; throws [AuthException] if sign-in fails. */
  suspend fun signInWithGoogle(): AuthenticatedCollectorProfile

  suspend fun signOut()

  /**
   * An ID token for the signed-in user, or null when signed out or when the client has no tokens
   * ([FakeAuthClient]). [forceRefresh] fetches a new one even if the cached token hasn't expired.
   */
  suspend fun idToken(forceRefresh: Boolean = false): String?

  /**
   * Suspends until [currentUser] reflects the session restored at startup (for example from the
   * browser's storage), so apps don't flash the sign-in screen for signed-in users.
   */
  suspend fun awaitInitialState() {}

  /**
   * True in development builds, where sign-in simulates a [DevRole] against a user data service in
   * development mode (`AUTH_MODE=dev`).
   */
  val supportsDevRoles: Boolean
    get() = false

  /** True when signing in uses a real account (Firebase) rather than a sample one. */
  val usesRealAccounts: Boolean
    get() = false

  /** The role calls simulate, when [supportsDevRoles]; it persists where the platform allows. */
  val devRole: DevRole
    get() = DevRole.ADMIN

  /** Sets the role the next sign-in and calls simulate, when [supportsDevRoles]. */
  fun setDevRole(role: DevRole) {}

  /** Headers every user data service call sends besides the ID token (development roles). */
  fun requestHeaders(): Map<String, String> = emptyMap()
}

/**
 * Role a development sign-in simulates. [header] is the user data service's `x-dev-role` value
 * ([DEV_ROLE_HEADER]).
 */
enum class DevRole(val header: String) {
  /** An administrator, who sees and may change all (sample) data. */
  ADMIN("admin"),

  /** A regular user, with access only through ownership, invitations, and sharing. */
  USER("user"),
}

/** Header carrying the [DevRole] a call simulates (`AUTH_MODE=dev` only). */
const val DEV_ROLE_HEADER = "x-dev-role"

/**
 * Sign-in failed. [cancelled] is true when the user dismissed the sign-in window, which callers
 * don't report as an error.
 */
class AuthException(message: String, val cancelled: Boolean = false) : Exception(message)

/** The platform's auth client when one is configured (Firebase in browsers), otherwise null. */
expect fun platformAuthClient(): AuthClient?

/** Whether this build offers development sign-in with a simulated [DevRole]. */
expect fun platformDevSignIn(): Boolean

/** [platformAuthClient], or a [FakeAuthClient] when the platform has none. */
fun defaultAuthClient(): AuthClient =
  platformAuthClient() ?: FakeAuthClient(supportsDevRoles = platformDevSignIn())
