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
package org.openforis.collectone.v2.app.data.mapper

import openforis.collectone.v1.survey.FormAvailability as FormAvailabilityProto
import openforis.collectone.v1.survey.SurveyOutcome as SurveyOutcomeProto
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcome
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.editor.isoUtc

/**
 * Maps a survey's lifecycle state, its forms' platform availability, and its post-survey outcome
 * between domain models and their `SurveyDef` / `FormLaunchConfig` encodings.
 */
internal object SurveyLifecycleMapper {
  fun surveyStateToProto(state: SurveyLifecycleState): SurveyState =
    when (state) {
      SurveyLifecycleState.DRAFT -> SurveyState.DRAFT
      SurveyLifecycleState.PUBLISHED -> SurveyState.PUBLISHED
      SurveyLifecycleState.CLOSED -> SurveyState.CLOSED
      SurveyLifecycleState.ARCHIVED -> SurveyState.ARCHIVED
    }

  fun surveyStateFromProto(proto: SurveyState): SurveyLifecycleState =
    when (proto) {
      SurveyState.DRAFT -> SurveyLifecycleState.DRAFT
      SurveyState.CLOSED -> SurveyLifecycleState.CLOSED
      SurveyState.ARCHIVED -> SurveyLifecycleState.ARCHIVED
      else -> SurveyLifecycleState.PUBLISHED
    }

  /** Encodes [availability]; `null` (unset) means available on all platforms. */
  fun formAvailabilityToProto(availability: FormAvailability): FormAvailabilityProto? =
    when (availability) {
      FormAvailability.BOTH -> null
      FormAvailability.MOBILE -> FormAvailabilityProto(web = false, mobile = true)
      FormAvailability.WEB -> FormAvailabilityProto(web = true, mobile = false)
      FormAvailability.NONE -> FormAvailabilityProto(web = false, mobile = false)
    }

  /**
   * Decodes `FormLaunchConfig.availability`: unset (how [formAvailabilityToProto] writes `BOTH`)
   * means both platforms, while a set message with neither flag means no platform.
   */
  fun formAvailabilityFromProto(proto: FormAvailabilityProto?): FormAvailability =
    when {
      proto == null -> FormAvailability.BOTH
      proto.web && proto.mobile -> FormAvailability.BOTH
      proto.mobile -> FormAvailability.MOBILE
      proto.web -> FormAvailability.WEB
      else -> FormAvailability.NONE
    }

  fun surveyOutcomeToProto(outcome: SurveyOutcome): SurveyOutcomeProto =
    SurveyOutcomeProto(
      outcomes =
        outcome.outcomes.map { kind ->
          when (kind) {
            SurveyOutcomeKind.SUBMITTED_EUDR_DDS -> SurveyOutcomeProto.Outcome.SUBMITTED_EUDR_DDS
            SurveyOutcomeKind.REPORTED_FERM -> SurveyOutcomeProto.Outcome.REPORTED_FERM
            SurveyOutcomeKind.LAND_TITLING -> SurveyOutcomeProto.Outcome.LAND_TITLING
            SurveyOutcomeKind.SHARED_WITH_BUYERS -> SurveyOutcomeProto.Outcome.SHARED_WITH_BUYERS
            SurveyOutcomeKind.TRAINED_OR_VALIDATED_MODEL ->
              SurveyOutcomeProto.Outcome.TRAINED_OR_VALIDATED_MODEL
            SurveyOutcomeKind.PROTECTED_AREA_MANAGEMENT ->
              SurveyOutcomeProto.Outcome.PROTECTED_AREA_MANAGEMENT
            SurveyOutcomeKind.NOT_YET -> SurveyOutcomeProto.Outcome.NOT_YET
          }
        },
      effort_comparison =
        when (outcome.effortComparison) {
          EffortComparison.LESS -> SurveyOutcomeProto.EffortComparison.LESS
          EffortComparison.SAME -> SurveyOutcomeProto.EffortComparison.SAME
          EffortComparison.MORE -> SurveyOutcomeProto.EffortComparison.MORE
          null -> SurveyOutcomeProto.EffortComparison.EFFORT_COMPARISON_UNSPECIFIED
        },
      answered_at = parseIsoToInstant(outcome.answeredAt),
      answered_by = outcome.answeredBy,
    )

  fun surveyOutcomeFromProto(proto: SurveyOutcomeProto): SurveyOutcome =
    SurveyOutcome(
      outcomes =
        proto.outcomes
          .mapNotNull { o ->
            when (o) {
              SurveyOutcomeProto.Outcome.SUBMITTED_EUDR_DDS -> SurveyOutcomeKind.SUBMITTED_EUDR_DDS
              SurveyOutcomeProto.Outcome.REPORTED_FERM -> SurveyOutcomeKind.REPORTED_FERM
              SurveyOutcomeProto.Outcome.LAND_TITLING -> SurveyOutcomeKind.LAND_TITLING
              SurveyOutcomeProto.Outcome.SHARED_WITH_BUYERS -> SurveyOutcomeKind.SHARED_WITH_BUYERS
              SurveyOutcomeProto.Outcome.TRAINED_OR_VALIDATED_MODEL ->
                SurveyOutcomeKind.TRAINED_OR_VALIDATED_MODEL
              SurveyOutcomeProto.Outcome.PROTECTED_AREA_MANAGEMENT ->
                SurveyOutcomeKind.PROTECTED_AREA_MANAGEMENT
              SurveyOutcomeProto.Outcome.NOT_YET -> SurveyOutcomeKind.NOT_YET
              SurveyOutcomeProto.Outcome.OUTCOME_UNSPECIFIED -> null
            }
          }
          .toSet(),
      effortComparison =
        when (proto.effort_comparison) {
          SurveyOutcomeProto.EffortComparison.LESS -> EffortComparison.LESS
          SurveyOutcomeProto.EffortComparison.SAME -> EffortComparison.SAME
          SurveyOutcomeProto.EffortComparison.MORE -> EffortComparison.MORE
          SurveyOutcomeProto.EffortComparison.EFFORT_COMPARISON_UNSPECIFIED -> null
        },
      answeredAt = proto.answered_at?.let { isoUtc(it.toKotlinInstant()) }.orEmpty(),
      answeredBy = proto.answered_by,
    )
}
