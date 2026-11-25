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
package org.openforis.collectone.v2.web.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.resources.Res as AppRes
import org.openforis.collectone.v2.app.resources.app_about
import org.openforis.collectone.v2.app.resources.app_close
import org.openforis.collectone.v2.app.ui.common.AboutContent
import org.openforis.collectone.v2.core.forms.ui.controls.TextButton

/** Test tag of the [WebAboutDialog]. */
const val WEB_ABOUT_DIALOG_TAG = "web_about_dialog"

/**
 * The web console's About dialog, opened from the account menu: the product name and app version,
 * what Collect One is for and its mission, and the link to the project's contributors
 * ([AboutContent], shared with the mobile app's About screen).
 */
@Composable
fun WebAboutDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag(WEB_ABOUT_DIALOG_TAG),
    title = { Text(text = stringResource(AppRes.string.app_about)) },
    text = {
      Column(
        modifier =
          Modifier.widthIn(min = 360.dp, max = 480.dp)
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
      ) {
        AboutContent()
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(AppRes.string.app_close)) }
    },
  )
}
