/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.core.forms.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GroundLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF1E6F50),
    onPrimary = Color.White,
    secondary = Color(0xFF4C6358),
    background = Color(0xFFFBFDF9),
    surface = Color(0xFFFBFDF9),
  )

private val GroundDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF8BD6B1),
    onPrimary = Color(0xFF003825),
    secondary = Color(0xFFB3CCC0),
    background = Color(0xFF191C1A),
    surface = Color(0xFF191C1A),
  )

/** Shared Ground 2.0 Material 3 theme for Mobile and Web surfaces. */
@Composable
fun GroundTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
  val colorScheme = if (darkTheme) GroundDarkColorScheme else GroundLightColorScheme
  MaterialTheme(colorScheme = colorScheme, content = content)
}
