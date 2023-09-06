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

import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {HeaderModule} from 'app/components/header/header.module';
import {SurveyHeaderComponent} from './survey-header.component';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatLegacyDialogModule as MatDialogModule} from '@angular/material/legacy-dialog';
import {InlineEditorModule} from 'app/components/inline-editor/inline-editor.module';
import {ShareDialogModule} from 'app/components/share-dialog/share-dialog.module';

@NgModule({
  declarations: [SurveyHeaderComponent],
  imports: [
    CommonModule,
    HeaderModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    InlineEditorModule,
    ShareDialogModule,
  ],
  exports: [SurveyHeaderComponent],
})
export class SurveyHeaderModule {}
