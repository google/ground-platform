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
import {
  ComponentFixture,
  TestBed,
  waitForAsync,
  tick,
  fakeAsync,
} from '@angular/core/testing';
import { MapComponent } from './map.component';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ProjectService } from '../../services/project/project.service';
import { FeatureService } from '../../services/feature/feature.service';
import { NavigationService } from '../../services/navigation/navigation.service';
import { Project } from '../../shared/models/project.model';
import {
  Feature,
  LocationFeature,
  GeoJsonFeature,
  PolygonFeature,
} from '../../shared/models/feature.model';
import { StringMap } from '../../shared/models/string-map.model';
import { Map, List } from 'immutable';
import { Layer } from '../../shared/models/layer.model';
import { BehaviorSubject, of } from 'rxjs';
import { GoogleMapsModule } from '@angular/google-maps';
import { urlPrefix } from './ground-pin';
import {
  DrawingToolsService,
  EditMode,
} from '../../services/drawing-tools/drawing-tools.service';

describe('MapComponent', () => {
  let component: MapComponent;
  let fixture: ComponentFixture<MapComponent>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let mockFeatures$: BehaviorSubject<List<Feature>>;
  let featureServiceSpy: jasmine.SpyObj<FeatureService>;
  let mockFeatureId$: BehaviorSubject<string | null>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  let mockDialogAfterClosed$: BehaviorSubject<string>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<unknown, unknown>>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let mockEditMode$: BehaviorSubject<EditMode>;
  let drawingToolsServiceSpy: jasmine.SpyObj<DrawingToolsService>;

  const locationFeatureId1 = 'loaction_feature001';
  const locationFeatureId2 = 'loaction_feature002';
  const locationFeatureId3 = 'loaction_feature003';
  const locationFeatureId4 = 'loaction_feature004';
  const geoJsonFeatureId1 = 'geo_json_feature001';
  const polygonFeatureId1 = 'polygon_feature001';
  const layerId1 = 'layer001';
  const layerId2 = 'layer002';
  const layerColor1 = 'red';
  const layerColor2 = 'green';
  const mockProject = new Project(
    'project001',
    StringMap({ en: 'title1' }),
    StringMap({ en: 'description1' }),
    /* layers= */ Map({
      layer001: new Layer(
        layerId1,
        /* index */ -1,
        layerColor1,
        StringMap({ en: 'layer001 name' }),
        /* forms= */ Map()
      ),
      layer002: new Layer(
        layerId2,
        /* index */ -1,
        layerColor2,
        StringMap({ en: 'layer002 name' }),
        /* forms= */ Map()
      ),
    }),
    /* acl= */ Map()
  );
  const locationFeature1 = new LocationFeature(
    locationFeatureId1,
    layerId1,
    new firebase.firestore.GeoPoint(1.23, 4.56)
  );
  const locationFeature2 = new LocationFeature(
    locationFeatureId2,
    layerId2,
    new firebase.firestore.GeoPoint(12.3, 45.6)
  );
  const locationFeature3 = new LocationFeature(
    locationFeatureId3,
    layerId2,
    new firebase.firestore.GeoPoint(78.9, 78.9)
  );
  const locationFeature4 = new LocationFeature(
    locationFeatureId4,
    layerId2,
    new firebase.firestore.GeoPoint(45, 45)
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
  const geoJsonFeature1 = new GeoJsonFeature(
    geoJsonFeatureId1,
    layerId1,
    geoJson1
  );
  const polygonFeature1 = new PolygonFeature(polygonFeatureId1, layerId2, [
    new firebase.firestore.GeoPoint(-10, -10),
    new firebase.firestore.GeoPoint(20, -10),
    new firebase.firestore.GeoPoint(20, 20),
    new firebase.firestore.GeoPoint(-10, 20),
    new firebase.firestore.GeoPoint(-10, -10),
  ]);

  beforeEach(
    waitForAsync(() => {
      projectServiceSpy = jasmine.createSpyObj<ProjectService>(
        'ProjectService',
        ['getActiveProject$']
      );
      projectServiceSpy.getActiveProject$.and.returnValue(
        of<Project>(mockProject)
      );

      featureServiceSpy = jasmine.createSpyObj<FeatureService>(
        'FeatureService',
        ['getFeatures$', 'updatePoint', 'addPoint']
      );
      mockFeatures$ = new BehaviorSubject<List<Feature>>(
        List<Feature>([
          locationFeature1,
          locationFeature2,
          geoJsonFeature1,
          polygonFeature1,
        ])
      );
      featureServiceSpy.getFeatures$.and.returnValue(mockFeatures$);

      navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
        'NavigationService',
        [
          'getFeatureId$',
          'getObservationId$',
          'selectFeature',
          'clearFeatureId',
        ]
      );
      mockFeatureId$ = new BehaviorSubject<string | null>(null);
      navigationServiceSpy.getFeatureId$.and.returnValue(mockFeatureId$);
      navigationServiceSpy.getObservationId$.and.returnValue(
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
        ['getEditMode$', 'setEditMode', 'getSelectedLayerId', 'setDisabled']
      );
      drawingToolsServiceSpy.getEditMode$.and.returnValue(mockEditMode$);
      drawingToolsServiceSpy.getSelectedLayerId.and.returnValue(layerId1);

      TestBed.configureTestingModule({
        imports: [GoogleMapsModule],
        declarations: [MapComponent],
        providers: [
          { provide: MatDialog, useValue: dialogSpy },
          { provide: ProjectService, useValue: projectServiceSpy },
          { provide: FeatureService, useValue: featureServiceSpy },
          { provide: NavigationService, useValue: navigationServiceSpy },
          { provide: DrawingToolsService, useValue: drawingToolsServiceSpy },
        ],
      }).compileComponents();
    })
  );

  beforeEach(() => {
    fixture = TestBed.createComponent(MapComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should render markers on map', () => {
    expect(component.markers.size).toEqual(2);
    const marker1 = component.markers.get(locationFeatureId1)!;
    assertMarkerLatLng(marker1, new google.maps.LatLng(1.23, 4.56));
    assertMarkerIcon(marker1, layerColor1, 30);
    expect(marker1.getMap()).toEqual(component.map.googleMap);
    const marker2 = component.markers.get(locationFeatureId2)!;
    assertMarkerLatLng(marker2, new google.maps.LatLng(12.3, 45.6));
    assertMarkerIcon(marker2, layerColor2, 30);
    expect(marker2.getMap()).toEqual(component.map.googleMap);
  });

  it('should render polygons on map - geojson feature', () => {
    const polygon = component.polygons.get(geoJsonFeatureId1)!;
    assertPolygonPaths(polygon, [
      [
        new google.maps.LatLng(0, 0),
        new google.maps.LatLng(0, 10),
        new google.maps.LatLng(10, 10),
        new google.maps.LatLng(10, 0),
      ],
    ]);
    assertPolygonStyle(polygon, layerColor1, 3);
    expect(polygon.getMap()).toEqual(component.map.googleMap!);
  });

  it('should render polygons on map - polygon feature', () => {
    const polygon = component.polygons.get(polygonFeatureId1)!;
    assertPolygonPaths(polygon, [
      [
        new google.maps.LatLng(-10, -10),
        new google.maps.LatLng(20, -10),
        new google.maps.LatLng(20, 20),
        new google.maps.LatLng(-10, 20),
        new google.maps.LatLng(-10, -10),
      ],
    ]);
    assertPolygonStyle(polygon, layerColor2, 3);
    expect(polygon.getMap()).toEqual(component.map.googleMap!);
  });

  it('should update features when backend features update', fakeAsync(() => {
    mockFeatures$.next(
      List<Feature>([locationFeature1, locationFeature3, geoJsonFeature1])
    );
    tick();

    expect(component.markers.size).toEqual(2);
    const marker1 = component.markers.get(locationFeatureId1)!;
    assertMarkerLatLng(marker1, new google.maps.LatLng(1.23, 4.56));
    assertMarkerIcon(marker1, layerColor1, 30);
    expect(marker1.getMap()).toEqual(component.map.googleMap);
    const marker2 = component.markers.get(locationFeatureId3)!;
    assertMarkerLatLng(marker2, new google.maps.LatLng(78.9, 78.9));
    assertMarkerIcon(marker2, layerColor2, 30);
    expect(marker2.getMap()).toEqual(component.map.googleMap);
    expect(component.polygons.size).toEqual(1);
    const polygon = component.polygons.get(geoJsonFeatureId1)!;
    assertPolygonPaths(polygon, [
      [
        new google.maps.LatLng(0, 0),
        new google.maps.LatLng(0, 10),
        new google.maps.LatLng(10, 10),
        new google.maps.LatLng(10, 0),
      ],
    ]);
    assertPolygonStyle(polygon, layerColor1, 3);
    expect(polygon.getMap()).toEqual(component.map.googleMap!);
  }));

  it('should select feature when marker is clicked', () => {
    const marker1 = component.markers.get(locationFeatureId1)!;
    google.maps.event.trigger(marker1, 'click');

    expect(navigationServiceSpy.selectFeature).toHaveBeenCalledOnceWith(
      locationFeatureId1
    );
  });

  it('should enlarge the marker when feature is selected', fakeAsync(() => {
    mockFeatureId$.next(locationFeatureId1);
    tick();

    const marker1 = component.markers.get(locationFeatureId1)!;
    assertMarkerIcon(marker1, layerColor1, 50);
  }));

  it('should select feature when polygon is clicked', () => {
    const polygon = component.polygons.get(polygonFeatureId1)!;
    google.maps.event.trigger(polygon, 'click', {
      latLng: new google.maps.LatLng(-9, -9),
    });

    expect(navigationServiceSpy.selectFeature).toHaveBeenCalledOnceWith(
      polygonFeatureId1
    );
  });

  it('should enlarge the stroke weight of the polygon when feature is selected', fakeAsync(() => {
    mockFeatureId$.next(geoJsonFeatureId1);
    tick();

    const polygon = component.polygons.get(geoJsonFeatureId1)!;
    assertPolygonStyle(polygon, layerColor1, 6);
  }));

  it('should clear selected feature when map is clicked', fakeAsync(() => {
    mockFeatureId$.next(locationFeatureId1);
    tick();

    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(navigationServiceSpy.clearFeatureId).toHaveBeenCalledTimes(1);
  }));

  it('markers are not draggable by default', () => {
    const marker1 = component.markers.get(locationFeatureId1)!;
    expect(marker1.getDraggable()).toBeFalse();
    const marker2 = component.markers.get(locationFeatureId2)!;
    expect(marker2.getDraggable()).toBeFalse();
  });

  it('should set marker draggable when feature is selected', fakeAsync(() => {
    mockFeatureId$.next(locationFeatureId1);
    tick();

    const marker1 = component.markers.get(locationFeatureId1)!;
    expect(marker1.getDraggable()).toBeTrue();
  }));

  it('reposition dialog is not displayed by default', () => {
    const repositionDialog = fixture.nativeElement.querySelector(
      '#reposition-confirm-dialog'
    ) as Element;
    expect(repositionDialog).toBeNull();
  });

  it('should pop up reposition dialog when marker dragged', fakeAsync(() => {
    mockFeatureId$.next(locationFeatureId1);
    tick();

    const marker = component.markers.get(locationFeatureId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });

    const repositionDialog = fixture.nativeElement.querySelector(
      '#reposition-confirm-dialog'
    ) as Element;
    expect(repositionDialog).toBeDefined();
  }));

  it('should disable drawing tools when marker dragged', fakeAsync(() => {
    mockFeatureId$.next(locationFeatureId1);
    tick();

    const marker = component.markers.get(locationFeatureId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });

    expect(drawingToolsServiceSpy.setDisabled).toHaveBeenCalledOnceWith(true);
  }));

  it('should disable marker click while repositioning a marker', fakeAsync(() => {
    mockFeatureId$.next(locationFeatureId1);
    tick();

    const marker1 = component.markers.get(locationFeatureId1)!;
    google.maps.event.trigger(marker1, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });
    const marker2 = component.markers.get(locationFeatureId1)!;
    google.maps.event.trigger(marker2, 'click');

    expect(navigationServiceSpy.selectFeature).toHaveBeenCalledTimes(0);
  }));

  it('should disable polygon click while repositioning a marker', fakeAsync(() => {
    mockFeatureId$.next(locationFeatureId1);
    tick();

    const marker = component.markers.get(locationFeatureId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });
    const polygon = component.polygons.get(geoJsonFeatureId1)!;
    google.maps.event.trigger(polygon, 'click');

    expect(navigationServiceSpy.selectFeature).toHaveBeenCalledTimes(0);
  }));

  it('should disable map click while repositioning a marker', fakeAsync(() => {
    mockFeatureId$.next(locationFeatureId1);
    tick();

    const marker = component.markers.get(locationFeatureId1)!;
    google.maps.event.trigger(marker, 'dragstart', {
      latLng: new google.maps.LatLng(1.23, 4.56),
    });
    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(navigationServiceSpy.clearFeatureId).toHaveBeenCalledTimes(0);
  }));

  it('should reposition marker when confirmed in reposition dialog', fakeAsync(() => {
    mockFeatureId$.next(locationFeatureId1);
    tick();

    const marker = component.markers.get(locationFeatureId1)!;
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
    expect(featureServiceSpy.updatePoint).toHaveBeenCalledOnceWith(
      new LocationFeature(
        locationFeature1.id,
        locationFeature1.layerId,
        new firebase.firestore.GeoPoint(2.23, 5.56)
      )
    );
  }));

  it('should enable drawing tools when confirmed in reposition dialog', fakeAsync(() => {
    mockFeatureId$.next(locationFeatureId1);
    tick();

    const marker = component.markers.get(locationFeatureId1)!;
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
    mockFeatureId$.next(locationFeatureId1);
    tick();

    const marker = component.markers.get(locationFeatureId1)!;
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
    expect(featureServiceSpy.updatePoint).toHaveBeenCalledTimes(0);
  }));

  it('should pop up dialog when overlapping polygons are clicked', fakeAsync(() => {
    const polygon = component.polygons.get(geoJsonFeatureId1)!;
    google.maps.event.trigger(polygon, 'click', {
      latLng: new google.maps.LatLng(0, 0),
    });
    mockDialogAfterClosed$.next(polygonFeatureId1);
    tick();

    expect(dialogSpy.open).toHaveBeenCalledTimes(1);
    expect(dialogRefSpy.afterClosed).toHaveBeenCalledTimes(1);
    expect(navigationServiceSpy.selectFeature).toHaveBeenCalledOnceWith(
      polygonFeatureId1
    );
  }));

  it('should add marker when map clicked and edit mode is "AddPoint"', fakeAsync(() => {
    mockEditMode$.next(EditMode.AddPoint);
    drawingToolsServiceSpy.getSelectedLayerId.and.returnValue(layerId2);
    featureServiceSpy.addPoint.and.returnValue(
      new Promise(resolve => resolve(locationFeature4))
    );
    tick();

    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(featureServiceSpy.addPoint).toHaveBeenCalledOnceWith(
      45,
      45,
      layerId2
    );
  }));

  it('should not add marker when layer id is undefined', fakeAsync(() => {
    mockEditMode$.next(EditMode.AddPoint);
    drawingToolsServiceSpy.getSelectedLayerId.and.returnValue(undefined);
    tick();

    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(featureServiceSpy.addPoint).toHaveBeenCalledTimes(0);
  }));

  it('should do nothing when map clicked and edit mode is "AddPolygon"', fakeAsync(() => {
    mockEditMode$.next(EditMode.AddPolygon);
    tick();

    google.maps.event.trigger(component.map.googleMap!, 'click', {
      latLng: new google.maps.LatLng(45, 45),
    });

    expect(featureServiceSpy.addPoint).toHaveBeenCalledTimes(0);
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
    const icon = marker.getIcon() as google.maps.ReadonlyIcon;
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
