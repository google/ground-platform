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
import {ActivatedRoute} from '@angular/router';
import {Job} from 'app/models/job.model';
import {Task} from 'app/models/task/task.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {DialogService} from 'app/services/dialog/dialog.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {Observable} from 'rxjs';

@Component({
  selector: 'edit-job',
  templateUrl: './edit-job.component.html',
  styleUrls: ['./edit-job.component.scss'],
})
export class EditJobComponent {
  // jobId$: Observable<string>;
  surveyId?: string;
  job$?: Observable<Job>;

  constructor(
    route: ActivatedRoute,
    private dataStoreService: DataStoreService,
    private navigationService: NavigationService,
    private dialogService: DialogService
  ) {
    this.navigationService.getSurveyId$().subscribe(surveyId => {
      if (surveyId) {
        route.params.subscribe(params => {
          const jobId = params['id'];
          this.job$ = this.dataStoreService.loadJob$(jobId, surveyId);
        });
      }
    });
  }

  // async ngOnInit(): Promise<void> {
  //   this.navigationService.getSurveyId$().subscribe(async surveyId => {
  //     if (surveyId) {
  //       this.jobId$.subscribe(jobId => {
  //         this.job$ = this.dataStoreService.loadJob$(jobId, surveyId);
  //       });
  //     }
  //   });
  // }

  getIndex(index: number) {
    return index;
  }

  onTaskUpdate(event: Task, index: number) {
    // const taskId = this.tasks.get(index)?.id;
    // const task = new Task(
    //   taskId || '',
    //   event.type,
    //   event.label,
    //   event.required,
    //   index,
    //   event.multipleChoice
    // );
    // this.tasks = this.tasks.set(index, task);
  }

  onTaskDelete(index: number) {
    // this.dialogService
    //   .openConfirmationDialog(
    //     'Warning',
    //     'Are you sure you wish to delete this question? Any associated data ' +
    //       'will be lost. This cannot be undone.'
    //   )
    //   .afterClosed()
    //   .subscribe(dialogResult => {
    //     if (dialogResult) {
    //       this.tasks = this.tasks.splice(index, 1);
    //     }
    //   });
  }
}
