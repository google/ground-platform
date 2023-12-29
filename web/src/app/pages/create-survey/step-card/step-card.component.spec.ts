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

// import {ActivatedRoute} from '@angular/router';
// import {ActivatedRouteStub} from 'testing/activated-route-stub';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {BrowserModule} from '@angular/platform-browser';

import {StepCardComponent} from './step-card.component';

describe('StepCardComponent', () => {
  let component: StepCardComponent;
  let fixture: ComponentFixture<StepCardComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BrowserModule],
      declarations: [StepCardComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(StepCardComponent);
    component = fixture.componentInstance;
    component.title = 'title';
    component.description = 'description';
    fixture.detectChanges();
  });

  it('loads card header', () => {
    expect(
      fixture.debugElement.nativeElement.querySelector('.title').textContent
    ).toBeNonEmptyString();
  });

  it('loads card description', () => {
    expect(
      fixture.debugElement.nativeElement.querySelector('.description')
        .textContent
    ).toBeNonEmptyString();
  });
});
