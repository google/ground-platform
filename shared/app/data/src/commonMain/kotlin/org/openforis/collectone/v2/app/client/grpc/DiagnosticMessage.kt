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
package org.openforis.collectone.v2.app.client.grpc

/**
 * Raw, untranslated text describing this failure, for diagnostics (logs and the stored detail of a
 * failed upload): the server's `grpc-message` for a [GrpcWebException], otherwise the exception
 * message, otherwise its class name. Users see the localized text of the failure's `DomainError`
 * instead (`ui/text/ErrorMessages`).
 */
fun Throwable.diagnosticMessage(): String =
  (this as? GrpcWebException)?.description ?: message ?: this::class.simpleName ?: "Unknown error"
