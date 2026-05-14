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

import { NgModule } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { BrowserModule } from '@angular/platform-browser';

import { SubmissionPanelComponent } from './submission-panel.component';
import { SubmissionDateViewComponent } from './views/submission-date-view/submission-date-view.component';
import { SubmissionMultipleChoiceViewComponent } from './views/submission-multiple-choice-view/submission-multiple-choice-view.component';
import { SubmissionTextViewComponent } from './views/submission-text-view/submission-text-view.component';
import { SubmissionTimeViewComponent } from './views/submission-time-view/submission-time-view.component';

@NgModule({
  imports: [
    BrowserModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatIconModule,
    MatListModule,
    MatProgressSpinnerModule,
  ],
  exports: [SubmissionPanelComponent],
  declarations: [
    SubmissionPanelComponent,
    SubmissionDateViewComponent,
    SubmissionMultipleChoiceViewComponent,
    SubmissionTextViewComponent,
    SubmissionTimeViewComponent,
  ],
})
export class SubmissionPanelModule {}
