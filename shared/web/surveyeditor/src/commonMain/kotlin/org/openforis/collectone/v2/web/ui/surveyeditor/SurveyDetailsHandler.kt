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
package org.openforis.collectone.v2.web.ui.surveyeditor

import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.SurveyPlaceItem
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyDetails
import org.openforis.collectone.v2.app.domain.repository.PlaceRepository
import org.openforis.collectone.v2.app.ui.text.labelRes
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_access_changed_no_organization
import org.openforis.collectone.v2.web.ui.text.EditorText

/**
 * [SurveyDetailsActions] of the Survey editor: title and other details, quotas, languages, the
 * survey area, and the organization, applied to the draft of [session]. Place search goes to
 * [placeRepository]; [dismissNotice] clears the editor's organization notice.
 */
internal class SurveyDetailsHandler(
  private val session: SurveyDraftSession,
  private val placeRepository: PlaceRepository,
  private val dismissNotice: () -> Unit,
) : SurveyDetailsActions {
  override fun updateDetails(transform: (SurveyDetails) -> SurveyDetails) =
    session.updateDetails(transform)

  override fun updateQuotaLimits(transform: (QuotaLimitsConfig) -> QuotaLimitsConfig) {
    updateDetails { it.copy(quotaLimits = transform(it.quotaLimits)) }
  }

  override fun addSupportedLanguage(code: String) {
    val normalized = code.trim().lowercase()
    if (normalized.isEmpty()) return
    updateDetails { current ->
      val currentList = current.supportedLanguages
      val newList = if (normalized in currentList) currentList else currentList + normalized
      val defaultLang = current.defaultLanguage.ifEmpty { normalized }
      current.copy(supportedLanguages = newList, defaultLanguage = defaultLang)
    }
  }

  override fun removeSupportedLanguage(code: String) {
    val normalized = code.trim().lowercase()
    updateDetails { current ->
      val newList = current.supportedLanguages.filterNot { it.lowercase() == normalized }
      val newDefault =
        if (current.defaultLanguage.lowercase() == normalized) {
          newList.firstOrNull() ?: ""
        } else {
          current.defaultLanguage
        }
      current.copy(supportedLanguages = newList, defaultLanguage = newDefault)
    }
  }

  override fun setDefaultLanguage(code: String) {
    val normalized = code.trim().lowercase()
    if (normalized.isEmpty()) return
    updateDetails { current ->
      val currentList = current.supportedLanguages
      val newList = if (normalized in currentList) currentList else currentList + normalized
      current.copy(defaultLanguage = normalized, supportedLanguages = newList)
    }
  }

  override fun setSurveyArea(area: SurveyArea?) = updateDetails { it.copy(surveyArea = area) }

  override fun setOrganization(organizationId: String?) = session.edit {
    val normalized = organizationId?.takeIf { it.isNotBlank() }
    organizationNotice = null
    details = details.copy(organizationId = normalized)
    if (normalized == null && sharing.policy == SharingPolicy.ORGANIZATION) {
      sharing = sharing.copy(policy = SharingPolicy.RESTRICTED)
      organizationNotice =
        EditorText.Resource(
          Res.string.surveyeditor_access_changed_no_organization,
          listOf(SharingPolicy.RESTRICTED.labelRes),
        )
    }
  }

  override fun dismissOrganizationNotice() {
    dismissNotice()
  }

  override fun searchPlaces(
    surveyId: String,
    query: String,
    regionSubtitle: String,
    center: LatLng,
    onResults: (List<SurveyPlaceItem>) -> Unit,
  ) {
    placeRepository.searchRemotePlaces(
      surveyId = surveyId,
      query = query,
      defaultRegionSubtitle = regionSubtitle,
      centerLongitude = center.longitude,
      centerLatitude = center.latitude,
      onResults = onResults,
    )
  }
}
