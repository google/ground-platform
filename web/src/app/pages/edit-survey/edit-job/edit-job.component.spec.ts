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

import {ActivatedRoute} from '@angular/router';
import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';
import {EditJobComponent} from 'app/pages/edit-survey/edit-job/edit-job.component';
import {By} from '@angular/platform-browser';
import {from} from 'rxjs';

describe('EditJobComponent', () => {
  let fixture: ComponentFixture<EditJobComponent>;
  const jobId = 'job-123';

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [EditJobComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            params: from([{id: jobId}]),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EditJobComponent);
    fixture.detectChanges();
  }));

  it('displays current job ID from router', () => {
    const tempTitle = fixture.debugElement.query(By.css('#temp-title'))
      .nativeElement.innerText;
    expect(tempTitle).toContain(jobId);
  });
});
