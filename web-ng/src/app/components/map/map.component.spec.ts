/**
 * Copyright 2021 Google LLC
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

import firebase from 'firebase/app';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { MapComponent } from './map.component';
import { MatDialog } from '@angular/material/dialog';
import { ProjectService } from '../../services/project/project.service';
import { FeatureService } from '../../services/feature/feature.service';
import { NavigationService } from '../../services/navigation/navigation.service';
import { Project } from '../../shared/models/project.model';
import { Feature, LocationFeature } from '../../shared/models/feature.model';
import { StringMap } from '../../shared/models/string-map.model';
import { Map, List } from 'immutable';
import { Layer } from '../../shared/models/layer.model';
import { of } from 'rxjs';
import { GoogleMapsModule } from '@angular/google-maps';
import { urlPrefix } from './ground-pin';

describe('MapComponent', () => {
  let component: MapComponent;
  let fixture: ComponentFixture<MapComponent>;
  let googleMap: google.maps.Map;
  const dialog: Partial<MatDialog> = {};

  const mockProject = new Project(
    'project001',
    StringMap({ en: 'title1' }),
    StringMap({ en: 'description1' }),
    /* layers= */ Map({
      layer001: new Layer(
        'layer001',
        /* index */ -1,
        'red',
        StringMap({ en: 'layer001 name' }),
        /* forms= */ Map()
      ),
      layer002: new Layer(
        'layer002',
        /* index */ -1,
        'green',
        StringMap({ en: 'layer002 name' }),
        /* forms= */ Map()
      ),
    }),
    /* acl= */ Map()
  );
  const mockFeatures = List<Feature>([
    new LocationFeature(
      'feature001',
      'layer001',
      new firebase.firestore.GeoPoint(1.23, 4.56)
    ),
    new LocationFeature(
      'feature002',
      'layer002',
      new firebase.firestore.GeoPoint(12.3, 45.6)
    ),
  ]);

  const projectServiceSpy = jasmine.createSpyObj('ProjectService', [
    'getActiveProject$',
  ]);
  const featureServiceSpy = jasmine.createSpyObj('FeatureService', [
    'getFeatures$',
  ]);
  const navigationService = jasmine.createSpyObj('NavigationService', [
    'getFeatureId$',
    'getObservationId$',
  ]);

  beforeEach(
    waitForAsync(() => {
      TestBed.configureTestingModule({
        imports: [GoogleMapsModule],
        declarations: [MapComponent],
        providers: [
          { provide: MatDialog, useValue: dialog },
          { provide: ProjectService, useValue: projectServiceSpy },
          { provide: FeatureService, useValue: featureServiceSpy },
          { provide: NavigationService, useValue: navigationService },
        ],
      }).compileComponents();
    })
  );

  beforeEach(() => {
    projectServiceSpy.getActiveProject$.and.returnValue(
      of<Project>(mockProject)
    );
    featureServiceSpy.getFeatures$.and.returnValue(
      of<List<Feature>>(mockFeatures)
    );
    navigationService.getFeatureId$.and.returnValue(of<string | null>(null));
    navigationService.getObservationId$.and.returnValue(
      of<string | null>(null)
    );

    googleMap = new google.maps.Map(document.createElement('div'), {});
    spyOn(googleMap, 'addListener').and.returnValue({ remove: () => {} });
    spyOn(googleMap, 'setOptions').and.stub();
    const constructorSpy = spyOn(google.maps, 'Map');
    constructorSpy.and.returnValue(googleMap);

    fixture = TestBed.createComponent(MapComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create a google map instance', () => {
    expect(component.map.googleMap === googleMap).toBeTrue();
  });

  it('should render 2 markers on map', () => {
    expect(component.markers.size).toEqual(2);
    const marker1 = component.markers.get('feature001')!;
    assertMarkerLatLng(marker1, new google.maps.LatLng(1.23, 4.56));
    assertMarkerIcon(marker1, 'red', 30);
    expect(marker1.getMap()).toBeDefined();
    const marker2 = component.markers.get('feature002')!;
    assertMarkerLatLng(marker2, new google.maps.LatLng(12.3, 45.6));
    assertMarkerIcon(marker2, 'green', 30);
    expect(marker2.getMap()).toBeDefined();
  });
});

function assertMarkerLatLng(
  marker: google.maps.Marker,
  latLng: google.maps.LatLng
): void {
  expect(marker.getPosition()?.lat()).toEqual(latLng.lat());
  expect(marker.getPosition()?.lng()).toEqual(latLng.lng());
}

function assertMarkerIcon(
  marker: google.maps.Marker,
  iconColor: string,
  iconSize: number
): void {
  const icon = marker.getIcon() as google.maps.ReadonlyIcon;
  expect(atob(icon.url.slice(urlPrefix.length))).toContain(iconColor);
  expect(icon.scaledSize?.height).toEqual(iconSize);
  expect(icon.scaledSize?.width).toEqual(iconSize);
}
