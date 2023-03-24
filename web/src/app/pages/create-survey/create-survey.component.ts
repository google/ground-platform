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

import {ActivatedRoute} from '@angular/router';
import {Component, OnInit, ViewChild} from '@angular/core';
import {SurveyService} from 'app/services/survey/survey.service';
import {JobService} from 'app/services/job/job.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyDetailsComponent} from 'app/pages/create-survey/survey-details/survey-details.component';
import {JobDetailsComponent} from 'app/pages/create-survey/job-details/job-details.component';
import {Survey} from 'app/models/survey.model';
import {Job} from 'app/models/job.model';
import {ShareSurveyComponent} from 'app/pages/create-survey/share-survey/share-survey.component';
import {first} from 'rxjs';

@Component({
  selector: 'create-survey',
  templateUrl: './create-survey.component.html',
  styleUrls: ['./create-survey.component.scss'],
})
export class CreateSurveyComponent implements OnInit {
  currentSurveyId: string | null = null;
  currentSurvey?: Survey;
  setupPhase = SetupPhase.SURVEY_DETAILS;
  readonly SetupPhase = SetupPhase;

  constructor(
    private surveyService: SurveyService,
    private jobService: JobService,
    private navigationService: NavigationService,
    route: ActivatedRoute
  ) {
    navigationService.init(route);
  }

  ngOnInit(): void {
    this.navigationService.getSurveyId$().subscribe(surveyId => {
      if (surveyId) {
        this.surveyService.activateSurvey(surveyId);
      }
      this.currentSurveyId = surveyId;
    });
    this.surveyService.getActiveSurvey$().subscribe(survey => {
      this.currentSurvey = survey;
    });
    this.surveyService
      .getActiveSurvey$()
      .pipe(first())
      .subscribe(survey => {
        if (this.isSetupFinished(survey)) {
          this.navigationService.navigateToEditSurvey(survey.id);
          return;
        }
        this.setupPhase = this.getSetupPhase(survey);
      });
  }

  private isSetupFinished(survey: Survey): boolean {
    // To make it simple we are not checking the LOIs here since defining tasks is the step after defining LOIs.
    return this.hasTitle(survey) && this.hasJob(survey) && this.hasTask(survey);
  }

  private getSetupPhase(survey: Survey): SetupPhase {
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
    return survey.jobs.values().next().value.tasks.size > 0;
  }

  job(): Job | undefined {
    if (this.currentSurvey?.jobs.size ?? 0 > 0) {
      return this.currentSurvey?.jobs.values().next().value;
    }
    return undefined;
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
      case SetupPhase.REVIEW:
        this.setupPhase = SetupPhase.DEFINE_LOIS;
        break;
      default:
        break;
    }
  }

  async continue(): Promise<void> {
    switch (this.setupPhase) {
      case SetupPhase.SURVEY_DETAILS: {
        const createdSurveyId = await this.saveSurveyTitleAndDescription();
        if (createdSurveyId) {
          this.navigationService.navigateToCreateSurvey(createdSurveyId);
        }
        this.setupPhase = SetupPhase.JOB_DETAILS;
        break;
      }
      case SetupPhase.JOB_DETAILS:
        await this.saveJobName();
        this.setupPhase = SetupPhase.DEFINE_LOIS;
        break;
      case SetupPhase.DEFINE_LOIS:
        this.setupPhase = SetupPhase.REVIEW;
        break;
      default:
        break;
    }
  }

  @ViewChild('surveyDetails')
  surveyDetails?: SurveyDetailsComponent;

  private async saveSurveyTitleAndDescription(): Promise<string | void> {
    const [title, description] = this.surveyDetails!.toTitleAndDescription();
    if (this.currentSurveyId) {
      return await this.surveyService.updateTitleAndDescription(
        this.currentSurveyId,
        title,
        description
      );
    } else {
      return await this.surveyService.createSurvey(title, description);
    }
  }

  @ViewChild('jobDetails')
  jobDetails?: JobDetailsComponent;

  private async saveJobName(): Promise<void> {
    const name = this.jobDetails!.toJobName();
    let job;
    if (this.currentSurvey!.jobs.size > 0) {
      // there should only be at most one job attached to this survey at this point when user is still in the survey creation flow
      job = this.currentSurvey!.jobs.values().next().value;
    } else {
      job = this.jobService.createNewJob();
    }
    await this.jobService.addOrUpdateJob(
      this.currentSurveyId!,
      job.copyWith({name})
    );
  }

  @ViewChild('shareSurvey')
  shareSurvey?: ShareSurveyComponent;
}

export enum SetupPhase {
  SURVEY_DETAILS,
  JOB_DETAILS,
  DEFINE_TASKS,
  DEFINE_LOIS,
  REVIEW,
}
