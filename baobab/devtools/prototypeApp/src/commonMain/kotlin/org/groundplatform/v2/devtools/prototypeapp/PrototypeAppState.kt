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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.groundplatform.v2.app.di.AppDataHolder
import org.groundplatform.v2.app.domain.model.PrototypeScreen
import org.groundplatform.v2.app.ui.AppSession
import org.groundplatform.v2.mobile.ui.MobileAppState
import org.groundplatform.v2.web.ui.WebAppState
import org.groundplatform.v2.web.ui.WebPage
import org.groundplatform.v2.workbench.ui.WorkbenchState

/**
 * State of the combined Ground 2.0 UI Prototype (`devtools/prototypeApp`): the [mobile] app shown
 * in the workbench's device preview, the [web] app, and the [workbench] around the device preview.
 *
 * The mobile and web apps share one [AppDataHolder] (so data changes made in one are live in the
 * other) and one settings ViewModel (so they share one theme), while their navigation and selection
 * stay separate. This class also routes between the workbench page and the web app's pages.
 */
class PrototypeAppState(
  initialScreen: PrototypeScreen = PrototypeScreen.SIGN_IN,
  val dataHolder: AppDataHolder = AppDataHolder(),
) {
  private val settings = AppSession.createSettingsViewModel(dataHolder)

  /** The mobile app shown in the workbench's device preview. */
  val mobile: MobileAppState = MobileAppState(initialScreen, dataHolder, settings)

  /** The web app. */
  val web: WebAppState = WebAppState(dataHolder, settings)

  /** The workbench around the device preview, also driving the [web] app's form and resets. */
  val workbench: WorkbenchState = WorkbenchState(mobile, linkedSessions = listOf(web))

  /** Whether the workbench page with the mobile device preview is shown (else a [web] page). */
  var isMobilePrototypeShown by mutableStateOf(true)
    private set

  /** The web page shown, or `null` while the mobile device preview is shown. */
  val activeWebPage: WebPage?
    get() = if (isMobilePrototypeShown) null else web.activePage

  /** Listener invoked on every page switch with the new [activeWebPage]. */
  var onPageChanged: ((WebPage?) -> Unit)? = null

  init {
    web.onPageChanged = { page ->
      isMobilePrototypeShown = false
      onPageChanged?.invoke(page)
    }
    web.onShowMobilePrototype = ::showMobilePrototype
  }

  /** Shows the workbench page with the mobile device preview. */
  fun showMobilePrototype() {
    isMobilePrototypeShown = true
    onPageChanged?.invoke(null)
  }

  /** Shows the web app's [page], or the mobile device preview when [page] is `null`. */
  fun showPage(page: WebPage?) {
    if (page == null) showMobilePrototype() else web.selectPage(page)
  }

  /** Whether the shared theme is dark. */
  val isDarkTheme: Boolean
    get() = mobile.isDarkTheme

  companion object {
    /** URL hash of the workbench page with the mobile device preview. */
    const val MOBILE_PROTOTYPE_HASH = "prototype"

    /** The URL hash for [page] (`null` for the mobile device preview). */
    fun hashFor(page: WebPage?, organizationId: String?): String =
      page?.let { WebPage.hashFor(it, organizationId) } ?: MOBILE_PROTOTYPE_HASH
  }
}
