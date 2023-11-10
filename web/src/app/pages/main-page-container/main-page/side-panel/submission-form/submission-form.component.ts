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

import {Component} from '@angular/core';
import {FormBuilder, FormControl, FormGroup, Validators} from '@angular/forms';
import {List, Map} from 'immutable';
import {Observable} from 'rxjs';
import {first, map, switchMap} from 'rxjs/operators';

import {JobListItemActionsType} from 'app/components/job-list-item/job-list-item.component';
import {AuditInfo} from 'app/models/audit-info.model';
import {Job} from 'app/models/job.model';
import {Result} from 'app/models/submission/result.model';
import {
  Submission,
  SubmissionData,
} from 'app/models/submission/submission.model';
import {Survey} from 'app/models/survey.model';
import {Cardinality} from 'app/models/task/multiple-choice.model';
import {Option} from 'app/models/task/option.model';
import {Task, TaskType} from 'app/models/task/task.model';
import {AuthService} from 'app/services/auth/auth.service';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {LoadingState} from 'app/services/loading-state.model';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SubmissionService} from 'app/services/submission/submission.service';
import {SurveyService} from 'app/services/survey/survey.service';

// To make ESLint happy:
/*global alert*/

@Component({
  selector: 'ground-submission-form',
  templateUrl: './submission-form.component.html',
  styleUrls: ['./submission-form.component.scss'],
})
export class SubmissionFormComponent {
  readonly taskTypes = TaskType;
  readonly cardinality = Cardinality;
  readonly jobListItemActionsType = JobListItemActionsType;
  readonly job$: Observable<Job>;
  surveyId?: string;
  submission?: Submission;
  submissionForm?: FormGroup;
  submissionTasks?: List<Task>;

  constructor(
    private dataStoreService: DataStoreService,
    private authService: AuthService,
    private formBuilder: FormBuilder,
    private navigationService: NavigationService,
    submissionService: SubmissionService,
    surveyService: SurveyService,
    loiService: LocationOfInterestService
  ) {
    surveyService.getActiveSurvey$().subscribe((survey?: Survey) => {
      this.surveyId = survey?.id;
    });
    submissionService
      .getSelectedSubmission$()
      .subscribe((submission?: Submission | LoadingState) =>
        this.onSelectSubmission(submission)
      );
    this.job$ = surveyService
      .getActiveSurvey$()
      .pipe(
        switchMap(survey =>
          loiService
            .getSelectedLocationOfInterest$()
            .pipe(map(loi => survey.jobs.get(loi.jobId)!))
        )
      );
  }

  onCancel() {
    this.navigateToLocationOfInterest();
  }

  onSave() {
    this.authService
      .getUser$()
      .pipe(first())
      .subscribe(user => {
        if (!user) {
          throw Error('Login required to update submission.');
        }
        const lastModified = new AuditInfo(
          user,
          /*clientTime=*/ new Date(),
          /*serverTime=*/ this.dataStoreService.getServerTimestamp()
        );
        const updatedData: SubmissionData = this.extractData();
        const updatedSubmission = this.submission!.withDataAndLastModified(
          updatedData,
          lastModified
        );
        this.dataStoreService
          .updateSubmission(this.surveyId!, updatedSubmission)
          .then(() => {
            this.submissionForm?.markAsPristine();
            return this.navigateToLocationOfInterest();
          })
          .catch(() => {
            alert('Submission update failed.');
          });
      });
  }

  private onSelectSubmission(submission?: Submission | LoadingState) {
    if (submission === LoadingState.NOT_LOADED && this.submissionForm?.dirty) {
      if (
        confirm(
          'You have unsaved changes in submission form, do you want to save them?'
        )
      ) {
        this.onSave();
      } else {
        this.submissionForm = undefined;
      }
    }
    if (submission instanceof Submission) {
      this.submission = submission;
      this.submissionTasks = submission!
        .job!.tasks!.toOrderedMap()
        .sortBy(entry => entry.index)
        .toList();
      this.initForm();
    }
  }

  private initForm() {
    if (this.submission === undefined) {
      throw Error('Submission is not selected.');
    }
    this.submissionForm = this.convertSubmissionToFormGroup(this.submission!);
  }

  private navigateToLocationOfInterest() {
    this.navigationService.clearSubmissionId();
  }

