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
 * The outcome of a repository or use case operation that can fail for a reason the user should hear
 * about: its [Success.value], or a [Failure.error].
 *
 * Used instead of `kotlin.Result` because that one fails with an untyped `Throwable` (callers can't
 * handle each [DomainError] exhaustively with `when`), and `runCatching` also catches coroutine
 * cancellation.
 */
sealed interface DomainResult<out T> {
  data class Success<out T>(val value: T) : DomainResult<T>

  data class Failure(val error: DomainError) : DomainResult<Nothing>
}

/** The value, or [onFailure]'s result for the error. */
inline fun <T> DomainResult<T>.getOrElse(onFailure: (DomainError) -> T): T =
  when (this) {
    is DomainResult.Success -> value
    is DomainResult.Failure -> onFailure(error)
  }

/** The value, or `null` on failure. */
fun <T> DomainResult<T>.getOrNull(): T? = getOrElse { null }

/** This result with its value transformed by [transform]; failures are passed through. */
inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> =
  when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(value))
    is DomainResult.Failure -> this
  }
