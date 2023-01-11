/*
Copyright 2020 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

import {ComponentFixture, TestBed} from '@angular/core/testing';
import {AngularFireAuth} from '@angular/fire/compat/auth';
import {AngularFirestore} from '@angular/fire/compat/firestore';
import {MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatListModule} from '@angular/material/list';
import {MatMenuModule} from '@angular/material/menu';
import {Router} from '@angular/router';
import {BehaviorSubject, NEVER, of} from 'rxjs';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {LocationOfInterestPanelHeaderComponent} from 'app/components/loi-panel-header/loi-panel-header.component';
import {
  GenericLocationOfInterest,
  LocationOfInterest,
} from 'app/models/loi.model';
import {Point} from 'app/models/geometry/point';
import {Coordinate} from 'app/models/geometry/coordinate';
import {List, Map} from 'immutable';
import {Polygon} from 'app/models/geometry/polygon';
import {LinearRing} from 'app/models/geometry/linear-ring';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {MultiPolygon} from 'app/models/geometry/multi-polygon';

type LoiPanelHeaderFixture =
  ComponentFixture<LocationOfInterestPanelHeaderComponent>;

function getAvatarElement(fixture: LoiPanelHeaderFixture): HTMLElement {
  return fixture.nativeElement.querySelector('img.mat-list-avatar');
}

function getHeaderElement(fixture: LoiPanelHeaderFixture): HTMLElement {
  const element = fixture.nativeElement.querySelector('h3');
  console.log(element);
  return element;
}

describe('LocationOfInterestPanelHeaderComponent', () => {
  let component: LocationOfInterestPanelHeaderComponent;
  let fixture: ComponentFixture<LocationOfInterestPanelHeaderComponent>;
  let mockSelectedLoi$: BehaviorSubject<LocationOfInterest>;

  const pointLocationOfInterest = new GenericLocationOfInterest(
    'somePoint',
    'someJob',
    new Point(new Coordinate(1.23, 4.56)),
    Map()
  );
  const polygon1 = new Polygon(
    new LinearRing(
      List([
        new Coordinate(0, 0),
        new Coordinate(10, 0),
        new Coordinate(10, 10),
        new Coordinate(0, 0),
      ])
    ),
    List()
  );
  const polygon2 = new Polygon(
    new LinearRing(
      List([new Coordinate(3, 3), new Coordinate(12, 12), new Coordinate(2, 6)])
    ),
    List()
  );
  const polygonLocationOfInterest = new GenericLocationOfInterest(
    'somePolygon',
    'someJob',
    polygon1,
    Map()
  );
  const multiPolygonLocationOfInterest = new GenericLocationOfInterest(
    'someMultiPolygon',
    'someJob',
    new MultiPolygon(List([polygon1, polygon2])),
    Map()
  );

  beforeEach(async () => {
    const navigationService = {
      getSurveyId$: () => of(''),
      getLocationOfInterestId$: () => of(''),
    };

    mockSelectedLoi$ = new BehaviorSubject<LocationOfInterest>(
      pointLocationOfInterest
    );
    const locationOfInterestService = {
      getSelectedLocationOfInterest$: () => mockSelectedLoi$,
    };

    await TestBed.configureTestingModule({
      declarations: [LocationOfInterestPanelHeaderComponent],
      imports: [MatIconModule, MatListModule, MatMenuModule, MatDialogModule],
      providers: [
        {provide: Router, useValue: {}},
        {provide: AngularFirestore, useValue: {}},
        {provide: AngularFireAuth, useValue: {authState: NEVER}},
        {provide: NavigationService, useValue: navigationService},
        {
          provide: LocationOfInterestService,
          useValue: locationOfInterestService,
        },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(LocationOfInterestPanelHeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('the avatar', () => {
    it('should be a point if the selected LOI is a point', () => {
      const avatarImg = getAvatarElement(fixture);

      expect(avatarImg.getAttribute('alt')).toBe('point');
      expect(avatarImg.getAttribute('src')).toContain('data:image/svg');
    });

    it('should be a polygon if the selected LOI is a polygon', () => {
      mockSelectedLoi$.next(polygonLocationOfInterest);
      fixture.detectChanges();

      const avatarImg = getAvatarElement(fixture);

      expect(avatarImg.getAttribute('alt')).toBe('polygon');
      expect(avatarImg.getAttribute('src')).toBe(
        '/assets/img/polygon_icon.svg'
      );
    });
  });

  describe('the LOI name', () => {
    it('should be "Point" if the LOI is a point', () => {
      const header = getHeaderElement(fixture);

      expect(header.textContent).toBe('Point');
    });

    it('should be "Polygon" if the LOI is a polygon', () => {
      mockSelectedLoi$.next(polygonLocationOfInterest);
      fixture.detectChanges();

      const header = getHeaderElement(fixture);

      expect(header.textContent).toBe('Polygon');
    });

    it('should be "Multipolygon" if the LOI is a multipolygon', () => {
      mockSelectedLoi$.next(multiPolygonLocationOfInterest);
      fixture.detectChanges();

      const header = getHeaderElement(fixture);

      expect(header.textContent).toBe('Multipolygon');
    });
  });
});
