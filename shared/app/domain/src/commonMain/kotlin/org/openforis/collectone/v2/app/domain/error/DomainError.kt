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
package org.openforis.collectone.v2.app.domain.error

/**
 * Why an operation failed, in terms the user can act on. Repositories and use cases return it in a
 * [DomainResult.Failure]; the data layer maps transport and auth failures to it in one place, and
 * ViewModels turn it into a localized message.
 *
 * Kept small on purpose: add a case only when some caller handles it differently from the others.
 */
sealed interface DomainError {
  /** The service couldn't be reached: the device is offline, the call timed out, or it's down. */
  data object Network : DomainError

  /** The user isn't signed in, or their session has expired. */
  data object Unauthenticated : DomainError

  /** The signed-in user isn't allowed to do this. */
  data object PermissionDenied : DomainError

  /** What the operation refers to doesn't exist (any longer). */
  data object NotFound : DomainError

  /** It clashes with the current state, for example a concurrent change or an existing record. */
  data object Conflict : DomainError

  /**
   * What the change targets no longer accepts changes in its current state, for example a closed or
   * archived survey. Unlike [Conflict], trying again won't help until that state changes.
   */
  data object NotEditable : DomainError

  /** The service rejected the request's contents as invalid. */
  data object Validation : DomainError

  /** A quota or rate limit was reached. */
  data object QuotaExceeded : DomainError

  /** The user dismissed the operation (for example closed the sign-in window): not an error. */
  data object Cancelled : DomainError

  /**
   * Anything else; [cause] is kept for logging, never shown to the user. It's `null` for failures
   * restored from storage (such as an outbox entry's), which keep their raw detail separately.
   */
  data class Unknown(val cause: Throwable? = null) : DomainError
}
