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
import {List} from 'immutable';
import {Subscription, combineLatest, filter} from 'rxjs';

import {DataCollectionStrategy, Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Survey, SurveyState} from 'app/models/survey.model';
import {DataSharingTermsComponent} from 'app/pages/create-survey/data-sharing-terms/data-sharing-terms.component';
import {JobDetailsComponent} from 'app/pages/create-survey/job-details/job-details.component';
import {SurveyDetailsComponent} from 'app/pages/create-survey/survey-details/survey-details.component';
import {TaskDetailsComponent} from 'app/pages/create-survey/task-details/task-details.component';
import {DraftSurveyService} from 'app/services/draft-survey/draft-survey.service';
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
  subscription: Subscription = new Subscription();
  surveyId?: string;
  survey?: Survey;
  lois?: List<LocationOfInterest>;
  canContinue = true;
  skipLoiSelection = false;
  setupPhase = SetupPhase.LOADING;

  readonly SetupPhase = SetupPhase;

  @ViewChild('surveyDetails')
  surveyDetails?: SurveyDetailsComponent;

  @ViewChild('jobDetails')
  jobDetails?: JobDetailsComponent;

  @ViewChild('surveyLoi')
  surveyLoi?: SurveyLoiComponent;

  @ViewChild('taskDetails')
  taskDetails?: TaskDetailsComponent;

  @ViewChild('dataSharingTerms')
  dataSharingTerms?: DataSharingTermsComponent;

  constructor(
    private surveyService: SurveyService,
    private draftSurveyService: DraftSurveyService,
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

  ngOnInit(): void {
    this.subscription.add(
      this.navigationService.getSurveyId$().subscribe(async surveyId => {
        this.surveyId = surveyId ? surveyId : NavigationService.SURVEY_ID_NEW;
        this.surveyService.activateSurvey(this.surveyId);
        await this.draftSurveyService.init(this.surveyId);
        this.draftSurveyService
          .getSurvey$()
          .subscribe(survey => (this.survey = survey));
      })
    );

    this.subscription.add(
      combineLatest([
        this.surveyService.getActiveSurvey$(),
        this.loiService.getLocationsOfInterest$(),
      ])
        .pipe(
          filter(
            ([survey]) =>
              this.surveyId === NavigationService.SURVEY_ID_NEW ||
              survey.id === this.surveyId
          )
        )
        .subscribe(([survey, lois]) => {
          this.survey = survey;
          if (this.isSetupFinished(this.survey)) {
            this.navigationService.navigateToEditSurvey(this.survey.id);
          }
          this.lois = lois;
          if (this.setupPhase === SetupPhase.LOADING) {
            this.setupPhase = this.getSetupPhase(survey, lois);
          }
          if (this.setupPhase === SetupPhase.DEFINE_LOIS) {
            this.canContinue =
              !this.lois.isEmpty() ||
              this.job()?.strategy === DataCollectionStrategy.MIXED;
          }
        })
    );
  }

  onValidationChange(valid: boolean) {
    this.canContinue = valid;
  }

  private isSetupFinished(survey: Survey): boolean {
    return survey.state === SurveyState.READY;
  }

  private getSetupPhase(
    survey: Survey,
    lois: Immutable.List<LocationOfInterest>
  ): SetupPhase {
    if (survey.state === SurveyState.READY) {
      return SetupPhase.DEFINE_DATA_SHARING_TERMS;
    }
    if (
      !lois.isEmpty() ||
      this.job()?.strategy === DataCollectionStrategy.MIXED
    ) {
      return SetupPhase.DEFINE_TASKS;
    }
    if (survey.hasJobs()) {
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

  readonly setupPhaseToTitle = new Map<SetupPhase, String>([
    [SetupPhase.SURVEY_DETAILS, 'Create survey'],
    [SetupPhase.JOB_DETAILS, 'Add a job'],
    [SetupPhase.DEFINE_LOIS, 'Data collection strategy'],
    [SetupPhase.DEFINE_TASKS, 'Define data collection tasks'],
    [SetupPhase.DEFINE_DATA_SHARING_TERMS, 'Define data sharing terms'],
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
    if (this.survey?.hasJobs()) {
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
      case SetupPhase.DEFINE_DATA_SHARING_TERMS:
        this.setupPhase = SetupPhase.DEFINE_TASKS;
        break;
      case SetupPhase.REVIEW:
        this.setupPhase = SetupPhase.DEFINE_DATA_SHARING_TERMS;
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
        this.setupPhase = SetupPhase.DEFINE_DATA_SHARING_TERMS;
        break;
      case SetupPhase.DEFINE_DATA_SHARING_TERMS:
        await this.saveDataSharingTerms();
        this.setupPhase = SetupPhase.REVIEW;
        break;
      case SetupPhase.REVIEW:
        await this.setSurveyStateToReady();
        await this.draftSurveyService.updateSurvey();
        !!this.surveyId && this.navigationService.selectSurvey(this.surveyId);
        break;
      default:
        break;
    }
    this.survey = this.surveyService.getActiveSurvey();
  }

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

  private getFirstJob(): Job {
    // there should only be at most one job attached to this survey at this
    // point when user is still in the survey creation flow.
    return this.survey!.jobs.values().next().value;
  }

  private async saveJobName(): Promise<void> {
    const name = this.jobDetails!.toJobName();
    let job;
    if (this.survey?.hasJobs()) {
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

  private async saveDataSharingTerms() {
    const type = this.dataSharingTerms?.formGroup.controls.type.value;

    const customText =
      this.dataSharingTerms?.formGroup.controls.customText.value ?? undefined;

    await this.surveyService.updateDataSharingTerms(type, customText);
  }

  private async setSurveyStateToReady() {
    await this.draftSurveyService.updateState(SurveyState.READY);
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}

export enum SetupPhase {
  LOADING,
  SURVEY_DETAILS,
  JOB_DETAILS,
  DEFINE_LOIS,
  DEFINE_TASKS,
  DEFINE_DATA_SHARING_TERMS,
  REVIEW,
}
