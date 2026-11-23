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
package org.openforis.collectone.v2.web.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_link_unavailable

/** A message the console shows after navigation took the user somewhere they didn't ask for. */
enum class NavigationNotice(val message: StringResource) {
  /** A link named a page that doesn't exist or that the signed-in user can't open. */
  LINK_UNAVAILABLE(Res.string.web_link_unavailable)
}

/**
 * Moves between the web console's pages ([WebRoute]s) on [navController]'s back stack, and records
 * how each move should show in the browser's history (see [BrowserHistoryBinding]).
 */
class WebNavigator(private val navController: NavHostController) : WebPageNavigation {

  private var replacesHistoryEntry = false

  // In-place updates (tabs, wizard steps, editor sections, Impact focus) keyed by back-stack entry
  // ID. `launchSingleTop` replaces the NavBackStackEntry when route arguments change, which would
  // tear down the page composition and re-run its entry effects (such as `startWizard` or
  // `onEditorOpened`).
  private val _updatedRoutes = MutableStateFlow<Map<String, WebRoute>>(emptyMap())

  internal val updatedRoutes: StateFlow<Map<String, WebRoute>> = _updatedRoutes.asStateFlow()

  private val _notice = MutableStateFlow<NavigationNotice?>(null)

  /** The message to show after a redirect, until [dismissNotice]. */
  val notice: StateFlow<NavigationNotice?> = _notice.asStateFlow()

  /** The page on top of the back stack, or `null` before the `NavHost` sets its graph. */
  val currentRoute: WebRoute?
    get() =
      navController.currentBackStackEntry?.let { entry ->
        _updatedRoutes.value[entry.id] ?: entry.toWebRoute()
      }

  /** The pages of the back stack, oldest first. */
  val backStack: List<WebRoute>
    get() {
      val updates = _updatedRoutes.value
      return navController.currentBackStack.value.mapNotNull { entry ->
        updates[entry.id] ?: entry.toWebRoute()
      }
    }

  /** [backStack] on every change. */
  val backStackChanges: Flow<List<WebRoute>> =
    combine(navController.currentBackStack, _updatedRoutes) { entries, updates ->
        entries.mapNotNull { entry -> updates[entry.id] ?: entry.toWebRoute() }
      }
      .filter { it.isNotEmpty() }
      .distinctUntilChanged()

  /**
   * Whether the latest change of the back stack replaced the current page (so the browser should
   * replace its history entry) rather than adding one. Reading it resets it.
   */
  internal fun takeReplacesHistoryEntry(): Boolean = replacesHistoryEntry.also {
    replacesHistoryEntry = false
  }

  override fun open(route: WebRoute) {
    if (route == currentRoute) return
    navController.navigate(route)
    pruneUpdatedRoutes()
  }

  override fun returnTo(route: WebRoute) {
    if (!popBackTo(route)) open(route)
  }

  override fun update(route: WebRoute) {
    val entry = navController.currentBackStackEntry ?: return
    val current = _updatedRoutes.value[entry.id] ?: entry.toWebRoute() ?: return
    // Only the current page updates itself; a late update from a page being left is dropped.
    if (route == current || !current.isSamePageAs(route)) return
    replacesHistoryEntry = true
    val liveIds = navController.currentBackStack.value.mapTo(HashSet()) { it.id }
    _updatedRoutes.update { updates -> updates.filterKeys { it in liveIds } + (entry.id to route) }
  }

  /** Shows [route] in place of the current page. */
  fun replace(route: WebRoute) {
    if (route == currentRoute) return
    replacesHistoryEntry = true
    val currentDestinationId = navController.currentBackStackEntry?.destination?.id
    navController.navigate(route) {
      if (currentDestinationId != null) popUpTo(currentDestinationId) { inclusive = true }
    }
    pruneUpdatedRoutes()
  }

  /**
   * Leaves the current page for [route]: returns to it if it's an earlier page of the back stack,
   * and otherwise shows it in place of the current page.
   */
  fun leaveTo(route: WebRoute) {
    if (!popBackTo(route)) replace(route)
  }

