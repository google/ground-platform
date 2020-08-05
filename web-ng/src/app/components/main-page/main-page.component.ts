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

import { Component, OnInit } from '@angular/core';
import { LayerDialogComponent } from '../layer-dialog/layer-dialog.component';
import { MatDialog } from '@angular/material/dialog';
import { Observable, Subscription } from 'rxjs';
import { Project } from '../../shared/models/project.model';
import { FeatureService } from '../../services/feature/feature.service';
import { ProjectService } from '../../services/project/project.service';
import { ObservationService } from '../../services/observation/observation.service';
import { take } from 'rxjs/operators';
import { RouterService } from '../../services/router/router.service';
import { ActivatedRoute } from '@angular/router';

/**
 * Root component for main application page showing map, layers list, and
 * project header. Responsible for coordinating page-level URL states with
 * various services.
 */
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
    private routerService: RouterService,
    private projectService: ProjectService,
    private featureService: FeatureService,
    private observationService: ObservationService,
    private dialog: MatDialog,
    route: ActivatedRoute
  ) {
    // TODO: Make dynamic to support i18n.
    this.sideNavOpened = true;
    this.activeProject$ = this.projectService.getActiveProject$();
    routerService.init(route);
  }

  ngOnInit() {
    // Activate new project on route changes.
    this.subscription.add(
      this.routerService.getProjectId$().subscribe(id => {
        id && this.projectService.activateProject(id);
      })
    );
    // Show layer dialog when non-null layer id set in URL.
    this.subscription.add(
      this.routerService
        .getLayerId$()
        .subscribe(id => id && this.showEditLayerDialog(id))
    );
    // Show feature details when non-null feature id set in URL.
    this.subscription.add(
      this.routerService
        .getFeatureId$()
        .subscribe(id => id && this.loadFeatureDetails(id))
    );
    // Show/hide observation when observation id set in URL.
    this.subscription.add(
      this.routerService
        .getObservationId$()
        .subscribe(id => this.editObservation(id))
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  private showEditLayerDialog(layerId: string) {
    this.activeProject$.pipe(take(1)).subscribe(project =>
      this.dialog.open(LayerDialogComponent, {
        data: {
          projectId: project.isUnsavedNew()
            ? Project.PROJECT_ID_NEW
            : project.id,
          createLayer: layerId === Project.PROJECT_ID_NEW,
          layer: project.layers?.get(layerId),
        },
      })
    );
  }

  private loadFeatureDetails(featureId: string) {
    this.featureService.selectFeature(featureId);
  }

  private editObservation(observationId: string | null) {
    if (observationId) {
      this.observationService.selectObservation(observationId);
    } else {
      this.observationService.deselectObservation();
    }
  }
}
