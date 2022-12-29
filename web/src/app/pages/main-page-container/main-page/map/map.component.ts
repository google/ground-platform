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
import { Survey } from 'app/models/survey.model';
import { Point } from 'app/models/geometry/point';
import {
  LocationOfInterest,
  GeoJsonLocationOfInterest,
  AreaOfInterest,
  GenericLocationOfInterest,
} from 'app/models/loi.model';
import {
  DrawingToolsService,
  EditMode,
} from 'app/services/drawing-tools/drawing-tools.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { combineLatest, Observable, Subscription } from 'rxjs';
import { List, Map as ImmutableMap } from 'immutable';
import { getPinImageSource } from 'app/ground-pin';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { GoogleMap } from '@angular/google-maps';
import { MatDialog } from '@angular/material/dialog';
import {
  LocationOfInterestData,
  SelectLocationOfInterestDialogComponent,
} from './select-loi-dialog/select-loi-dialog.component';
import { Coordinate } from 'app/models/geometry/coordinate';

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
  newLocationOfInterestToReposition?: GenericLocationOfInterest;
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
        this.navigationService.getSubmissionId$(),
      ]).subscribe(() => this.cancelReposition())
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  async onMapClick(
    event: google.maps.MapMouseEvent | google.maps.IconMouseEvent
  ) {
    if (this.disableMapClicks) {
      return;
    }
    const editMode = this.drawingToolsService.getEditMode$().getValue();
    const selectedJobId = this.drawingToolsService.getSelectedJobId();
    switch (editMode) {
      case EditMode.AddPoint: {
        if (!selectedJobId) {
          return;
        }
        this.drawingToolsService.setEditMode(EditMode.None);
        const newLocationOfInterest = await this.loiService.addPoint(
          event.latLng!.lat(),
          event.latLng!.lng(),
          selectedJobId
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
    const newLoiIds: List<string> = newLocationsOfInterest.map(f => f.id);
    this.removeDeletedMarkers(newLoiIds);
    this.removeDeletedPolygons(newLoiIds);
  }

  private removeDeletedMarkers(newLoiIds: List<string>) {
    for (const id of this.markers.keys()) {
      if (!newLoiIds.contains(id)) {
        this.markers.get(id)!.setMap(null);
        this.markers.delete(id);
      }
    }
  }

  private removeDeletedPolygons(newLoiIds: List<string>) {
    for (const id of this.polygons.keys()) {
      if (!newLoiIds.contains(id)) {
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
      if (!survey.getJob(loi.jobId)) {
        // Ignore lois whose job has been removed.
        console.debug(`Ignoring loi ${loi.id} with missing job ${loi.jobId}`);
        return;
      }
      if (existingLocationOfInterestIds.includes(loi.id)) {
        return;
      }
      const color = survey.jobs.get(loi.jobId)?.color;
      const jobName = survey.jobs.get(loi.jobId)?.name;

      if (loi.geometry instanceof Point) {
        const { id, jobId, geometry } = loi;
        this.addPointOfInterest({ id, jobId, color, geometry });
      }
      if (loi instanceof GeoJsonLocationOfInterest) {
        this.addGeoJsonLocationOfInterest(color, jobName, loi);
      }
      if (loi instanceof AreaOfInterest) {
        this.addAreaOfInterest(color, jobName, loi);
      }
    });
  }

  private addPointOfInterest({
    id,
    jobId,
    geometry,
    color,
  }: {
    id: string;
    jobId: string;
    geometry: Point;
    color: string | undefined;
  }) {
    const { x: latitude, y: longitude } = geometry.coord;
    const icon = {
      url: getPinImageSource(color),
      scaledSize: {
        width: normalIconScale,
        height: normalIconScale,
      },
    } as google.maps.Icon;
    const options: google.maps.MarkerOptions = {
      map: this.map.googleMap,
      position: new google.maps.LatLng(latitude, longitude),
      icon,
      draggable: false,
      title: id,
    };
    const marker = new google.maps.Marker(options);
    marker.addListener('click', () => this.onMarkerClick(id));
    marker.addListener('dragstart', (event: google.maps.Data.MouseEvent) =>
      this.onMarkerDragStart(event, marker)
    );
    marker.addListener('dragend', (event: google.maps.Data.MouseEvent) =>
      this.onMarkerDragEnd(event, id, jobId)
    );
    this.markers.set(id, marker);
  }

  private onMarkerClick(loiId: string) {
    if (this.disableMapClicks) {
      return;
    }
    this.navigationService.selectLocationOfInterest(loiId);
  }

  private onMarkerDragStart(
    event: google.maps.Data.MouseEvent,
    marker: google.maps.Marker
  ) {
    // TODO: Show confirm dialog and disable other components when entering reposition state.
    // Currently we are figuring out how should the UI trigger this state.
    this.showRepositionConfirmDialog = true;
    this.disableMapClicks = true;
    this.drawingToolsService.setDisabled(true);
    this.markerToReposition = marker;
    this.oldLatLng = new google.maps.LatLng(
      event.latLng!.lat(),
      event.latLng!.lng()
    );
    this.changeDetectorRef.detectChanges();
  }

  private onMarkerDragEnd(
    event: google.maps.Data.MouseEvent,
    id: string,
    jobId: string
  ) {
    this.newLatLng = new google.maps.LatLng(
      event.latLng!.lat(),
      event.latLng!.lng()
    );

    this.newLocationOfInterestToReposition = new GenericLocationOfInterest(
      id,
      jobId,
      new Point(new Coordinate(event.latLng!.lat(), event.latLng!.lng())),
      ImmutableMap<string, string | number>()
    );
  }

  private panAndZoom(position: google.maps.LatLng | null | undefined) {
    if (!position) {
      return;
    }
    this.map.panTo(position);
    if (this.map.getZoom()! < zoomedInLevel) {
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
    const icon = marker.getIcon() as google.maps.Icon;
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
    jobName: string | undefined,
    loi: GeoJsonLocationOfInterest
  ) {
    const paths: google.maps.LatLng[][] = [];
    const job = new google.maps.Data();
    job.addGeoJson(loi.geoJson);
    job.forEach(f => {
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

    this.addPolygonToMap(loi.id, color, jobName, paths);
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
    jobName: string | undefined,
    loi: AreaOfInterest
  ) {
    const vertices: google.maps.LatLng[] = loi.polygonVertices.map(
      vertex => new google.maps.LatLng(vertex.latitude, vertex.longitude)
    );

    this.addPolygonToMap(loi.id, color, jobName, [vertices]);
  }

  private addPolygonToMap(
    loiId: string,
    color: string | undefined,
    jobName: string | undefined,
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
    polygon.set('jobName', jobName);
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
      if (google.maps.geometry.poly.containsLocation(event.latLng!, polygon)) {
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
      jobName: polygon.get('jobName'),
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
