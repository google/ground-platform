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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Auth } from '@angular/fire/auth';
import { Firestore } from '@angular/fire/firestore';
import { GoogleMapsModule } from '@angular/google-maps';
import { List, Map, OrderedMap } from 'immutable';
import { BehaviorSubject, of } from 'rxjs';

import { Coordinate } from 'app/models/geometry/coordinate';
import { MultiPolygon } from 'app/models/geometry/multi-polygon';
import { Point } from 'app/models/geometry/point';
import { Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { Submission } from 'app/models/submission/submission.model';
import { Result } from 'app/models/submission/result.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { AuthService } from 'app/services/auth/auth.service';
import { Task, TaskType } from 'app/models/task/task.model';
import { AuditInfo } from 'app/models/audit-info.model';
import { User } from 'app/models/user.model';
import {
  DrawingToolsService,
  EditMode,
} from 'app/services/drawing-tools/drawing-tools.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SubmissionService } from 'app/services/submission/submission.service';
import { polygonShellCoordsToPolygon } from 'testing/helpers';

import { MapComponent } from './map.component';

describe('MapComponent', () => {
  let component: MapComponent;
  let fixture: ComponentFixture<MapComponent>;
  let mockLois$: BehaviorSubject<List<LocationOfInterest>>;
  let loiServiceSpy: jasmine.SpyObj<LocationOfInterestService>;
  let mockLocationOfInterestId$: BehaviorSubject<string | null>;
  let mockTaskId$: BehaviorSubject<string | null>;
  let mockSubmissionId$: BehaviorSubject<string | null>;
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
  const jobColor1 = '#aa0000';
  const jobColor2 = '#00aa00';
  const job1 = new Job(
    jobId1,
    /* index */ -1,
    jobColor1,
    'job001 name',
    /* tasks= */ Map()
  );
  const job2 = new Job(
    jobId2,
    /* index */ -1,
    jobColor2,
    'job002 name',
    /* tasks= */ Map()
  );
  const surveyId = 'survey001';
  const mockSurvey = new Survey(
    surveyId,
    'title1',
    'description1',
    /* jobs= */ Map({
      job001: job1,
      job002: job2,
    }),
    /* acl= */ Map(),
    /* ownerId= */ '',
    { type: DataSharingType.PRIVATE }
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

  beforeEach(async () => {
    loiServiceSpy = jasmine.createSpyObj<LocationOfInterestService>(
      'LocationOfInterestService',
      ['updatePoint', 'addPoint']
    );

    mockLois$ = new BehaviorSubject<List<LocationOfInterest>>(
      List<LocationOfInterest>([poi1, poi2, polygonLoi1])
    );

    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      [
        'getLocationOfInterestId$',
        'getTaskId$',
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
    mockTaskId$ = new BehaviorSubject<string | null>(null);
    navigationServiceSpy.getTaskId$.and.returnValue(mockTaskId$);
    mockSubmissionId$ = new BehaviorSubject<string | null>(null);
    navigationServiceSpy.getSubmissionId$.and.returnValue(mockSubmissionId$);

    submissionServiceSpy = jasmine.createSpyObj<SubmissionService>(
      'SubmissionService',
      ['getSubmission$']
    );
    submissionServiceSpy.getSubmission$.and.returnValue(
      of(null as unknown as Submission)
    );

    mockEditMode$ = new BehaviorSubject<EditMode>(EditMode.None);
    drawingToolsServiceSpy = jasmine.createSpyObj<DrawingToolsService>(
      'DrawingToolsService',
      ['getEditMode$', 'setEditMode', 'getSelectedJobId', 'setDisabled']
    );
    drawingToolsServiceSpy.getEditMode$.and.returnValue(mockEditMode$);
    drawingToolsServiceSpy.getSelectedJobId.and.returnValue(jobId1);

    await TestBed.configureTestingModule({
      imports: [GoogleMapsModule],
      declarations: [MapComponent],
      providers: [
        {
          provide: LocationOfInterestService,
          useValue: loiServiceSpy,
        },
        { provide: NavigationService, useValue: navigationServiceSpy },
        { provide: SubmissionService, useValue: submissionServiceSpy },
        { provide: DrawingToolsService, useValue: drawingToolsServiceSpy },
        { provide: AuthService, useValue: {} },
        {
          provide: Auth,
          useValue: {
            currentUser: {
              displayName: null,
              isAnonymous: true,
              uid: '',
            },
          },
        },
        { provide: Firestore, useValue: {} },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(MapComponent);
    fixture.componentRef.setInput('activeSurvey', mockSurvey);
    fixture.componentRef.setInput('lois', mockLois$.getValue());
    component = fixture.componentInstance;
    component.shouldEnableDrawingTools = true;
    fixture.detectChanges();
  });

  it('should fit the map when survey changed 2', async () => {
    spyOn(component.map, 'fitBounds');
    component.lastFitSurveyId = '0';
    component.ngOnChanges();
    await fixture.whenStable();

    expect(component.map.fitBounds).toHaveBeenCalledOnceWith(
      new google.maps.LatLngBounds(
        new google.maps.LatLng(0, 0),
        new google.maps.LatLng(45.6, 12.3)
      )
    );
  });

  it('should render markers on map', () => {
    expect(component.markers.size).toEqual(2);
    const marker1 = component.markers.get(poiId1)!;
    assertMarkerLatLng(marker1, new google.maps.LatLng(4.56, 1.23));
    assertMarkerColor(marker1, jobColor1);
    expect(marker1.map).toEqual(component.map.googleMap!);
    const marker2 = component.markers.get(poiId2)!;
    assertMarkerLatLng(marker2, new google.maps.LatLng(45.6, 12.3));
    assertMarkerColor(marker2, jobColor2);
    expect(marker2.map).toEqual(component.map.googleMap!);
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

  it('should render polygons on map - multipolygon loi', async () => {
    fixture.componentRef.setInput(
      'lois',
      List<LocationOfInterest>([multipolygonLoi1])
    );
    fixture.detectChanges();
    await fixture.whenStable();

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
  });

  describe('when selected job id is given', () => {
    beforeEach(() => {
      fixture = TestBed.createComponent(MapComponent);
      fixture.componentRef.setInput('activeSurvey', mockSurvey);
      fixture.componentRef.setInput('lois', mockLois$.getValue());
      component = fixture.componentInstance;
      component.selectedJob = job1;
      fixture.detectChanges();
    });

    it('should render only lois under the job', async () => {
      fixture.detectChanges();
      await fixture.whenStable();
      component.ngOnChanges();

      expect(component.markers.size).toEqual(1);
      const marker1 = component.markers.get(poiId1)!;
      assertMarkerLatLng(marker1, new google.maps.LatLng(4.56, 1.23));
      assertMarkerColor(marker1, jobColor1);
      expect(marker1.map).toEqual(component.map.googleMap!);
    });

    it('should fit the map when survey changed', async () => {
      fixture.detectChanges();
      await fixture.whenStable();
      spyOn(component.map, 'fitBounds');
      component.selectedJob = job2;
      component.ngOnChanges();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(component.map.fitBounds).toHaveBeenCalledOnceWith(
        new google.maps.LatLngBounds(new google.maps.LatLng(45.6, 12.3))
      );
    });
  });

  describe('when backend LOIs update', () => {
    it('should update lois when backend lois update', async () => {
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
      fixture.componentRef.setInput(
        'lois',
        List<LocationOfInterest>([poi2Modified, poi3, polygonLoi1Modified])
      );
      fixture.detectChanges();
      await fixture.whenStable();

      expect(component.markers.size).toEqual(2);
      const marker1 = component.markers.get(poiId2)!;
      assertMarkerLatLng(marker1, new google.maps.LatLng(45.7, 12.3));
      assertMarkerColor(marker1, jobColor2);
      expect(marker1.map).toEqual(component.map.googleMap!);
      const marker2 = component.markers.get(poiId3)!;
      assertMarkerLatLng(marker2, new google.maps.LatLng(78.9, 78.9));
      assertMarkerColor(marker2, jobColor2);
      expect(marker2.map).toEqual(component.map.googleMap!);
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
    });
  });

  it('should select loi when marker is clicked', () => {
    const marker1 = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker1, 'click');

    expect(
      navigationServiceSpy.selectLocationOfInterest
    ).toHaveBeenCalledOnceWith(surveyId, poiId1);
  });

  it('should select loi when polygon is clicked', () => {
    const [polygon] = component.polygons.get(polygonLoiId1)!;
    google.maps.event.trigger(polygon, 'click', {
      latLng: new google.maps.LatLng(3, 3),
    });

    expect(
      navigationServiceSpy.selectLocationOfInterest
    ).toHaveBeenCalledOnceWith(surveyId, polygonLoiId1);
  });

  it('should enlarge the stroke weight of the polygon when loi is selected', async () => {
    mockLocationOfInterestId$.next(polygonLoiId1);
    fixture.detectChanges();
    await fixture.whenStable();

    const [polygon] = component.polygons.get(polygonLoiId1)!;
    assertPolygonStyle(polygon, jobColor1, 6);
  });

  it('should clear selected loi when map is clicked', async () => {
    mockLocationOfInterestId$.next(poiId1);
    fixture.detectChanges();
    await fixture.whenStable();

    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(
      navigationServiceSpy.clearLocationOfInterestId
    ).toHaveBeenCalledTimes(1);
  });

  it('markers are not draggable by default', () => {
    const marker1 = component.markers.get(poiId1)!;
    expect(marker1.gmpDraggable).toBeFalse();
    const marker2 = component.markers.get(poiId2)!;
    expect(marker2.gmpDraggable).toBeFalse();
  });

  it('should set marker draggable when loi is selected', async () => {
    mockLocationOfInterestId$.next(poiId1);
    fixture.detectChanges();
    await fixture.whenStable();

    const marker1 = component.markers.get(poiId1)!;
    expect(marker1.gmpDraggable).toBeTrue();
  });

  it('should not set marker draggable when loi is selected and drawing tools turned off', async () => {
    component.shouldEnableDrawingTools = false;
    mockLocationOfInterestId$.next(poiId1);
    fixture.detectChanges();
    await fixture.whenStable();

    const marker1 = component.markers.get(poiId1)!;
    expect(marker1.gmpDraggable).toBeFalse();
  });

  it('reposition dialog is not displayed by default', () => {
    const repositionDialog = fixture.nativeElement.querySelector(
      '#reposition-confirm-dialog'
    ) as Element;
    expect(repositionDialog).toBeNull();
  });

  it('should pop up reposition dialog when marker dragged', async () => {
    mockLocationOfInterestId$.next(poiId1);
    fixture.detectChanges();
    await fixture.whenStable();

    const marker = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });

    const repositionDialog = fixture.nativeElement.querySelector(
      '#reposition-confirm-dialog'
    ) as Element;
    expect(repositionDialog).toBeDefined();
  });

  it('should disable drawing tools when marker dragged', async () => {
    mockLocationOfInterestId$.next(poiId1);
    fixture.detectChanges();
    await fixture.whenStable();

    const marker = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });

    expect(drawingToolsServiceSpy.setDisabled).toHaveBeenCalledOnceWith(true);
  });

  it('should disable marker click while repositioning a marker', async () => {
    mockLocationOfInterestId$.next(poiId1);
    fixture.detectChanges();
    await fixture.whenStable();

    const marker1 = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker1, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });
    const marker2 = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker2, 'click');

    expect(navigationServiceSpy.selectLocationOfInterest).toHaveBeenCalledTimes(
      0
    );
  });

  it('should disable polygon click while repositioning a marker', async () => {
    mockLocationOfInterestId$.next(poiId1);
    fixture.detectChanges();
    await fixture.whenStable();

    const marker = component.markers.get(poiId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });
    const [polygon] = component.polygons.get(polygonLoiId1)!;
    google.maps.event.trigger(polygon, 'click');

    expect(navigationServiceSpy.selectLocationOfInterest).toHaveBeenCalledTimes(
      0
    );
  });

  it('should disable map click while repositioning a marker', async () => {
    mockLocationOfInterestId$.next(poiId1);
    fixture.detectChanges();
    await fixture.whenStable();

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
  });

  it('should reposition marker when confirmed in reposition dialog', async () => {
    mockLocationOfInterestId$.next(poiId1);
    fixture.detectChanges();
    await fixture.whenStable();

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
    fixture.detectChanges(); // Update view after click

    assertMarkerLatLng(marker, new google.maps.LatLng(2.23, 5.56));
    expect(loiServiceSpy.updatePoint).toHaveBeenCalledOnceWith(
      new LocationOfInterest(
        poi1.id,
        poi1.jobId,
        new Point(new Coordinate(5.56, 2.23)),
        Map()
      )
    );
  });

  it('should enable drawing tools when confirmed in reposition dialog', async () => {
    mockLocationOfInterestId$.next(poiId1);
    fixture.detectChanges();
    await fixture.whenStable();

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
    fixture.detectChanges();

    expect(drawingToolsServiceSpy.setDisabled).toHaveBeenCalledTimes(2);
    expect(drawingToolsServiceSpy.setDisabled).toHaveBeenCalledWith(true);
    expect(drawingToolsServiceSpy.setDisabled).toHaveBeenCalledWith(false);
  });

  it('should move marker back when canceled in reposition dialog', async () => {
    mockLocationOfInterestId$.next(poiId1);
    fixture.detectChanges();
    await fixture.whenStable();

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
    fixture.detectChanges();

    assertMarkerLatLng(marker, new google.maps.LatLng(1.23, 4.56));
    expect(loiServiceSpy.updatePoint).toHaveBeenCalledTimes(0);
  });

  it('should pop up dialog when overlapping polygons are clicked', async () => {
    fixture.componentRef.setInput(
      'lois',
      List<LocationOfInterest>([polygonLoi1, multipolygonLoi1])
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const [polygon] = component.polygons.get(polygonLoiId1)!;
    google.maps.event.trigger(polygon, 'click', {
      latLng: new google.maps.LatLng(2, 2),
    });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(
      navigationServiceSpy.selectLocationOfInterest
    ).toHaveBeenCalledOnceWith(surveyId, polygonLoiId1);
  });

  it('should add marker when map clicked and edit mode is "AddPoint"', async () => {
    mockEditMode$.next(EditMode.AddPoint);
    drawingToolsServiceSpy.getSelectedJobId.and.returnValue(jobId2);
    loiServiceSpy.addPoint.and.returnValue(
      new Promise(resolve => resolve(poi4))
    );
    fixture.detectChanges();
    await fixture.whenStable();

    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(loiServiceSpy.addPoint).toHaveBeenCalledOnceWith(45, 45, jobId2);
  });

  it('should not add marker when job id is undefined', async () => {
    mockEditMode$.next(EditMode.AddPoint);
    drawingToolsServiceSpy.getSelectedJobId.and.returnValue(undefined);
    fixture.detectChanges();
    await fixture.whenStable();

    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(loiServiceSpy.addPoint).toHaveBeenCalledTimes(0);
  });

  it('should do nothing when map clicked and edit mode is "AddPolygon"', async () => {
    mockEditMode$.next(EditMode.AddPolygon);
    fixture.detectChanges();
    await fixture.whenStable();

    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(loiServiceSpy.addPoint).toHaveBeenCalledTimes(0);
  });

  function assertMarkerLatLng(
    marker: google.maps.marker.AdvancedMarkerElement,
    latLng: google.maps.LatLng
  ): void {
    expect((marker.position as google.maps.LatLng).lat()).toEqual(latLng.lat());
    expect((marker.position as google.maps.LatLng).lng()).toEqual(latLng.lng());
  }

  function assertMarkerColor(
    marker: google.maps.marker.AdvancedMarkerElement,
    color: string
  ): void {
    const image = marker.element.querySelector('img')!;
    const svg = atob(image.src.split(',')[1]);
    expect(svg).toContain(`fill="${color}"`);
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

  describe('clustering', () => {
    const world = { west: -180, south: -85, east: 180, north: 85 };

    /** Moves the (mock) map to the given zoom level and viewport. */
    function showMap(zoom: number, viewport = world) {
      const googleMap = component.map.googleMap as unknown as {
        zoom: number;
        bounds: { toJSON(): object };
      };
      googleMap.zoom = zoom;
      googleMap.bounds = { toJSON: () => viewport };
      component.render();
    }

    function bubbleMarkers() {
      return [...component['bubbleMarkers'].values()];
    }

    function bubbleLabels(): string[] {
      return bubbleMarkers()
        .map(marker => marker.element.textContent)
        .sort() as string[];
    }

    it('shows LOIs as themselves when zoomed in', () => {
      showMap(14);

      expect([...component.markers.keys()].sort()).toEqual([poiId1, poiId2]);
      expect([...component.polygons.keys()]).toEqual([polygonLoiId1]);
      expect(bubbleMarkers().length).toBe(0);
    });

    it('shows bubbles instead of LOIs when zoomed out', () => {
      showMap(3);

      expect(component.markers.size).toBe(0);
      expect(component.polygons.size).toBe(0);
      // poi1 and the polygon are close together, poi2 is far from both.
      expect(bubbleLabels()).toEqual(['0/1', '0/2']);
    });

    it('counts the LOIs with submissions in bubbles', () => {
      const withSubmissions = new LocationOfInterest(
        'with-submissions',
        jobId1,
        new Point(new Coordinate(-100, -40)),
        Map(),
        '',
        true,
        2
      );
      fixture.componentRef.setInput(
        'lois',
        List<LocationOfInterest>([withSubmissions])
      );
      fixture.detectChanges();
      showMap(3);

      expect(bubbleLabels()).toEqual(['1/1']);
    });

    it('draws only the LOIs near the viewport', () => {
      showMap(14, { west: -1, south: -1, east: 11, north: 11 });

      expect([...component.markers.keys()]).toEqual([poiId1]);
      expect([...component.polygons.keys()]).toEqual([polygonLoiId1]);
    });

    it('draws LOIs that come into view and drops those far away', () => {
      showMap(14, { west: -1, south: -1, east: 11, north: 11 });

      showMap(14, { west: 10, south: 44, east: 15, north: 48 });

      expect([...component.markers.keys()]).toEqual([poiId2]);
      expect(component.polygons.size).toBe(0);
    });

    it('keeps LOIs that are just out of view, so panning back and forth does not redraw them', () => {
      showMap(14, { west: -1, south: -1, east: 11, north: 11 });
      const marker = component.markers.get(poiId1);

      // Moves the map a little, keeping poi1 close to the viewport.
      showMap(14, { west: 8, south: 0, east: 20, north: 12 });

      expect(component.markers.get(poiId1)).toBe(marker);
    });

    it('swaps LOIs for bubbles when zooming out, and back when zooming in', () => {
      showMap(14);
      showMap(3);
      expect(component.markers.size).toBe(0);
      expect(bubbleMarkers().length).toBe(2);

      showMap(14);

      expect(component.markers.size).toBe(2);
      expect(bubbleMarkers().length).toBe(0);
    });

    it('removes the map object of a bubble that is no longer shown', () => {
      showMap(3);
      const [first] = bubbleMarkers();

      showMap(3, { west: 100, south: 40, east: 120, north: 50 });

      expect(first.map).toBeNull();
    });

    it('keeps the selected LOI drawn while zoomed out', () => {
      showMap(3, { west: -1, south: -1, east: 11, north: 11 });

      // poi2 is far outside the viewport.
      mockLocationOfInterestId$.next(poiId2);

      expect([...component.markers.keys()]).toEqual([poiId2]);
    });

    it('stops drawing the LOI that was selected once it is not, and is zoomed out', () => {
      showMap(3, { west: -1, south: -1, east: 11, north: 11 });
      mockLocationOfInterestId$.next(poiId2);

      mockLocationOfInterestId$.next(null);

      expect(component.markers.size).toBe(0);
    });

    it('zooms to the LOIs of a bubble when it is clicked', () => {
      showMap(3);
      const googleMap = component.map.googleMap!;
      spyOn(googleMap, 'fitBounds');
      const bubble = bubbleMarkers().find(
        marker => marker.element.textContent === '0/2'
      )!;

      google.maps.event.trigger(bubble, 'click');

      // The extent of poi1 and the polygon (0..10).
      expect(googleMap.fitBounds).toHaveBeenCalledOnceWith(
        { west: 0, south: 0, east: 10, north: 10 },
        50
      );
    });

    it('zooms in on a lone LOI when its bubble is clicked', () => {
      showMap(3);
      const googleMap = component.map.googleMap!;
      spyOn(googleMap, 'panTo');
      spyOn(googleMap, 'setZoom');
      spyOn(googleMap, 'fitBounds');
      const bubble = bubbleMarkers().find(
        marker => marker.element.textContent === '0/1'
      )!;

      google.maps.event.trigger(bubble, 'click');

      expect(googleMap.panTo).toHaveBeenCalledOnceWith({
        lat: 45.6,
        lng: 12.3,
      });
      expect(googleMap.setZoom).toHaveBeenCalledOnceWith(14);
      // Fitting the map to a single point would zoom in as far as it goes.
      expect(googleMap.fitBounds).not.toHaveBeenCalled();
    });

    it('updates the bubbles when LOIs change', () => {
      showMap(3);
      expect(bubbleLabels()).toEqual(['0/1', '0/2']);

      fixture.componentRef.setInput(
        'lois',
        List<LocationOfInterest>([poi1, poi2])
      );
      fixture.detectChanges();

      expect(bubbleLabels()).toEqual(['0/1', '0/1']);
    });

    it('does not count LOIs of a job that was removed', () => {
      const orphan = new LocationOfInterest(
        'orphan',
        'removed-job',
        new Point(new Coordinate(1.5, 4.5)),
        Map()
      );
      fixture.componentRef.setInput(
        'lois',
        List<LocationOfInterest>([poi1, orphan])
      );
      fixture.detectChanges();
      showMap(3);

      expect(bubbleLabels()).toEqual(['0/1']);
    });

    it('zooms the selected LOI in far enough to show it as itself', () => {
      showMap(5);
      const googleMap = component.map.googleMap!;
      spyOn(googleMap, 'setZoom');

      mockLocationOfInterestId$.next(poiId1);

      expect(googleMap.setZoom).toHaveBeenCalledWith(14);
    });
  });

  describe('when submission is selected', () => {
    const submissionId = 'submission001';
    const taskPointId = 'task001';
    const taskPolygonId = 'task002';

    // Create Tasks
    const taskPoint = new Task(
      taskPointId,
      TaskType.DROP_PIN,
      'Drop Pin',
      false,
      1
    );
    const taskPolygon = new Task(
      taskPolygonId,
      TaskType.DRAW_AREA,
      'Draw Area',
      false,
      2
    );

    // Create Job with Tasks
    const jobWithTasks = new Job(
      jobId1,
      -1,
      jobColor1,
      'job001 name',
      OrderedMap({
        [taskPointId]: taskPoint,
        [taskPolygonId]: taskPolygon,
      })
    );

    // Create AuditInfo
    const auditInfo = new AuditInfo(
      new User('user1', 'user@test.com', true, 'User'),
      new Date(),
      new Date()
    );

    const submissionWithTasks = new Submission(
      submissionId,
      poiId1,
      jobWithTasks,
      auditInfo,
      auditInfo,
      Map({
        [taskPointId]: new Result(new Point(new Coordinate(1.23, 4.56))),
        [taskPolygonId]: new Result(
          polygonShellCoordsToPolygon(polygon1ShellCoordinates)
        ),
      })
    );

    it('should render submission geometry on map', async () => {
      submissionServiceSpy.getSubmission$.and.returnValue(
        of(submissionWithTasks)
      );
      // We need to trigger the subscription in ngAfterViewInit
      // This subscription combines: activeSurvey$, lois$, loiId$, submissionId$
      // We need to allow these to emit.

      // Mock survey inputs
      fixture.componentRef.setInput('activeSurvey', mockSurvey);
      fixture.componentRef.setInput('lois', mockLois$.getValue());

      // Mock params
      mockLocationOfInterestId$.next(poiId1);
      mockSubmissionId$.next(submissionId);

      fixture.detectChanges();
      await fixture.whenStable();

      // Check markers (points)
      // One marker for LOI, one for Submission Task
      // Wait, LOI marker might be there too? Yes.
      // Task ID is 'task001'
      const submissionMarker = component.markers.get(taskPointId);
      expect(submissionMarker).toBeDefined();
      assertMarkerLatLng(submissionMarker!, new google.maps.LatLng(4.56, 1.23));

      // Check polygons
      const submissionPolygons = component.polygons.get(taskPolygonId);
      expect(submissionPolygons).toBeDefined();
      expect(submissionPolygons?.length).toBe(1);
      assertPolygonPaths(submissionPolygons![0], [
        [
          new google.maps.LatLng(0, 0),
          new google.maps.LatLng(0, 10),
          new google.maps.LatLng(10, 10),
          new google.maps.LatLng(10, 0),
          new google.maps.LatLng(0, 0),
        ],
      ]);
    });
  });
});
