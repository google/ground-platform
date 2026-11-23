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
@file:OptIn(ExperimentalWasmJsInterop::class)

package org.openforis.collectone.v2.app.ui.common

import androidx.compose.runtime.Composable
import org.openforis.collectone.v2.app.domain.model.AppEnvironment
import org.openforis.collectone.v2.app.domain.model.resolveWebEnvironment

@JsFun(
  """() => {
    if (typeof window === 'undefined') return '';
    const env = window.COLLECT_ONE_ENV;
    return typeof env === 'string' ? env : '';
  }"""
)
private external fun jsConfiguredAppEnv(): String

@JsFun("() => (typeof window !== 'undefined' && window.location ? window.location.hostname : '')")
private external fun jsLocationHostname(): String

@JsFun("() => (typeof window !== 'undefined' && window.location ? window.location.port : '')")
private external fun jsLocationPort(): String

@JsFun("() => (typeof window !== 'undefined' && window.COLLECT_ONE_DEV_SIGN_IN === true)")
private external fun jsIsDevSignIn(): Boolean

@Composable
actual fun platformAppEnvironment(): AppEnvironment =
  resolveWebEnvironment(
    configuredEnv = jsConfiguredAppEnv(),
    hostname = jsLocationHostname(),
    port = jsLocationPort(),
    isDevSignIn = jsIsDevSignIn(),
  )
