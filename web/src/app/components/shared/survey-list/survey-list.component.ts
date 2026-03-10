/**
 * Copyright 2021 The Ground Authors.
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

import { Component, computed, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { List, Map } from 'immutable';

import {
  DialogType,
  JobDialogComponent,
} from 'app/components/edit-survey/job-dialog/job-dialog.component';
import {
  Survey,
  SurveyGeneralAccess,
  SurveyState,
} from 'app/models/survey.model';
import { AuthService } from 'app/services/auth/auth.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';

export enum SurveyListFilter {
  ALL,
  RESTRICTED,
  UNLISTED,
  PUBLIC,
}

@Component({
  selector: 'ground-survey-list',
  templateUrl: './survey-list.component.html',
  styleUrls: ['./survey-list.component.scss'],
  standalone: false,
})
export class SurveyListComponent {
  private authService = inject(AuthService);
  private dialog = inject(MatDialog);
  private navigationService = inject(NavigationService);
  private surveyService = inject(SurveyService);

  readonly SurveyListFilter = SurveyListFilter;
  readonly SurveyGeneralAccess = SurveyGeneralAccess;

  private allSurveys = toSignal(this.surveyService.getAccessibleSurveys$(), {
    initialValue: List<Survey>(),
  });

  currentFilter = signal<SurveyListFilter>(SurveyListFilter.ALL);

  surveys = computed(() => {
    const surveys = this.allSurveys();
    const filter = this.currentFilter();

    if (filter === SurveyListFilter.ALL) return surveys;

    return surveys.filter(s => {
      switch (filter) {
        case SurveyListFilter.RESTRICTED:
          return s.generalAccess === SurveyGeneralAccess.RESTRICTED;
        case SurveyListFilter.UNLISTED:
          return s.generalAccess === SurveyGeneralAccess.UNLISTED;
        case SurveyListFilter.PUBLIC:
          return s.generalAccess === SurveyGeneralAccess.PUBLIC;
        default:
          return true;
      }
    });
  });

  filterCounters = computed(() => {
    const list = this.allSurveys();

    const counts = {
      [SurveyGeneralAccess.RESTRICTED]: 0,
      [SurveyGeneralAccess.UNLISTED]: 0,
      [SurveyGeneralAccess.PUBLIC]: 0,
    };

    list.forEach(s => {
      if (s.generalAccess && s.generalAccess in counts)
        counts[s.generalAccess]++;
    });

    return Map([
      [SurveyListFilter.ALL, list.size],
      [SurveyListFilter.RESTRICTED, counts[SurveyGeneralAccess.RESTRICTED]],
      [SurveyListFilter.UNLISTED, counts[SurveyGeneralAccess.UNLISTED]],
      [SurveyListFilter.PUBLIC, counts[SurveyGeneralAccess.PUBLIC]],
    ]);
  });

  constructor() {}

  handleFilterSelection(newFilter: SurveyListFilter): void {
    this.currentFilter.set(newFilter);
  }

  handleSurveySelection(clickedSurvey: Survey): void {
    if (clickedSurvey.state === SurveyState.READY) {
      this.navigationService.selectSurvey(clickedSurvey.id);
    } else {
      this.navigationService.navigateToCreateSurvey(clickedSurvey.id);
    }
  }

  async createNewSurvey(): Promise<void> {
    const isPasslisted = await this.authService.isPasslisted();

    if (!isPasslisted) {
      const dialogRef = this.dialog.open(JobDialogComponent, {
        data: { dialogType: DialogType.SurveyCreationDenied },
        panelClass: 'small-width-dialog',
      });

      const result = await firstValueFrom(dialogRef.afterClosed());
      if (result) {
        this.navigationService.navigateToSubscriptionForm();
      }
      return;
    }

    this.navigationService.navigateToCreateSurvey(null);
  }
}
