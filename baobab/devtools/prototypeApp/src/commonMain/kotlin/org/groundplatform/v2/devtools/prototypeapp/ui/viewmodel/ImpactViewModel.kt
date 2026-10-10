/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.groundplatform.v2.devtools.prototypeapp.client.pdf.ImpactPdfReports
import org.groundplatform.v2.devtools.prototypeapp.client.pdf.PdfExportClient
import org.groundplatform.v2.devtools.prototypeapp.client.pdf.PlatformPdfExportClient
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Countries
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEvent
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactSummary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.ImpactEventRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.LibraryRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.OrganizationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ComputeImpactUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ImpactSnapshot
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ImpactSurveySource
import org.groundplatform.v2.devtools.prototypeapp.ui.common.platformEpochMillis
import org.groundplatform.v2.devtools.prototypeapp.ui.state.ImpactFilter
import org.groundplatform.v2.devtools.prototypeapp.ui.state.ImpactUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.state.OrganizationImpactUiState

/** User intents of the Impact views. Implemented by [ImpactViewModel]. */
interface ImpactActions {
  /** Limits organization [organizationId]'s Impact view to survey [surveyId]; `null` for all. */
  fun setOrganizationSurveyFilter(organizationId: String, surveyId: String?)

  /**
   * Limits organization [organizationId]'s Impact view to country [countryCode]; `null` for all.
   */
  fun setOrganizationCountryFilter(organizationId: String, countryCode: String?)

  /** Downloads survey [surveyId]'s impact summary as a PDF. */
  fun downloadSurveySummary(surveyId: String)

  /** Downloads organization [organizationId]'s impact summary (with its filter) as a PDF. */
  fun downloadOrganizationSummary(organizationId: String)

  /** Downloads the platform-wide impact summary as a PDF (Managers of `"All users"` only). */
  fun downloadGlobalSummary()

  fun dismissMessage()
}

