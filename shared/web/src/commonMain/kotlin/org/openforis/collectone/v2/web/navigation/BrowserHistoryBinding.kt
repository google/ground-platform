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

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Keeps the browser's [history] and the [navigator]'s back stack in step:
 * - Every page change writes the new page's URL ([WebUrls.pathOf]), adding a history entry, or
 *   replacing the current one when the page was replaced or updated in place.
 * - Each history entry stores the whole back stack, so the browser's Back and Forward buttons, and
 *   reloading the page, restore it.
 */
class BrowserHistoryBinding(
  private val history: BrowserHistory,
  private val navigator: WebNavigator,
) {
  /**
   * Binds until cancelled. With [keepInitialUrl], the URL stays as it is until the first page
   * change (an invitation link stays in the address bar until it's redeemed). [onLinkOpened]
   * receives the links the user opens by editing the URL's hash (invitations and earlier console
   * URLs).
   */
  suspend fun bind(keepInitialUrl: Boolean = false, onLinkOpened: (WebLink) -> Unit) =
    coroutineScope {
      launch {
        history.pops.collect { state ->
          val routes = decodeBackStack(state)
          if (routes != null) {
            navigator.restore(routes)
          } else {
            onLinkOpened(WebUrls.parse(history.path, history.hash))
          }
        }
      }
      var skipNextWrite = keepInitialUrl
      navigator.backStackChanges.collect { routes ->
        if (skipNextWrite) {
          skipNextWrite = false
          navigator.takeReplacesHistoryEntry()
        } else {
          write(routes)
        }
      }
    }

  /**
   * Synchronizes [routes] to [history], replacing the current history entry on initial load,
   * in-place route updates, or duplicate states, and pushing a new entry on page transitions.
   */
  private fun write(routes: List<WebRoute>) {
    val state = encodeBackStack(routes)
    val path = WebUrls.pathOf(routes.last())
    val replace =
      navigator.takeReplacesHistoryEntry() || history.state == null || history.state == state
    if (replace) history.replace(path, state) else history.push(path, state)
  }

  companion object {
    /**
     * The back stack to open first: the one stored with the current history entry when the page is
     * reloaded, or else the page [link] names alone (the surveys list for any other link).
     */
    fun initialBackStack(history: BrowserHistory, link: WebLink): List<WebRoute> {
      val route = (link as? WebLink.Page)?.route ?: SurveysRoute
      val stored = decodeBackStack(history.state)
      return if (stored != null && stored.last() == route) stored else listOf(route)
    }

    /** The history state of [routes]: one URL path per line. */
    internal fun encodeBackStack(routes: List<WebRoute>): String =
      routes.joinToString("\n", transform = WebUrls::pathOf)

    /** The back stack in a history [state], or `null` when it holds none. */
    internal fun decodeBackStack(state: String?): List<WebRoute>? =
      state
        ?.lines()
        ?.map { (WebUrls.parse(it) as? WebLink.Page)?.route ?: return null }
        ?.takeIf { it.isNotEmpty() }
  }
}
