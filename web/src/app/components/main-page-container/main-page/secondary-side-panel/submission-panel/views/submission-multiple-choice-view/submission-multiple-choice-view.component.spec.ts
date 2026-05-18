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
import { List } from 'immutable';

import { MultipleSelection } from 'app/models/submission/multiple-selection';
import { Task, TaskType } from 'app/models/task/task.model';

import { SubmissionMultipleChoiceViewComponent } from './submission-multiple-choice-view.component';

describe('SubmissionMultipleChoiceViewComponent', () => {
  let fixture: ComponentFixture<SubmissionMultipleChoiceViewComponent>;
  let component: SubmissionMultipleChoiceViewComponent;

  const task = new Task(
    'task1',
    TaskType.MULTIPLE_CHOICE,
    'Multiple Choice',
    true,
    1
  );

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [SubmissionMultipleChoiceViewComponent],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(SubmissionMultipleChoiceViewComponent);
    component = fixture.componentInstance;
  });

  function setInputs(selection: MultipleSelection) {
    fixture.componentRef.setInput('task', task);
    fixture.componentRef.setInput('selection', selection);
    fixture.detectChanges();
  }

  it('formats "Other: <value>" when otherValue is non-empty', () => {
    setInputs(new MultipleSelection(List(['option1']), 'Other value'));
    expect(component.otherValue()).toBe('Other: Other value');
  });

  it('returns "Other" when both values and otherValue are empty', () => {
    setInputs(new MultipleSelection(List(), ''));
    expect(component.otherValue()).toBe('Other');
  });
});
