/**
 * Copyright 2019 Google LLC
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
import { LayerDialogComponent } from '../layer-dialog/layer-dialog.component';
import { LayerDialogModule } from '../layer-dialog/layer-dialog.module';
import { DrawingKitModule } from '../drawing-kit/drawing-kit.module';
import { MainPageComponent } from './main-page.component';
import { MapModule } from '../map/map.module';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSidenavModule } from '@angular/material/sidenav';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SidePanelModule } from '../side-panel/side-panel.module';
import { ProjectHeaderModule } from '../project-header/project-header.module';
import { MatIconModule } from '@angular/material/icon';

@NgModule({
  entryComponents: [LayerDialogComponent],
  declarations: [MainPageComponent],
  imports: [
    CommonModule,
    DrawingKitModule,
    LayerDialogModule,
    MapModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatSidenavModule,
    RouterModule,
    SidePanelModule,
    ProjectHeaderModule,
  ],
})
export class MainPageModule {}
