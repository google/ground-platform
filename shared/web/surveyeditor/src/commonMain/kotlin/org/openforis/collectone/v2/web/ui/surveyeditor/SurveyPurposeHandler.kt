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
package org.openforis.collectone.v2.web.ui.surveyeditor

/** [PurposeActions] of the Survey editor: the survey's purposes and programs in its details. */
internal class SurveyPurposeHandler(private val session: SurveyDraftSession) : PurposeActions {
  override fun togglePurpose(packId: String) = session.updateDetails { d ->
    if (packId in d.purposeIds) {
      d.copy(purposeIds = d.purposeIds - packId)
    } else {
      d.copy(purposeIds = d.purposeIds + packId)
    }
  }

  override fun updateOtherPurpose(text: String) = session.updateDetails { d ->
    d.copy(otherPurpose = text)
  }

  override fun toggleProgram(programId: String) = session.updateDetails { d ->
    d.copy(
      programIds =
        if (programId in d.programIds) d.programIds - programId else d.programIds + programId
    )
  }
}
