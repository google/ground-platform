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
package org.openforis.collectone.v2.app.domain.model.editor

/**
 * [value] as an XPath string literal. XPath 1.0 section 3.7 delimits literals with `'` or `"` and
 * has no escapes, so [value] is wrapped in whichever quote it doesn't contain. A value containing
 * both would need `concat()`; the editor's values (choice names, labels, titles) are written
 * through this one function so that case can be handled in one place.
 */
internal fun xpathStringLiteral(value: String): String =
  if (value.contains('\'')) "\"$value\"" else "'$value'"
