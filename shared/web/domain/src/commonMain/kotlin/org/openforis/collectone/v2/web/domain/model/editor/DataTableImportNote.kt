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
package org.openforis.collectone.v2.web.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.CountedMessageKey
import org.openforis.collectone.v2.app.domain.model.MessageKey

/** Notes on reading a CSV file as a Data table; arguments are listed per entry. */
enum class DataTableImportNote : MessageKey {
  FILE_EMPTY,
  NO_DATA_ROWS,
  /** The `id` column can't be the key, so a Row ID column is added. */
  ID_COLUMN_NOT_UNIQUE,
  /**
   * The quoted labels of the ambiguous Date columns, and the [DateOrder.example] they're read in.
   */
  AMBIGUOUS_DATES,
}

/** Count-dependent notes on reading a CSV file as a Data table, formatted with the count. */
enum class DataTableImportCountedNote : CountedMessageKey {
  /** Rows whose number of cells differs from the header's. */
  RAGGED_ROWS
}
