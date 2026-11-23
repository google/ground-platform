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
package org.openforis.collectone.v2.web.ui.surveys

import org.openforis.collectone.v2.web.ui.surveyoutcome.SurveyOutcomeCardState

/**
 * Screen state of the "Was this data used?" prompt on the web surveys page: which closed surveys
 * show the badge, and the card opened from one.
 */
data class SurveyOutcomePromptUiState(
  /** Closed surveys the signed-in user manages whose outcome question is due again. */
  val dueSurveyIds: Set<String> = emptySet(),
  /** The card opened from a badge, or `null`. */
  val card: SurveyOutcomeCardState? = null,
)
