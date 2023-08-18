/**
 * Copyright 2023 Google LLC
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
import {
  DataCollectionComponent,
  DataCollectionOption,
} from 'app/pages/create-survey/data-collection/data-collection.component';

describe('DataCollectionComponent', () => {
  let component: DataCollectionComponent;
  let fixture: ComponentFixture<DataCollectionComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BrowserModule],
      declarations: [DataCollectionComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(DataCollectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads section header', () => {
    expect(
      fixture.debugElement.nativeElement.querySelector('.title').textContent
    ).toBe('Who decides where data is collected?');
  });

  it('loads section description', () => {
    expect(
      fixture.debugElement.nativeElement.querySelector('.description')
        .textContent
    ).toContain(
      'Specify who defines locations of interest where data will be collected'
    );
  });

  it('sets the default data collection option to survey organizers', () => {
    expect(
      component.formGroup.controls[component.dataCollectionControlKey].value
    ).toBe(DataCollectionOption.SURVEY_ORGANIZERS);
  });

  it('displays mat-cards for each of the three data collection options', () => {
    const matCards: HTMLElement[] =
      fixture.debugElement.nativeElement.querySelectorAll(
        '.data-collection-option'
      );
    const contentValues = Array.from(matCards).map((card: HTMLElement) => ({
      label: card.querySelector('.option-radio-button')!.textContent!.trim(),
      description: card
        .querySelector('.option-description')!
        .textContent!.trim(),
    }));
    expect(contentValues).toEqual([
      {
        label: 'Survey organizers',
        description:
          'Data collectors collect data exclusively about locations of interest uploaded by you and other survey organizers.',
      },
      {
        label: 'Data collectors',
        description:
          'Data collectors suggest and collect data about new locations of interest as they discover them.',
      },
      {
        label: 'Both',
        description:
          'Data collectors may collect data about locations of interest uploaded by survey organizers, or suggest new locations as needed.',
      },
    ]);
  });

  it('updates the dataCollectionMethod value on click of the card', () => {
    const matCards: HTMLElement[] =
      fixture.debugElement.nativeElement.querySelectorAll(
        '.data-collection-option'
      );
    const [, collectorsCard, bothCard] = matCards;

    collectorsCard.click();

    expect(
      component.formGroup.controls[component.dataCollectionControlKey].value
    ).toBe(DataCollectionOption.DATA_COLLECTORS);

    bothCard.click();

    expect(
      component.formGroup.controls[component.dataCollectionControlKey].value
    ).toBe(DataCollectionOption.ORGANIZERS_AND_COLLECTORS);
  });
});
