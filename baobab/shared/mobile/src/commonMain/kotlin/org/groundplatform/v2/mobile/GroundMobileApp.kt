/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.groundplatform.v2.core.forms.ui.GroundTheme
import org.groundplatform.v2.core.forms.ui.LocalGroundBrandFontFamily

/** Root Compose Multiplatform entry point shared identically by `androidApp` and `iosApp`. */
@Composable
fun GroundMobileApp() {
  GroundTheme {
    val brandFont = LocalGroundBrandFontFamily.current
    Surface(modifier = Modifier.fillMaxSize()) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text =
            buildAnnotatedString {
              withStyle(SpanStyle(fontFamily = brandFont, fontWeight = FontWeight.ExtraBold)) {
                append("Ground")
              }
              append(" 2.0 Mobile")
            },
          style = MaterialTheme.typography.headlineMedium,
        )
      }
    }
  }
}
