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
package org.openforis.collectone.v2.web.ui.wizard

import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.PlotReviewOptions
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod

/**
 * User intents of the 6-step Survey Creation Wizard (`CreateSurveyWizardPage`), grouped by the part
 * of the wizard that sends them. Implemented by [CreateSurveyWizardViewModel]; page tests pass
 * fakes.
 */
interface CreateSurveyWizardActions :
  WizardSessionActions,
  WizardOverviewActions,
  WizardPurposeActions,
  WizardMapFeaturesActions,
  WizardSharingActions

/** Starting, moving through, and leaving the wizard. */
interface WizardSessionActions {
  /**
   * Starts a wizard session in [defaultOrganizationId] (the organization being browsed, if any).
   * Offers to continue an unfinished survey from an earlier session instead (see
   * [CreateSurveyWizardUiState.isPromptingUnsavedDraft]); otherwise creates a blank survey right
   * away, which the wizard then edits through the Survey editor.
   */
  fun startWizard(defaultOrganizationId: String? = null)

  /** Goes to [step]; moving forward is refused (and the step's errors shown) while it's invalid. */
  fun selectStep(step: CreateSurveyWizardStep)

  fun nextStep()

  fun previousStep()

  /** Moves on from an optional step without completing it. */
  fun skipStep()

  /**
   * Stores the survey (still a draft) and opens it in the Survey editor where the wizard left it.
   */
  fun openInSurveyEditor()

  /**
   * Stores the survey (still a draft), closes the wizard's Survey editor session, and opens the
   * survey in the Survey editor.
   */
  fun finishWizard()

  /** Deletes the working survey and returns to the survey that was active before. */
  fun cancelWizard()

  /** Dismisses the unsaved draft prompt and continues editing the previous survey draft. */
  fun continueUnsavedDraft() {}

  /** Discards the previous unsaved survey draft and starts a fresh survey creation session. */
  fun discardUnsavedDraft() {}
}

/** Step 1: the survey's title, organization, and how it starts (blank, copied, or imported). */
interface WizardOverviewActions {
  fun updateTitle(title: String)

  fun updateDescription(description: String)

  /**
   * Moves the survey to [organizationId] (or none); stops the default organization being applied.
   */
  fun selectOrganization(organizationId: String?)

  fun selectStartMethod(method: WizardStartMethod)

  fun selectCopySourceSurvey(surveyId: String?)

  /**
   * Reads an uploaded XForms or Collect Earth (CEP `.idm`) file. Nothing reaches the draft until
   * the organizer leaves Step 1, so they can still pick another file or start method.
   */
  fun importXFormsXml(fileName: String, xmlText: String)

  /** Picks one of several projects found in an imported Collect Earth file. */
  fun selectImportedCepProject(fileName: String)

  fun clearImportedXForms()
}

/** Step 2: the collection mode, purpose, starter template, and programs. */
interface WizardPurposeActions {
  /**
   * Chooses [mode] by hand, which also picks its map feature source and stops purposes doing so.
   */
  fun selectCollectionMode(mode: SurveyCollectionMode)

  /**
   * Selects Purpose Pack [packId] (or "Other") in place of the current purpose, or clears it if it
   * was selected: a survey has at most one purpose. A new purpose starts with its default starter
   * template, whose programs it presets.
   */
  fun togglePurposePack(packId: String)

  fun updateOtherPurpose(text: String)

  /** Selects or clears program [programId] (tracking and impact reporting only). */
  fun toggleProgram(programId: String)

  /**
   * Chooses the purpose's starter template [starterTemplateId], which presets the programs and
   * replaces the starting Forms. If the Forms seeded from the previous template were edited, asks
   * first (see [CreateSurveyWizardUiState.pendingStarterTemplateId]) whether to replace them.
   */
  fun selectStarterTemplate(starterTemplateId: String)

  /**
   * Answers the pending starter template question: [replaceForms] replaces the seeded Forms with
   * the new template's, `false` keeps them, and `null` cancels the change.
   */
  fun resolveStarterTemplateChange(replaceForms: Boolean?)
}

/** Steps 4 and 5: the primary map layer, its plots' field validation, and the primary Form. */
interface WizardMapFeaturesActions {
  /**
   * Chooses where map features come from (drawn in the field, generated sample plots, or an
   * imported file) and reshapes the primary layer to match.
   */
  fun selectMapFeatureSource(source: WizardMapFeatureSource)

  fun updatePrimaryLayerDisplayName(name: String)

  /** Sets the primary layer's geometry and makes the primary Form's geometry question match. */
  fun updatePrimaryLayerGeometryType(kind: GeometryType)

  fun updatePrimaryLayerFieldCreation(enabled: Boolean)

  /**
   * Records whether the organizer confirmed every EUDR plot is under 4 ha (which allows points
   * instead of polygons). Withdrawing it puts the primary layer back to polygons.
   */
  fun confirmEudrPlotsUnderFourHa(confirmed: Boolean)

  /** Imports [plan] as the primary layer, replacing an empty one the wizard created. */
  fun importPrimaryMapLayer(plan: MapLayerImportPlan)

  /**
   * Turns field validation of the sample plots on or off. On, the primary Form becomes a web-only
   * desk check that sends plots to a new mobile-only field verification Form (see
   * `PlotReviewWorkflow`); off, it goes back to how it was.
   */
  fun setPlotFieldValidation(enabled: Boolean)

  /** Changes which plots go to the field and what field crews see on them. */
  fun updatePlotFieldValidation(options: PlotReviewOptions)

  /**
   * Creates a new Map layer or Data table of [kind] and automatically binds question at
   * [questionIndex] of the wizard's primary Form to it.
   */
  fun createDatasetForChoiceQuestion(questionIndex: Int, kind: DatasetKind)
}

/** Step 6: collector consent and CARE data sovereignty. */
interface WizardSharingActions {
  /** Requires collectors to accept a consent statement, starting from a default text. */
  fun updateCollectorConsentRequired(required: Boolean)

  fun updateCollectorConsentText(text: String)

  /**
   * Applies the CARE principles' defaults: restricted access, collectors see only their own
   * submissions, and Free, Prior and Informed Consent (FPIC) is required.
   */
  fun applyCareSovereigntyDefaults()

  /** Expands or collapses the CARE card's full analysis on Step 6. */
  fun setCareCardExpanded(expanded: Boolean)
}
