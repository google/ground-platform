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

import {CdkDragDrop} from '@angular/cdk/drag-drop';
import {Component, ViewChild} from '@angular/core';
import {ActivatedRoute, Params} from '@angular/router';
import {List} from 'immutable';
import {Subscription} from 'rxjs';

import {LoiSelectionComponent} from 'app/components/loi-selection/loi-selection.component';
import {LocationOfInterest} from 'app/models/loi.model';
import {Task} from 'app/models/task/task.model';
import {
  TaskGroup,
  taskGroupToTypes,
} from 'app/pages/create-survey/task-details/task-details.component';
import {DialogService} from 'app/services/dialog/dialog.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {TaskService} from 'app/services/task/task.service';
import {TempSurveyService} from 'app/services/temp-survey/temp-survey.service';

@Component({
  selector: 'edit-job',
  templateUrl: './edit-job.component.html',
  styleUrls: ['./edit-job.component.scss'],
})
export class EditJobComponent {
  subscription: Subscription = new Subscription();

  surveyId?: string;
  jobId?: string;
  section: 'tasks' | 'lois' = 'tasks';
  lois!: List<LocationOfInterest>;

  tasks?: List<Task>;

  addableTaskGroups: Array<TaskGroup> = [
    TaskGroup.QUESTION,
    TaskGroup.PHOTO,
    TaskGroup.DROP_PIN,
    TaskGroup.DRAW_AREA,
    TaskGroup.CAPTURE_LOCATION,
  ];

  @ViewChild('loiSelection')
  loiSelection?: LoiSelectionComponent;

  constructor(
    private route: ActivatedRoute,
    private navigationService: NavigationService,
    private dialogService: DialogService,
    public surveyService: SurveyService,
    public tempSurveyService: TempSurveyService,
    private taskService: TaskService
  ) {
    this.subscription.add(
      this.navigationService
        .getSurveyId$()
        .subscribe(surveyId => this.onSurveyIdChange(surveyId))
    );
  }

  async ngOnInit(): Promise<void> {
    this.subscription.add(
      this.route.params.subscribe(async params => {
        await this.onJobIdChange(params);
      })
    );
  }

  private onSurveyIdChange(surveyId: string | null) {
    if (surveyId) {
      this.surveyId = surveyId;
    }
  }

  private async onJobIdChange(params: Params) {
    this.jobId = params['id'];

    this.tasks = this.tempSurveyService
      .getTempSurvey()
      .getJob(this.jobId!)
      ?.tasks?.toList()
      .sortBy(task => task.index);

    this.lois = this.tempSurveyService.getTempLoisByJobId(this.jobId!)
  }

  getIndex(index: number) {
    return index;
  }

  onChangeSection(section: 'tasks' | 'lois') {
    this.section = section;
  }

  onAddTask(group: TaskGroup) {
    const types = taskGroupToTypes.get(group);

    const type = types?.first();

    if (type && this.tasks) {
      const task = this.taskService.createTask(
        type,
        '',
        false,
        this.tasks.size
      );

      this.tasks = this.tasks.push(task);
    }

    // No need to call addOrUpdateTasks because ngOnChange emits an update once
    // the task-input component is created.
  }

  onUpdateTask(event: Task, index: number) {
    if (!this.tasks) {
      throw Error('tasks list is is empty');
    }

    this.tasks = this.tasks.set(index, event);
    this.tempSurveyService.addOrUpdateTasks(this.jobId!, this.tasks);
  }

  onDeleteTask(index: number) {
    this.dialogService
      .openConfirmationDialog(
        'Warning',
        'Are you sure you wish to delete this question? Any associated data ' +
          'will be lost. This cannot be undone.'
      )
      .afterClosed()
      .subscribe(dialogResult => {
        if (dialogResult) {
          this.tasks = this.tasks!.splice(index, 1);
        }
      });
  }

  drop(event: CdkDragDrop<string[]>): void {
    const {previousIndex, currentIndex} = event;
    this.tasks = this.tasks!.update(
      previousIndex,
      task => task?.copyWith({index: currentIndex}) as Task
    )
      .update(
        currentIndex,
        task => task?.copyWith({index: previousIndex}) as Task
      )
      .sortBy(task => task.index);
  }

  onDuplicateTask(index: number) {
    this.dialogService
      .openConfirmationDialog(
        'Duplicate task',
        'Are you sure you wish to duplicate this task?'
      )
      .afterClosed()
      .subscribe(dialogResult => {
        if (dialogResult) {
          const taskToDuplicate = this.tasks!.get(index);
          if (taskToDuplicate) {
            const task = this.taskService.createTask(
              taskToDuplicate?.type,
              taskToDuplicate?.label,
              taskToDuplicate?.required,
              this.tasks!.size,
              taskToDuplicate?.multipleChoice
            );
            this.tasks = this.tasks!.push(task);
          }
        }
      });
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
