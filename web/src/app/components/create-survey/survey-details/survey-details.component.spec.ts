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

import {NO_ERRORS_SCHEMA} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ReactiveFormsModule} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {SurveyDetailsComponent} from 'app/components/create-survey/survey-details/survey-details.component';

describe('SurveyDetailsComponent', () => {
  let component: SurveyDetailsComponent;
  let fixture: ComponentFixture<SurveyDetailsComponent>;

  const title = 'title';
  const description = 'description';

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [SurveyDetailsComponent],
      imports: [
        MatFormFieldModule,
        MatInputModule,
        ReactiveFormsModule,
        NoopAnimationsModule,
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(SurveyDetailsComponent);
    component = fixture.componentInstance;
    component.title = title;
    component.description = description;
    fixture.detectChanges();
    component.ngOnInit();
  });

  it('loads title and description to form', () => {
    expect(component.formGroup.controls[component.titleControlKey].value).toBe(
      title
    );
    expect(
      component.formGroup.controls[component.descriptionControlKey].value
    ).toBe(description);
  });

  it('toTitleAndDescription returns title and description from the form', () => {
    const newTitle = 'newTitle';
    const newDescription = 'newDescription';
    component.formGroup.controls[component.titleControlKey].setValue(newTitle);
    component.formGroup.controls[component.descriptionControlKey].setValue(
      newDescription
    );

    expect(component.toTitleAndDescription()).toEqual([
      newTitle,
      newDescription,
    ]);
  });
});
