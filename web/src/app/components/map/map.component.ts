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
import { Survey } from '../../shared/models/survey.model';
import {
  LocationOfInterest,
  PointOfInterest,
  GeoJsonLocationOfInterest,
  AreaOfInterest,
} from '../../shared/models/loi.model';
import {
  DrawingToolsService,
  EditMode,
} from '../../services/drawing-tools/drawing-tools.service';
import { SurveyService } from '../../services/survey/survey.service';
import { LocationOfInterestService } from '../../services/loi/loi.service';
import { combineLatest, Observable, Subscription } from 'rxjs';
import { List } from 'immutable';
import { getPinImageSource } from './ground-pin';
import { NavigationService } from '../../services/navigation/navigation.service';
import { GoogleMap } from '@angular/google-maps';
import firebase from 'firebase/app';
import { MatDialog } from '@angular/material/dialog';
import {
  LocationOfInterestData,
  SelectLocationOfInterestDialogComponent,
} from '../select-loi-dialog/select-loi-dialog.component';

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
  lois$: Observable<List<LocationOfInterest>>;
  activeSurvey$: Observable<Survey>;
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
  newLocationOfInterestToReposition?: PointOfInterest;
  oldLatLng?: google.maps.LatLng;
  newLatLng?: google.maps.LatLng;
  markerToReposition?: google.maps.Marker;
  disableMapClicks = false;

  @ViewChild(GoogleMap) map!: GoogleMap;

  constructor(
    private drawingToolsService: DrawingToolsService,
    private surveyService: SurveyService,
    private loiService: LocationOfInterestService,
    private navigationService: NavigationService,
    private zone: NgZone,
    private changeDetectorRef: ChangeDetectorRef,
    private dialog: MatDialog
  ) {
    this.lois$ = this.loiService.getLocationsOfInterest$();
    this.activeSurvey$ = this.surveyService.getActiveSurvey$();
  }

  ngAfterViewInit() {
    this.subscription.add(
      combineLatest([
        this.activeSurvey$,
        this.lois$,
        this.navigationService.getLocationOfInterestId$(),
      ]).subscribe(([survey, lois, selectedLocationOfInterestId]) =>
        this.onSurveyAndLocationsOfInterestUpdate(
          survey,
          lois,
          selectedLocationOfInterestId
        )
      )
    );

    this.subscription.add(
      this.drawingToolsService
        .getEditMode$()
        .subscribe(editMode => this.onEditModeChange(editMode))
    );

    this.subscription.add(
      combineLatest([
        this.navigationService.getLocationOfInterestId$(),
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
        const newLocationOfInterest = await this.loiService.addPoint(
          event.latLng.lat(),
          event.latLng.lng(),
          selectedLayerId
        );
        if (newLocationOfInterest) {
          this.navigationService.selectLocationOfInterest(
            newLocationOfInterest.id
          );
        }
        return;
      }
      case EditMode.AddPolygon:
        // TODO: Implement adding polygon.
        return;
      case EditMode.None:
      default:
        this.navigationService.clearLocationOfInterestId();
        return;
    }
  }

  private onSurveyAndLocationsOfInterestUpdate(
    survey: Survey,
    lois: List<LocationOfInterest>,
    selectedLocationOfInterestId: string | null
  ): void {
    this.removeDeletedLocationsOfInterest(lois);
    this.addNewLocationsOfInterest(survey, lois);
    this.selectLocationOfInterest(selectedLocationOfInterestId);
  }

  /**
   * Remove deleted lois to map. The LOIs that were displayed on
   * the map but not in the `newLocationsOfInterest` are considered as deleted.
   */
  private removeDeletedLocationsOfInterest(
    newLocationsOfInterest: List<LocationOfInterest>
  ) {
    const newLocationOfInterestIds: List<string> = newLocationsOfInterest.map(
      f => f.id
    );
    this.removeDeletedMarkers(newLocationOfInterestIds);
    this.removeDeletedPolygons(newLocationOfInterestIds);
  }

  private removeDeletedMarkers(newLocationOfInterestIds: List<string>) {
    for (const id of this.markers.keys()) {
      if (!newLocationOfInterestIds.contains(id)) {
        this.markers.get(id)!.setMap(null);
        this.markers.delete(id);
      }
    }
  }

  private removeDeletedPolygons(newLocationOfInterestIds: List<string>) {
    for (const id of this.polygons.keys()) {
      if (!newLocationOfInterestIds.contains(id)) {
        this.polygons.get(id)!.setMap(null);
        this.polygons.delete(id);
      }
    }
  }

  /**
   * Add new lois to map. The LOIs that were not displayed on
   * the map but in the `newLocationsOfInterest` are considered as new.
   */
  private addNewLocationsOfInterest(
    survey: Survey,
    lois: List<LocationOfInterest>
  ) {
    const existingLocationOfInterestIds = Array.from(
      this.markers.keys()
    ).concat(Array.from(this.polygons.keys()));

    lois.forEach(loi => {
      if (!survey.getLayer(loi.layerId)) {
        // Ignore LOIs whose layer has been removed.
        console.debug(
          `Ignoring loi ${loi.id} with missing layer ${loi.layerId}`
        );
        return;
      }
      if (existingLocationOfInterestIds.includes(loi.id)) {
        return;
      }
      const color = survey.layers.get(loi.layerId)?.color;
      const layerName = survey.layers.get(loi.layerId)?.name?.get('en');
      if (loi instanceof PointOfInterest) {
        this.addPointOfInterest(color, loi);
      }
      if (loi instanceof GeoJsonLocationOfInterest) {
        this.addGeoJsonLocationOfInterest(color, layerName, loi);
      }
      if (loi instanceof AreaOfInterest) {
        this.addAreaOfInterest(color, layerName, loi);
      }
    });
  }

  private addPointOfInterest(color: string | undefined, loi: PointOfInterest) {
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
        loi.location.latitude,
        loi.location.longitude
      ),
      icon,
      draggable: false,
      title: loi.id,
    };
    const marker = new google.maps.Marker(options);
    marker.addListener('click', () => this.onMarkerClick(loi.id));
    marker.addListener('dragstart', (event: google.maps.MouseEvent) =>
      this.onMarkerDragStart(event, marker)
    );
    marker.addListener('dragend', (event: google.maps.MouseEvent) =>
      this.onMarkerDragEnd(event, loi)
    );
    this.markers.set(loi.id, marker);
  }

  private onMarkerClick(loiId: string) {
    if (this.disableMapClicks) {
      return;
    }
    this.navigationService.selectLocationOfInterest(loiId);
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

  private onMarkerDragEnd(event: google.maps.MouseEvent, loi: PointOfInterest) {
    this.newLatLng = new google.maps.LatLng(
      event.latLng.lat(),
      event.latLng.lng()
    );
    this.newLocationOfInterestToReposition = new PointOfInterest(
      loi.id,
      loi.layerId,
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
      this.navigationService.clearLocationOfInterestId();
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
   * Selecting LOI enlarges the marker or border of the polygon,
   * pans and zooms to the marker/polygon. Selecting null is considered
   * as deselecting which will change the selected back to normal size.
   */
  private selectLocationOfInterest(
    selectedLocationOfInterestId: string | null
  ) {
    const markerToSelect = this.markers.get(
      selectedLocationOfInterestId as string
    );
    this.selectMarker(markerToSelect);
    const polygonToSelect = this.polygons.get(
      selectedLocationOfInterestId as string
    );
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

  private addGeoJsonLocationOfInterest(
    color: string | undefined,
    layerName: string | undefined,
    loi: GeoJsonLocationOfInterest
  ) {
    const paths: google.maps.LatLng[][] = [];
    const layer = new google.maps.Data();
    layer.addGeoJson(loi.geoJson);
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

    this.addPolygonToMap(loi.id, color, layerName, paths);
  }

  private geoJsonPolygonToPaths(
    polygon: google.maps.Data.Polygon
  ): google.maps.LatLng[][] {
    const paths: google.maps.LatLng[][] = polygon
      .getArray()
      .map(linearRing => linearRing.getArray());
    return paths;
  }

  private addAreaOfInterest(
    color: string | undefined,
    layerName: string | undefined,
    loi: AreaOfInterest
  ) {
    const vertices: google.maps.LatLng[] = loi.polygonVertices.map(
      vertex => new google.maps.LatLng(vertex.latitude, vertex.longitude)
    );

    this.addPolygonToMap(loi.id, color, layerName, [vertices]);
  }

  private addPolygonToMap(
    loiId: string,
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
    polygon.set('id', loiId);
    polygon.set('color', color);
    polygon.set('layerName', layerName);
    polygon.addListener('click', (event: google.maps.PolyMouseEvent) => {
      this.onPolygonClick(event);
    });
    this.polygons.set(loiId, polygon);
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
        this.navigationService.selectLocationOfInterest(
          candidatePolygons[0].get('id')
        );
      });
      return;
    }
    this.openSelectLocationOfInterestDialog(candidatePolygons);
  }

  private openSelectLocationOfInterestDialog(
    candidatePolygons: google.maps.Polygon[]
  ) {
    this.zone.run(() => {
      const dialogRef = this.dialog.open(
        SelectLocationOfInterestDialogComponent,
        {
          width: '500px',
          data: {
            clickedLocationsOfInterest: candidatePolygons.map(
              this.polygonToLocationOfInterestData
            ),
          },
        }
      );
      dialogRef.afterClosed().subscribe(result => {
        if (result) {
          this.navigationService.selectLocationOfInterest(result);
        }
      });
    });
  }

  private polygonToLocationOfInterestData(
    polygon: google.maps.Polygon
  ): LocationOfInterestData {
    return {
      loiId: polygon.get('id'),
      color: polygon.get('color'),
      layerName: polygon.get('layerName'),
    };
  }

  onSaveRepositionClick() {
    this.markerToReposition?.setPosition(this.newLatLng!);
    this.loiService.updatePoint(this.newLocationOfInterestToReposition!);
    this.resetReposition();
  }

  onCancelRepositionClick() {
    this.markerToReposition?.setPosition(this.oldLatLng!);
    this.resetReposition();
  }

  private resetReposition() {
    this.showRepositionConfirmDialog = false;
    this.newLocationOfInterestToReposition = undefined;
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
