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
package org.openforis.collectone.v2.web.domain.model

import org.openforis.collectone.v2.app.domain.model.MessageKey

/** Why the organization pages refuse a change; arguments are listed per entry. */
enum class OrganizationError : MessageKey {
  INVALID_EMAIL,
  ORGANIZATION_NOT_FOUND,
  /** Inviting a Member to the synthetic organization everyone belongs to: its name. */
  EVERYONE_ALREADY_MEMBER,
  /** The person's display name. */
  ALREADY_MEMBER,
  /** The person's email address. */
  ALREADY_INVITED,
  /** The person's display name. */
  ALREADY_REQUESTED,
  NAME_REQUIRED,
  CONCEPT_MISSING,
  CONCEPT_NOT_DRAFT,
}
