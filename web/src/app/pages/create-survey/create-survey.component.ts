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
import {NameSurveyComponent} from 'app/pages/create-survey/name-survey/name-survey.component';
import {NameJobComponent} from 'app/pages/create-survey/name-job/name-job.component';
import {Survey} from 'app/models/survey.model';
import {Job} from 'app/models/job.model';

@Component({
  selector: 'create-survey',
  templateUrl: './create-survey.component.html',
  styleUrls: ['./create-survey.component.scss'],
})
export class CreateSurveyComponent implements OnInit {
  currentSurveyId: string | null = null;
  currentSurvey?: Survey;
  setupPhase = SetupPhase.NAME_SURVEY;
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
      this.setupPhase = this.getSetupPhase(survey);
    });
  }

  private getSetupPhase(survey: Survey): SetupPhase {
    if (survey.title && survey.title.trim().length > 0) {
      return SetupPhase.NAME_JOB;
    }
    return SetupPhase.NAME_SURVEY;
  }

  job(): Job | undefined {
    if (this.currentSurvey?.jobs.size ?? 0 > 0) {
      return this.currentSurvey?.jobs.values().next().value;
    }
    return undefined;
  }

  back(): void {
    switch (this.setupPhase) {
      case SetupPhase.NAME_SURVEY:
        this.navigationService.navigateToSurveyList();
        break;
      case SetupPhase.NAME_JOB:
        this.setupPhase = SetupPhase.NAME_SURVEY;
        break;
      default:
        break;
    }
  }

  async continue(): Promise<void> {
    switch (this.setupPhase) {
      case SetupPhase.NAME_SURVEY:
        this.saveSurveyTitleAndDescription();
        break;
      case SetupPhase.NAME_JOB:
        this.saveJobName();
        break;
      default:
        break;
    }
  }

  @ViewChild('nameSurvey')
  nameSurvey?: NameSurveyComponent;

  private async saveSurveyTitleAndDescription(): Promise<void> {
    const [title, description] = this.nameSurvey!.toTitleAndDescription();
    if (this.currentSurveyId) {
      await this.surveyService.updateTitleAndDescription(
        this.currentSurveyId,
        title,
        description
      );
    } else {
      const createdSurveyId = await this.surveyService.createSurvey(
        title,
        description
      );
      this.navigationService.navigateToCreateSurvey(createdSurveyId);
    }
  }

  @ViewChild('nameJob')
  nameJob?: NameJobComponent;

  private async saveJobName(): Promise<void> {
    const name = this.nameJob!.toJobName();
    if (this.currentSurvey!.jobs.size > 0) {
      const existingJob = this.currentSurvey!.jobs.values().next().value;
      await this.jobService.addOrUpdateJob(
        this.currentSurveyId!,
        existingJob.copyWith({name})
      );
    } else {
      const newJob = this.jobService.createNewJob();
      await this.jobService.addOrUpdateJob(
        this.currentSurveyId!,
        newJob.copyWith({name})
      );
    }
  }
}

export enum SetupPhase {
  NAME_SURVEY,
  NAME_JOB,
  DEFINE_TASKS,
  DEFINE_LOIS,
  REVIEW,
}
