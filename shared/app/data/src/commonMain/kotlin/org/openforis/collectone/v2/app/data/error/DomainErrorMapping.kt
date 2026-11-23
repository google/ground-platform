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
package org.openforis.collectone.v2.app.data.error

import kotlin.coroutines.cancellation.CancellationException
import org.openforis.collectone.v2.app.client.auth.AuthException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebTransport
import org.openforis.collectone.v2.app.client.offline.OfflineRegionException
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult

/**
 * Runs [block] and returns its value as a [DomainResult.Success], or its failure mapped by
 * [toDomainError] as a [DomainResult.Failure]. Coroutine cancellation is rethrown, never mapped.
 *
 * This is the data layer's one place that turns exceptions into [DomainError]s: repositories wrap
 * calls that can fail for user-visible reasons in it instead of catching exceptions themselves.
 */
@Suppress("TooGenericExceptionCaught") // Every failure becomes a DomainError; see toDomainError.
inline fun <T> domainResultOf(block: () -> T): DomainResult<T> =
  try {
    DomainResult.Success(block())
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    DomainResult.Failure(e.toDomainError())
  }

/**
 * The [DomainError] this failure stands for: gRPC statuses from the user data service (see
 * `google.rpc.Code`) and auth client failures by their meaning, offline region client failures as
 * the error they carry, anything else as [DomainError.Unknown].
 */
fun Throwable.toDomainError(): DomainError =
  when (this) {
    is GrpcWebException -> grpcStatusError(status) ?: DomainError.Unknown(this)
    is AuthException -> authError(this)
    is OfflineRegionException -> error
    else -> DomainError.Unknown(this)
  }

private fun grpcStatusError(status: Int): DomainError? =
  when (status) {
    GrpcWebTransport.UNAVAILABLE,
    GrpcWebTransport.DEADLINE_EXCEEDED -> DomainError.Network
    GrpcWebTransport.UNAUTHENTICATED -> DomainError.Unauthenticated
    GrpcWebTransport.PERMISSION_DENIED -> DomainError.PermissionDenied
    GrpcWebTransport.NOT_FOUND -> DomainError.NotFound
    GrpcWebTransport.ALREADY_EXISTS,
    GrpcWebTransport.ABORTED,
    GrpcWebTransport.FAILED_PRECONDITION -> DomainError.Conflict
    GrpcWebTransport.INVALID_ARGUMENT,
    GrpcWebTransport.OUT_OF_RANGE -> DomainError.Validation
    GrpcWebTransport.RESOURCE_EXHAUSTED -> DomainError.QuotaExceeded
    else -> null
  }

/** Auth client failures by their Firebase Authentication error code (`auth/<code>: <message>`). */
private fun authError(e: AuthException): DomainError {
  if (e.cancelled) return DomainError.Cancelled
  val message = e.message.orEmpty()
  fun hasCode(vararg codes: String) = codes.any { message.startsWith("auth/$it") }
  return when {
    hasCode("network-request-failed", "unavailable", "timeout") -> DomainError.Network
    hasCode("too-many-requests", "quota-exceeded") -> DomainError.QuotaExceeded
    hasCode("user-disabled", "unauthorized-domain", "operation-not-allowed") ->
      DomainError.PermissionDenied
    else -> DomainError.Unknown(e)
  }
}
