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

import { ActivatedRoute } from '@angular/router';
import { NavigationService } from '../../services/navigation/navigation.service';
import { Observable, Subscription } from 'rxjs';
import { Project } from '../../shared/models/project.model';
import { ProjectService } from './../../services/project/project.service';
import { Component, OnInit, OnDestroy } from '@angular/core';

@Component({
  selector: 'ground-main-page-container',
  templateUrl: './main-page-container.component.html',
  styleUrls: ['./main-page-container.component.css'],
})
export class MainPageContainerComponent implements OnInit, OnDestroy {
  activeProject$: Observable<Project>;
  private subscription = new Subscription();

  constructor(
    private navigationService: NavigationService,
    private projectService: ProjectService,
    route: ActivatedRoute
  ) {
    this.activeProject$ = projectService.getActiveProject$();
    navigationService.init(route);
  }

  ngOnInit() {
    // Activate new project on route changes.
    this.subscription.add(
      this.navigationService.getProjectId$().subscribe(id => {
        id && this.projectService.activateProject(id);
      })
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
