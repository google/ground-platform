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
import { AngularFirestore } from '@angular/fire/firestore';
import { Observable } from 'rxjs';
import { Project } from '../../shared/models/project.model';
import { map } from 'rxjs/operators';
import { User } from './../../shared/models/user.model';

// TODO: Make DataStoreService and interface and turn this into concrete
// implementation (e.g., CloudFirestoreService).
@Injectable({
  providedIn: 'root',
})
export class DataStoreService {
  constructor(private db: AngularFirestore) {}

  loadProject(id: string) {
    return this.db
      .collection('projects')
      .doc(id)
      .get()
      .pipe(
        // Convert Firestore document to Project object.
        map(doc => doc.data() as Project)
      );
  }

  /**
   * Returns a stream containing the user with the specified id. Remote changes
   * to the user will cause a new value to be emitted.
   *
   * @param uid the unique id used to represent the user in the data store.
   */
  user$(uid: string): Observable<User | undefined> {
    return this.db.doc<User>(`users/${uid}`).valueChanges();
  }
 
  /**
   * Store user email, name, and avatar to db for use in application features.
   * These attributes are merged with other existing ones if they were added
   * by other clients.
   */
   updateUser$({ uid, email, displayName, photoURL }: User) {
    // TODO: Move into Cloud Function so this works for all clients.
    this.db.doc(`users/${uid}`).set(
      {
        uid,
        email,
        displayName,
        photoURL,
      },
      { merge: true }
    );
  }
}
