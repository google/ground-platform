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
import { Observable, ReplaySubject } from 'rxjs';
import { switchMap, shareReplay } from 'rxjs/operators';
import { Project } from '../../shared/models/project.model';
import { DataStoreService } from '../data-store/data-store.service';
import { AuthService } from '../auth/auth.service';
import { Role } from '../../shared/models/role.model';
import { Map } from 'immutable';
import { of } from 'rxjs';
@Injectable({
  providedIn: 'root',
})
export class ProjectService {
  private activeProjectId$ = new ReplaySubject<string>(1);
  private activeProject$: Observable<Project>;

  constructor(private dataStore: DataStoreService, authService: AuthService) {
    // Reload active project each time authenticated user changes.
    this.activeProject$ = authService.user$.pipe(
      switchMap(() =>
        //  on each change to project id.
        this.activeProjectId$.pipe(
          // Asynchronously load project. switchMap() internally disposes
          // of previous subscription if present.
          switchMap(id => {
            if (id === ':new') {
              return of(Project.EMPTY);
            }
            return this.dataStore.loadProject$(id);
          })
        )
      ),
      // Cache last loaded project so that late subscribers don't cause
      // project to be reloaded.
      shareReplay(1)
    );
  }

  activateProject(id: string) {
    this.activeProjectId$.next(id);
  }

  getActiveProject$(): Observable<Project> {
    return this.activeProject$;
  }

  /**
   * Updates the project with new title by calling the data-store service.
   *
   * @param projectId the id of the project.
   * @param newTitle the new title of the project.
   */
  updateTitle(projectId: string, newTitle: string): Promise<void> {
    return this.dataStore.updateProjectTitle(projectId, newTitle);
  }

  updateAcl(projectId: string, acl: Map<string, Role>): Promise<void> {
    return this.dataStore.updateAcl(projectId, acl);
  }

  createProject(title: string): Promise<string> {
    const projectId = this.dataStore.generateId();
    return new Promise((resolve, reject) => {
      this.dataStore
        .updateProjectTitle(projectId, title)
        .then(() => {
          resolve(projectId);
        })
        .catch(error => reject(error));
    });
  }
}