/**
 * ViewModel of the Impact views: the survey Impact tab of the web dashboard, the organization
 * Impact tab, and the platform-wide view for Managers of `"All users"`.
 *
 * Numbers come from [ComputeImpactUseCase] (the shared aggregation job) over every survey's map
 * features, editor draft, and outcome, every library, every organization, and the impact events.
 * They're recomputed [debounceMillis] after the data stops changing, so a burst of edits computes
 * once. PDF summaries are built by [ImpactPdfReports] and saved through [PdfExportClient].
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ImpactViewModel(
  surveyRepository: SurveyRepository,
  organizationRepository: OrganizationRepository,
  libraryRepository: LibraryRepository,
  impactEventRepository: ImpactEventRepository,
  authRepository: AuthRepository,
  scope: CoroutineScope,
  /** Editor drafts carry the column and question concept links; `null` counts no linked values. */
  surveyEditorRepository: SurveyEditorRepository? = null,
  private val computeImpact: ComputeImpactUseCase = ComputeImpactUseCase(),
  private val pdfExportClient: PdfExportClient = PlatformPdfExportClient(),
  /** Quiet time before recomputing after a change; 0 recomputes on every change (tests). */
  debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
  /** Current time in epoch milliseconds, printed on PDF summaries. */
  private val now: () -> Long = { platformEpochMillis() },
) : ImpactActions {
  private data class Inputs(
    val sources: List<ImpactSurveySource>,
    val libraries: Map<String, OrganizationLibrary>,
    val organizations: List<Organization>,
    val events: List<ImpactEvent>,
  )

  private val sources: Flow<List<ImpactSurveySource>> =
    surveyRepository.observeSurveys().flatMapLatest { surveys ->
      if (surveys.isEmpty()) {
        flowOf(emptyList())
      } else {
        combine(
          surveys.map { survey ->
            combine(
              surveyRepository.observeSurveyContent(survey.id),
              surveyEditorRepository?.observeDraft(survey.id)?.map<
                SurveyEditorDraft,
                SurveyEditorDraft?,
              > {
                it
              } ?: flowOf(null),
            ) { content, draft ->
              ImpactSurveySource(survey, content, draft)
            }
          }
        ) {
          it.toList()
        }
      }
    }

  private val inputs: Flow<Inputs> =
    combine(
        sources,
        libraryRepository.observeLibraries(),
        organizationRepository.observeOrganizations(),
        impactEventRepository.observeEvents(),
        ::Inputs,
      )
      .let { if (debounceMillis > 0) it.debounce(debounceMillis) else it }

  private val snapshot: StateFlow<ImpactSnapshot?> =
    inputs
      .map { computeImpact(it.sources, it.libraries, it.organizations, it.events) }
      .stateIn(scope, SharingStarted.Eagerly, null)

  private val filters = MutableStateFlow<Map<String, ImpactFilter>>(emptyMap())
  private val message = MutableStateFlow<String?>(null)

  val uiState: StateFlow<ImpactUiState> =
    combine(snapshot, filters, authRepository.observeSession(), message) {
        snapshot,
        filters,
        auth,
        message ->
        if (snapshot == null) return@combine ImpactUiState(message = message)
        val email = auth.profile.email
        val canSeeGlobal =
          snapshot.organizations.any { it.isSynthetic && email.isNotBlank() && it.isManager(email) }
        ImpactUiState(
          isLoading = false,
          surveys =
            snapshot.surveys
              .mapNotNull { survey ->
                computeImpact.surveySummary(snapshot, survey.id)?.let { survey.id to it }
              }
              .toMap(),
          organizations =
            snapshot.organizations
              .filterNot { it.isSynthetic }
              .mapNotNull { organization -> organizationState(snapshot, organization, filters) }
              .associateBy { it.summary.scopeId },
          global = if (canSeeGlobal) computeImpact.globalSummary(snapshot) else null,
          message = message,
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, ImpactUiState())

  private fun organizationState(
    snapshot: ImpactSnapshot,
    organization: Organization,
    filters: Map<String, ImpactFilter>,
  ): OrganizationImpactUiState? {
    val filter = filters[organization.id] ?: ImpactFilter()
    val summary =
      computeImpact.organizationSummary(
        snapshot,
        organization.id,
        surveyId = filter.surveyId,
        countryCode = filter.countryCode,
      ) ?: return null
    return OrganizationImpactUiState(
      summary = summary,
      filter = filter,
      surveys = snapshot.surveys.filter { it.organizationId == organization.id },
      countries =
        computeImpact
          .organizationCountries(snapshot, organization.id)
          .mapNotNull(Countries::byCode),
    )
  }

  override fun setOrganizationSurveyFilter(organizationId: String, surveyId: String?) {
    filters.update {
      it + (organizationId to (it[organizationId] ?: ImpactFilter()).copy(surveyId = surveyId))
    }
  }

  override fun setOrganizationCountryFilter(organizationId: String, countryCode: String?) {
    filters.update {
      it +
        (organizationId to (it[organizationId] ?: ImpactFilter()).copy(countryCode = countryCode))
    }
  }

  override fun downloadSurveySummary(surveyId: String) {
    uiState.value.surveys[surveyId]?.let { save(it, filterLabel = null) }
  }

  override fun downloadOrganizationSummary(organizationId: String) {
    val state = uiState.value.organizations[organizationId] ?: return
    save(state.summary, filterLabel(state))
  }

  override fun downloadGlobalSummary() {
    uiState.value.global?.let { save(it, filterLabel = null) }
  }

  override fun dismissMessage() {
    message.value = null
  }

  private fun save(summary: ImpactSummary, filterLabel: String?) {
    val pdf =
      ImpactPdfReports.summary(summary, generatedAtEpochMillis = now(), filterLabel = filterLabel)
    pdfExportClient.save(pdf.fileName, pdf.bytes)
    message.value = "Saved ${pdf.fileName} (${pdf.summaryLabel})"
  }

  /** The organization filter in words, e.g. `"Survey: Kenya coffee · Country: Kenya"`. */
  private fun filterLabel(state: OrganizationImpactUiState): String? {
    val parts = buildList {
      state.filter.surveyId?.let { id ->
        add("Survey: ${state.surveys.firstOrNull { it.id == id }?.title ?: id}")
      }
      state.filter.countryCode?.let { add("Country: ${Countries.byCode(it)?.name ?: it}") }
    }
    return parts.joinToString(" · ").ifEmpty { null }
  }

  /** Clears filters and messages (used by the prototype's Reset). */
  fun reset() {
    filters.value = emptyMap()
    message.value = null
  }

  companion object {
    const val DEFAULT_DEBOUNCE_MILLIS = 300L
  }
}
