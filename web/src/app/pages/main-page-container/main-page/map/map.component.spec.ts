/**
 * Copyright 2021 The Ground Authors.
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
  fakeAsync,
  tick,
  waitForAsync,
} from '@angular/core/testing';
import {AngularFireAuth} from '@angular/fire/compat/auth';
import {AngularFirestore} from '@angular/fire/compat/firestore';
import {GoogleMapsModule} from '@angular/google-maps';
import {List, Map} from 'immutable';
import {BehaviorSubject, of} from 'rxjs';

import {Coordinate} from 'app/models/geometry/coordinate';
import {MultiPolygon} from 'app/models/geometry/multi-polygon';
import {Point} from 'app/models/geometry/point';
import {Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Submission} from 'app/models/submission/submission.model';
import {DataSharingType, Survey} from 'app/models/survey.model';
import {AuthService} from 'app/services/auth/auth.service';
import {
  DrawingToolsService,
  EditMode,
} from 'app/services/drawing-tools/drawing-tools.service';
import {GroundPinService} from 'app/services/ground-pin/ground-pin.service';
import {LoadingState} from 'app/services/loading-state.model';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SubmissionService} from 'app/services/submission/submission.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {polygonShellCoordsToPolygon} from 'testing/helpers';

import {MapComponent} from './map.component';

describe('MapComponent', () => {
  let component: MapComponent;
  let fixture: ComponentFixture<MapComponent>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;
  let mockLois$: BehaviorSubject<List<LocationOfInterest>>;
  let loiServiceSpy: jasmine.SpyObj<LocationOfInterestService>;
  let mockLocationOfInterestId$: BehaviorSubject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let submissionServiceSpy: jasmine.SpyObj<SubmissionService>;
  let mockEditMode$: BehaviorSubject<EditMode>;
  let drawingToolsServiceSpy: jasmine.SpyObj<DrawingToolsService>;

  const poiId1 = 'poi001';
  const poiId2 = 'poi002';
  const poiId3 = 'poi003';
  const poiId4 = 'poi004';
  const polygonLoiId1 = 'polygon_loi001';
  const multipolygonLoiId1 = 'multipolygon_loi001';
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
    /* acl= */ Map(),
    {type: DataSharingType.PRIVATE}
  );
  const poi1 = new LocationOfInterest(
    poiId1,
    jobId1,
    new Point(new Coordinate(1.23, 4.56)),
    Map()
  );
  const poi2 = new LocationOfInterest(
    poiId2,
    jobId2,
    new Point(new Coordinate(12.3, 45.6)),
    Map()
  );
  const poi3 = new LocationOfInterest(
    poiId3,
    jobId2,
    new Point(new Coordinate(78.9, 78.9)),
    Map()
  );
  const poi4 = new LocationOfInterest(
    poiId4,
    jobId2,
    new Point(new Coordinate(45, 45)),
    Map()
  );
  const polygon1ShellCoordinates = [
    [0, 0],
    [10, 0],
    [10, 10],
    [0, 10],
    [0, 0],
  ];
  const polygon1ShellCoordinatesModified = [
    [0, 1],
    [10, 0],
    [10, 10],
    [0, 10],
    [0, 0],
  ];
  const polygon2ShellCoordinates = [
    [-10, -10],
    [-10, 20],
    [20, 20],
    [20, -10],
    [-10, -10],
  ];
  const polygonLoi1 = new LocationOfInterest(
    polygonLoiId1,
    jobId1,
    polygonShellCoordsToPolygon(polygon1ShellCoordinates),
    Map()
  );
  const multipolygonLoi1 = new LocationOfInterest(
    multipolygonLoiId1,
    jobId1,
    new MultiPolygon(
      List([
        polygonShellCoordsToPolygon(polygon1ShellCoordinates),
        polygonShellCoordsToPolygon(polygon2ShellCoordinates),
      ])
    ),
    Map()
  );

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
      List<LocationOfInterest>([poi1, poi2, polygonLoi1])
    );
    loiServiceSpy.getLocationsOfInterest$.and.returnValue(mockLois$);

    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      [
        'getLocationOfInterestId$',
        'getSubmissionId$',
        'selectLocationOfInterest',
        'clearLocationOfInterestId',
        'showSubmissionDetailWithHighlightedTask',
      ]
    );
    mockLocationOfInterestId$ = new BehaviorSubject<string | null>(null);
    navigationServiceSpy.getLocationOfInterestId$.and.returnValue(
      mockLocationOfInterestId$
    );
    navigationServiceSpy.getSubmissionId$.and.returnValue(
      of<string | null>(null)
    );

    submissionServiceSpy = jasmine.createSpyObj<SubmissionService>(
      'SubmissionService',
      ['getSelectedSubmission$']
    );
    submissionServiceSpy.getSelectedSubmission$.and.returnValue(
      new BehaviorSubject<Submission | LoadingState>(LoadingState.LOADING)
    );

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
        {provide: SurveyService, useValue: surveyServiceSpy},
        {
          provide: LocationOfInterestService,
          useValue: loiServiceSpy,
        },
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: SubmissionService, useValue: submissionServiceSpy},
        {provide: DrawingToolsService, useValue: drawingToolsServiceSpy},
        {provide: AuthService, useValue: {}},
        {
          provide: AngularFireAuth,
          useValue: {
            authState: of({
              displayName: null,
              isAnonymous: true,
              uid: '',
            }),
          },
        },
        {provide: AngularFirestore, useValue: {}},
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(MapComponent);
    component = fixture.componentInstance;
    component.shouldEnableDrawingTools = true;
    fixture.detectChanges();
  });

  it('should fit the map when survey changed', fakeAsync(() => {
    spyOn(component.map, 'fitBounds');
    component.lastFitSurveyId = '0';
    component.ngAfterViewInit();

    expect(component.map.fitBounds).toHaveBeenCalledOnceWith(
      new google.maps.LatLngBounds(
        new google.maps.LatLng(0, 0),
        new google.maps.LatLng(45.6, 12.3)
      )
    );
  }));

  it('should render markers on map', () => {
    expect(component.markers.size).toEqual(2);
    const marker1 = component.markers.get(poiId1)!;
    assertMarkerLatLng(marker1, new google.maps.LatLng(4.56, 1.23));
    assertMarkerIcon(marker1, jobColor1, 30);
    expect(marker1.getMap()).toEqual(component.map.googleMap!);
    const marker2 = component.markers.get(poiId2)!;
    assertMarkerLatLng(marker2, new google.maps.LatLng(45.6, 12.3));
    assertMarkerIcon(marker2, jobColor2, 30);
    expect(marker2.getMap()).toEqual(component.map.googleMap!);
  });

  it('should render polygons on map - polygon loi', () => {
    const [polygon] = component.polygons.get(polygonLoiId1)!;
    assertPolygonPaths(polygon, [
      [
        new google.maps.LatLng(0, 0),
        new google.maps.LatLng(0, 10),
        new google.maps.LatLng(10, 10),
        new google.maps.LatLng(10, 0),
        new google.maps.LatLng(0, 0),
      ],
    ]);
    assertPolygonStyle(polygon, jobColor1, 3);
    expect(polygon.getMap()).toEqual(component.map.googleMap!);
  });

  it('should render polygons on map - multipolygon loi', fakeAsync(() => {
    mockLois$.next(List<LocationOfInterest>([multipolygonLoi1]));
    tick();

    const [polygon1, polygon2] = component.polygons.get(multipolygonLoiId1)!;
    assertPolygonPaths(polygon1, [
      [
        new google.maps.LatLng(0, 0),
        new google.maps.LatLng(0, 10),
        new google.maps.LatLng(10, 10),
        new google.maps.LatLng(10, 0),
        new google.maps.LatLng(0, 0),
      ],
    ]);
    assertPolygonPaths(polygon2, [
      [
        new google.maps.LatLng(-10, -10),
        new google.maps.LatLng(20, -10),
        new google.maps.LatLng(20, 20),
        new google.maps.LatLng(-10, 20),
        new google.maps.LatLng(-10, -10),
      ],
    ]);
    assertPolygonStyle(polygon1, jobColor1, 3);
    assertPolygonStyle(polygon2, jobColor1, 3);
    expect(polygon1.getMap()).toEqual(component.map.googleMap!);
    expect(polygon2.getMap()).toEqual(component.map.googleMap!);
  }));

  describe('when selected job id is given', () => {
    beforeEach(() => {
      fixture = TestBed.createComponent(MapComponent);
      component = fixture.componentInstance;
      component.selectedJobId = jobId1;
      fixture.detectChanges();
    });

    it('should render only lois under the job', fakeAsync(() => {
      component.ngOnChanges();

      expect(component.markers.size).toEqual(1);
      const marker1 = component.markers.get(poiId1)!;
      assertMarkerLatLng(marker1, new google.maps.LatLng(4.56, 1.23));
      assertMarkerIcon(marker1, jobColor1, 30);
      expect(marker1.getMap()).toEqual(component.map.googleMap!);
    }));

    it('should fit the map when survey changed', fakeAsync(() => {
      spyOn(component.map, 'fitBounds');
      component.selectedJobId = jobId2;
      component.ngOnChanges();

      expect(component.map.fitBounds).toHaveBeenCalledOnceWith(
        new google.maps.LatLngBounds(new google.maps.LatLng(45.6, 12.3))
      );
    }));
  });

  describe('when backend LOIs update', () => {
    it('should update lois when backend lois update', fakeAsync(() => {
      const poi2Modified = new LocationOfInterest(
        poiId2,
        jobId2,
        new Point(new Coordinate(12.3, 45.7)),
        Map()
      );
      const polygonLoi1Modified = new LocationOfInterest(
        polygonLoiId1,
        jobId1,
        polygonShellCoordsToPolygon(polygon1ShellCoordinatesModified),
        Map()
      );
      // poi1 deleted, poi2 modified, poi3 added & polygonLoi1 modified
      mockLois$.next(
        List<LocationOfInterest>([poi2Modified, poi3, polygonLoi1Modified])
      );
      tick();

      expect(component.markers.size).toEqual(2);
      const marker1 = component.markers.get(poiId2)!;
      assertMarkerLatLng(marker1, new google.maps.LatLng(45.7, 12.3));
      assertMarkerIcon(marker1, jobColor2, 30);
      expect(marker1.getMap()).toEqual(component.map.googleMap!);
      const marker2 = component.markers.get(poiId3)!;
      assertMarkerLatLng(marker2, new google.maps.LatLng(78.9, 78.9));
      assertMarkerIcon(marker2, jobColor2, 30);
      expect(marker2.getMap()).toEqual(component.map.googleMap!);
      expect(component.polygons.size).toEqual(1);
      const [polygon] = component.polygons.get(polygonLoiId1)!;
      assertPolygonPaths(polygon, [
        [
          new google.maps.LatLng(1, 0),
          new google.maps.LatLng(0, 10),
          new google.maps.LatLng(10, 10),
          new google.maps.LatLng(10, 0),
          new google.maps.LatLng(0, 0),
        ],
      ]);
      assertPolygonStyle(polygon, jobColor1, 3);
      expect(polygon.getMap()).toEqual(component.map.googleMap!);
    }));
  });

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
    const [polygon] = component.polygons.get(polygonLoiId1)!;
    google.maps.event.trigger(polygon, 'click', {
      latLng: new google.maps.LatLng(3, 3),
    });

    expect(
      navigationServiceSpy.selectLocationOfInterest
    ).toHaveBeenCalledOnceWith(polygonLoiId1);
  });

  it('should enlarge the stroke weight of the polygon when loi is selected', fakeAsync(() => {
    mockLocationOfInterestId$.next(polygonLoiId1);
    tick();

    const [polygon] = component.polygons.get(polygonLoiId1)!;
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

  it('should not set marker draggable when loi is selected and drawing tools turned off', fakeAsync(() => {
    component.shouldEnableDrawingTools = false;
    mockLocationOfInterestId$.next(poiId1);
    tick();

    const marker1 = component.markers.get(poiId1)!;
    expect(marker1.getDraggable()).toBeFalse();
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
    const [polygon] = component.polygons.get(polygonLoiId1)!;
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
      new LocationOfInterest(
        poi1.id,
        poi1.jobId,
        new Point(new Coordinate(5.56, 2.23)),
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
    mockLois$.next(List<LocationOfInterest>([polygonLoi1, multipolygonLoi1]));
    tick();

    const [polygon] = component.polygons.get(polygonLoiId1)!;
    google.maps.event.trigger(polygon, 'click', {
      latLng: new google.maps.LatLng(2, 2),
    });
    tick();

    expect(
      navigationServiceSpy.selectLocationOfInterest
    ).toHaveBeenCalledOnceWith(polygonLoiId1);
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
    expect(atob(icon.url.slice(GroundPinService.urlPrefix.length))).toContain(
      iconColor
    );
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
