/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.core.ids

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * IDs of the records clients create, often offline: surveys, organizations, map features
 * (entities), submissions, and impact events. They're random (version 4) UUIDs, so clients never
 * wait for the server to assign one and two devices can't pick the same ID
 * (`docs/technical/backend/spanner-schema.md`, "Client-Generated UUID Keys").
 */
object RecordIds {
  /** A new random record ID in canonical lowercase form. */
  @OptIn(ExperimentalUuidApi::class) fun newId(): String = Uuid.random().toString()
}
