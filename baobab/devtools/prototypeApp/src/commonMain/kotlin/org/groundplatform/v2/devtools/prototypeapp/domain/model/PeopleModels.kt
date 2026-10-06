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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

/**
 * Identity primitives shared by survey ACLs (`Collaborator`) and organization memberships
 * (`OrganizationMember`): invitation state, cached account profiles, and invite links.
 */

/** Mirrors `AclEntry.InvitationStatus`. */
enum class InvitationStatus(val label: String) {
  PENDING("Invited"),
  ACCEPTED("Joined"),
}

/**
 * Name and photo copied from the invitee's account when they accept the invite link, so people
 * lists can show them by name without querying the identity provider every time.
 *
 * [photoUrl] is normally an HTTPS URL. The prototype uses `avatar:<n>` placeholders, which are
 * drawn as illustrated portraits (see `ProfileAvatar`).
 */
data class CachedProfile(
  val displayName: String,
  val photoUrl: String? = null,
  val cachedOn: String = "",
)

/** Invite links and the name suggested on the (simulated) acceptance screen. */
object InviteLinks {
  const val BASE_URL = "https://ground.example.org/join/"

  fun url(token: String) = BASE_URL + token

  /** Guesses a display name from an email local part, e.g. `grace.njeri@…` → "Grace Njeri". */
  fun suggestedName(email: String): String =
    email
      .substringBefore('@')
      .split('.', '_', '-', '+')
      .filter { it.isNotBlank() }
      .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }

  /** Up to two initials from a full name or email. */
  fun initials(nameOrEmail: String): String {
    val base = if ('@' in nameOrEmail) suggestedName(nameOrEmail) else nameOrEmail
    val words = base.split(' ').filter { it.isNotBlank() }
    return when {
      words.isEmpty() -> "?"
      words.size == 1 -> words[0].take(1).uppercase()
      else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
  }
}
