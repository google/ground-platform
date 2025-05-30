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

import {CommonModule} from '@angular/common';
import {NgModule} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatInputModule} from '@angular/material/input';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';

import {CopySurveyControlsModule} from 'app/components/copy-survey-controls/copy-survey-controls.module';
import {HeaderModule} from 'app/components/header/header.module';
import {ShareSurveyModule} from 'app/components/share-survey/share-survey.module';
import {CreateSurveyComponent} from 'app/pages/create-survey/create-survey.component';
import {DataSharingTermsModule} from 'app/pages/create-survey/data-sharing-terms/data-sharing-terms.module';
import {TaskDetailsModule} from 'app/pages/create-survey/task-details/task-details.module';

import {JobDetailsModule} from './job-details/job-details.module';
import {StepCardModule} from './step-card/step-card.module';
import {SurveyDetailsModule} from './survey-details/survey-details.module';
import {SurveyLoiModule} from './survey-loi/survey-loi.module';

@NgModule({
  declarations: [CreateSurveyComponent],
  imports: [
    CommonModule,
    DataSharingTermsModule,
    HeaderModule,
    JobDetailsModule,
    MatButtonModule,
    MatInputModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    CopySurveyControlsModule,
    ShareSurveyModule,
    StepCardModule,
    SurveyDetailsModule,
    SurveyLoiModule,
    TaskDetailsModule,
  ],
  exports: [CreateSurveyComponent],
})
export class CreateSurveyModule {}
