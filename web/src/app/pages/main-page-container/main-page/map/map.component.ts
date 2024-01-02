/**
 * Copyright 2019 The Ground Authors.
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
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  Input,
  NgZone,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import {GoogleMap} from '@angular/google-maps';
import {MatLegacyDialog as MatDialog} from '@angular/material/legacy-dialog';
import {Map as ImmutableMap, List} from 'immutable';
import {Observable, Subscription, combineLatest} from 'rxjs';

import {Coordinate} from 'app/models/geometry/coordinate';
import {MultiPolygon} from 'app/models/geometry/multi-polygon';
import {Point} from 'app/models/geometry/point';
import {Polygon} from 'app/models/geometry/polygon';
import {
  GenericLocationOfInterest,
  LocationOfInterest,
} from 'app/models/loi.model';
import {Survey} from 'app/models/survey.model';
import {
  DrawingToolsService,
  EditMode,
} from 'app/services/drawing-tools/drawing-tools.service';
import {GroundPinService} from 'app/services/ground-pin/ground-pin.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';

import {
  LocationOfInterestData,
  SelectLocationOfInterestDialogComponent,
} from './select-loi-dialog/select-loi-dialog.component';

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
    mapTypeControl: true,
    streetViewControl: false,
    mapTypeId: google.maps.MapTypeId.HYBRID,
  };
  private selectedLocationOfInterestId: string | null = null;
  markers: Map<string, google.maps.Marker> = new Map<
    string,
    google.maps.Marker
  >();
  polygons: Map<string, google.maps.Polygon[]> = new Map<
    string,
    google.maps.Polygon[]
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

  @Input() shouldEnableDrawingTools = false;

  constructor(
    private drawingToolsService: DrawingToolsService,
    private surveyService: SurveyService,
    private loiService: LocationOfInterestService,
    private navigationService: NavigationService,
    private groundPinService: GroundPinService,
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
      ]).subscribe(([survey, lois, locationOfInterestId]) =>
        this.onSurveyAndLocationsOfInterestUpdate(
          survey,
          lois,
          locationOfInterestId
        )
      )
    );

    if (this.shouldEnableDrawingTools) {
      this.subscription.add(
        this.drawingToolsService
          .getEditMode$()
          .subscribe(editMode => this.onEditModeChange(editMode))
      );
    }

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
    const editMode = this.shouldEnableDrawingTools
      ? this.drawingToolsService.getEditMode$().getValue()
      : EditMode.None;
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
    locationOfInterestId: string | null
  ): void {
    this.removeDeletedLocationsOfInterest(lois);
    this.addNewLocationsOfInterest(survey, lois);
    this.fitMapToLocationsOfInterest(lois);
    this.selectLocationOfInterest(locationOfInterestId);
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
        for (const polygon of this.polygons.get(id)!) {
          polygon.setMap(null);
        }
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
        const {id, jobId, geometry} = loi;
        const marker = this.addPointOfInterestToMap({
          id,
          jobId,
          color,
          geometry,
        });
        this.markers.set(id, marker);
      }
      if (loi.geometry instanceof Polygon) {
        const polygon = this.addPolygonToMap(
          loi.id,
          color,
          jobName,
          loi.geometry
        );
        this.polygons.set(loi.id, [polygon]);
      }
      if (loi.geometry instanceof MultiPolygon) {
        const geometry: MultiPolygon = loi.geometry;
        const polygons: google.maps.Polygon[] = [];
        for (const polygon of geometry.polygons) {
          polygons.push(this.addPolygonToMap(loi.id, color, jobName, polygon));
        }
        this.polygons.set(loi.id, polygons);
      }
    });
  }

  private fitMapToLocationsOfInterest(lois: List<LocationOfInterest>) {
    const bounds = LocationOfInterestService.getLatLngBoundsFromLois(
      lois.toArray()
    );
    if (bounds) {
      this.map.fitBounds(bounds);
    }
  }

  private addPointOfInterestToMap({
    id,
    jobId,
    geometry,
    color,
  }: {
    id: string;
    jobId: string;
    geometry: Point;
    color: string | undefined;
  }): google.maps.Marker {
    const {y: latitude, x: longitude} = geometry.coord;
    const icon = {
      url: this.groundPinService.getPinImageSource(color),
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
    if (this.shouldEnableDrawingTools) {
      marker.addListener('dragstart', (event: google.maps.Data.MouseEvent) =>
        this.onMarkerDragStart(event, marker)
      );
      marker.addListener('dragend', (event: google.maps.Data.MouseEvent) =>
        this.onMarkerDragEnd(event, id, jobId)
      );
    }
    return marker;
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
      new Point(new Coordinate(event.latLng!.lng(), event.latLng!.lat())),
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
   * Selecting LOI enlarges the marker or border of the polygon(s),
   * pans and zooms to the marker/polygon(s). Selecting null is considered
   * as deselecting which will change the selected back to normal size.
   */
  private selectLocationOfInterest(locationOfInterestId: string | null) {
    if (locationOfInterestId === this.selectedLocationOfInterestId) {
      return;
    }
    this.selectMarker(locationOfInterestId);
    this.selectPolygons(locationOfInterestId);
    this.selectedLocationOfInterestId = locationOfInterestId;
  }

  private selectMarker(locationOfInterestId: string | null) {
    const marker = locationOfInterestId
      ? this.markers.get(locationOfInterestId)
      : undefined;
    if (marker) {
      this.setIconSize(marker, enlargedIconScale);
      if (this.shouldEnableDrawingTools) {
        marker.setDraggable(true);
      }
    }
    const selectedMarker = this.selectedLocationOfInterestId
      ? this.markers.get(this.selectedLocationOfInterestId)
      : undefined;
    if (selectedMarker) {
      this.setIconSize(selectedMarker, normalIconScale);
      selectedMarker.setDraggable(false);
    }
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

  private selectPolygons(locationOfInterestId: string | null) {
    const polygons = locationOfInterestId
      ? this.polygons.get(locationOfInterestId)
      : undefined;
    if (polygons) {
      for (const polygon of polygons) {
        polygon.setOptions({strokeWeight: enlargedPolygonStrokeWeight});
      }
    }
    const selectedPolygons = this.selectedLocationOfInterestId
      ? this.polygons.get(this.selectedLocationOfInterestId)
      : undefined;
    if (selectedPolygons) {
      for (const polygon of selectedPolygons)
        polygon.setOptions({
          strokeWeight: normalPolygonStrokeWeight,
        });
    }
  }

  private addPolygonToMap(
    loiId: string,
    color: string | undefined,
    jobName: string | undefined,
    polygonModel: Polygon
  ): google.maps.Polygon {
    const linearRings = [polygonModel.shell, ...polygonModel.holes];
    const paths = linearRings.map(linearRing =>
      linearRing.points
        .map(({x, y}: {x: number; y: number}) => new google.maps.LatLng(y, x))
        .toJS()
    );
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
    return polygon;
  }

  private onPolygonClick(event: google.maps.PolyMouseEvent) {
    if (this.disableMapClicks) {
      return;
    }
    const candidatePolygons: google.maps.Polygon[] = [];
    for (const loiPolygons of this.polygons.values()) {
      for (const polygon of loiPolygons) {
        if (
          google.maps.geometry.poly.containsLocation(event.latLng!, polygon)
        ) {
          candidatePolygons.push(polygon);
        }
      }
    }
\
    const lois = candidatePolygons.map(p => this.getLoiById(p.get('id')));
    return LocationOfInterest.getSmallest(lois);
    // this.openSelectLocationOfInterestDialog(candidatePolygons);
  }

  private openSelectLocationOfInterestDialog(
    candidatePolygons: google.maps.Polygon[]
  ) {
    // TODO: Account for case where polygons in multipolygon overlap.
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
