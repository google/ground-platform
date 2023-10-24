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
import {
  LoiPermissionsComponent,
  LoiPermissionsOption,
} from './loi-permissions.component';

describe('LoiPermissionsComponent', () => {
  let component: LoiPermissionsComponent;
  let fixture: ComponentFixture<LoiPermissionsComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BrowserModule],
      declarations: [LoiPermissionsComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(LoiPermissionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads section header', () => {
    expect(
      fixture.debugElement.nativeElement.querySelector('.title').textContent
    ).toBeNonEmptyString();
  });

  it('loads section description', () => {
    expect(
      fixture.debugElement.nativeElement.querySelector('.description')
        .textContent
    ).toBeNonEmptyString();
  });

  it('sets the default data collection option to survey organizers', () => {
    expect(
      component.formGroup.controls[component.loiPermissionsControlKey].value
    ).toBe(LoiPermissionsOption.SURVEY_ORGANIZERS);
  });

  it('displays mat-cards for each of the three data collection options', () => {
    const matCards: HTMLElement[] =
      fixture.debugElement.nativeElement.querySelectorAll(
        '.loi-permissions-option'
      );
    const contentValues = Array.from(matCards).map((card: HTMLElement) => ({
      label: card.querySelector('.option-radio-button')!.textContent!.trim(),
      description: card
        .querySelector('.option-description')!
        .textContent!.trim(),
    }));
    expect(contentValues).toHaveSize(3);
  });

  it('updates the loiPermissionsMethod value on click of the card', () => {
    const matCards: HTMLElement[] =
      fixture.debugElement.nativeElement.querySelectorAll(
        '.loi-permissions-option'
      );
    const [, collectorsCard, bothCard] = matCards;

    collectorsCard.click();

    expect(
      component.formGroup.controls[component.loiPermissionsControlKey].value
    ).toBe(LoiPermissionsOption.DATA_COLLECTORS);

    bothCard.click();

    expect(
      component.formGroup.controls[component.loiPermissionsControlKey].value
    ).toBe(LoiPermissionsOption.ORGANIZERS_AND_COLLECTORS);
  });
});
