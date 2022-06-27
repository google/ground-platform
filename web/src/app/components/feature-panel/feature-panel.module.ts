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
import { FeaturePanelComponent } from './feature-panel.component';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';

import { LayerListItemModule } from '../layer-list-item/layer-list-item.module';
import { FeaturePanelHeaderModule } from '../feature-panel-header/feature-panel-header.module';

@NgModule({
  imports: [
    BrowserModule,
    MatCardModule,
    MatListModule,
    LayerListItemModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    FeaturePanelHeaderModule,
  ],
  exports: [FeaturePanelComponent],
  declarations: [FeaturePanelComponent],
})
export class FeaturePanelModule {}
