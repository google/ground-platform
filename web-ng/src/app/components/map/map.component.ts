/**
 * Copyright 2019 Google LLC
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
  Component,
  AfterViewInit,
  ViewChild,
  OnDestroy,
  NgZone,
} from '@angular/core';
import { Project } from '../../shared/models/project.model';
import {
  Feature,
  LocationFeature,
  GeoJsonFeature,
  PolygonFeature,
} from '../../shared/models/feature.model';
import {
  DrawingToolsService,
  EditMode,
} from '../../services/drawing-tools/drawing-tools.service';
import { ProjectService } from '../../services/project/project.service';
import { FeatureService } from '../../services/feature/feature.service';
import { combineLatest, Observable, Subscription } from 'rxjs';
import { List } from 'immutable';
import { getPinImageSource } from './ground-pin';
import { NavigationService } from '../../services/navigation/navigation.service';
import { GoogleMap } from '@angular/google-maps';
import firebase from 'firebase/app';
import { startWith } from 'rxjs/operators';

// To make ESLint happy:
/*global google*/

const normalIconScale = 30;
const enlargedIconScale = 50;
const zoomedInLevel = 13;

@Component({
  selector: 'ground-map',
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.css'],
})
export class MapComponent implements AfterViewInit, OnDestroy {
  private subscription: Subscription = new Subscription();
  features$: Observable<List<Feature>>;
  activeProject$: Observable<Project>;
  private initialMapOptions: google.maps.MapOptions = {
    center: new google.maps.LatLng(40.767716, -73.971714),
    zoom: 3,
    fullscreenControl: false,
    mapTypeControl: false,
    streetViewControl: false,
    mapTypeId: google.maps.MapTypeId.HYBRID,
  };
  private selectedMarker?: google.maps.Marker;
  private markers: google.maps.Marker[] = [];
  private polygons: google.maps.Polygon[] = [];
  private crosshairCursorMapOptions: google.maps.MapOptions = {
    draggableCursor: 'crosshair',
  };
  private defaultCursorMapOptions: google.maps.MapOptions = {
    draggableCursor: '',
  };
  mapOptions: google.maps.MapOptions = this.initialMapOptions;

  @ViewChild(GoogleMap) map!: GoogleMap;

  constructor(
    private drawingToolsService: DrawingToolsService,
    private projectService: ProjectService,
    private featureService: FeatureService,
    private navigationService: NavigationService,
    private zone: NgZone
  ) {
    this.features$ = this.featureService.getFeatures$();
    this.activeProject$ = this.projectService.getActiveProject$();
  }

