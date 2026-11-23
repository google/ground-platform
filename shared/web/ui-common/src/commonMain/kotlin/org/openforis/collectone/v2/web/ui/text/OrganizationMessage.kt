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
package org.openforis.collectone.v2.web.ui.text

import org.jetbrains.compose.resources.StringResource

/**
 * User-facing text produced by the organization pages' logic (notices and validation errors),
 * resolved by the UI layer.
 */
sealed interface OrganizationMessage {
  /** A string [resource] formatted with [args]. */
  data class Resource(val resource: StringResource, val args: List<Any> = emptyList()) :
    OrganizationMessage

  /** Text that's already resolved, from shared validators that still return plain strings. */
  data class Text(val text: String) : OrganizationMessage
}

/** An [OrganizationMessage] for [resource] formatted with [args]. */
fun organizationMessage(resource: StringResource, vararg args: Any): OrganizationMessage =
  OrganizationMessage.Resource(resource, args.toList())
