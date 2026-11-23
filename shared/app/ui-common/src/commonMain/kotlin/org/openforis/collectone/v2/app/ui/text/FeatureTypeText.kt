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
package org.openforis.collectone.v2.app.ui.text

import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_feature_type_generic
import org.openforis.collectone.v2.app.resources.app_target_standalone

/**
 * A map feature type's noun: the survey author's [noun] (e.g. `"Coffee Parcel"`), or `Location` in
 * the user's language when the author gave none.
 */
fun featureTypeText(noun: String): UiText =
  if (noun.isBlank()) Res.string.app_feature_type_generic.toUiText() else UiText.Plain(noun)

/** The feature's type, e.g. `"Coffee Parcel"`. */
val GeospatialEntityItem.typeText: UiText
  get() = featureTypeText(typeNoun)

/** The type of the features the form records, e.g. `"Coffee Parcel"`. */
val FormPreviewItem.targetTypeText: UiText
  get() = featureTypeText(targetTypeNoun)

/**
 * What the submission was recorded for: its feature's type (e.g. `"Coffee Parcel"`), or `Standalone
 * Field Log` for a submission without a map feature.
 */
val SubmissionPreviewItem.targetTypeText: UiText
  get() =
    if (hasAttachedEntity) featureTypeText(targetTypeNoun)
    else Res.string.app_target_standalone.toUiText()
