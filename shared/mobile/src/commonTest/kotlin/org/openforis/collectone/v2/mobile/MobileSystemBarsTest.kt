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
package org.openforis.collectone.v2.mobile

import kotlin.test.Test
import kotlin.test.assertEquals

class MobileSystemBarsTest {
  @Test
  fun mainMap_showsItsContentBehindTheStatusBarAndTheSheetBehindTheNavigationBar() {
    assertEquals(
      SystemBarBackdrops(top = SystemBarBackdrop.MAP, bottom = SystemBarBackdrop.SHEET),
      mobileSystemBarBackdrops(
        isMainMapScreenShowing = true,
        isSelectingOfflineArea = false,
        openForm = null,
      ),
    )
  }

  @Test
  fun mainMap_underAFeaturesHistory_continuesTheHistorySurfaceBehindTheNavigationBar() {
    assertEquals(
      SystemBarBackdrops(top = SystemBarBackdrop.MAP, bottom = SystemBarBackdrop.SURFACE),
      mobileSystemBarBackdrops(
        isMainMapScreenShowing = true,
        isSelectingOfflineArea = false,
        openForm = null,
        isFeatureHistoryShowing = true,
      ),
    )
  }

  @Test
  fun mainMap_whileSelectingAnOfflineArea_showsTheMapBehindBothBars() {
    assertEquals(
      SystemBarBackdrops(top = SystemBarBackdrop.MAP, bottom = SystemBarBackdrop.MAP),
      mobileSystemBarBackdrops(
        isMainMapScreenShowing = true,
        isSelectingOfflineArea = true,
        openForm = null,
      ),
    )
  }

  @Test
  fun dataCollectionForm_continuesItsBannerAndItsActionBarBehindTheBars() {
    assertEquals(
      SystemBarBackdrops(
        top = SystemBarBackdrop.FORM_BANNER,
        bottom = SystemBarBackdrop.FORM_ACTIONS,
      ),
      mobileSystemBarBackdrops(
        isMainMapScreenShowing = false,
        isSelectingOfflineArea = false,
        openForm = FormEdges(),
      ),
    )
  }

  @Test
  fun dataCollectionForm_onTheFeaturePickerStep_continuesThePickerActionsBehindTheNavigationBar() {
    assertEquals(
      SystemBarBackdrops(
        top = SystemBarBackdrop.FORM_BANNER,
        bottom = SystemBarBackdrop.PICKER_ACTIONS,
      ),
      mobileSystemBarBackdrops(
        isMainMapScreenShowing = false,
        isSelectingOfflineArea = false,
        openForm = FormEdges(isFeaturePickerStep = true),
      ),
    )
  }

  @Test
  fun dataCollectionForm_onceSubmitted_continuesTheSuccessScreenSurfaceBehindBothBars() {
    assertEquals(
      SystemBarBackdrops(top = SystemBarBackdrop.SURFACE, bottom = SystemBarBackdrop.SURFACE),
      mobileSystemBarBackdrops(
        isMainMapScreenShowing = false,
        isSelectingOfflineArea = false,
        openForm = FormEdges(isSubmissionConfirmationShown = true),
      ),
    )
  }

  @Test
  fun otherScreens_continueTheirSurfaceBehindBothBars() {
    assertEquals(
      SystemBarBackdrops(top = SystemBarBackdrop.SURFACE, bottom = SystemBarBackdrop.SURFACE),
      mobileSystemBarBackdrops(
        isMainMapScreenShowing = false,
        isSelectingOfflineArea = false,
        openForm = null,
      ),
    )
  }

  @Test
  fun anOfflineAreaSelectionLeftActive_doesNotAffectOtherScreens() {
    assertEquals(
      SystemBarBackdrops(top = SystemBarBackdrop.SURFACE, bottom = SystemBarBackdrop.SURFACE),
      mobileSystemBarBackdrops(
        isMainMapScreenShowing = false,
        isSelectingOfflineArea = true,
        openForm = null,
      ),
    )
  }
}
