/**
 * Copyright 2020 Google LLC
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

import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { SidePanelComponent } from 'app/components/side-panel/side-panel.component';
import { LocationOfInterestPanelModule } from 'app/components/loi-panel/loi-panel.module';
import { JobListModule } from 'app/components/job-list/job-list.module';
import { SubmissionFormModule } from 'app/components/submission-form/submission-form.module';
import { LocationOfInterestListModule } from 'app/components/loi-list/loi-list.module';

@NgModule({
  imports: [
    BrowserModule,
    LocationOfInterestPanelModule,
    JobListModule,
    SubmissionFormModule,
    LocationOfInterestListModule,
  ],
  exports: [SidePanelComponent],
  declarations: [SidePanelComponent],
})
export class SidePanelModule {}
