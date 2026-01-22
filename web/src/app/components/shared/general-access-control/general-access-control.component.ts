/**
 * Copyright 2025 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '@angular/localize/init';

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Map } from 'immutable';

import { Survey, SurveyGeneralAccess } from 'app/models/survey.model';

const generalAccessLabels = Map<
  SurveyGeneralAccess,
  { description: string; icon: string; label: string }
>([
  [
    SurveyGeneralAccess.RESTRICTED,
    {
      description: $localize`:@@app.texts.generalAccess.restricted:Only people with access can open with the link`,
      icon: 'lock',
      label: $localize`:@@app.labels.restricted:Restricted`,
    },
  ],
  [
    SurveyGeneralAccess.UNLISTED,
    {
      description: $localize`:@@app.texts.generalAccess.unlisted:Everyone with the survey QR code or link can collect data for this survey`,
      icon: 'account_circle',
      label: $localize`:@@app.labels.unlisted:Unlisted`,
    },
  ],
]);

@Component({
  selector: 'ground-general-access-control',
  templateUrl: './general-access-control.component.html',
  styleUrls: ['./general-access-control.component.scss'],
  standalone: false,
})
export class GeneralAccessControlComponent {
  @Input() survey?: Survey;
  @Output() onGeneralAccessChange = new EventEmitter<SurveyGeneralAccess>();

  readonly SurveyGeneralAccess = SurveyGeneralAccess;
  readonly generalAccessLabels = generalAccessLabels;

  get generalAccessKeys(): SurveyGeneralAccess[] {
    return Array.from(this.generalAccessLabels.keys());
  }

  get selectedGeneralAccess(): SurveyGeneralAccess {
    return this.survey?.generalAccess || SurveyGeneralAccess.RESTRICTED;
  }

  changeGeneralAccess(generalAccess: SurveyGeneralAccess) {
    this.onGeneralAccessChange.emit(generalAccess);
  }
}
