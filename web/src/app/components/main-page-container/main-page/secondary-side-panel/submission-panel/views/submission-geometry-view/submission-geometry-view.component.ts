/**
 * Copyright 2026 The Ground Authors.
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

import { Component, computed, input, output } from '@angular/core';

import { Geometry } from 'app/models/geometry/geometry';
import { Point } from 'app/models/geometry/point';
import { Task, TaskType } from 'app/models/task/task.model';

@Component({
  selector: 'submission-geometry-view',
  templateUrl: './submission-geometry-view.component.html',
  styleUrls: ['./submission-geometry-view.component.scss'],
  standalone: false,
})
export class SubmissionGeometryViewComponent {
  task = input.required<Task>();
  geometry = input.required<Geometry>();
  displayIndex = input.required<number>();
  jobColor = input<string>();
  isSelected = input<boolean>(false);

  readonly selected = output<void>();

  public taskType = TaskType;

  readonly capturedCoord = computed(() => {
    const task = this.task();
    if (task.type !== TaskType.CAPTURE_LOCATION) return null;
    return this.formatCaptureLocationCoord(this.geometry() as Point);
  });

  onClick(): void {
    this.selected.emit();
  }

  private formatCaptureLocationCoord(point: Point): string {
    // x represents longitude, y represents latitude
    const { coord, accuracy, altitude } = point;
    const { x, y } = coord;
    const lngSuffix =
      x >= 0
        ? $localize`:@@app.labels.lngEast:E`
        : $localize`:@@app.labels.lngWest:W`;
    const latSuffix =
      y >= 0
        ? $localize`:@@app.labels.latNorth:N`
        : $localize`:@@app.labels.latSouth:S`;
    const lng = `${Math.abs(x)}° ${lngSuffix}`;
    const lat = `${Math.abs(y)}° ${latSuffix}`;
    const result = [`${lat}, ${lng}`];
    if (altitude)
      result.push(
        $localize`:@@app.labels.altitude:Altitude: ${altitude}:altitude:m`
      );
    if (accuracy)
      result.push(
        $localize`:@@app.labels.accuracy:Accuracy: ${accuracy}:accuracy:m`
      );
    return result.join('\n');
  }
}
