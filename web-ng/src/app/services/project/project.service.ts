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
import { Observable, ReplaySubject, Subscription } from 'rxjs';
import { switchMap, map, shareReplay } from 'rxjs/operators';
import { Project } from '../../shared/models/project.model';
import { AngularFirestore } from '@angular/fire/firestore';

@Injectable({
  providedIn: 'root',
})
export class ProjectService {
  private activeProjectId$ = new ReplaySubject<string>(1);
  private activeProject$: Observable<Project>;

  constructor(private db: AngularFirestore) {
    //  on each change to project id.
    this.activeProject$ = this.activeProjectId$.pipe(
      // Asynchronously load project. switchMap() internally disposes
      // of previous subscription if present.
      switchMap(id => this.loadProject(id)),
      // Map Firestore document to Project object.
      map(doc => doc.data() as Project),
      // Cache last loaded project so that late subscribers don't cause
      // project to be reloaded.
      shareReplay(1)
    );
  }

  // TODO: More to datastore service.
  loadProject(id: string) {
    return this.db
      .collection('projects')
      .doc(id)
      .get();
  }

  activateProject(id: string) {
    this.activeProjectId$.next(id);
  }

  getActiveProject$(): Observable<Project> {
    return this.activeProject$;
  }
}
