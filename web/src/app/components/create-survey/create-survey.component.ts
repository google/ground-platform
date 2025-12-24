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

import '@angular/localize/init';

import { ChangeDetectorRef, Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { List } from 'immutable';
import { Subscription, combineLatest, filter } from 'rxjs';

import { DataSharingTermsComponent } from 'app/components/create-survey/data-sharing-terms/data-sharing-terms.component';
import { JobDetailsComponent } from 'app/components/create-survey/job-details/job-details.component';
import { SurveyDetailsComponent } from 'app/components/create-survey/survey-details/survey-details.component';
import { TaskDetailsComponent } from 'app/components/create-survey/task-details/task-details.component';
import { DataCollectionStrategy, Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { Survey, SurveyState } from 'app/models/survey.model';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';
import { JobService } from 'app/services/job/job.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { SURVEY_ID_NEW } from 'app/services/navigation/navigation.constants';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { TaskService } from 'app/services/task/task.service';

import { SurveyLoiComponent } from './survey-loi/survey-loi.component';

export enum CreateSurveyPhase {
  LOADING,
  SURVEY_DETAILS,
  JOB_DETAILS,
  DEFINE_LOIS,
  DEFINE_TASKS,
  DEFINE_DATA_SHARING_TERMS,
  SHARE_SURVEY,
}

const createSurveyPhaseMetadata = new Map<
  CreateSurveyPhase,
  {
    progressBarTitle: string;
    cardTitle?: string;
    cardDescription?: string;
  }
>([
  [
    CreateSurveyPhase.SURVEY_DETAILS,
    {
      progressBarTitle: $localize`:@@app.createSurvey.surveyDetails.progress:Create a survey`,
      cardTitle: $localize`:@@app.createSurvey.surveyDetails.title:Start building your survey`,
      cardDescription: $localize`:@@app.createSurvey.surveyDetails.description:Provide some basic info about your survey`,
    },
  ],
  [
    CreateSurveyPhase.JOB_DETAILS,
    {
      progressBarTitle: $localize`:@@app.createSurvey.jobDetails.progress:Add a job`,
      cardTitle: $localize`:@@app.createSurvey.jobDetails.title:Add a job`,
      cardDescription: $localize`:@@app.createSurvey.jobDetails.description:In the following steps, you'll define the data that should be collected for certain sites as part of this job`,
    },
  ],
  [
    CreateSurveyPhase.DEFINE_LOIS,
    {
      progressBarTitle: $localize`:@@app.createSurvey.defineLois.progress:Specify data collection sites`,
      cardTitle: $localize`:@@app.createSurvey.defineLois.title:Where should data be collected?`,
      cardDescription: $localize`:@@app.createSurvey.defineLois.description:Data collectors will complete specified tasks for these sites`,
    },
  ],
  [
    CreateSurveyPhase.DEFINE_TASKS,
    {
      progressBarTitle: $localize`:@@app.createSurvey.defineTasks.progress:Define data collection tasks`,
      cardTitle: $localize`:@@app.createSurvey.defineTasks.title:Which data should be collected?`,
      cardDescription: $localize`:@@app.createSurvey.defineTasks.description:Data collectors will be prompted to complete the following tasks for each site`,
    },
  ],
  [
    CreateSurveyPhase.DEFINE_DATA_SHARING_TERMS,
    {
      progressBarTitle: $localize`:@@app.createSurvey.defineDataSharingTerms.progress:Define data sharing terms`,
      cardTitle: $localize`:@@app.createSurvey.defineDataSharingTerms.title:Which terms must data collectors agree to?`,
      cardDescription: $localize`:@@app.createSurvey.defineDataSharingTerms.description:Select the terms data collectors need to agree to before they get started`,
    },
  ],
  [
    CreateSurveyPhase.SHARE_SURVEY,
    {
      progressBarTitle: $localize`:@@app.createSurvey.shareSurvey.progress:Share your survey`,
      cardTitle: $localize`:@@app.createSurvey.shareSurvey.title:Share your survey`,
      cardDescription: '',
    },
  ],
]);

@Component({
  selector: 'create-survey',
  templateUrl: './create-survey.component.html',
  styleUrls: ['./create-survey.component.scss'],
  standalone: false,
})
export class CreateSurveyComponent implements OnInit {
  subscription: Subscription = new Subscription();
  surveyId?: string;
  survey?: Survey;
  lois?: List<LocationOfInterest>;
  canContinue = true;
  skipLoiSelection = false;
  createSurveyPhase = CreateSurveyPhase.LOADING;

  readonly CreateSurveyPhase = CreateSurveyPhase;

  readonly createSurveyPhaseMetadata = createSurveyPhaseMetadata;

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
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      this.navigationService.getSurveyId$().subscribe(async surveyId => {
        this.surveyId = surveyId ? surveyId : SURVEY_ID_NEW;
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
              this.surveyId === SURVEY_ID_NEW || survey.id === this.surveyId
          )
        )
        .subscribe(([survey, lois]) => {
          this.survey = survey;
          if (this.isSetupFinished(this.survey)) {
            this.navigationService.selectSurvey(this.survey.id);
          }
          this.lois = lois;
          if (this.createSurveyPhase === CreateSurveyPhase.LOADING) {
            this.createSurveyPhase = this.getSurveyPhase(survey, lois);
          }
          if (this.createSurveyPhase === CreateSurveyPhase.DEFINE_LOIS) {
            this.canContinue =
              !this.lois.isEmpty() ||
              this.job()?.strategy === DataCollectionStrategy.MIXED;
          }
        })
    );
  }

  onValidationChange(valid: boolean) {
    // Defer update to prevent NG0100 when child updates parent during change detection
    setTimeout(() => {
      this.canContinue = valid;
    });
  }

  private isSetupFinished(survey: Survey): boolean {
    return survey.state === SurveyState.READY;
  }

  private getSurveyPhase(
    survey: Survey,
    lois: List<LocationOfInterest>
  ): CreateSurveyPhase {
    if (survey.state === SurveyState.READY) {
      return CreateSurveyPhase.DEFINE_DATA_SHARING_TERMS;
    }
    if (
      !lois.isEmpty() ||
      this.job()?.strategy === DataCollectionStrategy.MIXED
    ) {
      return CreateSurveyPhase.DEFINE_TASKS;
    }
    if (survey.hasJobs()) {
      return CreateSurveyPhase.DEFINE_LOIS;
    }
    if (this.hasTitle(survey)) {
      return CreateSurveyPhase.JOB_DETAILS;
    }
    return CreateSurveyPhase.SURVEY_DETAILS;
  }

  private hasTitle(survey: Survey): boolean {
    return survey.title.trim().length > 0;
  }

  progressBarTitle(): String {
    return (
      createSurveyPhaseMetadata.get(this.createSurveyPhase)?.progressBarTitle ??
      ''
    );
  }

  cardTitle(): String {
    return (
      createSurveyPhaseMetadata.get(this.createSurveyPhase)?.cardTitle ?? ''
    );
  }

  cardDescription(): String {
    return (
      createSurveyPhaseMetadata.get(this.createSurveyPhase)?.cardDescription ??
      ''
    );
  }

  progressBarValue(): number {
    const numberOfSteps = createSurveyPhaseMetadata.size;
    const currentPhaseIndex = Array.from(
      createSurveyPhaseMetadata.keys()
    ).findIndex(phase => phase === this.createSurveyPhase);
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
    switch (this.createSurveyPhase) {
      case CreateSurveyPhase.SURVEY_DETAILS:
        this.navigationService.navigateToSurveyList();
        break;
      case CreateSurveyPhase.JOB_DETAILS:
        this.createSurveyPhase = CreateSurveyPhase.SURVEY_DETAILS;
        break;
      case CreateSurveyPhase.DEFINE_LOIS:
        this.createSurveyPhase = CreateSurveyPhase.JOB_DETAILS;
        break;
      case CreateSurveyPhase.DEFINE_TASKS:
        this.canContinue = true;
        this.createSurveyPhase = CreateSurveyPhase.DEFINE_LOIS;
        break;
      case CreateSurveyPhase.DEFINE_DATA_SHARING_TERMS:
        this.createSurveyPhase = CreateSurveyPhase.DEFINE_TASKS;
        break;
      case CreateSurveyPhase.SHARE_SURVEY:
        this.createSurveyPhase = CreateSurveyPhase.DEFINE_DATA_SHARING_TERMS;
        break;
      default:
        break;
    }
    this.survey = this.surveyService.getActiveSurvey();
  }

  async continue(): Promise<void> {
    switch (this.createSurveyPhase) {
      case CreateSurveyPhase.SURVEY_DETAILS: {
        const createdSurveyId = await this.saveSurveyTitleAndDescription();
        if (createdSurveyId) {
          this.navigationService.navigateToCreateSurvey(createdSurveyId, true);
        }
        this.createSurveyPhase = CreateSurveyPhase.JOB_DETAILS;
        break;
      }
      case CreateSurveyPhase.JOB_DETAILS:
        await this.saveJobName();
        this.createSurveyPhase = CreateSurveyPhase.DEFINE_LOIS;
        break;
      case CreateSurveyPhase.DEFINE_LOIS:
        this.createSurveyPhase = CreateSurveyPhase.DEFINE_TASKS;
        break;
      case CreateSurveyPhase.DEFINE_TASKS:
        await this.saveTasks();
        this.createSurveyPhase = CreateSurveyPhase.DEFINE_DATA_SHARING_TERMS;
        break;
      case CreateSurveyPhase.DEFINE_DATA_SHARING_TERMS:
        await this.saveDataSharingTerms();
        this.createSurveyPhase = CreateSurveyPhase.SHARE_SURVEY;
        break;
      case CreateSurveyPhase.SHARE_SURVEY:
        await this.setSurveyStateToReady();
        break;
      default:
        break;
    }
    this.survey = this.surveyService.getActiveSurvey();
  }

  private async saveSurveyTitleAndDescription(): Promise<string | void> {
    const [name, description] = this.surveyDetails!.toTitleAndDescription();
    if (this.surveyId === SURVEY_ID_NEW) {
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

  private async saveDataSharingTerms(): Promise<void> {
    const type = this.dataSharingTerms?.formGroup.controls.type.value;

    const customText =
      this.dataSharingTerms?.formGroup.controls.customText.value ?? undefined;

    this.draftSurveyService.updateDataSharingTerms(type, customText);
    await this.surveyService.updateDataSharingTerms(type, customText);
  }

  private async setSurveyStateToReady(): Promise<void> {
    this.draftSurveyService.updateState(SurveyState.READY);
    await this.draftSurveyService.updateSurvey();
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
