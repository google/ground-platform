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
import io.grpc.StatusRuntimeException
import java.lang.reflect.InvocationTargetException
import org.slf4j.LoggerFactory

/**
 * A well-formed request that the current data doesn't allow, such as a stale base version or a
 * survey in Trash. Sent as `FAILED_PRECONDITION` with its message.
 *
 * It extends [IllegalStateException] so callers that catch state failures keep working, but only
 * this type maps to `FAILED_PRECONDITION`: any other [IllegalStateException] (a failed `check()` or
 * `error()`) is a server bug and is sent as `INTERNAL`.
 */
class FailedPreconditionException(message: String) : IllegalStateException(message)

/** Throws [FailedPreconditionException] with [lazyMessage] when [value] is false. */
inline fun checkPrecondition(value: Boolean, lazyMessage: () -> String) {
  if (!value) throw FailedPreconditionException(lazyMessage())
}

/**
 * Maps failures of RPC handlers to gRPC statuses, for whole calls ([toStatusException]) and for
 * single `SyncChanges` scopes ([scopeErrorCode]):
 * - [StatusException] and [StatusRuntimeException] keep their status.
 * - [FailedPreconditionException] becomes `FAILED_PRECONDITION`.
 * - [IllegalArgumentException] becomes `INVALID_ARGUMENT`.
 * - [UnsupportedOperationException] becomes `UNIMPLEMENTED`.
 * - Anything else is unexpected: it's logged and sent as `INTERNAL` with [INTERNAL_ERROR_MESSAGE],
 *   so internal details don't reach clients.
 */
object StatusMapper {
  /** The description sent with unexpected failures. */
  const val INTERNAL_ERROR_MESSAGE = "Internal server error"

  private val logger = LoggerFactory.getLogger(StatusMapper::class.java)

  /** The status exception to fail a call with for [e]. */
  fun toStatusException(e: Throwable): StatusException {
    val cause = unwrap(e)
    return when (cause) {
      is StatusException -> cause
      is StatusRuntimeException -> StatusException(cause.status, cause.trailers)
      else -> {
        val code = expectedCodeOf(cause)
        if (code == null) {
          logger.error("Unexpected failure serving an RPC", cause)
          Status.INTERNAL.withDescription(INTERNAL_ERROR_MESSAGE).withCause(cause).asException()
        } else {
          Status.fromCode(code).withDescription(cause.message).withCause(cause).asException()
        }
      }
    }
  }

  /**
   * The `google.rpc.Code` value to report [e] with on its `SyncChanges` scope, or null for an
   * unexpected failure, which fails the whole call (through [toStatusException]).
   */
  fun scopeErrorCode(e: Throwable): Int? = expectedCodeOf(unwrap(e))?.value()

  private fun unwrap(e: Throwable): Throwable =
    (e as? InvocationTargetException)?.targetException ?: e

  private fun expectedCodeOf(cause: Throwable): Status.Code? =
    when (cause) {
      is StatusException -> cause.status.code
      is StatusRuntimeException -> cause.status.code
      is FailedPreconditionException -> Status.Code.FAILED_PRECONDITION
      is IllegalArgumentException -> Status.Code.INVALID_ARGUMENT
      is UnsupportedOperationException -> Status.Code.UNIMPLEMENTED
      else -> null
    }
}
