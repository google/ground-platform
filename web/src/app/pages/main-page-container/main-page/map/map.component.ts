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
  OnChanges,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import {GoogleMap} from '@angular/google-maps';
import {Map as ImmutableMap, List} from 'immutable';
import {BehaviorSubject, Observable, Subscription, combineLatest} from 'rxjs';

import {Coordinate} from 'app/models/geometry/coordinate';
import {Geometry, GeometryType} from 'app/models/geometry/geometry';
import {MultiPolygon} from 'app/models/geometry/multi-polygon';
import {Point} from 'app/models/geometry/point';
import {Polygon} from 'app/models/geometry/polygon';
import {Job} from 'app/models/job.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Submission} from 'app/models/submission/submission.model';
import {Survey} from 'app/models/survey.model';
import {TaskType} from 'app/models/task/task.model';
import {
  DrawingToolsService,
  EditMode,
} from 'app/services/drawing-tools/drawing-tools.service';
import {GroundPinService} from 'app/services/ground-pin/ground-pin.service';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SubmissionService} from 'app/services/submission/submission.service';
import {SurveyService} from 'app/services/survey/survey.service';

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
export class MapComponent implements AfterViewInit, OnChanges, OnDestroy {
  private subscription: Subscription = new Subscription();
  private selectedJob$: BehaviorSubject<Job | undefined> = new BehaviorSubject<
    Job | undefined
  >(undefined);
  lois$: Observable<List<LocationOfInterest>>;
  loisMap: ImmutableMap<string, LocationOfInterest> = ImmutableMap();
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
  newLocationOfInterestToReposition?: LocationOfInterest;
  oldLatLng?: google.maps.LatLng;
  newLatLng?: google.maps.LatLng;
  markerToReposition?: google.maps.Marker;
  disableMapClicks = false;
  lastFitSurveyId = '';
  lastFitJobId = '';
  submission: Submission | null = null;
  showSubmissionGeometry = false;

  readonly DEFAULT_MARKER_COLOR = 'black';

  @ViewChild(GoogleMap) map!: GoogleMap;

  @Input() shouldEnableDrawingTools = false;
  @Input() showPredefinedLoisOnly = false;
  @Input() selectedJob: Job | undefined = undefined;

  constructor(
    private drawingToolsService: DrawingToolsService,
    private surveyService: SurveyService,
    private loiService: LocationOfInterestService,
    private navigationService: NavigationService,
    private groundPinService: GroundPinService,
    private submissionService: SubmissionService,
    private zone: NgZone,
    private changeDetectorRef: ChangeDetectorRef
  ) {
    this.lois$ = this.loiService.getLocationsOfInterest$();
    this.activeSurvey$ = this.surveyService.getActiveSurvey$();
  }

  ngOnChanges() {
    this.selectedJob$.next(this.selectedJob);
  }

