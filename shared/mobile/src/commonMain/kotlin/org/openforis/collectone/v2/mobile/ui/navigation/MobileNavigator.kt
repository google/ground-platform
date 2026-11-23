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
package org.openforis.collectone.v2.mobile.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph

/**
 * Moves the mobile app between its [MobileRoute]s.
 *
 * Once the app's `NavHost` [attach]es its [NavController], the back stack lives there: the
 * controller keeps it across Android process death and, on mobile web, mirrors it in the browser
 * history. Until then (and in unit tests, which run without composition) the navigator keeps the
 * back stack itself, starting at [startRoute].
 *
 * [onRouteChanged] is called whenever the [currentRoute] changes, whoever changed it (this
 * navigator, the system Back gesture, or the browser's Back button).
 *
 * A back stack restored from before the launch (after Android process death) is kept only when
 * every route on it [mayRestore]; otherwise the app starts over at [startRoute].
 */
class MobileNavigator(
  startRoute: MobileRoute = MobileRoute.SignIn,
  private val onRouteChanged: (MobileRoute) -> Unit = {},
  private val mayRestoreRoute: (MobileRoute) -> Boolean = { true },
) {
  private val detachedBackStack = mutableStateListOf(startRoute)
  private var navController: NavController? = null
  private var attachedRoute by mutableStateOf<MobileRoute?>(null)
  private val destinationListener = NavController.OnDestinationChangedListener { controller, _, _ ->
    val route =
      controller.currentBackStackEntry?.toMobileRoute() ?: return@OnDestinationChangedListener
    if (route != attachedRoute) {
      attachedRoute = route
      onRouteChanged(route)
    }
  }

  /** The route on top of the back stack: the screen the user sees. */
  val currentRoute: MobileRoute
    get() = attachedRoute ?: detachedBackStack.last()

  /** The routes of the back stack, bottom first. */
  val backStack: List<MobileRoute>
    get() =
      navController
        ?.currentBackStack
        ?.value
        ?.mapNotNull { it.toMobileRoute() }
        ?.takeIf {
          it.isNotEmpty()
        } ?: detachedBackStack.toList()

  /** Shows [route] on top of the current one; Back returns here. No-op if it's already on top. */
  fun navigate(route: MobileRoute) {
    if (route == currentRoute) return
    withController(
      attached = { it.navigate(route) { launchSingleTop = true } },
      detached = { detachedBackStack.add(route) },
    )
  }

  /** Starts over at [route], clearing the back stack (for example after signing in or out). */
  fun resetTo(route: MobileRoute) {
    if (backStack == listOf(route)) return
    withController(
      attached = { controller ->
        controller.navigate(route) {
          popUpTo(controller.graph.id) { inclusive = true }
          launchSingleTop = true
        }
      },
      detached = {
        detachedBackStack.clear()
        detachedBackStack.add(route)
      },
    )
  }

  /**
   * Pops the back stack down to [route], keeping it. Returns false (and changes nothing) when
   * [route] isn't on the back stack.
   */
  fun popBackTo(route: MobileRoute): Boolean {
    if (route !in backStack) return false
    if (route == currentRoute) return true
    withController(
      attached = { it.popBackStack(route, inclusive = false) },
      detached = {
        while (detachedBackStack.last() != route) detachedBackStack.removeAt(
          detachedBackStack.lastIndex
        )
      },
    )
    return true
  }

  /**
   * Pops the current route. Returns false (and changes nothing) at the bottom of the back stack.
   */
  fun back(): Boolean {
    if (backStack.size < 2) return false
    withController(
      attached = { it.popBackStack() },
      detached = { detachedBackStack.removeAt(detachedBackStack.lastIndex) },
    )
    return true
  }

  /**
   * Whether [route], shown before the launch (restored after Android process death, or named by the
   * browser history on a reload), may be shown again.
   */
  fun mayRestore(route: MobileRoute): Boolean = mayRestoreRoute(route)

  /**
   * Hands the back stack to [controller], whose graph the `NavHost` has set. A controller that
   * restored a back stack (after Android process death) keeps it when every route on it
   * [mayRestore], and otherwise starts over at this navigator's routes; a new one gets this
   * navigator's routes above its start destination. [detach] hands the back stack back.
   */
  fun attach(controller: NavController) {
    if (navController === controller) return
    navController?.let { detach(it) }
    val restored = controller.currentBackStack.value.mapNotNull { it.toMobileRoute() }
    val pending = detachedBackStack.toList()
    navController = controller
    if (!restored.all(::mayRestore)) {
      controller.navigate(pending.first()) {
        popUpTo(controller.graph.id) { inclusive = true }
        launchSingleTop = true
      }
      pending.drop(1).forEach { route -> controller.navigate(route) }
    } else if (restored.size <= 1) {
      pending.drop(1).forEach { route -> controller.navigate(route) }
    }
    controller.addOnDestinationChangedListener(destinationListener)
  }

  /** Takes the back stack back from [controller] (when its `NavHost` leaves composition). */
  fun detach(controller: NavController) {
    if (navController !== controller) return
    val routes = backStack
    controller.removeOnDestinationChangedListener(destinationListener)
    navController = null
    attachedRoute = null
    detachedBackStack.clear()
    detachedBackStack.addAll(routes)
  }

  private inline fun withController(
    attached: (NavController) -> Unit,
    detached: () -> Unit,
  ) {
    val controller = navController
    if (controller != null) {
      attached(controller)
    } else {
      val before = currentRoute
      detached()
      if (currentRoute != before) onRouteChanged(currentRoute)
    }
  }

  private companion object {
    fun NavBackStackEntry.toMobileRoute(): MobileRoute? = destination.toMobileRoute()
  }
}

/** The route [this] destination of the mobile app's graph shows, or null for a graph. */
private fun NavDestination.toMobileRoute(): MobileRoute? =
  if (this is NavGraph) null else MobileRoute.all.firstOrNull { hasRoute(it::class) }

/**
 * The route of [this] controller's graph that a URL fragment names (`survey` for `#survey`), or
 * null when it names none. The graph must be set.
 */
fun NavController.mobileRouteOfUrlFragment(fragment: String): MobileRoute? =
  fragment.takeIf { it.isNotEmpty() }?.let { graph.findNode(it) }?.toMobileRoute()