  /** Pops the back stack down to an earlier [route]; returns whether there was one. */
  private fun popBackTo(route: WebRoute): Boolean {
    val stack = backStack
    val targetIndex = stack.dropLast(1).lastIndexOf(route)
    if (targetIndex < 0) return false
    repeat(stack.lastIndex - targetIndex) { if (!navController.popBackStack()) return false }
    pruneUpdatedRoutes()
    return true
  }

  /** Shows [route] in place of a page the user can't see, explaining why with [notice]. */
  fun redirect(route: WebRoute, notice: NavigationNotice?) {
    if (notice != null) _notice.value = notice
    replace(route)
  }

  /** Returns to the previous page, or shows [fallback] in place of the first one. */
  fun back(fallback: WebRoute) {
    if (navController.previousBackStackEntry != null) {
      navController.popBackStack()
      pruneUpdatedRoutes()
    } else {
      replace(fallback)
    }
  }

  /** Clears the back stack and shows [route] alone, e.g. after signing out. */
  fun resetTo(route: WebRoute) {
    _updatedRoutes.value = emptyMap()
    navController.navigate(route) { popUpTo(navController.graph.id) { inclusive = true } }
  }

  /**
   * Makes [routes] (oldest first) the back stack, keeping the pages it shares with the current one
   * so they keep their state, e.g. when the user moves through the browser's history.
   */
  fun restore(routes: List<WebRoute>) {
    if (routes.isEmpty() || routes == backStack) return
    replacesHistoryEntry = true
    val current = backStack
    val shared = current.zip(routes).takeWhile { (a, b) -> a == b }.size
    if (shared == 0) {
      _updatedRoutes.value = emptyMap()
      navController.navigate(routes.first()) {
        popUpTo(navController.graph.id) { inclusive = true }
      }
    } else {
      repeat(current.size - shared) { navController.popBackStack() }
      pruneUpdatedRoutes()
    }
    routes.drop(maxOf(shared, 1)).forEach { navController.navigate(it) }
  }

  /** Drops in-place route overrides for back-stack entries that have been popped. */
  private fun pruneUpdatedRoutes() {
    if (_updatedRoutes.value.isEmpty()) return
    val liveIds = navController.currentBackStack.value.mapTo(HashSet()) { it.id }
    _updatedRoutes.update { current -> current.filterKeys { it in liveIds } }
  }

  /** Clears the current navigation error notice banner. */
  fun dismissNotice() {
    _notice.value = null
  }
}

/** Whether [other] updates the same open page in place (e.g. step, tab, section, or focus). */
private fun WebRoute.isSamePageAs(other: WebRoute): Boolean =
  when (this) {
    SurveysRoute -> other is SurveysRoute
    is SurveyDashboardRoute -> other is SurveyDashboardRoute && other.surveyId == surveyId
    is SurveyEditorRoute -> other is SurveyEditorRoute && other.surveyId == surveyId
    is CreateSurveyRoute -> other is CreateSurveyRoute && other.organizationId == organizationId
    OrganizationsRoute -> other is OrganizationsRoute
    is OrganizationRoute -> other is OrganizationRoute && other.organizationId == organizationId
    is ImpactRoute -> other is ImpactRoute
    is AdminRoute -> other is AdminRoute
  }

/** The [WebRoute] this entry shows, or `null` for the navigation graph's own entry. */
internal fun NavBackStackEntry.toWebRoute(): WebRoute? =
  with(destination) {
    when {
      hasRoute<SurveysRoute>() -> SurveysRoute
      hasRoute<SurveyDashboardRoute>() -> toRoute<SurveyDashboardRoute>()
      hasRoute<SurveyEditorRoute>() -> toRoute<SurveyEditorRoute>()
      hasRoute<CreateSurveyRoute>() -> toRoute<CreateSurveyRoute>()
      hasRoute<OrganizationsRoute>() -> OrganizationsRoute
      hasRoute<OrganizationRoute>() -> toRoute<OrganizationRoute>()
      hasRoute<ImpactRoute>() -> toRoute<ImpactRoute>()
      hasRoute<AdminRoute>() -> toRoute<AdminRoute>()
      else -> null
    }
  }
