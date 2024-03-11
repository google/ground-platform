/**
 * Copyright 2024 The Ground Authors.
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

import {DataCollectionStrategy, Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Survey} from 'app/models/survey.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';

import {ImportDialogComponent} from '../import-dialog/import-dialog.component';

@Component({
  selector: 'loi-editor',
  templateUrl: './loi-editor.component.html',
  styleUrls: ['./loi-editor.component.scss'],
})
export class LoiEditorComponent {
  @Input() canImport!: boolean;
  @Input() survey!: Survey;
  @Input() job!: Job;
  @Input() lois!: List<LocationOfInterest>;

  @Output() updateStrategy: EventEmitter<DataCollectionStrategy> =
    new EventEmitter<DataCollectionStrategy>();

  predefinedLois = List<LocationOfInterest>([]);

  DataCollectionStrategy = DataCollectionStrategy;

  constructor(
    private dataStoreService: DataStoreService,
    private importDialog: MatDialog
  ) {}

  ngOnChanges() {
    this.predefinedLois = this.lois.filter(loi => loi.predefined !== false);
  }

  importLois() {
    if (!this.survey.id || !this.job?.id) return;

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
        ? DataCollectionStrategy.MIXED
        : DataCollectionStrategy.PREDEFINED
    );
  }
}
