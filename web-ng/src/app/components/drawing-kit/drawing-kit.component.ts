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

import { Component } from '@angular/core';
import { DrawingKitService } from '../../services/drawing-kit/drawing-kit.service';
import { ProjectService } from '../../services/project/project.service';
import { Observable } from 'rxjs';
import { Layer } from '../../shared/models/layer.model';
import { List } from 'immutable';
import { map } from 'rxjs/internal/operators/map';
import { getPinImageSource } from '../map/ground-pin';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';

@Component({
  selector: 'drawing-kit',
  templateUrl: './drawing-kit.component.html',
  styleUrls: ['./drawing-kit.component.css'],
})
export class DrawingKitComponent {
  selectedValue: string | undefined;
  addPointValue = 'point';
  addPolygonValue = 'polygon';
  selectedLayerId = '';
  readonly layers$: Observable<List<Layer>>;
  readonly lang: string;

  constructor(
    private drawingKitService: DrawingKitService,
    private sanitizer: DomSanitizer,
    projectService: ProjectService
  ) {
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';
    this.layers$ = projectService
      .getActiveProject$()
      .pipe(
        map(project =>
          List(project.layers.valueSeq().toArray()).sortBy(l => l.index)
        )
      );
  }

  onButtonClick() {
    if (this.selectedValue === this.addPointValue) {
      if (this.drawingKitService.getIsAddingPoint()) {
        this.selectedValue = undefined;
        this.drawingKitService.setIsAddingPoint(false);
      } else {
        this.drawingKitService.setIsAddingPoint(true);
        this.drawingKitService.setIsAddingPolygon(false);
      }
    } else if (this.selectedValue === this.addPolygonValue) {
      if (this.drawingKitService.getIsAddingPolygon()) {
        this.selectedValue = undefined;
        this.drawingKitService.setIsAddingPolygon(false);
      } else {
        this.drawingKitService.setIsAddingPolygon(true);
        this.drawingKitService.setIsAddingPoint(false);
      }
    }
  }

  layerPinUrl(layer: Layer): SafeUrl {
    return this.sanitizer.bypassSecurityTrustUrl(
      getPinImageSource(layer?.color)
    );
  }

  onLayerIdChange() {
    this.drawingKitService.setLayerId(this.selectedLayerId);
  }

  showDrawingLayerSelector(): boolean {
    return this.drawingKitService.getIsAddingPoint();
  }
}
