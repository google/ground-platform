/**
 * Copyright 2023 Google LLC
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
import {MatLegacySelectChange as MatSelectChange} from '@angular/material/legacy-select';
import {AclEntry} from 'app/models/acl-entry.model';
import {Role} from 'app/models/role.model';
import {Survey} from 'app/models/survey.model';
import {SurveyService} from 'app/services/survey/survey.service';
import {Subscription, take} from 'rxjs';
import {Map} from 'immutable';

@Component({
  selector: 'ground-share-list',
  templateUrl: './share-list.component.html',
  styleUrls: ['./share-list.component.scss'],
})
export class ShareListComponent {
  surveyId?: string;
  acl?: Array<AclEntry>;

  private subscription = new Subscription();

  readonly ROLE_OPTIONS = [
    {label: 'Data Collector', value: Role.DATA_COLLECTOR},
    {label: 'Survey Organizer', value: Role.SURVEY_ORGANIZER},
    {label: 'Viewer', value: Role.VIEWER},
  ];

  roles = Role;

  constructor(readonly surveyService: SurveyService) {
    this.subscription.add(
      this.surveyService
        .getActiveSurvey$()
        .subscribe(survey => this.onSurveyLoaded(survey))
    );
  }

  private onSurveyLoaded(survey: Survey): void {
    this.surveyId = survey.id;

    this.acl = survey.acl
      .entrySeq()
      .map(([key, value]) => new AclEntry(key, value))
      .toArray();
  }

  onRoleChange(event: MatSelectChange, index: number) {
    if (!this.acl) {
      return;
    }
    // value holds the selected Role enum value, or -1 if "Remove" was selected.
    if (event.value < 0) {
      // Remove data collector.
      this.acl.splice(index, 1);
    } else {
      // Update data collector role.
      this.acl[index] = new AclEntry(this.acl[index].email, event.value);
    }

    this.surveyService.updateAcl(
      this.surveyId!,
      Map(this.acl.map(entry => [entry.email, entry.role]))
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
