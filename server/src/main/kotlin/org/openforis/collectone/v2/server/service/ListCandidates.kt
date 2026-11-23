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
import openforis.collectone.v1.survey.SurveyDef
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.data.UserDataReader

/**
 * Surveys that may belong in [caller]'s survey list, read through the per-user indexes: owned, on
 * the ACL (by user ID or verified email), or in an organization the caller belongs to.
 * Administrators get every survey. [Access.listsSurvey] then decides between an overview and a
 * tombstone (for example, after an organization survey stops being shared with members).
 *
 * Surveys the caller lost access to through other changes (ACL entries removed, organizations left)
 * aren't candidates any more; those changes bump the caller's access epoch instead, which
 * invalidates the caller's list tokens (`docs/technical/backend/spanner-schema.md`, "Access
 * Changes").
 */
internal fun UserDataReader.listCandidates(access: Access, caller: Caller?): List<SurveyDef> {
  if (caller == null || access.isAdmin) return surveys()
  val email = if (caller.emailVerified) caller.normalizedEmail else ""
  val candidates = LinkedHashMap<String, SurveyDef>()
  surveysOwnedBy(caller.userId, email).forEach { candidates[it.survey_id] = it }
  val aclSurveyIds =
    surveyIdsWithAclUser(caller.userId) +
      (if (email.isBlank()) emptyList() else surveyIdsWithAclEmail(email))
  for (id in aclSurveyIds.distinct()) {
    if (id !in candidates) survey(id)?.let { candidates[id] = it }
  }
  for (organizationId in memberOrganizationIds(caller, email)) {
    surveysInOrganization(organizationId).forEach { candidates.putIfAbsent(it.survey_id, it) }
  }
  return candidates.values.toList()
}

/** Organizations that may belong in [caller]'s organization list (all for administrators). */
internal fun UserDataReader.organizationCandidates(
  access: Access,
  caller: Caller?,
): List<Organization> {
  if (caller == null || access.isAdmin) return organizations()
  val email = if (caller.emailVerified) caller.normalizedEmail else ""
  return memberOrganizationIds(caller, email).mapNotNull(::organization)
}

/** Organizations with a membership (or invitation) for [caller]'s user ID or verified [email]. */
internal fun UserDataReader.memberOrganizationIds(caller: Caller, email: String): List<String> =
  (organizationIdsWithMemberUser(caller.userId) +
      (if (email.isBlank()) emptyList() else organizationIdsWithMemberEmail(email)))
    .distinct()
