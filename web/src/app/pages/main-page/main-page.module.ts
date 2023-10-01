/**
 * Copyright 2019 The Ground Authors.
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
import {JobDialogModule} from './job-dialog/job-dialog.module';
import {DrawingToolsModule} from './drawing-tools/drawing-tools.module';
import {MainPageComponent} from './main-page.component';
import {MapModule} from './map/map.module';
import {MatButtonModule} from '@angular/material/button';
import {MatLegacyDialogModule as MatDialogModule} from '@angular/material/legacy-dialog';
import {MatSidenavModule} from '@angular/material/sidenav';
import {MatLegacyProgressSpinnerModule as MatProgressSpinnerModule} from '@angular/material/legacy-progress-spinner';
import {NgModule} from '@angular/core';
import {SidePanelModule} from './side-panel/side-panel.module';
import {SurveyHeaderModule} from './survey-header/survey-header.module';
import {MatIconModule} from '@angular/material/icon';
import {SurveyPageContainerModule} from 'app/components/survey-page-container/survey-page-container.module';

@NgModule({
  declarations: [MainPageComponent],
  exports: [MainPageComponent],
  imports: [
    CommonModule,
    DrawingToolsModule,
    JobDialogModule,
    MapModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSidenavModule,
    SidePanelModule,
    SurveyHeaderModule,
    SurveyPageContainerModule,
  ],
})
export class MainPageModule {}
