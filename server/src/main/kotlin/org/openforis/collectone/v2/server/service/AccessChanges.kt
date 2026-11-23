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
package org.openforis.collectone.v2.server.service

import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.data.UserAccount
import org.openforis.collectone.v2.server.data.UserDataReader
import org.openforis.collectone.v2.server.data.UserDataWriter

/** Users whose ACL entries or memberships a transaction changes, by user ID or email. */
internal class AccessChanges {
  val userIds = HashSet<String>()
  val emails = HashSet<String>()

  /**
   * Records the principals whose grant differs between [before] and [after], each a list of (user
   * ID, email, grant) triples.
   */
  fun record(
    before: List<Triple<String, String, Any>>,
    after: List<Triple<String, String, Any>>,
  ) {
    fun key(userId: String, email: String) = email.trim().lowercase().ifEmpty { "uid:$userId" }
    val old = before.associateBy { key(it.first, it.second) }
    val new = after.associateBy { key(it.first, it.second) }
    for (k in old.keys + new.keys) {
      val o = old[k]
      val n = new[k]
      if (o == n) continue
      listOfNotNull(o, n).forEach { (userId, email) ->
        if (userId.isNotBlank()) userIds += userId
        if (email.isNotBlank()) emails += email.trim().lowercase()
      }
    }
  }
}

/** Sets [userId] on unbound ACL entries and organization invites addressed to [email]. */
internal fun UserDataWriter.bindInvites(userId: String, email: String, access: AccessChanges) {
  fun unbound(entryEmail: String, entryUserId: String) =
    entryUserId.isBlank() && entryEmail.trim().lowercase() == email
  for (surveyId in surveyIdsWithAclEmail(email)) {
    val acl = acl(surveyId) ?: continue
    if (acl.entries.none { unbound(it.email, it.user_id) }) continue
    val entries =
      acl.entries.map { if (unbound(it.email, it.user_id)) it.copy(user_id = userId) else it }
    putAclWithRecordVersion(surveyId, acl.copy(entries = entries), access)
  }
  for (organizationId in organizationIdsWithMemberEmail(email)) {
    val org = organization(organizationId) ?: continue
    if (org.members.none { unbound(it.email, it.user_id) }) continue
    val members =
      org.members.map { if (unbound(it.email, it.user_id)) it.copy(user_id = userId) else it }
    putOrganizationTracked(org.copy(members = members, version = org.version + 1L), access)
  }
}

/**
 * Increases the access epoch of every user [changes] names, and writes [pending] (a user being
 * registered in the same transaction).
 */
internal fun UserDataWriter.applyAccessChanges(
  changes: AccessChanges,
  pending: UserAccount? = null,
) {
  val affected = LinkedHashMap<String, UserAccount>()
  fun current(user: UserAccount) = if (user.userId == pending?.userId) pending else user
  changes.userIds.forEach { id ->
    val user = if (id == pending?.userId) pending else user(id)
    user?.let { affected[it.userId] = it }
  }
  changes.emails.forEach { email ->
    usersByEmail(email).forEach { affected.putIfAbsent(it.userId, current(it)) }
    if (pending != null && pending.email == email) affected.putIfAbsent(pending.userId, pending)
  }
  affected.values.forEach { putUser(it.copy(accessEpoch = it.accessEpoch + 1L)) }
  if (pending != null && pending.userId !in affected) putUser(pending)
}

/** The single verified user with normalized email [email], if known. */
internal fun UserDataReader.verifiedUserByEmail(email: String): UserAccount? =
  email
    .trim()
    .lowercase()
    .takeIf { it.isNotEmpty() }
    ?.let { e -> usersByEmail(e).filter { it.emailVerified }.singleOrNull() }

/**
 * Writes [updated], binding invites for known verified users and recording membership changes in
 * [access]. Returns the written organization.
 */
internal fun UserDataWriter.putOrganizationTracked(
  updated: Organization,
  access: AccessChanges,
): Organization {
  val bound =
    updated.copy(
      members =
        updated.members.map { m ->
          if (m.user_id.isBlank())
            verifiedUserByEmail(m.email)?.let { m.copy(user_id = it.userId) } ?: m
          else m
        }
    )
  val before = organization(updated.organization_id)?.members.orEmpty()
  access.record(
    before.map { Triple(it.user_id, it.email, it.role to it.status) },
    bound.members.map { Triple(it.user_id, it.email, it.role to it.status) },
  )
  putOrganization(bound)
  return bound
}

/** Like [putOrganizationTracked], for a survey's ACL entries. */
internal fun UserDataWriter.putAclTracked(updated: SurveyAcl, access: AccessChanges): SurveyAcl {
  val bound =
    updated.copy(
      entries =
        updated.entries.map { e ->
          if (e.user_id.isBlank())
            verifiedUserByEmail(e.email)?.let { e.copy(user_id = it.userId) } ?: e
          else e
        }
    )
  val before = acl(updated.survey_id)?.entries.orEmpty()
  access.record(
    before.map { Triple(it.user_id, it.email, it.role) },
    bound.entries.map { Triple(it.user_id, it.email, it.role) },
  )
  putAcl(bound)
  return bound
}

/**
 * Writes [acl] for survey [surveyId] with the survey's next shared record version, restamping its
 * saved revision and working copy. Returns the written saved revision and ACL.
 */
internal fun UserDataWriter.putAclWithRecordVersion(
  surveyId: String,
  acl: SurveyAcl,
  access: AccessChanges,
): Pair<SurveyDef?, SurveyAcl> {
  val recordVersion = nextSurveyRecordVersion(surveyId)
  val written = putAclTracked(acl.copy(record_version = recordVersion), access)
  val saved = survey(surveyId)?.copy(record_version = recordVersion)
  saved?.let { putSurvey(it) }
  workingCopy(surveyId)?.let { wc ->
    putWorkingCopy(surveyId, wc.copy(record_version = recordVersion))
  }
  return saved to written
}

/**
 * The caller's access epoch, carried by caller-dependent list tokens, plus the caller's identity
 * and whether the authenticator made it an administrator (so signing in or switching a simulated
 * role resyncs); empty without a caller.
 */
internal fun UserDataReader.accessEpochOf(caller: Caller?): String {
  caller ?: return ""
  val epoch = user(caller.userId)?.accessEpoch?.toString().orEmpty()
  val role = if (caller.isAdmin || caller.isDefault) "+admin" else ""
  return "${caller.userId}:$epoch$role"
}
