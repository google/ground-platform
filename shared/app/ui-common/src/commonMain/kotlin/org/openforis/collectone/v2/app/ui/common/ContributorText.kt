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
package org.openforis.collectone.v2.app.ui.common

import org.openforis.collectone.v2.app.domain.model.ContributorVisibility
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SubmissionViewer
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_collector_with_email
import org.openforis.collectone.v2.app.resources.app_collector_you
import org.openforis.collectone.v2.app.resources.app_participant_identity_hidden
import org.openforis.collectone.v2.app.ui.text.UiText

/** [submission]'s contributor name as [viewer] may see it, for submission lists. */
fun collectorNameText(viewer: SubmissionViewer, submission: SubmissionPreviewItem): UiText =
  when (ContributorVisibility.resolve(viewer, submission)) {
    ContributorVisibility.HIDDEN -> UiText.Resource(Res.string.app_participant_identity_hidden)
    ContributorVisibility.YOU ->
      UiText.Resource(Res.string.app_collector_you, listOf(submission.collectorName))
    ContributorVisibility.NAMED -> UiText.Plain(submission.collectorName)
  }

/**
 * [submission]'s contributor attribution (`Name (email)`) as [viewer] may see it, for the
 * submission details.
 */
fun collectorAttributionText(viewer: SubmissionViewer, submission: SubmissionPreviewItem): UiText =
  when (ContributorVisibility.resolve(viewer, submission)) {
    ContributorVisibility.HIDDEN -> UiText.Resource(Res.string.app_participant_identity_hidden)
    ContributorVisibility.YOU ->
      UiText.Resource(Res.string.app_collector_you, listOf(submission.collectorName))
    ContributorVisibility.NAMED ->
      UiText.Resource(
        Res.string.app_collector_with_email,
        listOf(submission.collectorName, submission.collectorEmail),
      )
  }
