/**
 * Copyright 2025 The Ground Authors.
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
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';

import {ShareDialogModule} from 'app/components/shared/share-dialog/share-dialog.module';
import {ShareListModule} from 'app/components/shared/share-list/share-list.module';

import {ShareSurveyComponent} from './share-survey.component';
import {DataVisibilityControlModule} from '../data-visibility-control/data-visibility-control.module';
import {GeneralAccessControlModule} from '../general-access-control/general-access-control.module';

@NgModule({
  declarations: [ShareSurveyComponent],
  imports: [
    CommonModule,
    DataVisibilityControlModule,
    FormsModule,
    ReactiveFormsModule,
    GeneralAccessControlModule,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatIconModule,
    MatInputModule,
    ShareDialogModule,
    ShareListModule,
  ],
  exports: [ShareSurveyComponent],
})
export class ShareSurveyModule {}