  ngAfterViewInit() {
    this.subscription.add(
      combineLatest([
        this.activeSurvey$,
        this.lois$,
        this.navigationService.getLocationOfInterestId$(),
        this.selectedJob$,
      ]).subscribe(([survey, lois, locationOfInterestId, selectedJob]) => {
        const loisMap = ImmutableMap(
          lois
            .filter(loi =>
              this.showPredefinedLoisOnly ? loi.predefined : true
            )
            .filter(
              loi => selectedJob === undefined || loi.jobId === selectedJob.id
            )
            .map(loi => [loi.id, loi])
        );

        const loiIdsToRemove = this.loisMap
          .filter(
            (value, key) =>
              !(
                loisMap.has(key) &&
                this.isLocationOfInterestEqual(loisMap.get(key)!, value)
              )
          )
          .keySeq()
          .toList();
        const loisToAdd = loisMap
          .filter(
            (value, key) =>
              !(
                this.loisMap.has(key) &&
                this.isLocationOfInterestEqual(this.loisMap.get(key)!, value)
              )
          )
          .toList();
        this.loisMap = loisMap;

        this.removeDeletedLocationsOfInterest(loiIdsToRemove);
        this.addNewLocationsOfInterest(survey, loisToAdd);
        if (
          this.lastFitSurveyId !== survey.id ||
          this.lastFitJobId !== (selectedJob?.id ?? '')
        ) {
          this.fitMapToLocationsOfInterest(List(this.loisMap.values()));
          this.lastFitSurveyId = survey.id;
          this.lastFitJobId = selectedJob?.id ?? '';
        }
        this.selectLocationOfInterest(locationOfInterestId);
      })
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
        this.submissionService.getSelectedSubmission$(),
      ]).subscribe(([, submissionId, submission]) => {
        this.showSubmissionGeometry = !!submissionId;
        if (submission instanceof Submission) {
          this.submission = submission;
          this.removeSubmissionResultsOnMap();
          if (this.showSubmissionGeometry) {
            this.addSubmissionResultsOnMap();
          }
        }
        this.cancelReposition();
      })
    );
  }

  /**
   * Add submission geometry based task results to existing map
   */
  addSubmissionResultsOnMap() {
    this.submission?.job?.tasks?.forEach(task => {
      // TODO(#1477): Add geometry annotations in map as well
      if (
        task.type === TaskType.DRAW_AREA ||
        task.type === TaskType.DROP_PIN ||
        task.type === TaskType.CAPTURE_LOCATION
      ) {
        const taskResult = this.submission?.data.get(task.id);
        const geometryType = (taskResult?.value as Geometry)?.geometryType;
        if (geometryType === GeometryType.POINT) {
          const marker = this.addSubmissionMarkerToMap(
            task.id,
            taskResult!.value as Point,
            this.submission?.job?.color
          );
          this.markers.set(task.id, marker);
        } else if (geometryType === GeometryType.POLYGON) {
          const polygon = this.addSubmissionPolygonToMap(
            task.id,
            taskResult!.value as Polygon,
            this.submission?.job?.color
          );
          this.polygons.set(task.id, [polygon]);
        }
      }
    });
  }

  /**
   * Remove submission geometry based task results from existing map
   */
  removeSubmissionResultsOnMap() {
    let idsToRemove = List<string>();
    this.submission?.job?.tasks?.forEach(task => {
      if (
        task.type === TaskType.DRAW_AREA ||
        task.type === TaskType.DROP_PIN ||
        task.type === TaskType.CAPTURE_LOCATION
      ) {
        idsToRemove = idsToRemove.push(task.id);
      }
    });
    this.removeDeletedLocationsOfInterest(idsToRemove);
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

  // TODO(#1445): override equals function in LocationOfInterest after making it concrete and removing all its inherent classes
  private isLocationOfInterestEqual(
    a: LocationOfInterest,
    b: LocationOfInterest
  ): boolean {
    return JSON.stringify(a) === JSON.stringify(b);
  }

  /**
   * Remove deleted lois to map. The LOIs that were displayed on
   * the map but not in the `newLocationsOfInterest` are considered as deleted.
   */
  private removeDeletedLocationsOfInterest(idsToRemove: List<string>) {
    this.removeDeletedMarkers(idsToRemove);
    this.removeDeletedPolygons(idsToRemove);
  }

  private removeDeletedMarkers(idsToRemove: List<string>) {
    for (const id of this.markers.keys()) {
      if (idsToRemove.includes(id)) {
        this.markers.get(id)!.setMap(null);
        this.markers.delete(id);
      }
    }
  }

  private removeDeletedPolygons(idsToRemove: List<string>) {
    for (const id of this.polygons.keys()) {
      if (idsToRemove.includes(id)) {
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
    loisToAdd: List<LocationOfInterest>
  ) {
    loisToAdd.forEach(loi => {
      if (!this.showPredefinedLoisOnly && !survey.getJob(loi.jobId)) {
        // Ignore lois whose job has been removed.
        console.debug(`Ignoring loi ${loi.id} with missing job ${loi.jobId}`);
        return;
      }
      const color = this.showPredefinedLoisOnly
        ? this.selectedJob?.color
        : survey.getJob(loi.jobId)?.color;
      const jobName = this.showPredefinedLoisOnly
        ? this.selectedJob?.name
        : survey.getJob(loi.jobId)?.name;

      if (loi.geometry instanceof Point) {
        const {id, jobId, geometry} = loi;
        const marker = this.addLocationOfInterestMarkerToMap(
          id,
          jobId,
          geometry,
          color
        );
        this.markers.set(id, marker);
      }
      if (loi.geometry instanceof Polygon) {
        const polygon = this.addLocationOfInterestPolygonToMap(
          loi.id,
          jobName,
          loi.geometry,
          color
        );
        this.polygons.set(loi.id, [polygon]);
      }
      if (loi.geometry instanceof MultiPolygon) {
        const geometry: MultiPolygon = loi.geometry;
        const polygons: google.maps.Polygon[] = [];
        for (const polygon of geometry.polygons) {
          polygons.push(
            this.addLocationOfInterestPolygonToMap(
              loi.id,
              jobName,
              polygon,
              color
            )
          );
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

  /**
   * Adds new marker to existing map
   */
  private addMarkerToMap(
    id: string,
    geometry: Point,
    color: string | undefined
  ): google.maps.Marker {
    const {y: latitude, x: longitude} = geometry.coord;
    // Default color on Google Maps marker is red if unspecified
    if (color === undefined) {
      color = this.DEFAULT_MARKER_COLOR;
    }
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

    return new google.maps.Marker(options);
  }

  /**
   * Adds new marker that represents a location of interest to existing map
   */
  private addLocationOfInterestMarkerToMap(
    loiId: string,
    jobId: string,
    geometry: Point,
    color: string | undefined
  ): google.maps.Marker {
    const marker = this.addMarkerToMap(loiId, geometry, color);
    marker.addListener('click', () =>
      this.onLocationOfInterestMarkerClick(loiId)
    );
    if (this.shouldEnableDrawingTools) {
      marker.addListener('dragstart', (event: google.maps.Data.MouseEvent) =>
        this.onMarkerDragStart(event, marker)
      );
      marker.addListener('dragend', (event: google.maps.Data.MouseEvent) =>
        this.onMarkerDragEnd(event, loiId, jobId)
      );
    }
    return marker;
  }

  /**
   * Adds new marker that represents a geometry based task submission to existing map
   */
  private addSubmissionMarkerToMap(
    taskId: string,
    geometry: Point,
    color: string | undefined
  ): google.maps.Marker {
    const marker = this.addMarkerToMap(taskId, geometry, color);
    marker.addListener('click', () => this.onSubmissionGeometryClick(taskId));
    return marker;
  }

  private onLocationOfInterestMarkerClick(loiId: string) {
    if (this.disableMapClicks) {
      return;
    }
    this.navigationService.selectLocationOfInterest(loiId);
  }

  private onSubmissionGeometryClick(taskId: string) {
    if (this.disableMapClicks) {
      return;
    }
    this.navigationService.showSubmissionDetailWithHighlightedTask(taskId);
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

    this.newLocationOfInterestToReposition = new LocationOfInterest(
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
      this.map.googleMap?.setZoom(zoomedInLevel);
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
    this.unselectMarker();
    this.selectMarker(locationOfInterestId);
    this.unselectPolygons();
    this.selectPolygons(locationOfInterestId);
    this.selectedLocationOfInterestId = locationOfInterestId;
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

  private selectMarker(locationOfInterestId: string | null) {
    if (!locationOfInterestId) return;

    const marker = this.markers.get(locationOfInterestId);

    if (!marker) return;

    this.setIconSize(marker, enlargedIconScale);

    marker.setDraggable(this.shouldEnableDrawingTools);

    this.panAndZoom(marker.getPosition());
  }

  private unselectMarker() {
    if (!this.selectedLocationOfInterestId) return;

    const selectedMarker = this.markers.get(this.selectedLocationOfInterestId);

    if (!selectedMarker) return;

    this.setIconSize(selectedMarker, normalIconScale);

    selectedMarker.setDraggable(false);
  }

  private selectPolygons(locationOfInterestId: string | null) {
    if (!locationOfInterestId) return;

    const polygons = this.polygons.get(locationOfInterestId);

    polygons?.forEach(polygon =>
      polygon.setOptions({strokeWeight: enlargedPolygonStrokeWeight})
    );

    this.fitMapToLocationsOfInterest(
      this.getLoisByIds(List([locationOfInterestId]))
    );
  }

  private unselectPolygons() {
    if (!this.selectedLocationOfInterestId) return;

    const selectedPolygons = this.polygons.get(
      this.selectedLocationOfInterestId
    );

    selectedPolygons?.forEach(polygon =>
      polygon.setOptions({
        strokeWeight: normalPolygonStrokeWeight,
      })
    );
  }

  /**
   * Adds new polygon to existing map
   */
  private addPolygonToMap(
    polygonModel: Polygon,
    color: string | undefined
  ): google.maps.Polygon {
    const linearRings = [polygonModel.shell, ...polygonModel.holes];
    const paths = linearRings.map(linearRing =>
      linearRing.points
        .map(({x, y}: {x: number; y: number}) => new google.maps.LatLng(y, x))
        .toJS()
    );
    return new google.maps.Polygon({
      paths: paths,
      clickable: true,
      strokeColor: color,
      strokeOpacity: 1,
      strokeWeight: normalPolygonStrokeWeight,
      fillOpacity: 0,
      map: this.map.googleMap,
    });
  }

  /**
   * Adds new polygon that represents a location of interest to existing map
   */
  private addLocationOfInterestPolygonToMap(
    loiId: string,
    jobName: string | undefined,
    polygonModel: Polygon,
    color: string | undefined
  ): google.maps.Polygon {
    const polygon = this.addPolygonToMap(polygonModel, color);
    polygon.set('id', loiId);
    polygon.set('color', color);
    polygon.set('jobName', jobName);
    polygon.addListener('click', (event: google.maps.PolyMouseEvent) => {
      this.onLocationOfInterestPolygonClick(event);
    });
    return polygon;
  }

  /**
   * Adds new polygon that represents a geometry based task submission to existing map
   */
  private addSubmissionPolygonToMap(
    taskId: string,
    polygonModel: Polygon,
    color: string | undefined
  ): google.maps.Polygon {
    const polygon = this.addPolygonToMap(polygonModel, color);
    polygon.set('id', taskId);
    polygon.set('color', color);
    polygon.addListener('click', () => this.onSubmissionGeometryClick(taskId));
    return polygon;
  }

  private getIntersectingPolygons(
    latLng: google.maps.LatLng
  ): List<google.maps.Polygon> {
    return ImmutableMap(this.polygons)
      .toList()
      .flatMap((polygons: google.maps.Polygon[]) =>
        polygons.filter(p =>
          google.maps.geometry.poly.containsLocation(latLng, p)
        )
      );
  }

  private getLoisByIds(ids: List<string>): List<LocationOfInterest> {
    return ids.map(id => this.loisMap.get(id)!);
  }

  private onLocationOfInterestPolygonClick(event: google.maps.PolyMouseEvent) {
    if (this.disableMapClicks) {
      return;
    }

    const candidatePolygonsIds = this.getIntersectingPolygons(
      event.latLng!
    ).map(p => p.get('id'));

    const candidateLois = this.getLoisByIds(candidatePolygonsIds);

    const loi = LocationOfInterest.getSmallestByArea(candidateLois);

    this.zone.run(() => {
      if (loi) this.navigationService.selectLocationOfInterest(loi.id);
    });
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