  private convertSubmissionToFormGroup(submission: Submission): FormGroup {
    const group: {[taskId: string]: FormControl} = {};
    for (const [taskId, task] of submission.job!.tasks!) {
      const result = submission!.data?.get(taskId);
      switch (task.type) {
        case TaskType.TEXT:
          this.addControlsForTextTask(group, task, result);
          break;
        case TaskType.NUMBER:
          this.addControlsForNumberTask(group, task, result);
          break;
        case TaskType.MULTIPLE_CHOICE:
          this.addControlsForMultipleChoiceTask(group, task, result);
          break;
        default:
          console.debug(`Skipping unsupported task type: ${task.type}`);
      }
    }
    return this.formBuilder.group(group);
  }

  private extractData(): SubmissionData {
    return Map<string, Result>(
      this.submissionTasks!.map(task => [
        task.id,
        this.extractDataForTask(task),
      ])
    );
  }

  private extractDataForTask(task: Task) {
    switch (task.type) {
      case TaskType.TEXT:
        return this.extractDataForTextTask(task);
      case TaskType.NUMBER:
        return this.extractDataForNumberTask(task);
      case TaskType.MULTIPLE_CHOICE:
        return this.extractDataForMultipleChoiceTask(task);
      default:
        console.warn(`Unsupported task type ${task.type}`);
        return new Result('');
    }
  }

  private addControlsForTextTask(
    group: {[taskId: string]: FormControl},
    task: Task,
    result?: Result
  ): void {
    const value = result?.value as string;
    group[task.id] = task.required
      ? new FormControl(value, Validators.required)
      : new FormControl(value);
  }

  private addControlsForNumberTask(
    group: {[taskId: string]: FormControl},
    task: Task,
    result?: Result
  ): void {
    const value = result?.value as number;
    group[task.id] = task.required
      ? new FormControl(value, Validators.required)
      : new FormControl(value);
  }

  private extractDataForTextTask(task: Task): Result {
    return new Result(this.submissionForm?.value[task.id]);
  }

  private extractDataForNumberTask(task: Task): Result {
    return new Result(this.submissionForm?.value[task.id]);
  }

  private addControlsForMultipleChoiceTask(
    group: {[taskId: string]: FormControl},
    task: Task,
    result?: Result
  ): void {
    switch (task.multipleChoice?.cardinality) {
      case Cardinality.SELECT_ONE:
        this.addControlsForSelectOneTask(group, task, result);
        return;
      case Cardinality.SELECT_MULTIPLE:
        this.addControlsForSelectMultipleTask(group, task, result);
        return;
      default:
        console.warn(
          `Unimplemented conversion to FormControl(s) for Task with
           Cardinality:${task.multipleChoice?.cardinality}`
        );
    }
  }

  private extractDataForMultipleChoiceTask(task: Task): Result {
    switch (task.multipleChoice?.cardinality) {
      case Cardinality.SELECT_ONE:
        return this.extractDataForSelectOneTask(task);
      case Cardinality.SELECT_MULTIPLE:
        return this.extractDataForSelectMultipleTask(task);
      default:
        throw Error(
          `Unimplemented Result extraction for Task with
           Cardinality:${task.multipleChoice?.cardinality}`
        );
    }
  }

  private addControlsForSelectOneTask(
    group: {[taskId: string]: FormControl},
    task: Task,
    result?: Result
  ): void {
    const selectedOptionId = (
      (result?.value as List<Option>)?.first() as Option
    )?.id;
    group[task.id] = task.required
      ? new FormControl(selectedOptionId, Validators.required)
      : new FormControl(selectedOptionId);
  }

  private extractDataForSelectOneTask(task: Task): Result {
    const selectedOption: Option = task.getMultipleChoiceOption(
      this.submissionForm?.value[task.id]
    );
    return new Result(List([selectedOption]));
  }

  private addControlsForSelectMultipleTask(
    group: {[taskId: string]: FormControl},
    task: Task,
    result?: Result
  ): void {
    const selectedOptions = result?.value as List<Option>;
    for (const option of task.multipleChoice!.options) {
      group[option.id] = new FormControl(selectedOptions?.contains(option));
    }
  }

  private extractDataForSelectMultipleTask(task: Task): Result {
    const selectedOptions: List<Option> = task.multipleChoice!.options!.filter(
      (option: Option) => this.submissionForm?.value[option.id]
    );
    return new Result(selectedOptions);
  }
}
