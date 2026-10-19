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
package org.openforis.collectone.v2.app.domain.model

/** The signed-in person's identity as shown across the app (drawer, sharing, audit trail). */
data class AuthProfile(val displayName: String, val email: String, val organizationName: String) {
  /** Two-letter initials for avatar badges (`"ML"` for `"Maya Lin"`; `"U"` when blank). */
  val initials: String
    get() {
      val parts = displayName.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
      return when {
        parts.isEmpty() -> "U"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts.first().first()}${parts.last().first()}".uppercase()
      }
    }
}

/**
 * Whether someone is signed in, and who. [profile] is the last known identity even when signed out,
 * so screens that preview the account (sign-in button, sharing owner) have a name to show.
 */
data class AuthSession(val isSignedIn: Boolean, val profile: AuthProfile)

/**
 * Signing in failed. [cancelled] is true when the user dismissed the sign-in window, which isn't
 * reported as an error.
 */
class SignInException(message: String, val cancelled: Boolean = false) : Exception(message)

/** The app role a development sign-in simulates against a user data service in development mode. */
enum class SimulatedRole {
  /** Sees and may change all sample data. */
  ADMINISTRATOR,

  /** Has access only through ownership, invitations, organization membership, and sharing. */
  REGULAR_USER,
}

/** How this build signs users in, for the development sign-in dialog. */
data class SignInMode(
  /** True in development builds, where sign-in asks which [SimulatedRole] to use. */
  val simulatesRoles: Boolean,
  /** True when signing in uses a real Google account rather than a sample user. */
  val usesRealAccounts: Boolean,
  /** The role the last development sign-in simulated (preselected next time). */
  val lastRole: SimulatedRole,
)
