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

import firebase from 'firebase/app';
import { Injectable } from '@angular/core';
import { AngularFirestore, DocumentData } from '@angular/fire/firestore';
import { FirebaseDataConverter } from '../../shared/converters/firebase-data-converter';
import { Observable } from 'rxjs';
import { Project } from '../../shared/models/project.model';
import { map } from 'rxjs/operators';
import { User } from './../../shared/models/user.model';
import { Feature } from '../../shared/models/feature.model';
import { Job } from './../../shared/models/job.model';
import { List, Map } from 'immutable';
import { Observation } from '../../shared/models/observation/observation.model';
import { Role } from '../../shared/models/role.model';
import { OfflineBaseMapSource } from '../../shared/models/offline-base-map-source';
import 'firebase/storage';

// TODO: Make DataStoreService and interface and turn this into concrete
// implementation (e.g., CloudFirestoreService).
@Injectable({
  providedIn: 'root',
})
export class DataStoreService {
  private readonly VALID_ROLES = ['owner', 'contributor', 'manager', 'viewer'];
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
   * Returns an Observable that loads and emits the list of projects accessible to the specified user.
   *
   */
  loadAccessibleProject$(userEmail: string): Observable<List<Project>> {
    return this.db
      .collection('projects', ref =>
        ref.where(
          new firebase.firestore.FieldPath('acl', userEmail),
          'in',
          this.VALID_ROLES
        )
      )
      .snapshotChanges()
      .pipe(
        map(projects =>
          List(
            projects.map(a => {
              const docData = a.payload.doc.data() as DocumentData;
              const id = a.payload.doc.id;
              return FirebaseDataConverter.toProject(id, docData);
            })
          )
        )
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

  addOrUpdateJob(projectId: string, job: Job): Promise<void> {
    return this.db
      .collection('projects')
      .doc(projectId)
      .update({
        [`jobs.${job.id}`]: FirebaseDataConverter.jobToJS(job),
      });
  }

  async deleteJob(projectId: string, jobId: string) {
    await this.deleteAllFeaturesInJob(projectId, jobId);
    await this.deleteAllObservationsInJob(projectId, jobId);
    return await this.db
      .collection('projects')
      .doc(projectId)
      .update({
        [`jobs.${jobId}`]: firebase.firestore.FieldValue.delete(),
      });
  }

  private async deleteAllObservationsInJob(
    projectId: string,
    jobId: string
  ) {
    const observations = this.db.collection(
      `projects/${projectId}/observations`,
      ref => ref.where('jobId', '==', jobId)
    );
    const querySnapshot = await observations.get().toPromise();
    return await Promise.all(querySnapshot.docs.map(doc => doc.ref.delete()));
  }

  private async deleteAllObservationsInFeature(
    projectId: string,
    featureId: string
  ) {
    const observations = this.db.collection(
      `projects/${projectId}/observations`,
      ref => ref.where('featureId', '==', featureId)
    );
    const querySnapshot = await observations.get().toPromise();
    return await Promise.all(querySnapshot.docs.map(doc => doc.ref.delete()));
  }

  private async deleteAllFeaturesInJob(projectId: string, jobId: string) {
    const featuresInJob = this.db.collection(
      `projects/${projectId}/features`,
      ref => ref.where('jobId', '==', jobId)
    );
    const querySnapshot = await featuresInJob.get().toPromise();
    return await Promise.all(querySnapshot.docs.map(doc => doc.ref.delete()));
  }

  async deleteFeature(projectId: string, featureId: string) {
    await this.deleteAllObservationsInFeature(projectId, featureId);
    return await this.db
      .collection('projects')
      .doc(projectId)
      .collection('features')
      .doc(featureId)
      .delete();
  }

  async deleteObservation(projectId: string, observationId: string) {
    return await this.db
      .collection('projects')
      .doc(projectId)
      .collection('observations')
      .doc(observationId)
      .delete();
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
  loadFeature$(projectId: string, featureId: string): Observable<Feature> {
    return this.db
      .collection(`projects/${projectId}/features`)
      .doc(featureId)
      .get()
      .pipe(
        // Fail with error if feature could not be loaded.
        map(doc =>
          FirebaseDataConverter.toFeature(doc.id, doc.data()! as DocumentData)
        ),
        // Cast to Feature to remove undefined from type. Done as separate
        // map() operation since compiler doesn't recognize cast when defined in
        // previous map() step.
        map(f => f as Feature)
      );
  }

  /**
   * Returns a stream containing the user with the specified id. Remote changes
   * to the user will cause a new value to be emitted.
   *
   * @param uid the unique id used to represent the user in the data store.
   */
  user$(uid: string): Observable<User | undefined> {
    return this.db
      .doc<User>(`users/${uid}`)
      .valueChanges()
      .pipe(map(data => FirebaseDataConverter.toUser(data as DocumentData)));
  }

  features$({ id }: Project): Observable<List<Feature>> {
    return this.db
      .collection(`projects/${id}/features`)
      .valueChanges({ idField: 'id' })
      .pipe(
        map(array =>
          List(
            array
              .map(obj => FirebaseDataConverter.toFeature(obj.id, obj))
              // Filter out features that could not be loaded (i.e., undefined).
              .filter(f => !!f)
              // Cast items in List to Feature to remove undefined from type.
              .map(f => f as Feature)
          )
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
                  .getJob(feature.jobId)!
                  .getForm((obj as DocumentData).formId)!,
                obj.id,
                obj
              );
            })
          )
        )
      );
  }

  // TODO: Define return type here and throughout.
  loadObservation$(project: Project, feature: Feature, observationId: string) {
    return this.db
      .collection(`projects/${project.id}/observations`)
      .doc(observationId)
      .get()
      .pipe(
        map(doc => {
          return FirebaseDataConverter.toObservation(
            project
              .getJob(feature.jobId)!
              .getForm((doc.data()! as DocumentData).formId)!,
            doc.id,
            doc.data()! as DocumentData
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
    return firebase.firestore.FieldValue.serverTimestamp();
  }

  updateFeature(projectId: string, feature: Feature): Promise<void> {
    return this.db
      .collection('projects')
      .doc(projectId)
      .collection('features')
      .doc(feature.id)
      .set(FirebaseDataConverter.featureToJS(feature));
  }

  /**
   * Creates a new project in the remote db using the specified title,
   * returning the id of the newly created project. ACLs are initialized
   * to include the specified user email as project owner.
   */
  async createProject(
    ownerEmail: string,
    title: string,
    offlineBaseMapSources?: OfflineBaseMapSource[]
  ): Promise<string> {
    const projectId = this.generateId();
    await this.updateProjectTitle(projectId, title);
    await this.db
      .collection('projects')
      .doc(projectId)
      .set(
        FirebaseDataConverter.newProjectJS(
          ownerEmail,
          title,
          offlineBaseMapSources
        )
      );
    return Promise.resolve(projectId);
  }

  getImageDownloadURL(path: string) {
    return firebase.storage().ref().child(path).getDownloadURL();
  }
}
