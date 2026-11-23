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

import org.openforis.collectone.v2.server.data.UserDataReader
import org.openforis.collectone.v2.server.web.FailedPreconditionException
import org.openforis.collectone.v2.server.web.checkPrecondition

/**
 * Fails with `FAILED_PRECONDITION` (via [FailedPreconditionException]) when a whole-record
 * replacement was based on a stale version. Unset base versions (`0` / blank) and new records
 * always pass.
 */
internal fun checkBaseVersion(baseVersion: Long, currentVersion: Long?, what: String) {
  if (baseVersion == 0L || currentVersion == null) return
  checkPrecondition(baseVersion == currentVersion) {
    "Stale base version for $what: based on $baseVersion, current is $currentVersion"
  }
}

internal fun checkBaseVersion(baseVersion: String, currentVersion: String?, what: String) {
  if (baseVersion.isBlank() || currentVersion == null) return
  checkPrecondition(baseVersion == currentVersion) {
    "Stale base version for $what: based on $baseVersion, current is $currentVersion"
  }
}

/**
 * The next shared record version of survey [surveyId]: one more than the highest version on its
 * saved revision, working copy, and ACL (`SurveyDef.record_version`).
 */
internal fun UserDataReader.nextSurveyRecordVersion(surveyId: String): Long =
  maxOf(
    survey(surveyId)?.record_version ?: 0L,
    workingCopy(surveyId)?.record_version ?: 0L,
    acl(surveyId)?.record_version ?: 0L,
  ) + 1L
