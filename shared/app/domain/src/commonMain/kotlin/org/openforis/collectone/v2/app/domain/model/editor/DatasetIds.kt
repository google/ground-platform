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
 * IDs of new map layers and data tables ([EntityDataset.id]), which forms and features refer to.
 *
 * An ID is chosen once, when the dataset is created, and never follows its display name: renaming a
 * dataset keeps its ID.
 */
object DatasetIds {
  /** Prefix of the fallback IDs: `dataset_1`, `dataset_2`, … */
  const val FALLBACK_PREFIX = "dataset"

  /**
   * The ID of a new dataset named [name], unique among [takenIds]: the Latin slug of the name (see
   * [slugify]), e.g. `survey_sites` for "Survey sites", when it has one; otherwise, for names in
   * other scripts such as Khmer or Thai, the first free `dataset_<n>`.
   */
  fun forName(name: String, takenIds: Collection<String>): String {
    val slug = slugify(name, fallback = "")
    return if (slug.isNotEmpty()) {
      FormDatasetLinks.uniqueId(slug, takenIds.toList())
    } else {
      generateSequence(1) { it + 1 }.map { "${FALLBACK_PREFIX}_$it" }.first { it !in takenIds }
    }
  }
}
