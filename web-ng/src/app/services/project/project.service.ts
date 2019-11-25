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

import { Injectable } from '@angular/core';
import { Observable, of, BehaviorSubject } from 'rxjs';
import { Project } from '../../shared/models/project.model';

const EMPTY_PROJECT = new Project();

@Injectable({
  providedIn: 'root',
})
export class ProjectService {
  private activeProject = new BehaviorSubject<Project>(EMPTY_PROJECT);

  activateProject(id: string) {
    this.activeProject.next(new Project('Project ' + id));
  }

  getActiveProject$(): Observable<Project> {
    return this.activeProject;
  }
}
