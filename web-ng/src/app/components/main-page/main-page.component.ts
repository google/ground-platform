/**
 * Copyright 2019 Google LLC
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

import {ActivatedRoute} from '@angular/router';
import {Component, OnInit} from '@angular/core';
import {HttpParams} from '@angular/common/http';
import {LayerDialogComponent} from '../layer-dialog/layer-dialog.component';
import {MatDialog} from '@angular/material/dialog';
import {Observable, Subscription} from 'rxjs';
import {Project} from '../../shared/models/project.model';
import {FeatureService} from '../../services/feature/feature.service';
import {ProjectService} from '../../services/project/project.service';

@Component({
  selector: 'ground-main-page',
  templateUrl: './main-page.component.html',
  styleUrls: ['./main-page.component.css'],
})
export class MainPageComponent implements OnInit {
  activeProject$: Observable<Project>;
  subscription: Subscription = new Subscription();
  sideNavOpened: boolean;
  constructor(
    private route: ActivatedRoute,
    private projectService: ProjectService,
    private featureService: FeatureService,
    private dialog: MatDialog
  ) {
    // TODO: Make dynamic to support i18n.
    this.sideNavOpened = true;
    this.activeProject$ = this.projectService.getActiveProject$();
  }

  ngOnInit() {
    // Activate new project on route changes.
    this.subscription.add(
      this.route.paramMap.subscribe(params => {
        this.projectService.activateProject(params.get('projectId')!);
      })
    );
    this.subscription.add(
      this.route.fragment.subscribe(fragment => {
        this.onFragmentChange(fragment);
      })
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  private onFragmentChange(fragment?: string) {
    if (!fragment) {
      return;
    }
    const params = new HttpParams({fromString: fragment});
    // The 'l' param is used to represent the layer id being
    // edited.
    if (params.get('l')) {
      this.showEditLayerDialog(params.get('l')!);
    }
    // The 'f' param is used to represent the feature id that
    // was selected by e.g. clicking the marker.
    if (params.get('f')) {
      this.featureService.selectFeature(params.get('f')!);
    }
  }

  private showEditLayerDialog(layerId: string) {
    this.dialog.open(LayerDialogComponent, {
      data: {layerId},
    });
  }
}
