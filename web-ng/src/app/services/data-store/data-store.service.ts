import { Layer } from './../../shared/models/layer.model';
import { StringMap } from './../../shared/models/string-map.model';
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
import { AngularFirestore, DocumentData } from '@angular/fire/firestore';
import { Observable } from 'rxjs';
import { Project } from '../../shared/models/project.model';
import { map } from 'rxjs/operators';
import { User } from './../../shared/models/user.model';
import { Feature } from '../../shared/models/feature.model';
import { List } from 'immutable';

// TODO: Make DataStoreService and interface and turn this into concrete
// implementation (e.g., CloudFirestoreService).
@Injectable({
  providedIn: 'root',
})
export class DataStoreService {
  constructor(private db: AngularFirestore) {}

  /**
   * Returns an Observable that loads and emits the project with the specified
   * uuid.
   *
   * @param id the id of the requested project.
   */
  loadProject$(id: string) {
    return this.db
      .collection('projects')
      .doc(id)
      .get()
      .pipe(
        // Convert object to Project instance.
        map(doc => DataStoreService.toProject(doc.id, doc.data()!))
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

  features$({ id }: Project): Observable<List<Feature>> {
    return this.db
      .collection(`projects/${id}/features`)
      .valueChanges({ idField: 'id' })
      .pipe(
        map(array =>
          List(array.map(obj => DataStoreService.toFeature(obj.id, obj)))
        )
      );
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Project instance.
   *
   * @param id the uuid of the project instance.
   * @param data the source data in a dictionary keyed by string.
   */
  private static toProject(id: string, data: DocumentData): Project {
    return new Project(
      id,
      StringMap(data.title),
      StringMap(data.description),
      List(DataStoreService.toLayers(data.layers || {}))
    );
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Layer instance.
   *
   * @param id the uuid of the layer instance.
   * @param data the source data in a dictionary keyed by string.
   */
  private static toLayer(id: string, data: DocumentData): Layer {
    return new Layer(id, StringMap(data.name));
  }

  /**
   * Converts a map of id to raw object deserialized from Firebase into an array
   * of immutable Layer instances.
   *
   * @param layers a map of raw layer objects keyed by id.
   */
  private static toLayers(layers: DocumentData) {
    return Object.keys(layers).map((id: string) =>
      DataStoreService.toLayer(id, layers[id])
    );
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Feature instance.
   *
   * @param id the uuid of the project instance.
   * @param data the source data in a dictionary keyed by string.
   */
  private static toFeature(id: string, data: DocumentData): Feature {
    return new Feature(id, data.layerId, data.location);
  }
}
