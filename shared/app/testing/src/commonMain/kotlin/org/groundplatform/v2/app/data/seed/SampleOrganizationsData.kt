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
package org.groundplatform.v2.app.data.seed

import org.groundplatform.v2.app.data.seed.backend.BackendStaticOrganizationsData
import org.groundplatform.v2.app.domain.model.Organization

/**
 * Facade delegating to [BackendStaticOrganizationsData] (static sample data moved out of the client
 * and into the Kotlin gRPC backend service).
 */
object SampleOrganizationsData {
  const val ALL_USERS = BackendStaticOrganizationsData.ALL_USERS
  const val KENYA_FOREST_SERVICE = BackendStaticOrganizationsData.KENYA_FOREST_SERVICE
  const val MEKONG_MANGROVE_ALLIANCE = BackendStaticOrganizationsData.MEKONG_MANGROVE_ALLIANCE
  const val OPEN_FORIS_COMMUNITY = BackendStaticOrganizationsData.OPEN_FORIS_COMMUNITY
  const val SIGNED_IN_EMAIL = BackendStaticOrganizationsData.SIGNED_IN_EMAIL

  fun defaultOrganizations(): List<Organization> =
    BackendStaticOrganizationsData.defaultOrganizations()

  fun organizationIdForSurvey(surveyId: String): String? =
    BackendStaticOrganizationsData.organizationIdForSurvey(surveyId)

  fun ownerEmailForSurvey(surveyId: String): String =
    BackendStaticOrganizationsData.ownerEmailForSurvey(surveyId)
}
