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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactCoverage
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEvent
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.ConnectivityRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.ImpactEventRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository

/**
 * Records a "data was used" [ImpactEvent] for a survey (see
 * `docs/technical/model/data/04-impact-events.md`), filling in the survey's organization and
 * purposes and the signed-in user.
 *
 * Events are stored on the device first. Online, they upload right away; offline, they wait and
 * upload with the other pending changes ([SyncMutationsUseCase]).
 */
@OptIn(ExperimentalUuidApi::class)
class RecordImpactEventUseCase(
  private val impactEventRepository: ImpactEventRepository,
  private val surveyRepository: SurveyRepository,
  private val surveyEditorRepository: SurveyEditorRepository,
  private val authRepository: AuthRepository,
  private val connectivityRepository: ConnectivityRepository,
  private val now: () -> String,
  private val newId: () -> String = { "impact-" + Uuid.random().toString() },
) {
  suspend operator fun invoke(
    type: ImpactEventType,
    surveyId: String,
    coverage: ImpactCoverage = ImpactCoverage(),
    exportProfileId: String? = null,
  ): ImpactEvent {
    val survey = surveyRepository.getSurveys().firstOrNull { it.id == surveyId }
    val purposeIds = surveyEditorRepository.getDraft(surveyId).details.purposeIds
    val event =
      ImpactEvent(
        id = newId(),
        type = type,
        surveyId = surveyId,
        organizationId = survey?.organizationId,
        purposeIds = purposeIds,
        exportProfileId = exportProfileId,
        featureCount = coverage.featureCount,
        areaHa = coverage.areaHa,
        occurredAt = now(),
        // The prototype identifies users by email; Ground stores the account's user ID.
        actorUserId = authRepository.getSession().profile.email,
        isUploaded = connectivityRepository.isOnline(),
      )
    impactEventRepository.append(event)
    return event
  }
}
