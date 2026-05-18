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

import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Coordinate } from 'app/models/geometry/coordinate';
import { Point } from 'app/models/geometry/point';
import { Task, TaskType } from 'app/models/task/task.model';

import { SubmissionGeometryViewComponent } from './submission-geometry-view.component';

describe('SubmissionGeometryViewComponent', () => {
  let fixture: ComponentFixture<SubmissionGeometryViewComponent>;
  let component: SubmissionGeometryViewComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [SubmissionGeometryViewComponent],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(SubmissionGeometryViewComponent);
    component = fixture.componentInstance;
  });

  it('formats capture location coordinates', () => {
    const task = new Task(
      'task1',
      TaskType.CAPTURE_LOCATION,
      'Capture Location',
      true,
      1
    );
    const point = new Point(new Coordinate(10, 20), 10, 100);
    fixture.componentRef.setInput('task', task);
    fixture.componentRef.setInput('geometry', point);
    fixture.componentRef.setInput('displayIndex', 1);
    fixture.detectChanges();

    const text = component.capturedCoord()!;
    expect(text).toContain('20° N, 10° E');
    expect(text).toContain('Altitude: 100m');
    expect(text).toContain('Accuracy: 10m');
  });

  it('emits "selected" when clicked', () => {
    const task = new Task('task1', TaskType.DRAW_AREA, 'Draw Area', true, 1);
    fixture.componentRef.setInput('task', task);
    fixture.componentRef.setInput(
      'geometry',
      new Point(new Coordinate(0, 0))
    );
    fixture.componentRef.setInput('displayIndex', 1);
    fixture.detectChanges();

    let emitted = false;
    component.selected.subscribe(() => (emitted = true));
    component.onClick();

    expect(emitted).toBe(true);
  });
});
