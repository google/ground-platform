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
import { MatSelectChange } from '@angular/material/select';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { TaskType } from 'app/models/task/task.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { DialogService } from 'app/services/dialog/dialog.service';

import { TaskFormComponent, TaskTypeOptions } from './task-form.component';
import { TaskFormModule } from './task-form.module';

describe('TaskFormComponent', () => {
  let fixture: ComponentFixture<TaskFormComponent>;
  let component: TaskFormComponent;
  let formGroup: FormGroup;
  let dialogService: jasmine.SpyObj<DialogService>;

  const dateOption = TaskTypeOptions.find(o => o.type === TaskType.DATE)!;

  function answerDialog(confirmed: boolean) {
    dialogService.openConfirmationDialog.and.returnValue({
      afterClosed: () => of(confirmed),
    } as ReturnType<DialogService['openConfirmationDialog']>);
  }

  function selectChange(value: unknown) {
    const source = { value: undefined as unknown };
    return {
      change: { value, source } as unknown as MatSelectChange,
      source,
    };
  }

  beforeEach(async () => {
    dialogService = jasmine.createSpyObj('DialogService', [
      'openConfirmationDialog',
    ]);
    await TestBed.configureTestingModule({
      imports: [TaskFormModule, NoopAnimationsModule],
      providers: [
        { provide: DataStoreService, useValue: { generateId: () => 'id' } },
        { provide: DialogService, useValue: dialogService },
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
    fixture.detectChanges();
  });

  describe('when the survey is not published', () => {
    it('changes the type without asking', () => {
      component.onTaskTypeChange(selectChange(dateOption).change);

      expect(dialogService.openConfirmationDialog).not.toHaveBeenCalled();
      expect(formGroup.get('type')!.value).toBe(TaskType.DATE);
    });
  });

  describe('when the survey is published', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('warnOnTypeChange', true);
    });

    it('changes the type once the user confirms', () => {
      answerDialog(true);

      component.onTaskTypeChange(selectChange(dateOption).change);

      expect(dialogService.openConfirmationDialog).toHaveBeenCalledTimes(1);
      expect(formGroup.get('type')!.value).toBe(TaskType.DATE);
    });

    it('keeps the type and puts the select back if the user declines', () => {
      answerDialog(false);
      const previous = component.taskTypeOption;
      const { change, source } = selectChange(dateOption);

      component.onTaskTypeChange(change);

      expect(formGroup.get('type')!.value).toBe(TaskType.TEXT);
      expect(source.value).toBe(previous);
    });

    it('keeps the group of a location task if the user declines', () => {
      formGroup.get('type')!.setValue(TaskType.DROP_PIN);
      component.ngOnInit();
      answerDialog(false);
      const group = component.taskGroup;
      const { change } = selectChange(component.TaskGroup.DRAW_AREA);

      component.onTaskGroupChange(change);

      expect(formGroup.get('type')!.value).toBe(TaskType.DROP_PIN);
      expect(component.taskGroup).toBe(group);
    });
  });
});
