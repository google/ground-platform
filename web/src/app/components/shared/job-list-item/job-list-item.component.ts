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

import { CdkVirtualScrollViewport } from '@angular/cdk/scrolling';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
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

/** Height in px of an LOI row. Rows are fixed-height so they can be virtualized. */
const LOI_ROW_HEIGHT = 36;

/** Most rows the LOI list shows at once, before it scrolls. */
const LOI_LIST_MAX_ROWS = 12;

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

  readonly loiRowHeight = LOI_ROW_HEIGHT;

  /** Height of the LOI list: fits its rows, up to `LOI_LIST_MAX_ROWS`. */
  readonly loiListHeight = computed(
    () => Math.min(this.loiItems().length, LOI_LIST_MAX_ROWS) * LOI_ROW_HEIGHT
  );

  private readonly viewport = viewChild(CdkVirtualScrollViewport);

  constructor() {
    // The selected LOI may be one the list hasn't rendered (e.g. picked on the
    // map), so bring it into view.
    effect(() => {
      const loiId = this.loiId();
      const items = this.loiItems();
      const viewport = this.viewport();
      if (!loiId || !viewport) return;
      const index = items.findIndex(item => item.loi.id === loiId);
      if (index < 0) return;
      untracked(() => this.scrollToIndexIfHidden(viewport, index));
    });
  }

  trackByLoiId(_: number, item: LoiListItem): string {
    return item.loi.id;
  }

  private scrollToIndexIfHidden(
    viewport: CdkVirtualScrollViewport,
    index: number
  ) {
    const itemTop = index * LOI_ROW_HEIGHT;
    const itemBottom = itemTop + LOI_ROW_HEIGHT;
    const scrollOffset = viewport.measureScrollOffset();
    if (
      itemTop < scrollOffset ||
      itemBottom > scrollOffset + this.loiListHeight()
    )
      viewport.scrollToIndex(index);
  }

  toggleExpanded() {
    this.expanded.update(expanded => !expanded);
  }

  onGoBackClick() {
    this.navigationService.clearLocationOfInterestId();
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
