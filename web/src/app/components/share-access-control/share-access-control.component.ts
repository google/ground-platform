/**
 * Copyright 2023 The Ground Authors.
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

import {Component} from '@angular/core';
import {Map} from 'immutable';
import {Subscription} from 'rxjs';

import {Survey, SurveyGeneralAccess} from 'app/models/survey.model';
import {AuthService} from 'app/services/auth/auth.service';
import {DraftSurveyService} from 'app/services/draft-survey/draft-survey.service';

const generalAccessLabels = Map<SurveyGeneralAccess, any>([
  [
    SurveyGeneralAccess.RESTRICTED,
    {
      description: 'Only people with access can open with the link',
      icon: 'lock',
      label: 'Restricted',
    },
  ],
  [
    SurveyGeneralAccess.UNLISTED,
    {
      description:
        'Everyone with the survey QR code or link can collect data for this survey',
      icon: 'account_circle',
      label: 'Unlisted',
    },
  ],

  [
    SurveyGeneralAccess.PUBLIC,
    {
      description: 'Everyone can collect data for this survey',
      icon: 'public',
      label: 'Public',
    },
  ],
]);

@Component({
  selector: 'ground-share-access-control',
  templateUrl: './share-access-control.component.html',
  styleUrls: ['./share-access-control.component.scss'],
})
export class ShareAccessControlComponent {
  private subscription = new Subscription();

  selectedGeneralAccess!: SurveyGeneralAccess;

  SurveyGeneralAccess = SurveyGeneralAccess;

  generalAccessLabels = generalAccessLabels;

  constructor(
    readonly authService: AuthService,
    readonly draftSurveyService: DraftSurveyService
  ) {
    this.subscription.add(
      this.draftSurveyService
        .getSurvey$()
        .subscribe(survey => this.onSurveyLoaded(survey))
    );
  }

  get generalAccessKeys(): SurveyGeneralAccess[] {
    return Array.from(this.generalAccessLabels.keys());
  }

  private async onSurveyLoaded(survey: Survey): Promise<void> {
    this.selectedGeneralAccess =
      survey.generalAccess || SurveyGeneralAccess.RESTRICTED;
  }

  changeGeneralAccess(generalAccess: SurveyGeneralAccess) {
    this.selectedGeneralAccess = generalAccess;
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
