/**
 * Copyright 2020 Google LLC
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
  ChangeDetectionStrategy,
  OnDestroy,
  OnInit,
  ChangeDetectorRef,
} from '@angular/core';
import {
  DrawingToolsService,
  EditMode,
} from '../../services/drawing-tools/drawing-tools.service';
import { ProjectService } from '../../services/project/project.service';
import { Observable, Subscription } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Job } from '../../shared/models/job.model';
import { List } from 'immutable';
import { map } from 'rxjs/internal/operators/map';
import { getPinImageSource } from '../map/ground-pin';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { NavigationService } from '../../services/navigation/navigation.service';
import { AuthService } from '../../services/auth/auth.service';
import { Project } from '../../shared/models/project.model';

@Component({
  selector: 'ground-drawing-tools',
  templateUrl: './drawing-tools.component.html',
  styleUrls: ['./drawing-tools.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DrawingToolsComponent implements OnInit, OnDestroy {
  private subscription: Subscription = new Subscription();
  pointValue = 'point';
  polygonValue = 'polygon';
  selectedValue = '';
  private lastSelectedValue = '';
  selectedJobId = '';
  private activeProject!: Project;
  readonly jobs$: Observable<List<Job>>;
  readonly lang: string;
  readonly black = '#202225';
  readonly addPointIconBlack = this.sanitizer.bypassSecurityTrustUrl(
    getPinImageSource(this.black)
  );
  readonly green = '#3d7d40';
  readonly addPointIconGreen = this.sanitizer.bypassSecurityTrustUrl(
    getPinImageSource(this.green)
  );
  addPointIcon = this.addPointIconBlack;
  isObservationSelected$: Observable<boolean>;
  disabled$: Observable<boolean>;

  constructor(
    private readonly changeDetectorRef: ChangeDetectorRef,
    private drawingToolsService: DrawingToolsService,
    private sanitizer: DomSanitizer,
    private navigationService: NavigationService,
    projectService: ProjectService,
    authService: AuthService
  ) {
    this.isObservationSelected$ = this.navigationService
      .getObservationId$()
      .pipe(map(obs => !!obs));
    this.disabled$ = drawingToolsService.getDisabled$();
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';
    this.jobs$ = projectService.getActiveProject$().pipe(
      tap(project => {
        this.activeProject = project;
        this.selectedJobId = project.jobs.keySeq().first();
        this.drawingToolsService.setSelectedJobId(this.selectedJobId);
      }),
      map(project =>
        List(project.jobs.valueSeq().toArray())
          .sortBy(l => l.index)
          .filter(l =>
            authService.canUserAddPointsToJob(this.activeProject, l)
          )
      )
    );
  }

  ngOnInit() {
    this.subscription.add(
      this.drawingToolsService
        .getEditMode$()
        .subscribe(editMode => this.onEditModeChange(editMode))
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  onButtonClick() {
    if (this.lastSelectedValue === this.selectedValue) {
      this.selectedValue = '';
      this.drawingToolsService.setEditMode(EditMode.None);
    } else if (this.selectedValue === this.pointValue) {
      this.drawingToolsService.setEditMode(EditMode.AddPoint);
    } else if (this.selectedValue === this.polygonValue) {
      this.drawingToolsService.setEditMode(EditMode.AddPolygon);
    }
    this.lastSelectedValue = this.selectedValue;
  }

  jobPinUrl(job: Job): SafeUrl {
    return this.sanitizer.bypassSecurityTrustUrl(
      getPinImageSource(job?.color)
    );
  }

  onJobIdChange() {
    this.drawingToolsService.setSelectedJobId(this.selectedJobId);
  }

  onCancel() {
    this.drawingToolsService.setEditMode(EditMode.None);
  }

  onEditModeChange(editMode: EditMode) {
    switch (editMode) {
      case EditMode.AddPoint:
        this.selectedValue = this.pointValue;
        this.addPointIcon = this.addPointIconGreen;
        break;
      case EditMode.AddPolygon:
        this.selectedValue = this.polygonValue;
        this.addPointIcon = this.addPointIconBlack;
        break;
      case EditMode.None:
      default:
        this.selectedValue = '';
        this.lastSelectedValue = '';
        this.addPointIcon = this.addPointIconBlack;
        break;
    }
    this.changeDetectorRef.detectChanges();
    return;
  }
}
