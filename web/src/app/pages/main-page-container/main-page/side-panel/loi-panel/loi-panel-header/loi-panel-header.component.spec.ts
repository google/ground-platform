/*
Copyright 2020 The Ground Authors.

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
import {List, Map} from 'immutable';
import {BehaviorSubject, NEVER, of} from 'rxjs';

import {Coordinate} from 'app/models/geometry/coordinate';
import {LinearRing} from 'app/models/geometry/linear-ring';
import {MultiPolygon} from 'app/models/geometry/multi-polygon';
import {Point} from 'app/models/geometry/point';
import {Polygon} from 'app/models/geometry/polygon';
import {LocationOfInterest} from 'app/models/loi.model';
import {LocationOfInterestPanelHeaderComponent} from 'app/pages/main-page-container/main-page/side-panel/loi-panel/loi-panel-header/loi-panel-header.component';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';

type LoiPanelHeaderFixture =
  ComponentFixture<LocationOfInterestPanelHeaderComponent>;

function getAvatarElement(fixture: LoiPanelHeaderFixture): HTMLElement {
  return fixture.nativeElement.querySelector('img.mat-mdc-list-item-avatar');
}

function getHeaderElement(fixture: LoiPanelHeaderFixture): HTMLElement {
  const element = fixture.nativeElement.querySelector('h3');
  return element;
}

describe('LocationOfInterestPanelHeaderComponent', () => {
  let component: LocationOfInterestPanelHeaderComponent;
  let fixture: ComponentFixture<LocationOfInterestPanelHeaderComponent>;
  let mockSelectedLoi$: BehaviorSubject<LocationOfInterest>;

  const pointLocationOfInterest = new LocationOfInterest(
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
  const polygonLocationOfInterest = new LocationOfInterest(
    'somePolygon',
    'someJob',
    polygon1,
    Map()
  );
  const multiPolygonLocationOfInterest = new LocationOfInterest(
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
    it('return correct label if unnamed point with no id', () => {
      const header = getHeaderElement(fixture);

      expect(header.textContent).toBe('Unnamed point');
    });

    it('return correct label if unnamed polygon with no id', () => {
      mockSelectedLoi$.next(polygonLocationOfInterest);
      fixture.detectChanges();

      const header = getHeaderElement(fixture);

      expect(header.textContent).toBe('Unnamed area');
    });

    it('return correct label if unnamed multipolygon with no id', () => {
      mockSelectedLoi$.next(multiPolygonLocationOfInterest);
      fixture.detectChanges();

      const header = getHeaderElement(fixture);

      expect(header.textContent).toBe('Unnamed area');
    });
  });
});
