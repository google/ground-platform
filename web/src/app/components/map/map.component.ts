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
  ChangeDetectorRef,
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
import { MatDialog } from '@angular/material/dialog';
import {
  FeatureData,
  SelectFeatureDialogComponent,
} from '../select-feature-dialog/select-feature-dialog.component';

// To make ESLint happy:
/*global google*/

const normalIconScale = 30;
const enlargedIconScale = 50;
const zoomedInLevel = 13;
const normalPolygonStrokeWeight = 3;
const enlargedPolygonStrokeWeight = 6;

@Component({
  selector: 'ground-map',
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.scss'],
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
  private selectedPolygon?: google.maps.Polygon;
  markers: Map<string, google.maps.Marker> = new Map<
    string,
    google.maps.Marker
  >();
  polygons: Map<string, google.maps.Polygon> = new Map<
    string,
    google.maps.Polygon
  >();
  private crosshairCursorMapOptions: google.maps.MapOptions = {
    draggableCursor: 'crosshair',
  };
  private defaultCursorMapOptions: google.maps.MapOptions = {
    draggableCursor: '',
  };
  mapOptions: google.maps.MapOptions = this.initialMapOptions;
  showRepositionConfirmDialog = false;
  newFeatureToReposition?: LocationFeature;
  oldLatLng?: google.maps.LatLng;
  newLatLng?: google.maps.LatLng;
  markerToReposition?: google.maps.Marker;
  disableMapClicks = false;

  @ViewChild(GoogleMap) map!: GoogleMap;

  constructor(
    private drawingToolsService: DrawingToolsService,
    private projectService: ProjectService,
    private featureService: FeatureService,
    private navigationService: NavigationService,
    private zone: NgZone,
    private changeDetectorRef: ChangeDetectorRef,
    private dialog: MatDialog
  ) {
    this.features$ = this.featureService.getFeatures$();
    this.activeProject$ = this.projectService.getActiveProject$();
  }

  ngAfterViewInit() {
    this.subscription.add(
      combineLatest([
        this.activeProject$,
        this.features$,
        this.navigationService.getFeatureId$(),
      ]).subscribe(([project, features, selectedFeatureId]) =>
        this.onProjectAndFeaturesUpdate(project, features, selectedFeatureId)
      )
    );

    this.subscription.add(
      this.drawingToolsService
        .getEditMode$()
        .subscribe(editMode => this.onEditModeChange(editMode))
    );

    this.subscription.add(
      combineLatest([
        this.navigationService.getFeatureId$(),
        this.navigationService.getObservationId$(),
      ]).subscribe(() => this.cancelReposition())
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  async onMapClick(event: google.maps.MouseEvent) {
    if (this.disableMapClicks) {
      return;
    }
    const editMode = this.drawingToolsService.getEditMode$().getValue();
    const selectedLayerId = this.drawingToolsService.getSelectedLayerId();
    switch (editMode) {
      case EditMode.AddPoint: {
        if (!selectedLayerId) {
          return;
        }
        this.drawingToolsService.setEditMode(EditMode.None);
        const newFeature = await this.featureService.addPoint(
          event.latLng.lat(),
          event.latLng.lng(),
          selectedLayerId
        );
        if (newFeature) {
          this.navigationService.selectFeature(newFeature.id);
        }
        return;
      }
      case EditMode.AddPolygon:
        // TODO: Implement adding polygon.
        return;
      case EditMode.None:
      default:
        this.navigationService.clearFeatureId();
        return;
    }
  }

  private onProjectAndFeaturesUpdate(
    project: Project,
    features: List<Feature>,
    selectedFeatureId: string | null
  ): void {
    this.removeDeletedFeatures(features);
    this.addNewFeatures(project, features);
    this.selectFeature(selectedFeatureId);
  }

  /**
   * Remove deleted features to map. The features that were displayed on
   * the map but not in the `newFeatures` are considered as deleted.
   */
  private removeDeletedFeatures(newFeatures: List<Feature>) {
    const newFeatureIds: List<string> = newFeatures.map(f => f.id);
    this.removeDeletedMarkers(newFeatureIds);
    this.removeDeletedPolygons(newFeatureIds);
  }

  private removeDeletedMarkers(newFeatureIds: List<string>) {
    for (const id of this.markers.keys()) {
      if (!newFeatureIds.contains(id)) {
        this.markers.get(id)!.setMap(null);
        this.markers.delete(id);
      }
    }
  }

  private removeDeletedPolygons(newFeatureIds: List<string>) {
    for (const id of this.polygons.keys()) {
      if (!newFeatureIds.contains(id)) {
        this.polygons.get(id)!.setMap(null);
        this.polygons.delete(id);
      }
    }
  }

  /**
   * Add new features to map. The features that were not displayed on
   * the map but in the `newFeatures` are considered as new.
   */
  private addNewFeatures(project: Project, features: List<Feature>) {
    const existingFeatureIds = Array.from(this.markers.keys()).concat(
      Array.from(this.polygons.keys())
    );

    features.forEach(feature => {
      if (!project.getLayer(feature.layerId)) {
        // Ignore features whose layer has been removed.
        console.debug(
          `Ignoring feature ${feature.id} with missing layer ${feature.layerId}`
        );
        return;
      }
      if (existingFeatureIds.includes(feature.id)) {
        return;
      }
      const color = project.layers.get(feature.layerId)?.color;
      const layerName = project.layers.get(feature.layerId)?.name?.get('en');
      if (feature instanceof LocationFeature) {
        this.addLocationFeature(color, feature);
      }
      if (feature instanceof GeoJsonFeature) {
        this.addGeoJsonFeature(color, layerName, feature);
      }
      if (feature instanceof PolygonFeature) {
        this.addPolygonFeature(color, layerName, feature);
      }
    });
  }

  private addLocationFeature(
    color: string | undefined,
    feature: LocationFeature
  ) {
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
    marker.addListener('click', () => this.onMarkerClick(feature.id));
    marker.addListener('dragstart', (event: google.maps.MouseEvent) =>
      this.onMarkerDragStart(event, marker)
    );
    marker.addListener('dragend', (event: google.maps.MouseEvent) =>
      this.onMarkerDragEnd(event, feature)
    );
    this.markers.set(feature.id, marker);
  }

  private onMarkerClick(featureId: string) {
    if (this.disableMapClicks) {
      return;
    }
    this.navigationService.selectFeature(featureId);
  }

  private onMarkerDragStart(
    event: google.maps.MouseEvent,
    marker: google.maps.Marker
  ) {
    // TODO: Show confirm dialog and disable other components when entering reposition state.
    // Currently we are figuring out how should the UI trigger this state.
    this.showRepositionConfirmDialog = true;
    this.disableMapClicks = true;
    this.drawingToolsService.setDisabled(true);
    this.markerToReposition = marker;
    this.oldLatLng = new google.maps.LatLng(
      event.latLng.lat(),
      event.latLng.lng()
    );
    this.changeDetectorRef.detectChanges();
  }

  private onMarkerDragEnd(
    event: google.maps.MouseEvent,
    feature: LocationFeature
  ) {
    this.newLatLng = new google.maps.LatLng(
      event.latLng.lat(),
      event.latLng.lng()
    );
    this.newFeatureToReposition = new LocationFeature(
      feature.id,
      feature.layerId,
      new firebase.firestore.GeoPoint(event.latLng.lat(), event.latLng.lng())
    );
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
      this.navigationService.clearFeatureId();
      for (const marker of this.markers) {
        marker[1].setClickable(false);
      }
    } else {
      for (const marker of this.markers) {
        marker[1].setClickable(true);
      }
    }
    this.mapOptions =
      editMode === EditMode.AddPoint
        ? this.crosshairCursorMapOptions
        : this.defaultCursorMapOptions;
  }

  /**
   * Selecting feature enlarges the marker or border of the polygon,
   * pans and zooms to the marker/polygon. Selecting null is considered
   * as deselecting which will change the selected back to normal size.
   */
  private selectFeature(selectedFeatureId: string | null) {
    const markerToSelect = this.markers.get(selectedFeatureId as string);
    this.selectMarker(markerToSelect);
    const polygonToSelect = this.polygons.get(selectedFeatureId as string);
    this.selectPolygon(polygonToSelect);
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

  private selectPolygon(polygon: google.maps.Polygon | undefined) {
    if (polygon === this.selectedPolygon) {
      return;
    }
    if (polygon) {
      polygon.setOptions({ strokeWeight: enlargedPolygonStrokeWeight });
    }
    if (this.selectedPolygon) {
      this.selectedPolygon.setOptions({
        strokeWeight: normalPolygonStrokeWeight,
      });
    }
    this.selectedPolygon = polygon;
    this.panAndZoom(polygon?.getPaths().getAt(0).getAt(0));
  }

  private addGeoJsonFeature(
    color: string | undefined,
    layerName: string | undefined,
    feature: GeoJsonFeature
  ) {
    const paths: google.maps.LatLng[][] = [];
    const layer = new google.maps.Data();
    layer.addGeoJson(feature.geoJson);
    layer.forEach(f => {
      if (f.getGeometry() instanceof google.maps.Data.Polygon) {
        paths.push(
          ...this.geoJsonPolygonToPaths(
            f.getGeometry() as google.maps.Data.Polygon
          )
        );
      }
      if (f.getGeometry() instanceof google.maps.Data.MultiPolygon) {
        (f.getGeometry() as google.maps.Data.MultiPolygon)
          .getArray()
          .forEach(polygon =>
            paths.push(...this.geoJsonPolygonToPaths(polygon))
          );
      }
    });

    this.addPolygonToMap(feature.id, color, layerName, paths);
  }

  private geoJsonPolygonToPaths(
    polygon: google.maps.Data.Polygon
  ): google.maps.LatLng[][] {
    const paths: google.maps.LatLng[][] = polygon
      .getArray()
      .map(linearRing => linearRing.getArray());
    return paths;
  }

  private addPolygonFeature(
    color: string | undefined,
    layerName: string | undefined,
    feature: PolygonFeature
  ) {
    const vertices: google.maps.LatLng[] = feature.polygonVertices.map(
      vertex => new google.maps.LatLng(vertex.latitude, vertex.longitude)
    );

    this.addPolygonToMap(feature.id, color, layerName, [vertices]);
  }

  private addPolygonToMap(
    featureId: string,
    color: string | undefined,
    layerName: string | undefined,
    paths: google.maps.LatLng[][]
  ) {
    const polygon = new google.maps.Polygon({
      paths: paths,
      clickable: true,
      strokeColor: color,
      strokeOpacity: 1,
      strokeWeight: normalPolygonStrokeWeight,
      fillOpacity: 0,
      map: this.map.googleMap,
    });
    polygon.set('id', featureId);
    polygon.set('color', color);
    polygon.set('layerName', layerName);
    polygon.addListener('click', (event: google.maps.PolyMouseEvent) => {
      this.onPolygonClick(event);
    });
    this.polygons.set(featureId, polygon);
  }

  private onPolygonClick(event: google.maps.PolyMouseEvent) {
    if (this.disableMapClicks) {
      return;
    }
    const candidatePolygons: google.maps.Polygon[] = [];
    for (const polygon of this.polygons.values()) {
      if (google.maps.geometry.poly.containsLocation(event.latLng, polygon)) {
        candidatePolygons.push(polygon);
      }
    }
    if (candidatePolygons.length === 1) {
      this.zone.run(() => {
        this.navigationService.selectFeature(candidatePolygons[0].get('id'));
      });
      return;
    }
    this.openSelectFeatureDialog(candidatePolygons);
  }

  private openSelectFeatureDialog(candidatePolygons: google.maps.Polygon[]) {
    this.zone.run(() => {
      const dialogRef = this.dialog.open(SelectFeatureDialogComponent, {
        width: '500px',
        data: {
          clickedFeatures: candidatePolygons.map(this.polygonToFeatureData),
        },
      });
      dialogRef.afterClosed().subscribe(result => {
        if (result) {
          this.navigationService.selectFeature(result);
        }
      });
    });
  }

  private polygonToFeatureData(polygon: google.maps.Polygon): FeatureData {
    return {
      featureId: polygon.get('id'),
      color: polygon.get('color'),
      layerName: polygon.get('layerName'),
    };
  }

  onSaveRepositionClick() {
    this.markerToReposition?.setPosition(this.newLatLng!);
    this.featureService.updatePoint(this.newFeatureToReposition!);
    this.resetReposition();
  }

  onCancelRepositionClick() {
    this.markerToReposition?.setPosition(this.oldLatLng!);
    this.resetReposition();
  }

  private resetReposition() {
    this.showRepositionConfirmDialog = false;
    this.newFeatureToReposition = undefined;
    this.oldLatLng = undefined;
    this.newLatLng = undefined;
    this.markerToReposition = undefined;
    this.disableMapClicks = false;
    this.drawingToolsService.setDisabled(false);
  }

  private cancelReposition() {
    if (this.showRepositionConfirmDialog) {
      this.onCancelRepositionClick();
    }
  }
}
