/**
 * Copyright 2023 The Ground Authors.
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

import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatDialog } from '@angular/material/dialog';
import { List } from 'immutable';
import { combineLatest, concat, delay, of, switchMap } from 'rxjs';

import { LoiPropertiesDialogComponent } from 'app/components/shared/loi-properties-dialog/loi-properties-dialog.component';
import { LocationOfInterest } from 'app/models/loi.model';
import { Survey } from 'app/models/survey.model';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SubmissionService } from 'app/services/submission/submission.service';
import { getLoiIcon } from 'app/utils/utils';

@Component({
  selector: 'ground-loi-panel',
  templateUrl: './loi-panel.component.html',
  styleUrls: ['./loi-panel.component.scss'],
  standalone: false,
})
export class LocationOfInterestPanelComponent {
  private submissionService = inject(SubmissionService);
  private navigationService = inject(NavigationService);

  activeSurvey = input<Survey>();
  lois = input<List<LocationOfInterest>>();
  loiId = input<string>();

  readonly isLoading = computed(() => {
    return this.submissions() === undefined;
  });

  readonly selectedLoi = computed(() =>
    this.lois()?.find(l => l.id === this.loiId())
  );

  readonly name = computed(() => {
    const loi = this.selectedLoi();
    return loi ? LocationOfInterestService.getDisplayName(loi) : null;
  });

  readonly icon = computed(() => {
    const loi = this.selectedLoi();
    return loi ? getLoiIcon(loi) : '';
  });

  readonly iconColor = computed(() => {
    const survey = this.activeSurvey();
    const loi = this.selectedLoi();
    if (!survey || !loi) return '';
    return survey.getJob(loi.jobId)?.color ?? '';
  });

  constructor(private dialog: MatDialog) {}

  submissions = toSignal(
    combineLatest([
      toObservable(this.activeSurvey),
      toObservable(this.selectedLoi),
    ]).pipe(
      switchMap(([survey, loi]) => {
        if (survey && loi) {
          return concat(
            of(undefined),
            this.submissionService.getSubmissions$(survey, loi).pipe(delay(100))
          );
        }
        return of(null).pipe(delay(100));
      })
    ),
    { initialValue: undefined }
  );

  onSelectSubmission(submissionId: string): void {
    const survey = this.activeSurvey();
    const loi = this.selectedLoi();

    if (!survey || !loi) {
      console.error('Missing survey or LOI');
      return;
    }

    this.navigationService.showSubmissionDetail(
      survey.id,
      loi.id,
      submissionId
    );
  }

  onClosePanel(): void {
    this.navigationService.clearLocationOfInterestId();
  }

  hasProperties(): boolean {
    return !!this.selectedLoi()?.properties?.size;
  }

  openPropertiesDialog(event: Event): void {
    event.stopPropagation();
    const loi = this.selectedLoi();
    if (!loi) return;
    this.dialog.open(LoiPropertiesDialogComponent, {
      width: '580px',
      height: '70%',
      autoFocus: false,
      data: {
        iconColor: this.iconColor,
        iconName: this.icon,
        loiDisplayName: this.name,
        properties: loi.properties.toObject(),
      },
      panelClass: 'loi-properties-dialog-container',
    });
  }
}
