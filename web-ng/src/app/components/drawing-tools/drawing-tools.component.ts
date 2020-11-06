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

import { Component, ChangeDetectionStrategy } from '@angular/core';
import {
  DrawingToolsService,
  EditMode,
} from '../../services/drawing-tools/drawing-tools.service';
import { ProjectService } from '../../services/project/project.service';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Layer } from '../../shared/models/layer.model';
import { List } from 'immutable';
import { map } from 'rxjs/internal/operators/map';
import { getPinImageSource } from '../map/ground-pin';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';

@Component({
  selector: 'ground-drawing-tools',
  templateUrl: './drawing-tools.component.html',
  styleUrls: ['./drawing-tools.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DrawingToolsComponent {
  pointValue = 'point';
  polygonValue = 'polygon';
  selectedValue = '';
  private lastSelectedValue = '';
  selectedLayerId = '';
  readonly layers$: Observable<List<Layer>>;
  readonly lang: string;

  constructor(
    private drawingToolsService: DrawingToolsService,
    private sanitizer: DomSanitizer,
    projectService: ProjectService
  ) {
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';
    this.layers$ = projectService.getActiveProject$().pipe(
      tap(project => {
        this.selectedLayerId = project.layers.keySeq().first();
        this.drawingToolsService.setSelectedLayerId(this.selectedLayerId);
      }),
      map(project =>
        List(project.layers.valueSeq().toArray()).sortBy(l => l.index)
      )
    );
  }

  onButtonClick() {
    if (this.lastSelectedValue === this.selectedValue) {
      this.selectedValue = '';
      this.drawingToolsService.setEditMode(EditMode.None);
    } else if (this.selectedValue === this.pointValue) {
      this.drawingToolsService.setEditMode(EditMode.AddPoint);
    } else if (this.selectedValue === this.polygonValue) {
      this.drawingToolsService.setEditMode(EditMode.AddPolygon);
    }
    this.lastSelectedValue = this.selectedValue;
  }

  layerPinUrl(layer: Layer): SafeUrl {
    return this.sanitizer.bypassSecurityTrustUrl(
      getPinImageSource(layer?.color)
    );
  }

  onLayerIdChange() {
    this.drawingToolsService.setSelectedLayerId(this.selectedLayerId);
  }
}
