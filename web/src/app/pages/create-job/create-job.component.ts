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
import {Component, OnInit} from '@angular/core';
import {SurveyService} from 'app/services/survey/survey.service';
import {JobService} from 'app/services/job/job.service';
import {FormGroup, FormBuilder} from '@angular/forms';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {Survey} from 'app/models/survey.model';

@Component({
  selector: 'create-job',
  templateUrl: './create-job.component.html',
  styleUrls: ['./create-job.component.scss'],
})
export class CreateJobComponent implements OnInit {
  readonly nameControlKey = 'name';
  private currentSurveyId = '';
  private currentSurvey: Survey | null = null;
  formGroup: FormGroup;

  constructor(
    private jobService: JobService,
    private surveyService: SurveyService,
    private navigationService: NavigationService,
    route: ActivatedRoute
  ) {
    this.formGroup = new FormBuilder().group({
      [this.nameControlKey]: '',
    });
    navigationService.init(route);
  }

  ngOnInit(): void {
    this.navigationService.getSurveyId$().subscribe(surveyId => {
      this.surveyService.activateSurvey(surveyId!);
      this.currentSurveyId = surveyId!;
    });
    this.surveyService.getActiveSurvey$().subscribe(survey => {
      this.loadExistingJob(survey);
      this.currentSurvey = survey!;
    });
  }

  loadExistingJob(survey: Survey): void {
    if (survey.jobs.size > 0) {
      this.formGroup.controls[this.nameControlKey].setValue(
        survey.jobs.values().next().value.name
      );
    }
  }

  back(): void {
    this.navigationService.navigateToCreateSurvey(this.currentSurveyId);
  }

  async continue(): Promise<void> {
    const name = this.formGroup.controls[this.nameControlKey].value;
    if (this.currentSurvey!.jobs.size > 0) {
      const existingJob = this.currentSurvey!.jobs.values().next().value;
      await this.jobService.addOrUpdateJob(
        this.currentSurveyId,
        existingJob.copyWith({name})
      );
    } else {
      const newJob = this.jobService.createNewJob();
      await this.jobService.addOrUpdateJob(
        this.currentSurveyId,
        newJob.copyWith({name})
      );
    }
    // TODO go to edit tasks page with survey id once the page is created
    this.navigationService.navigateToCreateJob(this.currentSurveyId);
  }
}
