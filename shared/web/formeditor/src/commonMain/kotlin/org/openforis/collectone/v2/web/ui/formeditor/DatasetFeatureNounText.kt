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
package org.openforis.collectone.v2.web.ui.formeditor

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.editor.EditorDataset
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.formeditor_feature_noun_map_feature
import org.openforis.collectone.v2.web.resources.formeditor_feature_noun_map_features
import org.openforis.collectone.v2.web.resources.formeditor_feature_noun_table_row
import org.openforis.collectone.v2.web.resources.formeditor_feature_noun_table_rows

/** What one record of the dataset is called in a sentence: `map feature` or `table row`. */
@Composable
internal fun EditorDataset.featureNounText(): String =
  stringResource(
    if (isMapLayer) Res.string.formeditor_feature_noun_map_feature
    else Res.string.formeditor_feature_noun_table_row
  )

/** What several records of the dataset are called in a sentence: `map features` or `table rows`. */
@Composable
internal fun EditorDataset.featureNounPluralText(): String =
  stringResource(
    if (isMapLayer) Res.string.formeditor_feature_noun_map_features
    else Res.string.formeditor_feature_noun_table_rows
  )
