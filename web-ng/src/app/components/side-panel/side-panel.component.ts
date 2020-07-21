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

import { Component, Input } from '@angular/core';
import { LoadingState } from '../../services/loading-state.model';
import { RouterService } from '../../services/router/router.service';
import { ActivatedRoute } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import { of, Observable } from 'rxjs';

@Component({
  selector: 'ground-side-panel',
  templateUrl: './side-panel.component.html',
  styleUrls: ['./side-panel.component.css'],
})
export class SidePanelComponent {
  readonly sideNavContentType$: Observable<SideNavContentType>;
  readonly loadingState = LoadingState;
  readonly lang: string;

  constructor(
    private routerService: RouterService,
    route: ActivatedRoute) {
    routerService.init(route);
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';

    this.sideNavContentType$ =
      routerService.getObservationId$().pipe(
        switchMap(observationId => {
          if (observationId) {
            return of(SideNavContentType.OBSERVATION);
          }
          return routerService.getFeatureId$().pipe(
            switchMap(featureId => {
              if (featureId) {
                return of(SideNavContentType.FEATURE);
              }
              return of(SideNavContentType.LAYER_LIST);
            })
          );
        })
      );
  }
}

export enum SideNavContentType {
  LAYER_LIST = 0,
  OBSERVATION = 1,
  FEATURE = 2,
}
