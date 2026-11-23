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
package org.openforis.collectone.v2.app.ui.text

import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_error_cancelled
import org.openforis.collectone.v2.app.resources.app_error_conflict
import org.openforis.collectone.v2.app.resources.app_error_network
import org.openforis.collectone.v2.app.resources.app_error_not_editable
import org.openforis.collectone.v2.app.resources.app_error_not_found
import org.openforis.collectone.v2.app.resources.app_error_permission_denied
import org.openforis.collectone.v2.app.resources.app_error_quota_exceeded
import org.openforis.collectone.v2.app.resources.app_error_unauthenticated
import org.openforis.collectone.v2.app.resources.app_error_unknown
import org.openforis.collectone.v2.app.resources.app_error_validation
import org.openforis.collectone.v2.app.resources.app_sign_in_failed

/**
 * Localized messages for [DomainError]s: the one place ViewModels turn a failure into text, so the
 * same reason reads the same everywhere.
 */
object ErrorMessages {
  /** Why the operation failed, as a sentence that tells the user what to do next. */
  fun reason(error: DomainError): UiText =
    when (error) {
      DomainError.Network -> Res.string.app_error_network
      DomainError.Unauthenticated -> Res.string.app_error_unauthenticated
      DomainError.PermissionDenied -> Res.string.app_error_permission_denied
      DomainError.NotFound -> Res.string.app_error_not_found
      DomainError.Conflict -> Res.string.app_error_conflict
      DomainError.NotEditable -> Res.string.app_error_not_editable
      DomainError.Validation -> Res.string.app_error_validation
      DomainError.QuotaExceeded -> Res.string.app_error_quota_exceeded
      DomainError.Cancelled -> Res.string.app_error_cancelled
      is DomainError.Unknown -> Res.string.app_error_unknown
    }.toUiText()

  /** The notice shown when signing in fails with [error]. */
  fun signInFailed(error: DomainError): UiText =
    Res.string.app_sign_in_failed.toUiText(reason(error))
}
