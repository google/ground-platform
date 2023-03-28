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
import {GoogleMapsModule} from '@angular/google-maps';
import {Coordinate} from 'app/models/geometry/coordinate';
import {Point} from 'app/models/geometry/point';
import {
  GenericLocationOfInterest,
  LocationOfInterest,
} from 'app/models/loi.model';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {List, Map} from 'immutable';
import {BehaviorSubject} from 'rxjs';
import {LoiSelectionComponent} from './loi-selection.component';

describe('LoiSelectionFormComponent', () => {
  let fixture: ComponentFixture<LoiSelectionComponent>;
  let mockLois$: BehaviorSubject<List<LocationOfInterest>>;
  let loiServiceSpy: jasmine.SpyObj<LocationOfInterestService>;

  const poiId1 = 'poi001';
  const poiId2 = 'poi002';
  const jobId1 = 'job001';
  const jobId2 = 'job002';
  const poi1 = new GenericLocationOfInterest(
    poiId1,
    jobId1,
    new Point(new Coordinate(1.23, 4.56)),
    Map()
  );
  const poi2 = new GenericLocationOfInterest(
    poiId2,
    jobId2,
    new Point(new Coordinate(12.3, 45.6)),
    Map()
  );

  beforeEach(() => {
    loiServiceSpy = jasmine.createSpyObj<LocationOfInterestService>(
      'LocationOfInterestService',
      ['getLocationsOfInterest$', 'updatePoint', 'addPoint']
    );
    mockLois$ = new BehaviorSubject<List<LocationOfInterest>>(
      List<LocationOfInterest>([poi1, poi2])
    );
    loiServiceSpy.getLocationsOfInterest$.and.returnValue(mockLois$);

    TestBed.configureTestingModule({
      imports: [GoogleMapsModule],
      declarations: [LoiSelectionComponent],
      providers: [
        {
          provide: LocationOfInterestService,
          useValue: loiServiceSpy,
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LoiSelectionComponent);
    fixture.detectChanges();
  });

  it('loads LOI section header', () => {
    expect(
      fixture.debugElement.nativeElement.querySelector('h1').textContent
    ).toBe('Where should data be collected?');
  });

  it('loads map component', () => {
    expect(
      fixture.debugElement.nativeElement.querySelector('ground-map')
    ).toBeDefined();
  });

  it('shows list of LOIs', () => {
    const loiList: HTMLElement =
      fixture.debugElement.nativeElement.querySelector('.loi-list');
    const loiListValues = Array.from(loiList.querySelectorAll('li')).map(
      (item: HTMLElement) => item.textContent
    );
    expect(loiListValues).toEqual(['poi001', 'poi002']);
  });
});
