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
package org.groundplatform.v2.devtools.prototypeapp.formeditor

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Generates XForms form IDs (the primary instance root `id` attribute, XLSForm `form_id`) for new
 * Forms created in this client.
 *
 * Form IDs are internal and hidden from users, who identify Forms by title. An ID is assigned once
 * when a Form is created and never regenerated, because ODK servers treat a changed ID as a
 * different form. The `form_` prefix keeps IDs starting with a letter, as ODK recommends, and
 * avoids characters such as `:` that are unsafe in filenames and URLs.
 *
 * Only Form creation calls this generator. All other code treats form IDs as opaque strings, so IDs
 * from imported XForms are kept exactly as authored.
 */
object FormIds {
  const val PREFIX = "form_"

  @OptIn(ExperimentalUuidApi::class) fun newFormId(): String = PREFIX + Uuid.random().toString()
}
