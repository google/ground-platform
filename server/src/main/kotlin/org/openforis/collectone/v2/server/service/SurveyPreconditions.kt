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

import io.grpc.Status
import io.grpc.StatusException
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.server.data.UserDataReader
import org.openforis.collectone.v2.server.web.checkPrecondition

/** Fails with `FAILED_PRECONDITION` when survey [surveyId] is in Trash or was purged. */
internal fun UserDataReader.checkNotTrashed(surveyId: String) {
  val s = survey(surveyId)
  checkPrecondition(s?.trash_time == null && s?.state != SurveyState.DELETED) {
    "Survey $surveyId is in Trash"
  }
}

/** What a closed or archived survey refuses, worded as the end of its refusal message. */
internal enum class ClosedSurveyRefusal(val consequence: String) {
  /** Design, ACL, and invitation changes. */
  EDITS("read-only. Reopen it or switch it to draft to make changes."),
  DATA_CHANGES("no longer accepts data changes."),
  PLOT_LOCKS("no longer accepts plot locks."),
}

/** Whether this survey is closed or archived, so it no longer takes changes. */
internal val SurveyDef.isClosedOrArchived: Boolean
  get() = state == SurveyState.CLOSED || state == SurveyState.ARCHIVED

/** The `FAILED_PRECONDITION` error for a change that closed or archived [survey] refuses. */
internal fun closedSurveyError(survey: SurveyDef, refusal: ClosedSurveyRefusal): StatusException {
  val stateLabel = if (survey.state == SurveyState.ARCHIVED) "archived" else "closed"
  return StatusException(
    Status.FAILED_PRECONDITION.withDescription(
      "This survey is $stateLabel and ${refusal.consequence}"
    )
  )
}

/** Fails with [closedSurveyError] when [survey] is closed or archived. */
internal fun checkNotClosed(survey: SurveyDef?, refusal: ClosedSurveyRefusal) {
  if (survey != null && survey.isClosedOrArchived) throw closedSurveyError(survey, refusal)
}
