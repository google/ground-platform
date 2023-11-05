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

import {NgModule} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatDividerModule} from '@angular/material/divider';
import {MatIconModule} from '@angular/material/icon';
import {BrowserModule} from '@angular/platform-browser';

import {LocationOfInterestPanelModule} from './loi-panel/loi-panel.module';
import {SecondarySidePanelComponent} from './secondary-side-panel.component';
import {SubmissionPanelModule} from './submission-panel/submission-panel.module';

@NgModule({
  imports: [
    BrowserModule,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatIconModule,
    LocationOfInterestPanelModule,
    SubmissionPanelModule,
  ],
  exports: [SecondarySidePanelComponent],
  declarations: [SecondarySidePanelComponent],
})
export class SecondarySidePanelModule {}
