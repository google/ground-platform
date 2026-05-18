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

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SubmissionTimeViewComponent } from './submission-time-view.component';

describe('SubmissionTimeViewComponent', () => {
  let fixture: ComponentFixture<SubmissionTimeViewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [SubmissionTimeViewComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SubmissionTimeViewComponent);
  });

  it('renders the time in locale format', () => {
    const date = new Date('2023-01-01T12:00:00');
    fixture.componentRef.setInput('time', date);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent.trim()).toBe(
      date.toLocaleTimeString([], { hour: 'numeric', minute: 'numeric' })
    );
  });
});
