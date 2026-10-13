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
package org.groundplatform.v2.web.domain.usecase

import kotlinx.coroutines.flow.first
import org.groundplatform.v2.app.data.datasource.local.store.LocalStore
import org.groundplatform.v2.app.data.datasource.local.store.runNow
import org.groundplatform.v2.app.data.datasource.local.store.seededStore
import org.groundplatform.v2.app.data.repository.AuthRepositoryImpl
import org.groundplatform.v2.app.data.repository.ConnectivityRepositoryImpl
import org.groundplatform.v2.app.data.repository.ImpactEventRepositoryImpl
import org.groundplatform.v2.app.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.app.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.app.domain.model.ImpactEventType
import org.groundplatform.v2.app.domain.model.editor.isoUtc
import org.groundplatform.v2.app.domain.usecase.RecordImpactEventUseCase

/** A [SurveyLifecycleUseCase] over [store] with a settable clock, recording impact events. */
class SurveyLifecycleFixture(val store: LocalStore = seededStore()) {
  var nowMillis: Long = 1_791_331_200_000L // 2026-10-07T00:00:00Z
  val surveyRepository = SurveyRepositoryImpl(store)
  val impactEventRepository = ImpactEventRepositoryImpl(store)
  val authRepository = AuthRepositoryImpl()
  val lifecycle =
    SurveyLifecycleUseCase(
      surveyRepository = surveyRepository,
      authRepository = authRepository,
      recordImpactEvent =
        RecordImpactEventUseCase(
          impactEventRepository = impactEventRepository,
          surveyRepository = surveyRepository,
          surveyEditorRepository = SurveyEditorRepositoryImpl(store),
          authRepository = authRepository,
          connectivityRepository = ConnectivityRepositoryImpl(),
          now = { isoUtc(nowMillis) },
        ),
      now = { nowMillis },
    )

  fun survey(id: String) = runNow { surveyRepository.getSurveys().first { it.id == id } }

  /** Survey closed events recorded for survey [id] (the seed may hold other events). */
  fun closedEvents(id: String) = runNow {
    impactEventRepository.getEvents().filter {
      it.surveyId == id && it.type == ImpactEventType.SURVEY_CLOSED
    }
  }

  fun entities(id: String) = runNow { surveyRepository.observeSurveyContent(id).first().entities }
}
