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

import {Component, EventEmitter, Input, Output} from '@angular/core';
import {MatLegacyDialog as MatDialog} from '@angular/material/legacy-dialog';
import {MatSlideToggleChange} from '@angular/material/slide-toggle';
import {List} from 'immutable';

import {ImportDialogComponent} from 'app/components/import-dialog/import-dialog.component';
import {DataCollectionStrategy, Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Survey} from 'app/models/survey.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';

@Component({
  selector: 'loi-selection',
  templateUrl: './loi-selection.component.html',
  styleUrls: ['./loi-selection.component.scss'],
})
export class LoiSelectionComponent {
  @Input() canImport!: boolean;
  @Input() dataCollectorsCanAddLois!: boolean;
  @Input() lois!: List<LocationOfInterest>;
  @Input() survey!: Survey;
  @Input() jobId?: string;
  @Output() updateStrategy: EventEmitter<DataCollectionStrategy> =
    new EventEmitter<DataCollectionStrategy>();

  job?: Job;

  DataCollectionStrategy = DataCollectionStrategy;

  constructor(
    private dataStoreService: DataStoreService,
    private importDialog: MatDialog
  ) {}

  ngOnChanges() {
    this.job = this.jobId
      ? this.survey.getJob(this.jobId)
      : this.survey.jobs.toList().first();
  }

  onImportLois() {
    if (!this.survey.id || !this.job?.id) {
      return;
    }

    this.importDialog.open(ImportDialogComponent, {
      data: {surveyId: this.survey.id, jobId: this.job.id},
      width: '350px',
      maxHeight: '800px',
    });
  }

  clearLois(surveyId: string, lois: List<LocationOfInterest>) {
    for (const loi of lois) {
      this.dataStoreService.deleteLocationOfInterest(surveyId, loi.id);
    }
  }

  toggleDataCollectorsCanAddLois(event: MatSlideToggleChange) {
    this.updateStrategy.emit(
      event.checked
        ? DataCollectionStrategy.AD_HOC
        : DataCollectionStrategy.PREDEFINED
    );
  }

  getDisplayName(loi: LocationOfInterest): string {
    return LocationOfInterestService.getDisplayName(loi);
  }
}
