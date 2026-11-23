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
package org.openforis.collectone.v2.core.library

import openforis.collectone.v1.library.FormTemplateDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LibraryStatus

/**
 * Keeps `FormTemplateDef.version` increasing: a template whose title, description, form, or default
 * dataset changed gets the next version, and an unchanged one never goes back to an older version.
 */
object TemplateVersioning {
  /**
   * The version template [next] should have, given the stored [previous] version of it (`null` for
   * a new template). Versions start at 1.
   */
  fun nextVersion(previous: FormTemplateDef?, next: FormTemplateDef): Int {
    val proposed = next.version.coerceAtLeast(1)
    if (previous == null) return proposed
    val stored = previous.version.coerceAtLeast(1)
    return if (content(previous) == content(next)) maxOf(proposed, stored)
    else maxOf(proposed, stored + 1)
  }

  /** [next] with each template's version set by [nextVersion] against [previous]. */
  fun withVersions(previous: LibraryBundle?, next: LibraryBundle): LibraryBundle {
    val before = previous?.form_templates.orEmpty().associateBy { it.id }
    return next.copy(
      form_templates = next.form_templates.map { it.copy(version = nextVersion(before[it.id], it)) }
    )
  }

  /** What counts as an edit: everything but the version, status, order, and audit info. */
  private fun content(template: FormTemplateDef): FormTemplateDef =
    template.copy(
      version = 0,
      status = LibraryStatus.LIBRARY_STATUS_UNSPECIFIED,
      sort_order = 0,
      audit_info = null,
    )
}
