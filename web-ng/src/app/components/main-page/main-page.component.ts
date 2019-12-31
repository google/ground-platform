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

import { ActivatedRoute } from '@angular/router';
import { AuthService } from './../../services/auth/auth.service';
import { Component, OnInit } from '@angular/core';
import { LayerDialogComponent } from '../layer-dialog/layer-dialog.component';
import { MatDialog } from '@angular/material/dialog';
import { Observable, Subscription } from 'rxjs';
import { Project } from '../../shared/models/project.model';
import { ProjectService } from '../../services/project/project.service';
import { URLSearchParams } from '@angular/http';

@Component({
  selector: 'ground-main-page',
  templateUrl: './main-page.component.html',
  styleUrls: ['./main-page.component.css'],
})
export class MainPageComponent implements OnInit {
  activeProject$: Observable<Project>;
  lang: string;
  subscription: Subscription = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private projectService: ProjectService,
    private dialog: MatDialog
    public auth: AuthService
  ) {
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';
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
    const params = new URLSearchParams(fragment);
    if (params.get('l')) {
      this.showEditLayerDialog(params.get('l')!);
    }
  }

  private showEditLayerDialog(layerId: string) {
    this.dialog.open(LayerDialogComponent, {
      data: { layerId },
    });
  }
}
