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
package org.openforis.collectone.v2.server.web

import io.grpc.Status
import io.grpc.StatusException
import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class StatusMapperTest {

  @Test
  fun failedPrecondition_keepsItsMessage() {
    val status = StatusMapper.toStatusException(FailedPreconditionException("Survey is in Trash"))

    assertEquals(Status.Code.FAILED_PRECONDITION, status.status.code)
    assertEquals("Survey is in Trash", status.status.description)
  }

  @Test
  fun checkPrecondition_throwsFailedPreconditionWhenFalse() {
    checkPrecondition(true) { "unused" }

    val e = assertFailsWith<FailedPreconditionException> { checkPrecondition(false) { "Stale" } }

    assertEquals("Stale", e.message)
  }

  @Test
  fun unexpectedIllegalState_isInternalWithAGenericMessage() {
    val status =
      StatusMapper.toStatusException(IllegalStateException("Missing row in table Secrets"))

    assertEquals(Status.Code.INTERNAL, status.status.code)
    assertEquals(StatusMapper.INTERNAL_ERROR_MESSAGE, status.status.description)
  }

  @Test
  fun otherUnexpectedFailures_areInternalWithAGenericMessage() {
    val status = StatusMapper.toStatusException(NullPointerException("field x"))

    assertEquals(Status.Code.INTERNAL, status.status.code)
    assertEquals(StatusMapper.INTERNAL_ERROR_MESSAGE, status.status.description)
  }

  @Test
  fun illegalArgument_isInvalidArgumentWithItsMessage() {
    val status = StatusMapper.toStatusException(IllegalArgumentException("client_id is required"))

    assertEquals(Status.Code.INVALID_ARGUMENT, status.status.code)
    assertEquals("client_id is required", status.status.description)
  }

  @Test
  fun unsupportedOperation_isUnimplemented() {
    val status = StatusMapper.toStatusException(UnsupportedOperationException("Not yet"))

    assertEquals(Status.Code.UNIMPLEMENTED, status.status.code)
  }

  @Test
  fun statusExceptions_keepTheirStatus() {
    val thrown = StatusException(Status.PERMISSION_DENIED.withDescription("No access"))
    val runtime = Status.NOT_FOUND.withDescription("Map feature not found").asRuntimeException()

    assertSame(thrown, StatusMapper.toStatusException(thrown))
    assertEquals(Status.Code.NOT_FOUND, StatusMapper.toStatusException(runtime).status.code)
    assertEquals(
      "Map feature not found",
      StatusMapper.toStatusException(runtime).status.description,
    )
  }

  @Test
  fun reflectiveInvocationFailures_mapTheirTarget() {
    val wrapped = InvocationTargetException(FailedPreconditionException("Stale base version"))

    assertEquals(
      Status.Code.FAILED_PRECONDITION,
      StatusMapper.toStatusException(wrapped).status.code,
    )
    assertEquals(
      Status.Code.FAILED_PRECONDITION.value(),
      StatusMapper.scopeErrorCode(wrapped),
    )
  }

  @Test
  fun scopeErrorCode_reportsExpectedFailuresOnTheirScope() {
    assertEquals(
      Status.Code.FAILED_PRECONDITION.value(),
      StatusMapper.scopeErrorCode(FailedPreconditionException("Survey is in Trash")),
    )
    assertEquals(
      Status.Code.INVALID_ARGUMENT.value(),
      StatusMapper.scopeErrorCode(IllegalArgumentException("Malformed")),
    )
    assertEquals(
      Status.Code.INTERNAL.value(),
      StatusMapper.scopeErrorCode(StatusException(Status.INTERNAL)),
    )
  }

  @Test
  fun scopeErrorCode_isNullForUnexpectedFailures() {
    assertNull(StatusMapper.scopeErrorCode(IllegalStateException("bug")))
    assertNull(StatusMapper.scopeErrorCode(RuntimeException("bug")))
  }
}
