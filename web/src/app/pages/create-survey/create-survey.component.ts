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

import {ChangeDetectorRef, Component, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {filter, first, firstValueFrom} from 'rxjs';

import {Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Survey} from 'app/models/survey.model';
import {JobDetailsComponent} from 'app/pages/create-survey/job-details/job-details.component';
import {SurveyDetailsComponent} from 'app/pages/create-survey/survey-details/survey-details.component';
import {TaskDetailsComponent} from 'app/pages/create-survey/task-details/task-details.component';
import {JobService} from 'app/services/job/job.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {TaskService} from 'app/services/task/task.service';

import {SurveyLoiComponent} from './survey-loi/survey-loi.component';

@Component({
  selector: 'create-survey',
  templateUrl: './create-survey.component.html',
  styleUrls: ['./create-survey.component.scss'],
})
export class CreateSurveyComponent implements OnInit {
  surveyId?: string;
  survey?: Survey;
  canContinue = true;
  skipLoiSelection = false;
  // TODO(#1119): when we refresh, the setupPhase below is always displayed for a split of a second.
  // We should display a loading bar while we are waiting for the data to make a decision
  // about which phase we are in.
  setupPhase = SetupPhase.SURVEY_DETAILS;
  readonly SetupPhase = SetupPhase;

  constructor(
    private surveyService: SurveyService,
    private jobService: JobService,
    private taskService: TaskService,
    private navigationService: NavigationService,
    private loiService: LocationOfInterestService,
    private cdr: ChangeDetectorRef,
    route: ActivatedRoute
  ) {
    navigationService.init(route);
  }

  ngAfterViewChecked(): void {
    this.cdr.detectChanges();
  }

  async ngOnInit(): Promise<void> {
    this.navigationService.getSurveyId$().subscribe(surveyId => {
      this.surveyId = surveyId ? surveyId : NavigationService.SURVEY_ID_NEW;
      this.surveyService.activateSurvey(this.surveyId);
    });

    const survey = await firstValueFrom(
      this.surveyService
        .getActiveSurvey$()
        .pipe(
          filter(
            survey =>
              this.surveyId === NavigationService.SURVEY_ID_NEW ||
              survey.id === this.surveyId
          )
        )
    );
    if (this.isSetupFinished(survey)) {
      this.navigationService.navigateToEditSurvey(survey.id);
      return;
    }
    this.loiService
      .getLocationsOfInterest$()
      .pipe(first())
      .subscribe(lois => {
        this.setupPhase = this.getSetupPhase(survey, lois);
        this.survey = survey;
      });
  }

  onValidationChange(valid: boolean) {
    this.canContinue = valid;
  }

  private isSetupFinished(survey: Survey): boolean {
    // To make it simple we are not checking the LOIs here since defining tasks is the step after defining LOIs.
    return this.hasTitle(survey) && this.hasJob(survey) && this.hasTask(survey);
  }

  private getSetupPhase(
    survey: Survey,
    lois: Immutable.List<LocationOfInterest>
  ): SetupPhase {
    if (!lois.isEmpty()) {
      return SetupPhase.DEFINE_TASKS;
    }
    if (survey.jobs.size > 0) {
      return SetupPhase.DEFINE_LOIS;
    }
    if (this.hasTitle(survey)) {
      return SetupPhase.JOB_DETAILS;
    }
    return SetupPhase.SURVEY_DETAILS;
  }

  private hasTitle(survey: Survey): boolean {
    return survey.title.trim().length > 0;
  }

  private hasJob(survey: Survey): boolean {
    return survey.jobs.size > 0;
  }

  private hasTask(survey: Survey): boolean {
    return survey.jobs.valueSeq().some(job => (job.tasks?.size || 0) > 0);
  }

  readonly setupPhaseToTitle = new Map<SetupPhase, String>([
    [SetupPhase.SURVEY_DETAILS, 'Create survey'],
    [SetupPhase.JOB_DETAILS, 'Add a job'],
    [SetupPhase.DEFINE_LOIS, 'Data collection strategy'],
    [SetupPhase.DEFINE_TASKS, 'Define data collection tasks'],
    [SetupPhase.REVIEW, 'Review and share survey'],
  ]);

  progressBarTitle(): String {
    return this.setupPhaseToTitle.get(this.setupPhase) ?? '';
  }

  progressBarValue(): number {
    const numberOfSteps = this.setupPhaseToTitle.size;
    const currentPhaseIndex = Array.from(
      this.setupPhaseToTitle.keys()
    ).findIndex(phase => phase === this.setupPhase);
    return currentPhaseIndex > -1
      ? Math.round((currentPhaseIndex / numberOfSteps) * 100)
      : 0;
  }

  job(): Job | undefined {
    if (this.survey?.jobs.size ?? 0 > 0) {
      return this.survey?.jobs.values().next().value;
    }
    return undefined;
  }

  jobName(): string {
    return this.job()?.name ?? '';
  }

  back(): void {
    switch (this.setupPhase) {
      case SetupPhase.SURVEY_DETAILS:
        this.navigationService.navigateToSurveyList();
        break;
      case SetupPhase.JOB_DETAILS:
        this.setupPhase = SetupPhase.SURVEY_DETAILS;
        break;
      case SetupPhase.DEFINE_LOIS:
        this.setupPhase = SetupPhase.JOB_DETAILS;
        break;
      case SetupPhase.DEFINE_TASKS:
        this.canContinue = true;
        this.setupPhase = SetupPhase.DEFINE_LOIS;
        break;
      case SetupPhase.REVIEW:
        this.setupPhase = SetupPhase.DEFINE_TASKS;
        break;
      default:
        break;
    }
    this.survey = this.surveyService.getActiveSurvey();
  }

  async continue(): Promise<void> {
    switch (this.setupPhase) {
      case SetupPhase.SURVEY_DETAILS: {
        const createdSurveyId = await this.saveSurveyTitleAndDescription();
        if (createdSurveyId) {
          this.navigationService.navigateToCreateSurvey(createdSurveyId, true);
        }
        this.setupPhase = SetupPhase.JOB_DETAILS;
        break;
      }
      case SetupPhase.JOB_DETAILS:
        await this.saveJobName();
        this.setupPhase = SetupPhase.DEFINE_LOIS;
        break;
      case SetupPhase.DEFINE_LOIS:
        this.setupPhase = SetupPhase.DEFINE_TASKS;
        break;
      case SetupPhase.DEFINE_TASKS:
        await this.saveTasks();
        this.setupPhase = SetupPhase.REVIEW;
        break;
      case SetupPhase.REVIEW:
        !!this.surveyId && this.navigationService.selectSurvey(this.surveyId);
        break;
      default:
        break;
    }
    this.survey = this.surveyService.getActiveSurvey();
  }

  @ViewChild('surveyDetails')
  surveyDetails?: SurveyDetailsComponent;

  private async saveSurveyTitleAndDescription(): Promise<string | void> {
    const [name, description] = this.surveyDetails!.toTitleAndDescription();
    if (this.surveyId === NavigationService.SURVEY_ID_NEW) {
      return await this.surveyService.createSurvey(name, description);
    }
    return await this.surveyService.updateTitleAndDescription(
      this.surveyId!,
      name,
      description
    );
  }

  @ViewChild('jobDetails')
  jobDetails?: JobDetailsComponent;

  private getFirstJob(): Job {
    // there should only be at most one job attached to this survey at this
    // point when user is still in the survey creation flow.
    return this.survey!.jobs.values().next().value;
  }

  private async saveJobName(): Promise<void> {
    const name = this.jobDetails!.toJobName();
    let job;
    if (this.survey!.jobs.size > 0) {
      job = this.getFirstJob();
    } else {
      job = this.jobService.createNewJob();
    }
    await this.jobService.addOrUpdateJob(
      this.surveyId!,
      job.copyWith({
        name,
        color: job.color || this.jobService.getNextColor(this.survey?.jobs),
      })
    );
  }

  private async saveTasks() {
    const tasks = this.taskDetails?.toTasks();

    // Assume the survey exists.
    const survey = this.survey!;

    await this.taskService.addOrUpdateTasks(
      survey.id,
      // Assume there is at least one job.
      survey.jobs.first(),
      tasks!
    );
  }

  @ViewChild('surveyLoi')
  surveyLoi?: SurveyLoiComponent;

  @ViewChild('taskDetails')
  taskDetails?: TaskDetailsComponent;
}

export enum SetupPhase {
  SURVEY_DETAILS,
  JOB_DETAILS,
  DEFINE_LOIS,
  DEFINE_TASKS,
  REVIEW,
}
