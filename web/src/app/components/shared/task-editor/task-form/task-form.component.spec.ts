/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder, FormGroup } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { TaskType } from 'app/models/task/task.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';

import { TaskFormComponent, TaskTypeOptions } from './task-form.component';
import { TaskFormModule } from './task-form.module';

describe('TaskFormComponent', () => {
  let fixture: ComponentFixture<TaskFormComponent>;
  let component: TaskFormComponent;
  let formGroup: FormGroup;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TaskFormModule, NoopAnimationsModule],
      providers: [
        { provide: DataStoreService, useValue: { generateId: () => 'id' } },
      ],
    }).compileComponents();

    formGroup = new FormBuilder().group({
      id: 'task1',
      type: TaskType.TEXT,
      label: 'Label',
      required: false,
      cardinality: null,
      options: new FormBuilder().array([]),
      hasOtherOption: false,
      addLoiTask: false,
    });
    fixture = TestBed.createComponent(TaskFormComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('formGroup', formGroup);
    fixture.componentRef.setInput('formGroupIndex', 0);
  });

  function typeSelect(): HTMLElement {
    return fixture.nativeElement.querySelector('.task-type-select');
  }

  it('lets the type of a new task change', () => {
    fixture.detectChanges();

    expect(typeSelect().classList).not.toContain('mat-mdc-select-disabled');

    component.onTaskTypeSelect(
      TaskTypeOptions.find(o => o.type === TaskType.DATE)!
    );

    expect(formGroup.get('type')!.value).toBe(TaskType.DATE);
  });

  it('keeps the type of a locked task', () => {
    fixture.componentRef.setInput('typeLocked', true);
    fixture.detectChanges();

    expect(typeSelect().classList).toContain('mat-mdc-select-disabled');

    component.onTaskTypeSelect(
      TaskTypeOptions.find(o => o.type === TaskType.DATE)!
    );
    component.onTaskGroupSelect(component.TaskGroup.DROP_PIN);

    expect(formGroup.get('type')!.value).toBe(TaskType.TEXT);
  });
});
