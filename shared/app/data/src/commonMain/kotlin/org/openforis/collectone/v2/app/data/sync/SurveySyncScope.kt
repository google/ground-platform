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
package org.openforis.collectone.v2.app.data.sync

/**
 * Scope of a targeted per-survey sync triggered by a Firebase Cloud Messaging (FCM) topic tickle
 * (`docs/technical/sync-engine.md`, "FCM Topic Tickles (Mobile)").
 */
enum class SurveySyncScope(val wireValue: String) {
  /**
   * Survey definition, forms, dataset schemas, map config, lifecycle/trash state, and ACLs
   * (`survey_<survey_id>_def` topic → `BatchGetSurveyOverviews` + `GetSurvey`).
   */
  DEFINITION("def"),

  /**
   * Map features and plot locks (`survey_<survey_id>_entities` topic → `ListEntities` delta pull).
   */
  ENTITIES("entities");

  companion object {
    fun fromWireValue(value: String?): SurveySyncScope? = entries.firstOrNull {
      it.wireValue.equals(value?.trim(), ignoreCase = true)
    }
  }
}
