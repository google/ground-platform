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

/**
 * The page changes a web console page asks for. Pages and route adapters depend on this interface
 * rather than on the navigation library, so they can be shown and tested without a `NavHost`.
 */
interface WebPageNavigation {
  /** Opens [route] on top of the current page; Back returns to the current page. */
  fun open(route: WebRoute)

  /**
   * Goes to [route] like a menu item or an up arrow: returns to it if it's an earlier page of the
   * back stack, and otherwise opens it on top of the current page.
   */
  fun returnTo(route: WebRoute)

  /**
   * Shows the current page with new arguments ([route] is the same page, on another tab, wizard
   * step, editor section, or Impact focus) without adding a page to the back stack.
   */
  fun update(route: WebRoute)
}
