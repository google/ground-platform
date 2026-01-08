/**
 * Copyright 2020 The Ground Authors.
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

import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { List } from 'immutable';

import { Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { Survey } from 'app/models/survey.model';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { NavigationService } from 'app/services/navigation/navigation.service';

@Component({
  selector: 'ground-job-list',
  templateUrl: './job-list.component.html',
  styleUrls: ['./job-list.component.scss'],
  standalone: false,
})
export class JobListComponent {
  private loiService = inject(LocationOfInterestService);
  readonly navigationService = inject(NavigationService);

  activeSurvey = input<Survey>();

  readonly jobs = computed(() => {
    const survey = this.activeSurvey();
    return survey
      ? List(survey.jobs.valueSeq().toArray()).sortBy(l => l.index)
      : List<Job>();
  });

  readonly defaultLois = List<LocationOfInterest>();

  readonly lois = toSignal(this.loiService.getLocationsOfInterest$(), {
    initialValue: List<LocationOfInterest>(),
  });

  readonly loisByJob = computed(() => {
    return this.lois().groupBy(loi => loi.jobId);
  });

  constructor() {}

  isSidePanelExpanded() {
    return this.navigationService.getSidePanelExpanded();
  }
}
