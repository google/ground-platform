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

import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterModule } from '@angular/router';
import { EditSurveyComponent } from 'app/components/edit-survey/edit-survey.component';
import { CopySurveyControlsModule } from 'app/components/shared/copy-survey-controls/copy-survey-controls.module';

import { SurveyHeaderModule } from '../main-page-container/main-page/survey-header/survey-header.module';

@NgModule({
  declarations: [EditSurveyComponent],
  imports: [
    CommonModule,
    MatDialogModule,
    MatDividerModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    RouterModule,
    CopySurveyControlsModule,
    SurveyHeaderModule,
  ],
  exports: [EditSurveyComponent],
})
export class EditSurveyModule {}
