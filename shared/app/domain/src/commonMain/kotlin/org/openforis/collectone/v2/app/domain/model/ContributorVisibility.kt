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
package org.openforis.collectone.v2.app.domain.model

/**
 * The person looking at submissions: their [email] and whether they organize the survey the
 * submissions belong to.
 */
data class SubmissionViewer(val email: String, val isOrganizer: Boolean)

/**
 * How much of a submission's contributor identity (name and email) a viewer may see.
 *
 * Contributor identities are visible only to survey organizers and to the contributor themselves.
 */
enum class ContributorVisibility {
  /** The contributor's name and email are hidden from the viewer. */
  HIDDEN,

  /** The viewer is the contributor (and not an organizer), so they see their own name only. */
  YOU,

  /** The viewer organizes the survey, so they see the contributor's name and email. */
  NAMED;

  companion object {
    /** Resolves what [viewer] may see of the contributor of [submission]. */
    fun resolve(
      viewer: SubmissionViewer,
      submission: SubmissionPreviewItem,
    ): ContributorVisibility {
      if (viewer.isOrganizer) return NAMED
      val isOwnSubmission =
        viewer.email.isNotBlank() &&
          submission.collectorEmail.equals(viewer.email, ignoreCase = true)
      return if (isOwnSubmission) YOU else HIDDEN
    }
  }
}
