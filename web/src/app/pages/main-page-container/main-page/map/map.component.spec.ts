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

import {
  ComponentFixture,
  TestBed,
  waitForAsync,
  tick,
  fakeAsync,
} from '@angular/core/testing';
import {MapComponent} from './map.component';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';
import {SurveyService} from 'app/services/survey/survey.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {Survey} from 'app/models/survey.model';
import {
  LocationOfInterest,
  GeoJsonLocationOfInterest,
  AreaOfInterest,
  GenericLocationOfInterest,
} from 'app/models/loi.model';
import {Map, List} from 'immutable';
import {Job} from 'app/models/job.model';
import {BehaviorSubject, of} from 'rxjs';
import {GeoPoint} from 'firebase/firestore';
import {GoogleMapsModule} from '@angular/google-maps';
import {urlPrefix} from 'app/ground-pin';
import {
  DrawingToolsService,
  EditMode,
} from 'app/services/drawing-tools/drawing-tools.service';
import {Point} from 'app/models/geometry/point';
import {Coordinate} from 'app/models/geometry/coordinate';

describe('MapComponent', () => {
  let component: MapComponent;
  let fixture: ComponentFixture<MapComponent>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;
  let mockLois$: BehaviorSubject<List<LocationOfInterest>>;
  let loiServiceSpy: jasmine.SpyObj<LocationOfInterestService>;
  let mockLocationOfInterestId$: BehaviorSubject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let mockDialogAfterClosed$: BehaviorSubject<string>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<unknown, unknown>>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let mockEditMode$: BehaviorSubject<EditMode>;
  let drawingToolsServiceSpy: jasmine.SpyObj<DrawingToolsService>;

  const poiId1 = 'poi001';
  const poiId2 = 'poi002';
  const poiId3 = 'poi003';
  const poiId4 = 'poi004';
  const geoJsonLoiId1 = 'geo_json_loi001';
  const aoiId1 = 'aoi001';
  const jobId1 = 'job001';
  const jobId2 = 'job002';
  const jobColor1 = 'red';
  const jobColor2 = 'green';
  const mockSurvey = new Survey(
    'survey001',
    'title1',
    'description1',
    /* jobs= */ Map({
      job001: new Job(
        jobId1,
        /* index */ -1,
        jobColor1,
        'job001 name',
        /* tasks= */ Map()
      ),
      job002: new Job(
        jobId2,
        /* index */ -1,
        jobColor2,
        'job002 name',
        /* tasks= */ Map()
      ),
    }),
    /* acl= */ Map()
  );
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
  const poi3 = new GenericLocationOfInterest(
    poiId3,
    jobId2,
    new Point(new Coordinate(78.9, 78.9)),
    Map()
  );
  const poi4 = new GenericLocationOfInterest(
    poiId4,
    jobId2,
    new Point(new Coordinate(45, 45)),
    Map()
  );
  const geoJson1 = {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: [
            [
              [0, 0],
              [10, 0],
              [10, 10],
              [0, 10],
              [0, 0],
            ],
          ],
        },
      },
    ],
  };
  const geoJsonLoi1 = new GeoJsonLocationOfInterest(
    geoJsonLoiId1,
    jobId1,
    geoJson1
  );
  const aoi1 = new AreaOfInterest(aoiId1, jobId2, [
    new GeoPoint(-10, -10),
    new GeoPoint(20, -10),
    new GeoPoint(20, 20),
    new GeoPoint(-10, 20),
    new GeoPoint(-10, -10),
  ]);

  beforeEach(waitForAsync(() => {
    surveyServiceSpy = jasmine.createSpyObj<SurveyService>('SurveyService', [
      'getActiveSurvey$',
    ]);
    surveyServiceSpy.getActiveSurvey$.and.returnValue(of<Survey>(mockSurvey));

    loiServiceSpy = jasmine.createSpyObj<LocationOfInterestService>(
      'LocationOfInterestService',
      ['getLocationsOfInterest$', 'updatePoint', 'addPoint']
    );
    mockLois$ = new BehaviorSubject<List<LocationOfInterest>>(
      List<LocationOfInterest>([poi1, poi2, geoJsonLoi1, aoi1])
    );
    loiServiceSpy.getLocationsOfInterest$.and.returnValue(mockLois$);

    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      [
        'getLocationOfInterestId$',
        'getSubmissionId$',
        'selectLocationOfInterest',
        'clearLocationOfInterestId',
      ]
    );
    mockLocationOfInterestId$ = new BehaviorSubject<string | null>(null);
    navigationServiceSpy.getLocationOfInterestId$.and.returnValue(
      mockLocationOfInterestId$
    );
    navigationServiceSpy.getSubmissionId$.and.returnValue(
      of<string | null>(null)
    );

    mockDialogAfterClosed$ = new BehaviorSubject<string>('');
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<unknown, unknown>>(
      'MatDialogRef',
      ['afterClosed']
    );
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialogSpy.open.and.returnValue(dialogRefSpy);
    dialogRefSpy.afterClosed.and.returnValue(mockDialogAfterClosed$);

    mockEditMode$ = new BehaviorSubject<EditMode>(EditMode.None);
    drawingToolsServiceSpy = jasmine.createSpyObj<DrawingToolsService>(
      'DrawingToolsService',
      ['getEditMode$', 'setEditMode', 'getSelectedJobId', 'setDisabled']
    );
    drawingToolsServiceSpy.getEditMode$.and.returnValue(mockEditMode$);
    drawingToolsServiceSpy.getSelectedJobId.and.returnValue(jobId1);

    TestBed.configureTestingModule({
      imports: [GoogleMapsModule],
      declarations: [MapComponent],
      providers: [
        {provide: MatDialog, useValue: dialogSpy},
        {provide: SurveyService, useValue: surveyServiceSpy},
        {
          provide: LocationOfInterestService,
          useValue: loiServiceSpy,
        },
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: DrawingToolsService, useValue: drawingToolsServiceSpy},
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(MapComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should render markers on map', () => {
    expect(component.markers.size).toEqual(2);
    const marker1 = component.markers.get(poiId1)!;
    assertMarkerLatLng(marker1, new google.maps.LatLng(1.23, 4.56));
    assertMarkerIcon(marker1, jobColor1, 30);
    expect(marker1.getMap()).toEqual(component.map.googleMap!);
    const marker2 = component.markers.get(poiId2)!;
    assertMarkerLatLng(marker2, new google.maps.LatLng(12.3, 45.6));
    assertMarkerIcon(marker2, jobColor2, 30);
    expect(marker2.getMap()).toEqual(component.map.googleMap!);
  });

  it('should render polygons on map - geojson loi', () => {
    const polygon = component.polygons.get(geoJsonLoiId1)!;
    assertPolygonPaths(polygon, [
      [
        new google.maps.LatLng(0, 0),
        new google.maps.LatLng(0, 10),
        new google.maps.LatLng(10, 10),
        new google.maps.LatLng(10, 0),
      ],
    ]);
    assertPolygonStyle(polygon, jobColor1, 3);
    expect(polygon.getMap()).toEqual(component.map.googleMap!);
  });

  it('should render polygons on map - polygon loi', () => {
    const polygon = component.polygons.get(aoiId1)!;
    assertPolygonPaths(polygon, [
      [
        new google.maps.LatLng(-10, -10),
        new google.maps.LatLng(20, -10),
        new google.maps.LatLng(20, 20),
        new google.maps.LatLng(-10, 20),
        new google.maps.LatLng(-10, -10),
      ],
    ]);
    assertPolygonStyle(polygon, jobColor2, 3);
    expect(polygon.getMap()).toEqual(component.map.googleMap!);
  });

  it('should update lois when backend lois update', fakeAsync(() => {
    mockLois$.next(List<LocationOfInterest>([poi1, poi3, geoJsonLoi1]));
    tick();

    expect(component.markers.size).toEqual(2);
    const marker1 = component.markers.get(poiId1)!;
    assertMarkerLatLng(marker1, new google.maps.LatLng(1.23, 4.56));
    assertMarkerIcon(marker1, jobColor1, 30);
    expect(marker1.getMap()).toEqual(component.map.googleMap!);
    const marker2 = component.markers.get(poiId3)!;
    assertMarkerLatLng(marker2, new google.maps.LatLng(78.9, 78.9));
    assertMarkerIcon(marker2, jobColor2, 30);
    expect(marker2.getMap()).toEqual(component.map.googleMap!);
    expect(component.polygons.size).toEqual(1);
    const polygon = component.polygons.get(geoJsonLoiId1)!;
    assertPolygonPaths(polygon, [
      [
        new google.maps.LatLng(0, 0),
        new google.maps.LatLng(0, 10),
        new google.maps.LatLng(10, 10),
        new google.maps.LatLng(10, 0),
      ],
    ]);
    assertPolygonStyle(polygon, jobColor1, 3);
    expect(polygon.getMap()).toEqual(component.map.googleMap!);
  }));

  it('should select loi when marker is clicked', () => {
    const marker1 = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker1, 'click');

    expect(
      navigationServiceSpy.selectLocationOfInterest
    ).toHaveBeenCalledOnceWith(poiId1);
  });

  it('should enlarge the marker when loi is selected', fakeAsync(() => {
    mockLocationOfInterestId$.next(poiId1);
    tick();

    const marker1 = component.markers.get(poiId1)!;
    assertMarkerIcon(marker1, jobColor1, 50);
  }));

  it('should select loi when polygon is clicked', () => {
    const polygon = component.polygons.get(aoiId1)!;
    google.maps.event.trigger(polygon, 'click', {
      latLng: new google.maps.LatLng(-9, -9),
    });

    expect(
      navigationServiceSpy.selectLocationOfInterest
    ).toHaveBeenCalledOnceWith(aoiId1);
  });

  it('should enlarge the stroke weight of the polygon when loi is selected', fakeAsync(() => {
    mockLocationOfInterestId$.next(geoJsonLoiId1);
    tick();

    const polygon = component.polygons.get(geoJsonLoiId1)!;
    assertPolygonStyle(polygon, jobColor1, 6);
  }));

  it('should clear selected loi when map is clicked', fakeAsync(() => {
    mockLocationOfInterestId$.next(poiId1);
    tick();

    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(
      navigationServiceSpy.clearLocationOfInterestId
    ).toHaveBeenCalledTimes(1);
  }));

  it('markers are not draggable by default', () => {
    const marker1 = component.markers.get(poiId1)!;
    expect(marker1.getDraggable()).toBeFalse();
    const marker2 = component.markers.get(poiId2)!;
    expect(marker2.getDraggable()).toBeFalse();
  });

  it('should set marker draggable when loi is selected', fakeAsync(() => {
    mockLocationOfInterestId$.next(poiId1);
    tick();

    const marker1 = component.markers.get(poiId1)!;
    expect(marker1.getDraggable()).toBeTrue();
  }));

  it('reposition dialog is not displayed by default', () => {
    const repositionDialog = fixture.nativeElement.querySelector(
      '#reposition-confirm-dialog'
    ) as Element;
    expect(repositionDialog).toBeNull();
  });

  it('should pop up reposition dialog when marker dragged', fakeAsync(() => {
    mockLocationOfInterestId$.next(poiId1);
    tick();

    const marker = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });

    const repositionDialog = fixture.nativeElement.querySelector(
      '#reposition-confirm-dialog'
    ) as Element;
    expect(repositionDialog).toBeDefined();
  }));

  it('should disable drawing tools when marker dragged', fakeAsync(() => {
    mockLocationOfInterestId$.next(poiId1);
    tick();

    const marker = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });

    expect(drawingToolsServiceSpy.setDisabled).toHaveBeenCalledOnceWith(true);
  }));

  it('should disable marker click while repositioning a marker', fakeAsync(() => {
    mockLocationOfInterestId$.next(poiId1);
    tick();

    const marker1 = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker1, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });
    const marker2 = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker2, 'click');

    expect(navigationServiceSpy.selectLocationOfInterest).toHaveBeenCalledTimes(
      0
    );
  }));

  it('should disable polygon click while repositioning a marker', fakeAsync(() => {
    mockLocationOfInterestId$.next(poiId1);
    tick();

    const marker = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });
    const polygon = component.polygons.get(geoJsonLoiId1)!;
    google.maps.event.trigger(polygon, 'click');

    expect(navigationServiceSpy.selectLocationOfInterest).toHaveBeenCalledTimes(
      0
    );
  }));

  it('should disable map click while repositioning a marker', fakeAsync(() => {
    mockLocationOfInterestId$.next(poiId1);
    tick();

    const marker = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });
    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(
      navigationServiceSpy.clearLocationOfInterestId
    ).toHaveBeenCalledTimes(0);
  }));

  it('should reposition marker when confirmed in reposition dialog', fakeAsync(() => {
    mockLocationOfInterestId$.next(poiId1);
    tick();

    const marker = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });
    google.maps.event.trigger(marker, 'dragend', {
      latLng: new google.maps.LatLng(2.23, 5.56),
    });
    const confirmRepositionButton = fixture.nativeElement.querySelector(
      '#confirm-reposition'
    ) as HTMLElement;
    confirmRepositionButton.click();

    assertMarkerLatLng(marker, new google.maps.LatLng(2.23, 5.56));
    expect(loiServiceSpy.updatePoint).toHaveBeenCalledOnceWith(
      new GenericLocationOfInterest(
        poi1.id,
        poi1.jobId,
        new Point(new Coordinate(2.23, 5.56)),
        Map()
      )
    );
  }));

  it('should enable drawing tools when confirmed in reposition dialog', fakeAsync(() => {
    mockLocationOfInterestId$.next(poiId1);
    tick();

    const marker = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });
    google.maps.event.trigger(marker, 'dragend', {
      latLng: new google.maps.LatLng(2.23, 5.56),
    });
    const confirmRepositionButton = fixture.nativeElement.querySelector(
      '#confirm-reposition'
    ) as HTMLElement;
    confirmRepositionButton.click();

    expect(drawingToolsServiceSpy.setDisabled).toHaveBeenCalledTimes(2);
    expect(drawingToolsServiceSpy.setDisabled).toHaveBeenCalledWith(true);
    expect(drawingToolsServiceSpy.setDisabled).toHaveBeenCalledWith(false);
  }));

  it('should move marker back when canceled in reposition dialog', fakeAsync(() => {
    mockLocationOfInterestId$.next(poiId1);
    tick();

    const marker = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });
    google.maps.event.trigger(marker, 'dragend', {
      latLng: new google.maps.LatLng(2.23, 5.56),
    });
    const cancelRepositionButton = fixture.nativeElement.querySelector(
      '#cancel-reposition'
    ) as HTMLElement;
    cancelRepositionButton.click();

    assertMarkerLatLng(marker, new google.maps.LatLng(1.23, 4.56));
    expect(loiServiceSpy.updatePoint).toHaveBeenCalledTimes(0);
  }));

  it('should pop up dialog when overlapping polygons are clicked', fakeAsync(() => {
    const polygon = component.polygons.get(geoJsonLoiId1)!;
    google.maps.event.trigger(polygon, 'click', {
      latLng: new google.maps.LatLng(0, 0),
    });
    mockDialogAfterClosed$.next(aoiId1);
    tick();

    expect(dialogSpy.open).toHaveBeenCalledTimes(1);
    expect(dialogRefSpy.afterClosed).toHaveBeenCalledTimes(1);
    expect(
      navigationServiceSpy.selectLocationOfInterest
    ).toHaveBeenCalledOnceWith(aoiId1);
  }));

  it('should add marker when map clicked and edit mode is "AddPoint"', fakeAsync(() => {
    mockEditMode$.next(EditMode.AddPoint);
    drawingToolsServiceSpy.getSelectedJobId.and.returnValue(jobId2);
    loiServiceSpy.addPoint.and.returnValue(
      new Promise(resolve => resolve(poi4))
    );
    tick();

    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(loiServiceSpy.addPoint).toHaveBeenCalledOnceWith(45, 45, jobId2);
  }));

  it('should not add marker when job id is undefined', fakeAsync(() => {
    mockEditMode$.next(EditMode.AddPoint);
    drawingToolsServiceSpy.getSelectedJobId.and.returnValue(undefined);
    tick();

    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(loiServiceSpy.addPoint).toHaveBeenCalledTimes(0);
  }));

  it('should do nothing when map clicked and edit mode is "AddPolygon"', fakeAsync(() => {
    mockEditMode$.next(EditMode.AddPolygon);
    tick();

    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(loiServiceSpy.addPoint).toHaveBeenCalledTimes(0);
  }));

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
    const icon = marker.getIcon() as google.maps.Icon;
    expect(atob(icon.url.slice(urlPrefix.length))).toContain(iconColor);
    expect(icon.scaledSize?.height).toEqual(iconSize);
    expect(icon.scaledSize?.width).toEqual(iconSize);
  }

  function assertPolygonPaths(
    polygon: google.maps.Polygon,
    paths: google.maps.LatLng[][]
  ): void {
    const polygonPaths = polygon.getPaths();
    expect(paths.length).toEqual(polygonPaths.getLength());
    paths.forEach((path, index) => {
      assertPath(path, polygonPaths.getAt(index));
    });
  }

  function assertPath(
    path: google.maps.LatLng[],
    polygonPath: google.maps.MVCArray<google.maps.LatLng>
  ): void {
    expect(path.length).toEqual(polygonPath.getLength());
    path.forEach((latLng, index) => {
      expect(latLng.lat()).toEqual(polygonPath.getAt(index).lat());
      expect(latLng.lng()).toEqual(polygonPath.getAt(index).lng());
    });
  }

  function assertPolygonStyle(
    polygon: google.maps.Polygon,
    color: string,
    strokeWeight: number
  ) {
    expect(polygon.get('strokeColor')).toEqual(color);
    expect(polygon.get('strokeWeight')).toEqual(strokeWeight);
  }
});
