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

import { CommonModule } from '@angular/common';
import { FlexLayoutModule } from '@angular/flex-layout';
import { MainPageContainerComponent } from './main-page-container.component';
import { MainPageModule } from './../main-page/main-page.module';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

@NgModule({
  entryComponents: [],
  declarations: [MainPageContainerComponent],
  imports: [
    CommonModule,
    FlexLayoutModule,
    RouterModule,
    MainPageModule,
    MatProgressSpinnerModule,
  ],
})
export class MainPageContainerModule {}
