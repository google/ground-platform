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
package org.openforis.collectone.v2.web.ui.text

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.web.domain.usecase.InviteCollaboratorUseCase.Rejection
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.surveyeditor_invite_already_joined
import org.openforis.collectone.v2.web.resources.surveyeditor_invite_enter_name
import org.openforis.collectone.v2.web.resources.surveyeditor_invite_invalid_email
import org.openforis.collectone.v2.web.resources.surveyeditor_invite_is_owner
import org.openforis.collectone.v2.web.resources.surveyeditor_invite_not_invited

/** The message shown to the person editing a survey's access list when a change is refused. */
val Rejection.messageRes: StringResource
  get() =
    when (this) {
      Rejection.INVALID_EMAIL -> Res.string.surveyeditor_invite_invalid_email
      Rejection.IS_OWNER -> Res.string.surveyeditor_invite_is_owner
      Rejection.NAME_REQUIRED -> Res.string.surveyeditor_invite_enter_name
      Rejection.NOT_INVITED -> Res.string.surveyeditor_invite_not_invited
      Rejection.ALREADY_JOINED -> Res.string.surveyeditor_invite_already_joined
    }
