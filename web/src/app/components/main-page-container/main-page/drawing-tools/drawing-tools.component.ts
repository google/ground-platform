/**
 * Copyright 2020 The Ground Authors.
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
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  computed,
  effect,
  input,
} from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { List } from 'immutable';
import { Observable, Subscription } from 'rxjs';
import { map } from 'rxjs/operators';

import { Job } from 'app/models/job.model';
import { Survey } from 'app/models/survey.model';
import { AuthService } from 'app/services/auth/auth.service';
import {
  DrawingToolsService,
  EditMode,
} from 'app/services/drawing-tools/drawing-tools.service';
import { GroundPinService } from 'app/services/ground-pin/ground-pin.service';
import { NavigationService } from 'app/services/navigation/navigation.service';

@Component({
  selector: 'ground-drawing-tools',
  templateUrl: './drawing-tools.component.html',
  styleUrls: ['./drawing-tools.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: false,
})
export class DrawingToolsComponent implements OnInit, OnDestroy {
  private subscription: Subscription = new Subscription();
  survey = input<Survey>();
  pointValue = 'point';
  polygonValue = 'polygon';
  selectedValue = '';
  private lastSelectedValue = '';
  selectedJobId = '';

  readonly jobs = computed(() => {
    const survey = this.survey();
    if (!survey) return List<Job>();
    return List(survey.jobs.valueSeq().toArray())
      .sortBy(l => l.index)
      .filter(l => this.authService.canUserAddPointsToJob(survey, l));
  });

  readonly black = '#202225';
  readonly addPointIconBlack = this.sanitizer.bypassSecurityTrustUrl(
    this.groundPinService.getPinImageSource(this.black)
  );
  readonly green = '#3d7d40';
  readonly addPointIconGreen = this.sanitizer.bypassSecurityTrustUrl(
    this.groundPinService.getPinImageSource(this.green)
  );
  addPointIcon = this.addPointIconBlack;
  isSubmissionSelected$: Observable<boolean>;
  disabled$: Observable<boolean>;

  constructor(
    private readonly changeDetectorRef: ChangeDetectorRef,
    private drawingToolsService: DrawingToolsService,
    private sanitizer: DomSanitizer,
    private navigationService: NavigationService,
    private groundPinService: GroundPinService,
    private authService: AuthService
  ) {
    this.isSubmissionSelected$ = this.navigationService
      .getSubmissionId$()
      .pipe(map(obs => !!obs));
    this.disabled$ = drawingToolsService.getDisabled$();

    effect(() => {
      const survey = this.survey();
      if (survey) {
        this.selectedJobId = survey.jobs.keySeq().first();
        this.drawingToolsService.setSelectedJobId(this.selectedJobId);
      }
    });
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
      this.groundPinService.getPinImageSource(job?.color)
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
