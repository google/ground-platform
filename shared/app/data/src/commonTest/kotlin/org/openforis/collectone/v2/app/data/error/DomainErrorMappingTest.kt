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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.openforis.collectone.v2.app.client.auth.AuthException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult

class DomainErrorMappingTest {
  private fun grpcError(status: Int) = GrpcWebException(status, "/Service/Method", "detail")

  @Test
  fun grpcStatuses_mapToTheirMeaning() {
    // google.rpc.Code values.
    val expected =
      mapOf(
        14 to DomainError.Network, // UNAVAILABLE
        4 to DomainError.Network, // DEADLINE_EXCEEDED
        16 to DomainError.Unauthenticated,
        7 to DomainError.PermissionDenied,
        5 to DomainError.NotFound,
        6 to DomainError.Conflict, // ALREADY_EXISTS
        10 to DomainError.Conflict, // ABORTED
        9 to DomainError.Conflict, // FAILED_PRECONDITION
        3 to DomainError.Validation, // INVALID_ARGUMENT
        11 to DomainError.Validation, // OUT_OF_RANGE
        8 to DomainError.QuotaExceeded, // RESOURCE_EXHAUSTED
      )

    expected.forEach { (status, error) ->
      assertEquals(error, grpcError(status).toDomainError(), "status $status")
    }
  }

  @Test
  fun otherGrpcStatuses_areUnknownWithTheirCause() {
    val internal = grpcError(13)

    assertEquals(DomainError.Unknown(internal), internal.toDomainError())
  }

  @Test
  fun cancelledSignIn_isCancelled() {
    val error = AuthException("auth/popup-closed-by-user", cancelled = true)

    assertEquals(DomainError.Cancelled, error.toDomainError())
  }

  @Test
  fun authFailures_mapByFirebaseErrorCode() {
    assertEquals(
      DomainError.Network,
      AuthException("auth/network-request-failed: offline").toDomainError(),
    )
    assertEquals(
      DomainError.Network,
      AuthException("auth/unavailable: Firebase Authentication is unreachable").toDomainError(),
    )
    assertEquals(DomainError.QuotaExceeded, AuthException("auth/too-many-requests").toDomainError())
    assertEquals(DomainError.PermissionDenied, AuthException("auth/user-disabled").toDomainError())
  }

  @Test
  fun unrecognizedFailures_areUnknownWithTheirCause() {
    val popupBlocked = AuthException("auth/popup-blocked")
    val other = IllegalStateException("boom")

    assertEquals(DomainError.Unknown(popupBlocked), popupBlocked.toDomainError())
    assertEquals(DomainError.Unknown(other), other.toDomainError())
  }

  @Test
  fun domainResultOf_wrapsTheValue() {
    assertEquals(DomainResult.Success(3), domainResultOf { 1 + 2 })
  }

  @Test
  fun domainResultOf_mapsFailures() {
    val result = domainResultOf<Int> { throw grpcError(7) }

    assertEquals(DomainResult.Failure(DomainError.PermissionDenied), result)
  }

  @Test
  fun domainResultOf_rethrowsCancellation() {
    assertFailsWith<CancellationException> {
      domainResultOf<Int> { throw CancellationException("cancelled") }
    }
  }
}
