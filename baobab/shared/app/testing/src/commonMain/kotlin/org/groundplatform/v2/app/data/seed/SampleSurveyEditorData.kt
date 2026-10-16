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

import org.groundplatform.v2.app.data.seed.backend.BackendStaticSurveyEditorData
import org.groundplatform.v2.app.domain.model.editor.EntityDataset
import org.groundplatform.v2.app.domain.model.editor.SharingSettings
import org.groundplatform.v2.app.domain.model.editor.SurveyArea
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDraft

/**
 * Facade delegating to [BackendStaticSurveyEditorData] (static sample data moved out of the client
 * and into the Kotlin gRPC backend service).
 */
object SampleSurveyEditorData {
  const val ORGANIZATION_SHARED_SURVEY_ID: String =
    BackendStaticSurveyEditorData.ORGANIZATION_SHARED_SURVEY_ID

  fun kenyaCoffeeDraft(derived: SurveyEditorDraft): SurveyEditorDraft =
    BackendStaticSurveyEditorData.kenyaCoffeeDraft(derived)

  fun organizationSharedDraft(derived: SurveyEditorDraft): SurveyEditorDraft =
    BackendStaticSurveyEditorData.organizationSharedDraft(derived)

  fun serengetiSurveyArea(): SurveyArea = BackendStaticSurveyEditorData.serengetiSurveyArea()

  fun kenyaSurveyArea(): SurveyArea = BackendStaticSurveyEditorData.kenyaSurveyArea()

  fun kenyaSharing(): SharingSettings = BackendStaticSurveyEditorData.kenyaSharing()

  fun organizationSharing(): SharingSettings = BackendStaticSurveyEditorData.organizationSharing()

  fun farmers(): EntityDataset = BackendStaticSurveyEditorData.farmers()

  fun treeSpecies(): EntityDataset = BackendStaticSurveyEditorData.treeSpecies()
}
