/**
 * Copyright 2024 The Ground Authors.
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

import {CommonModule} from '@angular/common';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {DataSharingType} from 'app/models/survey.model';

import {DataSharingTermsComponent} from 'app/pages/create-survey/data-sharing-terms/data-sharing-terms.component';

describe('DataSharingTermsComponent', () => {
  let component: DataSharingTermsComponent;
  let fixture: ComponentFixture<DataSharingTermsComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [DataSharingTermsComponent],
      imports: [CommonModule],
    }).compileComponents();

    fixture = TestBed.createComponent(DataSharingTermsComponent);
    fixture.componentInstance.type = DataSharingType.PUBLIC;
    fixture.componentInstance.customText = 'Hey there here is an agreement';
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('displays mat-cards for each of the three data sharing types', () => {
    const matCards: HTMLElement[] =
      fixture.debugElement.nativeElement.querySelectorAll(
        '.data-sharing-terms-card'
      );
    const contentValues = Array.from(matCards).map((card: HTMLElement) => ({
      label: card
        .querySelector('.option-radio-button .data-sharing-label')!
        .textContent!.trim(),
      description: card
        .querySelector('.option-radio-button p')!
        .textContent!.trim(),
    }));
    expect(contentValues).toEqual([
      {
        label: 'Private',
        description: 'Data will be shared with survey organizers only',
      },
      {
        label: 'Public',
        description:
          'Survey organizers may share and use data publicly with no constraints',
      },
      {
        label: 'Custom agreement',
        description:
          'Survey organizers create terms which must be accepted by data collectors before collecting data',
      },
    ]);
  });
});
