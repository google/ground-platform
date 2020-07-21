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
import { FirebaseDataConverter } from '../../shared/converters/firebase-data-converter';
import { Observable } from 'rxjs';
import { Project } from '../../shared/models/project.model';
import { map } from 'rxjs/operators';
import { User } from './../../shared/models/user.model';
import { Feature } from '../../shared/models/feature.model';
import { Layer } from './../../shared/models/layer.model';
import { List, Map } from 'immutable';
import { Observation } from '../../shared/models/observation/observation.model';
import { Role } from '../../shared/models/role.model';
import { firestore } from 'firebase/app';

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
      .valueChanges()
      .pipe(
        // Convert object to Project instance.
        map(data => FirebaseDataConverter.toProject(id, data as DocumentData))
      );
  }

  /**
   * Updates the project with new title.
   *
   * @param projectId the id of the project.
   * @param newTitle the new title of the project.
   */
  updateProjectTitle(projectId: string, newTitle: string): Promise<void> {
    return this.db
      .collection('projects')
      .doc(projectId)
      .set({ title: { en: newTitle } }, { merge: true });
  }

  // TODO: Define return types for methods in this class
  updateLayer(projectId: string, layer: Layer) {
    return this.db
      .collection('projects')
      .doc(projectId)
      .update({
        [`layers.${layer.id}`]: FirebaseDataConverter.layerToJS(layer),
      });
  }

  async deleteLayer(projectId: string, layerId: string) {
    await this.deleteAllFeaturesInLayer(projectId, layerId);
    return await this.db
      .collection('projects')
      .doc(projectId)
      .update({
        [`layers.${layerId}`]: firestore.FieldValue.delete(),
      });
  }

  private async deleteAllFeaturesInLayer(projectId: string, layerId: string) {
    const featuresInLayer = this.db.collection(
      `projects/${projectId}/features`,
      ref => ref.where('layerId', '==', layerId)
    );
    const querySnapshot = await featuresInLayer.get().toPromise();
    return await Promise.all(querySnapshot.docs.map(doc => doc.ref.delete()));
  }

  updateObservation(projectId: string, observation: Observation) {
    return this.db
      .collection(`projects/${projectId}/observations`)
      .doc(observation.id)
      .set(FirebaseDataConverter.observationToJS(observation));
  }

  /**
   * Returns an Observable that loads and emits the feature with the specified
   * uuid.
   *
   * @param projectId the id of the project in which requested feature is.
   * @param featureId the id of the requested feature.
   */
  loadFeature$(projectId: string, featureId: string) {
    return this.db
      .collection(`projects/${projectId}/features`)
      .doc(featureId)
      .get()
      .pipe(map(doc => FirebaseDataConverter.toFeature(doc.id, doc.data()!)));
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

  features$({ id }: Project): Observable<List<Feature>> {
    return this.db
      .collection(`projects/${id}/features`)
      .valueChanges({ idField: 'id' })
      .pipe(
        map(array =>
          List(array.map(obj => FirebaseDataConverter.toFeature(obj.id, obj)))
        )
      );
  }

  /**
   * Returns an Observable that loads and emits the observations with the specified
   * uuid.
   *
   * @param id the id of the requested project (it should have forms inside).
   * @param featureId the id of the requested feature.
   */
  observations$(
    project: Project,
    feature: Feature
  ): Observable<List<Observation>> {
    return this.db
      .collection(`projects/${project.id}/observations`, ref =>
        ref.where('featureId', '==', feature.id)
      )
      .valueChanges({ idField: 'id' })
      .pipe(
        map(array =>
          List(
            array.map(obj => {
              return FirebaseDataConverter.toObservation(
                project
                  .getLayer(feature.layerId)!
                  .getForm((obj as DocumentData).formId)!,
                obj.id,
                obj
              );
            })
          )
        )
      );
  }

  loadObservation$(project: Project, feature: Feature, observationId: string) {
    return this.db
      .collection(`projects/${project.id}/observations`)
      .doc(observationId)
      .get()
      .pipe(
        map(doc => {
          return FirebaseDataConverter.toObservation(
            project.getLayer(feature.layerId)!.getForm(doc.data()!.formId)!,
            doc.id,
            doc.data()!
          );
        })
      );
  }

  /**
   * Adds or overwrites the role of the specified user in the project with the
   * specified id.
   * @param projectId the id of the project to be updated.
   * @param email the email of the user whose role is to be updated.
   * @param role the new role of the specified user.
   */
  updateAcl(projectId: string, acl: Map<string, Role>): Promise<void> {
    return this.db
      .collection('projects')
      .doc(projectId)
      .update({ acl: FirebaseDataConverter.aclToJs(acl) });
  }

  generateId() {
    return this.db.collection('ids').ref.doc().id;
  }

  getServerTimestamp() {
    return firestore.FieldValue.serverTimestamp();
  }
}
