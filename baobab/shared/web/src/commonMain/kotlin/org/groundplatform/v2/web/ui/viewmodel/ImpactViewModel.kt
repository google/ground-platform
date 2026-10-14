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
package org.groundplatform.v2.web.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.groundplatform.v2.app.client.pdf.PdfExportClient
import org.groundplatform.v2.app.client.pdf.PlatformPdfExportClient
import org.groundplatform.v2.app.domain.model.AuthSession
import org.groundplatform.v2.app.domain.model.Countries
import org.groundplatform.v2.app.domain.model.ImpactEvent
import org.groundplatform.v2.app.domain.model.Organization
import org.groundplatform.v2.app.domain.model.OrganizationLibrary
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.app.domain.repository.AuthRepository
import org.groundplatform.v2.app.domain.repository.ImpactEventRepository
import org.groundplatform.v2.app.domain.repository.LibraryRepository
import org.groundplatform.v2.app.domain.repository.OrganizationRepository
import org.groundplatform.v2.app.domain.repository.SurveyEditorRepository
import org.groundplatform.v2.app.domain.repository.SurveyRepository
import org.groundplatform.v2.app.ui.common.platformEpochMillis
import org.groundplatform.v2.web.client.pdf.ImpactPdfReports
import org.groundplatform.v2.web.domain.model.ImpactSummary
import org.groundplatform.v2.web.domain.usecase.ComputeImpactUseCase
import org.groundplatform.v2.web.domain.usecase.ImpactSnapshot
import org.groundplatform.v2.web.domain.usecase.ImpactSurveySource
import org.groundplatform.v2.web.ui.state.ImpactDashboardFocus
import org.groundplatform.v2.web.ui.state.ImpactDashboardOrganization
import org.groundplatform.v2.web.ui.state.ImpactDashboardUiState
import org.groundplatform.v2.web.ui.state.ImpactFilter
import org.groundplatform.v2.web.ui.state.ImpactUiState
import org.groundplatform.v2.web.ui.state.OrganizationImpactUiState

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

  /**
   * Downloads organization [organizationId]'s published numbers (with its country filter) as a PDF.
   */
  fun downloadPublishedOrganizationSummary(organizationId: String)

  /**
   * Downloads the platform-wide impact summary as a PDF: the full one for Managers of `"All
   * users"`, the published one for everyone else.
   */
  fun downloadGlobalSummary()

  fun dismissMessage()

  // --- Impact page ---

  /** Puts organization [organizationId] on the signed-in user's Impact page, if they can add it. */
  fun addToDashboard(organizationId: String)

  /** Takes organization [organizationId] off the signed-in user's Impact page. */
  fun removeFromDashboard(organizationId: String)

  /**
   * Replaces the organizations on the signed-in user's Impact page with [organizationIds] (in
   * order), ignoring ones they can't add.
   */
  fun setDashboardOrganizations(organizationIds: List<String>)

  /** Shows [focus] in the Impact page's content pane. */
  fun focusDashboard(focus: ImpactDashboardFocus)

  /** Switches members between their full numbers and the published view everyone else sees. */
  fun setPreviewingPublished(isPreviewing: Boolean)
}

