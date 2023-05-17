/**
 * Copyright 2023 Google LLC
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
import {LoiSelectionComponent} from './loi-selection.component';
import {MatButtonModule} from '@angular/material/button';
import {MapModule} from 'app/pages/main-page-container/main-page/map/map.module';
import {MatIconModule} from '@angular/material/icon';
import {MatListModule} from '@angular/material/list';

@NgModule({
  declarations: [LoiSelectionComponent],
  imports: [
    CommonModule,
    MatButtonModule,
    MatListModule,
    MapModule,
    MatIconModule,
  ],
  exports: [LoiSelectionComponent],
})
export class LoiSelectionModule {}