  ngAfterViewInit() {
    this.subscription.add(
      combineLatest([
        this.activeProject$,
        this.features$,
        this.featureService.getSelectedFeatureId$().pipe(startWith('')),
      ]).subscribe(([project, features, selectedFeatureId]) =>
        this.onProjectAndFeaturesUpdate(project, features, selectedFeatureId)
      )
    );

    this.subscription.add(
      this.drawingToolsService
        .getEditMode$()
        .subscribe(editMode => this.onEditModeChange(editMode))
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  onMapClick(event: google.maps.MouseEvent): Promise<void> {
    this.selectMarker(undefined);
    const editMode = this.drawingToolsService.getEditMode$().getValue();
    const selectedLayerId = this.drawingToolsService.getSelectedLayerId();
    switch (editMode) {
      case EditMode.AddPoint:
        if (!selectedLayerId) {
          return Promise.resolve();
        }
        this.drawingToolsService.setEditMode(EditMode.None);
        return this.featureService.addPoint(
          event.latLng.lat(),
          event.latLng.lng(),
          selectedLayerId
        );
      case EditMode.AddPolygon:
        // TODO: Implement adding polygon.
        if (!selectedLayerId) {
          return Promise.resolve();
        }
        return Promise.resolve();
      case EditMode.None:
      default:
        this.navigationService.clearFeatureId();
        return Promise.resolve();
    }
  }

  private onProjectAndFeaturesUpdate(
    project: Project,
    features: List<Feature>,
    selectedFeatureId: string
  ): void {
    this.removeMarkersAndGeoJsonsOnMap(features);
    this.addFeaturesToMap(project, features);
    this.updateStylingFunctionForAllGeoJsons(project);
    this.selectMarkerWithFeatureId(selectedFeatureId);
  }

  private removeMarkersAndGeoJsonsOnMap(features: List<Feature>) {
    const newFeatureIds: List<string> = features.map(f => f.id);
    this.removeMarkersOnMap(newFeatureIds);
    this.removeGeoJsonsOnMap(newFeatureIds);
    this.removePolygonOnMap(newFeatureIds);
  }

  private removeMarkersOnMap(newFeatureIds: List<string>) {
    for (let i = this.markers.length - 1; i >= 0; i--) {
      if (!newFeatureIds.contains(this.markers[i].getTitle()!)) {
        this.markers[i].setMap(null);
        this.markers.splice(i, 1);
      }
    }
  }

  private removeGeoJsonsOnMap(newFeatureIds: List<string>) {
    this.map.data.forEach(f => {
      if (!newFeatureIds.contains(f.getProperty('featureId'))) {
        this.map.data.remove(f);
      }
    });
  }

  private removePolygonOnMap(newFeatureIds: List<string>) {
    this.map.data.forEach(f => {
      if (!newFeatureIds.contains(f.getProperty('featureId'))) {
        this.map.data.remove(f);
      }
    });
  }

  private addFeaturesToMap(project: Project, features: List<Feature>) {
    const locationFeatureIds = this.markers.map(m => m.getTitle());
    const geoJsonFeatureIds: String[] = [];
    const polygonFeatureIds: String[] = [];
    this.map.data.forEach(f => {
      geoJsonFeatureIds.push(f.getProperty('featureId'));
      polygonFeatureIds.push(f.getProperty('featureId'));
    });

    features.forEach(feature => {
      if (feature instanceof LocationFeature) {
        if (!locationFeatureIds.includes(feature.id)) {
          this.addMarkerToMap(project, feature);
        }
      }
      if (feature instanceof GeoJsonFeature) {
        if (!geoJsonFeatureIds.includes(feature.id)) {
          this.addGeoJsonToMap(feature);
        }
      }

      if (feature instanceof PolygonFeature) {
        if (!polygonFeatureIds.includes(feature.id)) {
          this.addPolygonToMap(feature);
        }
      }
    });
  }

  private addMarkerToMap(project: Project, feature: LocationFeature) {
    const color = project.layers.get(feature.layerId)?.color;
    const icon = {
      url: getPinImageSource(color),
      scaledSize: {
        width: normalIconScale,
        height: normalIconScale,
      },
    } as google.maps.Icon;
    const options: google.maps.MarkerOptions = {
      map: this.map.googleMap,
      position: new google.maps.LatLng(
        feature.location.latitude,
        feature.location.longitude
      ),
      icon,
      draggable: false,
      title: feature.id,
    };
    const marker = new google.maps.Marker(options);
    marker.addListener('click', () => {
      this.selectMarker(marker);
      this.navigationService.selectFeature(feature.id);
    });
    marker.addListener('dragend', (event: google.maps.MouseEvent) => {
      const newFeature = new LocationFeature(
        feature.id,
        feature.layerId,
        new firebase.firestore.GeoPoint(event.latLng.lat(), event.latLng.lng())
      );
      this.featureService.updatePoint(newFeature);
    });
    this.markers.push(marker);
  }

  private panAndZoom(position: google.maps.LatLng | null | undefined) {
    if (!position) {
      return;
    }
    this.map.panTo(position);
    if (this.map.getZoom() < zoomedInLevel) {
      this.map.zoom = zoomedInLevel;
    }
  }

  private onEditModeChange(editMode: EditMode) {
    if (editMode !== EditMode.None) {
      this.selectMarker(undefined);
      this.navigationService.clearFeatureId();
      for (const marker of this.markers) {
        marker.setClickable(false);
      }
    } else {
      for (const marker of this.markers) {
        marker.setClickable(true);
      }
    }
    this.mapOptions =
      editMode === EditMode.AddPoint
        ? this.crosshairCursorMapOptions
        : this.defaultCursorMapOptions;
  }

  private selectMarker(marker: google.maps.Marker | undefined) {
    if (marker === this.selectedMarker) {
      return;
    }
    if (marker) {
      this.setIconSize(marker, enlargedIconScale);
      marker.setDraggable(true);
    }
    if (this.selectedMarker) {
      this.setIconSize(this.selectedMarker, normalIconScale);
      this.selectedMarker.setDraggable(false);
    }
    this.selectedMarker = marker;
    this.panAndZoom(marker?.getPosition());
  }

  private selectMarkerWithFeatureId(selectedFeatureId: string) {
    for (const marker of this.markers) {
      if (marker.getTitle() === selectedFeatureId) {
        this.selectMarker(marker);
      }
    }
  }

  private setIconSize(marker: google.maps.Marker, size: number) {
    const icon = marker.getIcon() as google.maps.ReadonlyIcon;
    const newIcon = {
      url: icon.url,
      scaledSize: {
        width: size,
        height: size,
      },
    } as google.maps.Icon;
    marker.setIcon(newIcon);
  }

  private addGeoJsonToMap(feature: GeoJsonFeature) {
    const addedFeatures = this.map.data.addGeoJson(feature.geoJson);
    // Set property 'layerId' so that it knows what color to render later.
    addedFeatures.forEach(f => {
      f.setProperty('featureId', feature.id);
      f.setProperty('layerId', feature.layerId);
    });
  }

  private addPolygonToMap(feature: PolygonFeature) {
    const vertices: google.maps.LatLng[] = [];
    feature.polygonVertices.map(vertex => {
      vertices.push(new google.maps.LatLng(vertex.latitude, vertex.longitude));
    });
    // Construct the polygon.
    const polygon = new google.maps.Polygon({
      paths: vertices,
      strokeColor: '#ff9131',
      strokeOpacity: 1,
      strokeWeight: 4,
      fillOpacity: 0,
    });
    polygon.addListener('click', () => {
      this.panAndZoom(polygon?.get('paths'));
      this.zone.run(() => {
        this.navigationService.selectFeature(feature.id);
      });
    });
    if (this.map.googleMap != null) {
      polygon.setMap(this.map.googleMap);
    }
  }

  private updateStylingFunctionForAllGeoJsons(project: Project) {
    this.map.data.setStyle(mapFeature => {
      const layerId = mapFeature.getProperty('layerId');
      const color = project.layers.get(layerId)?.color;
      return {
        fillColor: color,
      };
    });
  }
}