/**
 * ViewModel of the Impact views: the Impact page (`#impact`), the survey Impact pane, and the
 * platform-wide view.
 *
 * Every organization publishes anonymous totals (see
 * [ComputeImpactUseCase.publishedOrganizationSummary]) that anyone signed in can put on their
 * Impact page; its members can also see its full numbers. Platform-wide numbers are published to
 * everyone, in full to Managers of `"All users"`.
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

  /** The Impact page's choices, kept apart per signed-in user. */
  private data class DashboardChoices(
    /** Organizations each user put on their Impact page, by lower-case email; absent = default. */
    val selections: Map<String, List<String>> = emptyMap(),
    val focus: ImpactDashboardFocus = ImpactDashboardFocus.Overview,
    val isPreviewingPublished: Boolean = false,
  )

  private val dashboardChoices = MutableStateFlow(DashboardChoices())

  private val authSession: StateFlow<AuthSession?> =
    authRepository.observeSession().stateIn(scope, SharingStarted.Eagerly, null)

  val uiState: StateFlow<ImpactUiState> =
    combine(snapshot, filters, authSession, dashboardChoices, message) {
        snapshot,
        filters,
        auth,
        choices,
        message ->
        if (snapshot == null) return@combine ImpactUiState(message = message)
        val email = auth?.profile?.email.orEmpty()
        val isPlatformAdmin =
          snapshot.organizations.any { it.isSynthetic && email.isNotBlank() && it.isManager(email) }
        val memberOrganizations =
          snapshot.organizations.filter {
            !it.isSynthetic && email.isNotBlank() && it.isMember(email)
          }
        ImpactUiState(
          isLoading = false,
          surveys =
            snapshot.surveys
              .mapNotNull { survey ->
                computeImpact.surveySummary(snapshot, survey.id)?.let { survey.id to it }
              }
              .toMap(),
          organizations =
            memberOrganizations
              .mapNotNull { organization -> organizationState(snapshot, organization, filters) }
              .associateBy { it.summary.scopeId },
          global =
            if (isPlatformAdmin) computeImpact.globalSummary(snapshot)
            else computeImpact.publishedGlobalSummary(snapshot),
          dashboard = dashboardState(snapshot, filters, email, choices),
          message = message,
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, ImpactUiState())

  /** An [openDashboard] call made before the first computation finished. */
  private data class PendingOpen(val organizationId: String?)

  private var pendingOpen: PendingOpen? = null

  init {
    scope.launch {
      uiState.first { !it.isLoading }
      pendingOpen?.let { pending ->
        pendingOpen = null
        openDashboard(pending.organizationId)
      }
    }
  }

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
      countries = countries(snapshot, organization.id),
    )
  }

  /** [organization]'s published numbers: no survey filter, only its country filter. */
  private fun publishedState(
    snapshot: ImpactSnapshot,
    organization: Organization,
    filters: Map<String, ImpactFilter>,
  ): OrganizationImpactUiState? {
    val filter = ImpactFilter(countryCode = filters[organization.id]?.countryCode)
    val summary =
      computeImpact.publishedOrganizationSummary(
        snapshot,
        organization.id,
        countryCode = filter.countryCode,
      ) ?: return null
    return OrganizationImpactUiState(
      summary = summary,
      filter = filter,
      countries = countries(snapshot, organization.id),
    )
  }

  private fun countries(snapshot: ImpactSnapshot, organizationId: String) =
    computeImpact.organizationCountries(snapshot, organizationId).mapNotNull(Countries::byCode)

  private fun dashboardState(
    snapshot: ImpactSnapshot,
    filters: Map<String, ImpactFilter>,
    email: String,
    choices: DashboardChoices,
  ): ImpactDashboardUiState {
    val available =
      availableOrganizations(snapshot.organizations, email).mapNotNull { organization ->
        val published = publishedState(snapshot, organization, filters) ?: return@mapNotNull null
        ImpactDashboardOrganization(
          organization = organization,
          isMember = organization.isMember(email),
          isManager = organization.isManager(email),
          published = published,
        )
      }
    val availableIds = available.map { it.id }.toSet()
    val selectedIds =
      (choices.selections[email.lowercase()] ?: available.filter { it.isMember }.map { it.id })
        .filter { it in availableIds }
    val focus =
      when (val focus = choices.focus) {
        is ImpactDashboardFocus.OrganizationFocus ->
          if (focus.organizationId in selectedIds) focus else ImpactDashboardFocus.Overview
        else -> focus
      }
    return ImpactDashboardUiState(
      available = available,
      selectedIds = selectedIds,
      focus = focus,
      isPreviewingPublished = choices.isPreviewingPublished,
    )
  }

  /**
   * Organizations [email] can put on their Impact page, by name: listed ones plus the ones they
   * belong to. The synthetic `"All users"` organization is the platform-wide view instead.
   */
  private fun availableOrganizations(organizations: List<Organization>, email: String) =
    organizations
      .filter { !it.isSynthetic && (it.isListed || (email.isNotBlank() && it.isMember(email))) }
      .sortedBy { it.name.lowercase() }

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

  override fun downloadPublishedOrganizationSummary(organizationId: String) {
    val state = uiState.value.dashboard.organization(organizationId)?.published ?: return
    save(state.summary, filterLabel(state))
  }

  override fun downloadGlobalSummary() {
    uiState.value.global?.let { save(it, filterLabel = null) }
  }

  override fun dismissMessage() {
    message.value = null
  }

  // --- Impact page ---

  override fun addToDashboard(organizationId: String) {
    val dashboard = uiState.value.dashboard
    if (organizationId in dashboard.selectedIds) return
    setDashboardOrganizations(dashboard.selectedIds + organizationId)
  }

  override fun removeFromDashboard(organizationId: String) {
    setDashboardOrganizations(uiState.value.dashboard.selectedIds - organizationId)
  }

  override fun setDashboardOrganizations(organizationIds: List<String>) {
    val email = signedInEmail() ?: return
    val dashboard = uiState.value.dashboard
    val ids = organizationIds.distinct().filter { dashboard.organization(it) != null }
    dashboardChoices.update { it.copy(selections = it.selections + (email to ids)) }
  }

  override fun focusDashboard(focus: ImpactDashboardFocus) {
    dashboardChoices.update { it.copy(focus = focus) }
  }

  override fun setPreviewingPublished(isPreviewing: Boolean) {
    dashboardChoices.update { it.copy(isPreviewingPublished = isPreviewing) }
  }

  /**
   * Opens the Impact page on [organizationId]: adds it to the signed-in user's dashboard (if they
   * can add it) and shows it. `null` shows the overview; the synthetic `"All users"` organization
   * shows the platform-wide numbers.
   */
  fun openDashboard(organizationId: String?) {
    if (uiState.value.isLoading) {
      // Opened from a link before the first computation: open it once the numbers are in.
      pendingOpen = PendingOpen(organizationId)
      return
    }
    val dashboard = uiState.value.dashboard
    val focus =
      when {
        organizationId == null -> ImpactDashboardFocus.Overview
        organizationId == Organization.ALL_USERS_ID -> ImpactDashboardFocus.Platform
        dashboard.organization(organizationId) == null -> ImpactDashboardFocus.Overview
        else -> {
          addToDashboard(organizationId)
          ImpactDashboardFocus.OrganizationFocus(organizationId)
        }
      }
    focusDashboard(focus)
  }

  private fun signedInEmail(): String? =
    authSession.value?.profile?.email?.takeIf { it.isNotBlank() }?.lowercase()

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

  /**
   * Clears filters, messages, and the Impact page's focus and preview (used by session resets).
   * Each user's chosen organizations are kept.
   */
  fun reset() {
    filters.value = emptyMap()
    message.value = null
    dashboardChoices.update { DashboardChoices(selections = it.selections) }
  }

  companion object {
    const val DEFAULT_DEBOUNCE_MILLIS = 300L
  }
}
