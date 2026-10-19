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
package org.openforis.collectone.v2.web.di

import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.domain.model.editor.isoUtc
import org.openforis.collectone.v2.app.ui.common.platformEpochMillis
import org.openforis.collectone.v2.web.domain.usecase.ComputeImpactUseCase
import org.openforis.collectone.v2.web.domain.usecase.CreateOrganizationUseCase
import org.openforis.collectone.v2.web.domain.usecase.GenerateSamplePlotsUseCase
import org.openforis.collectone.v2.web.domain.usecase.InviteCollaboratorUseCase
import org.openforis.collectone.v2.web.domain.usecase.InviteOrganizationMemberUseCase
import org.openforis.collectone.v2.web.domain.usecase.ManageImagerySourcesUseCase
import org.openforis.collectone.v2.web.domain.usecase.ManageLibraryUseCase
import org.openforis.collectone.v2.web.domain.usecase.SearchConceptsUseCase
import org.openforis.collectone.v2.web.domain.usecase.SurveyLifecycleUseCase

/**
 * The Domain Use Cases only the web console uses (organizations, library, sampling, survey
 * lifecycle, and impact), built from the repositories and shared use cases of [dataHolder]. The use
 * cases both clients use stay in [AppDataHolder].
 */
class WebUseCases(
  dataHolder: AppDataHolder,
  val createOrganizationUseCase: CreateOrganizationUseCase =
    CreateOrganizationUseCase(dataHolder.organizationRepository),
  val inviteOrganizationMemberUseCase: InviteOrganizationMemberUseCase =
    InviteOrganizationMemberUseCase(dataHolder.organizationRepository),
  val manageImagerySourcesUseCase: ManageImagerySourcesUseCase =
    ManageImagerySourcesUseCase(dataHolder.organizationRepository),
  val manageLibraryUseCase: ManageLibraryUseCase =
    ManageLibraryUseCase(dataHolder.libraryRepository),
  val computeImpactUseCase: ComputeImpactUseCase =
    ComputeImpactUseCase(resolveLibrary = dataHolder.resolveLibraryUseCase),
  val searchConceptsUseCase: SearchConceptsUseCase = SearchConceptsUseCase(),
  val generateSamplePlotsUseCase: GenerateSamplePlotsUseCase =
    GenerateSamplePlotsUseCase(now = { isoUtc(platformEpochMillis()) }),
  val inviteCollaboratorUseCase: InviteCollaboratorUseCase = InviteCollaboratorUseCase(),
  val surveyLifecycleUseCase: SurveyLifecycleUseCase =
    SurveyLifecycleUseCase(
      surveyRepository = dataHolder.surveyRepository,
      authRepository = dataHolder.authRepository,
      recordImpactEvent = dataHolder.recordImpactEventUseCase,
      now = { platformEpochMillis() },
    ),
)
