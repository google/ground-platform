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
import {List} from 'immutable';
import {LocationOfInterest} from 'app/models/loi.model';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {Observable, map} from 'rxjs';
import {SurveyService} from 'app/services/survey/survey.service';
import {Survey} from 'app/models/survey.model';
import {MatLegacyDialog as MatDialog} from '@angular/material/legacy-dialog';
import {ImportDialogComponent} from 'app/components/import-dialog/import-dialog.component';
import {DataStoreService} from 'app/services/data-store/data-store.service';

@Component({
  selector: 'loi-selection',
  templateUrl: './loi-selection.component.html',
  styleUrls: ['./loi-selection.component.scss'],
})
export class LoiSelectionComponent {
  lois$: Observable<List<LocationOfInterest>>;

  constructor(
    private dataStoreService: DataStoreService,
    private importDialog: MatDialog,
    readonly loiService: LocationOfInterestService,
    readonly surveyService: SurveyService
  ) {
    this.lois$ = this.loiService
      .getLocationsOfInterest$()
      .pipe(map(lois => LocationOfInterestService.getLoisWithNames(lois)));
  }

  onImportLois(survey: Survey) {
    const [job] = survey.jobs.values();
    if (!survey.id || !job.id) {
      return;
    }
    this.importDialog.open(ImportDialogComponent, {
      data: {surveyId: survey.id, jobId: job.id},
      width: '350px',
      maxHeight: '800px',
    });
  }

  clearLois(surveyId: string, lois: List<LocationOfInterest>) {
    for (const loi of lois) {
      this.dataStoreService.deleteLocationOfInterest(surveyId, loi.id);
    }
  }
}
