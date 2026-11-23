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
package org.openforis.collectone.v2.app.ui.datacollection

import org.openforis.collectone.v2.app.ui.text.UiText

/**
 * Active PDF export & app-share sheet state for either a Geospatial Entity or a Form Submission.
 */
data class SharedPdfSheetState(
  val targetId: String,
  /** Title the system share sheet gives the PDF, e.g. `"Coffee parcel report"`. */
  val shareTitle: UiText,
  val subtitle: String,
  val pdfFileName: String,
  /** Number of pages in the generated PDF. */
  val pageCount: Int = 0,
  /** Generated PDF size for display, e.g. `"14 KB"`. */
  val fileSizeLabel: String = "",
)
