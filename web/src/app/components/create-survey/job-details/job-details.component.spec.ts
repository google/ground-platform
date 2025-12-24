/**
 * Copyright 2023 The Ground Authors.
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
import { JobDetailsComponent } from 'app/components/create-survey/job-details/job-details.component';

describe('JobDetailsComponent', () => {
  let component: JobDetailsComponent;
  let fixture: ComponentFixture<JobDetailsComponent>;

  const jobName = 'jobName';

  beforeEach(() => {
    fixture = TestBed.createComponent(JobDetailsComponent);
    component = fixture.componentInstance;
    component.name = jobName;
    fixture.detectChanges();
    component.ngOnInit();
  });

  it('loads job name to form', () => {
    expect(component.formGroup.controls[component.nameControlKey].value).toBe(
      jobName
    );
  });

  it('toJobName returns job name from the form', () => {
    const newJobName = 'newName';
    component.formGroup.controls[component.nameControlKey].setValue(newJobName);

    expect(component.toJobName()).toBe(newJobName);
  });
});
