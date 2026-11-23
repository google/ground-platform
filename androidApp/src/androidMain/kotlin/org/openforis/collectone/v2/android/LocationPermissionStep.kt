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
package org.openforis.collectone.v2.android

/** What to do about the location permission when a location question needs a fix. */
internal enum class LocationPermissionStep {
  /** Nothing: the permission is granted, or the app already asked this session. */
  NONE,

  /** Ask for the permission with the system dialog. */
  REQUEST,

  /** Explain why the app needs the location first, since the user denied it before. */
  SHOW_RATIONALE;

  companion object {
    /**
     * The step for a location question that opens while the permission [isGranted] or not, after
     * the app [hasAsked] in this session or not, when Android says a rationale should be shown
     * ([shouldShowRationale], true once the user denied the permission without "Don't ask again").
     * The app asks at most once per session, so closing the dialog isn't followed by another one at
     * the next location question.
     */
    fun of(isGranted: Boolean, hasAsked: Boolean, shouldShowRationale: Boolean) =
      when {
        isGranted || hasAsked -> NONE
        shouldShowRationale -> SHOW_RATIONALE
        else -> REQUEST
      }
  }
}
