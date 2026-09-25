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
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { List } from 'immutable';

import { Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { AuthService } from 'app/services/auth/auth.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { getLoiIcon } from 'app/utils/utils';
import { environment } from 'environments/environment';

/** An LOI row, with the values its template needs precomputed. */
interface LoiListItem {
  loi: LocationOfInterest;
  name: string;
  iconName: string;
}

@Component({
  selector: 'ground-job-list-item',
  templateUrl: './job-list-item.component.html',
  styleUrls: ['./job-list-item.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: false,
})
export class JobListItemComponent {
  private authService = inject(AuthService);
  private navigationService = inject(NavigationService);
  private urlParamsSignal = this.navigationService.getUrlParams();

  job = input.required<Job>();
  lois = input<List<LocationOfInterest>>(List());
  actionsType = input<JobListItemActionsType>(JobListItemActionsType.MENU);

  readonly jobListItemActionsType = JobListItemActionsType;

  readonly surveyId = computed(() => this.urlParamsSignal().surveyId);
  readonly loiId = computed(() => this.urlParamsSignal().loiId);

  readonly expanded = signal(false);

  readonly hasLois = computed(() => this.lois().size > 0);

  readonly loiItems = computed<LoiListItem[]>(() =>
    this.lois()
      .map(loi => ({
        loi,
        name: LocationOfInterestService.getDisplayName(loi),
        iconName: getLoiIcon(loi),
      }))
      .toArray()
  );

  toggleExpanded() {
    this.expanded.update(expanded => !expanded);
  }

  onGoBackClick() {
    this.navigationService.clearLocationOfInterestId();
  }

  onClose() {
    return this.navigationService.selectSurvey(this.surveyId()!);
  }

  async onDownloadCsvClick() {
    await this.authService.createSessionCookie();
    window.open(
      `${environment.cloudFunctionsUrl}/exportCsv?` +
        `survey=${this.surveyId()}&job=${this.job().id}`,
      '_blank'
    );
  }

  async onDownloadGeoJsonClick() {
    await this.authService.createSessionCookie();
    window.open(
      `${environment.cloudFunctionsUrl}/exportGeojson?` +
        `survey=${this.surveyId()}&job=${this.job().id}`,
      '_blank'
    );
  }

  selectLoi(loi: LocationOfInterest) {
    const surveyId = this.surveyId();
    if (surveyId) {
      this.navigationService.selectLocationOfInterest(surveyId, loi.id);
    }
  }

  isSidePanelExpanded() {
    return this.navigationService.getSidePanelExpanded();
  }
}

export enum JobListItemActionsType {
  MENU = 1,
  BACK = 2,
}
