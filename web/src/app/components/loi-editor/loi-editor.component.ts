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
import {MatDialog} from '@angular/material/dialog';
import {MatSlideToggleChange} from '@angular/material/slide-toggle';
import {List} from 'immutable';

import {DataCollectionStrategy, Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Survey} from 'app/models/survey.model';
import {
  DialogData,
  DialogType,
  JobDialogComponent,
} from 'app/pages/edit-survey/job-dialog/job-dialog.component';
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

  DataCollectionStrategy = DataCollectionStrategy;

  constructor(
    private dataStoreService: DataStoreService,
    private dialog: MatDialog
  ) {}

  importLois() {
    this.dialog.open(ImportDialogComponent, {
      data: {surveyId: this.survey.id, jobId: this.job.id},
      width: '350px',
      maxHeight: '800px',
    });
  }

  clearLois() {
    this.dialog
      .open(JobDialogComponent, {
        data: {
          dialogType: DialogType.DeleteLois,
          surveyId: this.survey.id,
          jobId: this.job.id,
        },
        panelClass: 'small-width-dialog',
      })
      .afterClosed()
      .subscribe(async (result: DialogData) => {
        if (!result) return;

        for (const loi of this.lois) {
          this.dataStoreService.deleteLocationOfInterest(
            this.survey.id,
            loi.id
          );
        }
      });
  }

  toggleDataCollectorsCanAddLois(event: MatSlideToggleChange) {
    if (!event.checked) {
      this.dialog
        .open(JobDialogComponent, {
          data: {
            dialogType: DialogType.DisableFreeForm,
          },
          panelClass: 'small-width-dialog',
        })
        .afterClosed()
        .subscribe(async (result: DialogData) => {
          if (!result) {
            event.source.checked = event.checked = true;
            return;
          }

          this.updateStrategy.emit(DataCollectionStrategy.PREDEFINED);
        });
    } else {
      this.updateStrategy.emit(DataCollectionStrategy.MIXED);
    }
  }
}
