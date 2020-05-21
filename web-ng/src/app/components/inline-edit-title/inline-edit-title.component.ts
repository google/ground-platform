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

import {Component, Inject, OnDestroy} from '@angular/core';
import {DataStoreService} from '../../services/data-store/data-store.service';
import {ProjectService} from '../../services/project/project.service';
import {Observable, Subject} from 'rxjs';
import {Project} from '../../shared/models/project.model';
import {MainPageComponent} from '../main-page/main-page.component';
import {Subscription} from 'rxjs';

@Component({
  selector: 'app-inline-edit-title',
  templateUrl: './inline-edit-title.component.html',
  styleUrls: ['./inline-edit-title.component.scss'],
})
export class InlineEditTitleComponent implements OnDestroy {
  readonly activeProject$: Observable<Project>;
  lang: string;
  title!: string;
  projectId!: string;

  subscription: Subscription = new Subscription();
  constructor(
    private dataStoreService: DataStoreService,
    private projectService: ProjectService
  ) {
    this.lang = 'en';
    this.activeProject$ = this.projectService.getActiveProject$();
    this.subscription.add(
      this.activeProject$.subscribe(project => {
        this.title = project.title.get(this.lang)!;
        this.projectId = project.id;
      })
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  updateProjectTitle(evt: {target: HTMLInputElement}) {
    if (!this.projectId) {
      return Promise.reject(new Error('Project not loaded'));
    }
    if (evt.target.value === this.title) return;
    return this.dataStoreService.updateProjectTitle(
      this.projectId,
      evt.target.value
    );
  }

  handleKeyPress(evt: {key: string; target: HTMLInputElement}) {
    switch (evt.key) {
      case 'Enter':
        evt.target.blur();
        break;
      case 'Escape':
        evt.target.value = this.title;
        evt.target.blur();
        break;
      default:
      // n/a.
    }
  }
}
