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

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FlexLayoutModule } from '@angular/flex-layout';
import { HeaderLayoutModule } from 'app/components/header-layout/header-layout.module';
import { SurveyHeaderComponent } from 'app/components/survey-header/survey-header.component';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule } from '@angular/material/dialog';
import { InlineEditorModule } from 'app/components/inline-editor/inline-editor.module';

@NgModule({
  declarations: [SurveyHeaderComponent],
  imports: [
    CommonModule,
    FlexLayoutModule,
    HeaderLayoutModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    InlineEditorModule,
  ],
  exports: [SurveyHeaderComponent],
})
export class SurveyHeaderModule {}
