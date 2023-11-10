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

import {Component} from '@angular/core';
import {Observable} from 'rxjs';

import {
  NavigationService,
  SideNavMode,
} from 'app/services/navigation/navigation.service';

@Component({
  selector: 'ground-secondary-side-panel',
  templateUrl: './secondary-side-panel.component.html',
  styleUrls: ['./secondary-side-panel.component.css'],
})
export class SecondarySidePanelComponent {
  readonly sideNavMode = SideNavMode;
  readonly sideNavMode$: Observable<SideNavMode>;
  locationOfInterestId = '';
  submissionId = '';

  constructor(private navigationService: NavigationService) {
    this.navigationService
      .getLocationOfInterestId$()
      .subscribe(id => (this.locationOfInterestId = id || ''));
    this.navigationService
      .getSubmissionId$()
      .subscribe(id => (this.submissionId = id || ''));
    this.sideNavMode$ = navigationService.getSideNavMode$();
  }
}
