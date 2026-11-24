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
package org.openforis.collectone.v2.web.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.openforis.collectone.v2.app.ui.common.VerticalScrollBar

/**
 * Centers [content] within the available viewport height when it fits, and scrolls vertically with
 * a slim scrollbar when the viewport is too short for the onboarding card.
 */
@Composable
internal fun WebOnboardingScrollContainer(
  modifier: Modifier = Modifier,
  scrollTag: String? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val scrollState = rememberScrollState()
  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val viewportHeight = maxHeight
    val scrollModifier =
      Modifier.fillMaxSize()
        .let { if (scrollTag != null) it.testTag(scrollTag) else it }
        .verticalScroll(scrollState)
    Box(modifier = scrollModifier, contentAlignment = Alignment.TopCenter) {
      Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = viewportHeight).padding(24.dp),
        contentAlignment = Alignment.Center,
        content = content,
      )
    }
    VerticalScrollBar(
      scrollState = scrollState,
      modifier =
        Modifier.align(Alignment.CenterEnd)
          .fillMaxHeight()
          .padding(vertical = 8.dp, horizontal = 2.dp),
    )
  }
}
