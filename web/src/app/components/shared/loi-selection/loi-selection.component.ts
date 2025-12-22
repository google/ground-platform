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

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { List } from 'immutable';

import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { Survey } from 'app/models/survey.model';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { getLoiIcon } from 'app/utils/utils';

@Component({
    selector: 'loi-selection',
    templateUrl: './loi-selection.component.html',
    styleUrls: ['./loi-selection.component.scss'],
    standalone: false
})
export class LoiSelectionComponent {
  @Input() lois!: List<LocationOfInterest>;
  @Input() survey!: Survey;
  @Input() jobId?: string;
  @Output() updateStrategy: EventEmitter<DataCollectionStrategy> =
    new EventEmitter<DataCollectionStrategy>();

  job?: Job;

  DataCollectionStrategy = DataCollectionStrategy;

  getLoiIcon = getLoiIcon;

  constructor() {}

  ngOnChanges() {
    this.job = this.jobId
      ? this.survey.getJob(this.jobId)
      : this.survey.jobs.toList().first();
  }

  getDisplayName(loi: LocationOfInterest): string {
    return LocationOfInterestService.getDisplayName(loi);
  }
}
