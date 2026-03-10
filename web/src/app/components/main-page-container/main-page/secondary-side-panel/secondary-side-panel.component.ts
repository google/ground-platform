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
import { List } from 'immutable';

import { LocationOfInterest } from 'app/models/loi.model';
import { Survey } from 'app/models/survey.model';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SideNavMode } from 'app/services/navigation/url-params';

@Component({
  selector: 'ground-secondary-side-panel',
  templateUrl: './secondary-side-panel.component.html',
  styleUrls: ['./secondary-side-panel.component.css'],
  standalone: false,
})
export class SecondarySidePanelComponent {
  private navigationService = inject(NavigationService);

  activeSurvey = input<Survey>();
  lois = input<List<LocationOfInterest>>();

  loiIdSignal = this.navigationService.getLoiId();
  submissionIdSignal = this.navigationService.getSubmissionId();
  sideNavModeSignal = this.navigationService.getSideNavMode();

  SideNavMode = SideNavMode;

  readonly selectedLoi = computed(() => {
    const id = this.loiIdSignal();
    return this.lois()?.find(l => l.id === id);
  });

  constructor() {}
}
