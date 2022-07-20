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
import { Survey } from '../../shared/models/survey.model';
import { map } from 'rxjs/operators';
import { User } from './../../shared/models/user.model';
import { Feature } from '../../shared/models/feature.model';
import { Job } from './../../shared/models/job.model';
import { List, Map } from 'immutable';
import { Observation } from '../../shared/models/observation/observation.model';
import { Role } from '../../shared/models/role.model';
import { OfflineBaseMapSource } from '../../shared/models/offline-base-map-source';
import 'firebase/storage';

const SURVEYS_COLLECTION_NAME = 'surveys';

// TODO: Make DataStoreService and interface and turn this into concrete
// implementation (e.g., CloudFirestoreService).
@Injectable({
  providedIn: 'root',
})
export class DataStoreService {
  private readonly VALID_ROLES = ['owner', 'contributor', 'manager', 'viewer'];
  constructor(private db: AngularFirestore) {}

  /**
   * Returns an Observable that loads and emits the survey with the specified
   * uuid.
   *
   * @param id the id of the requested survey.
   */
  loadSurvey$(id: string) {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(id)
      .valueChanges()
      .pipe(
        // Convert object to Survey instance.
        map(data => FirebaseDataConverter.toSurvey(id, data as DocumentData))
      );
  }

  /**
   * Returns an Observable that loads and emits the list of surveys accessible to the specified user.
   *
   */
  loadAccessibleSurvey$(userEmail: string): Observable<List<Survey>> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME, ref =>
        ref.where(
          new firebase.firestore.FieldPath('acl', userEmail),
          'in',
          this.VALID_ROLES
        )
      )
      .snapshotChanges()
      .pipe(
        map(surveys =>
          List(
            surveys.map(a => {
              const docData = a.payload.doc.data() as DocumentData;
              const id = a.payload.doc.id;
              return FirebaseDataConverter.toSurvey(id, docData);
            })
          )
        )
      );
  }

  /**
   * Updates the survey with new title.
   *
   * @param surveyId the id of the survey.
   * @param newTitle the new title of the survey.
   */
  updateSurveyTitle(surveyId: string, newTitle: string): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .set({ title: { en: newTitle } }, { merge: true });
  }

  addOrUpdateJob(surveyId: string, job: Job): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .update({
        [`jobs.${job.id}`]: FirebaseDataConverter.jobToJS(job),
      });
  }

  async deleteJob(surveyId: string, jobId: string) {
    await this.deleteAllFeaturesInJob(surveyId, jobId);
    await this.deleteAllObservationsInJob(surveyId, jobId);
    return await this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .update({
        [`jobs.${jobId}`]: firebase.firestore.FieldValue.delete(),
      });
  }

  private async deleteAllObservationsInJob(surveyId: string, jobId: string) {
    const observations = this.db.collection(
      `${SURVEYS_COLLECTION_NAME}/${surveyId}/observations`,
      ref => ref.where('jobId', '==', jobId)
    );
    const querySnapshot = await observations.get().toPromise();
    return await Promise.all(querySnapshot.docs.map(doc => doc.ref.delete()));
  }

  private async deleteAllObservationsInFeature(
    surveyId: string,
    featureId: string
  ) {
    const observations = this.db.collection(
      `${SURVEYS_COLLECTION_NAME}/${surveyId}/observations`,
      ref => ref.where('featureId', '==', featureId)
    );
    const querySnapshot = await observations.get().toPromise();
    return await Promise.all(querySnapshot.docs.map(doc => doc.ref.delete()));
  }

  private async deleteAllFeaturesInJob(surveyId: string, jobId: string) {
    const featuresInJob = this.db.collection(
      `${SURVEYS_COLLECTION_NAME}/${surveyId}/features`,
      ref => ref.where('jobId', '==', jobId)
    );
    const querySnapshot = await featuresInJob.get().toPromise();
    return await Promise.all(querySnapshot.docs.map(doc => doc.ref.delete()));
  }

  async deleteFeature(surveyId: string, featureId: string) {
    await this.deleteAllObservationsInFeature(surveyId, featureId);
    return await this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .collection('features')
      .doc(featureId)
      .delete();
  }

  async deleteObservation(surveyId: string, observationId: string) {
    return await this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .collection('observations')
      .doc(observationId)
      .delete();
  }

  updateObservation(surveyId: string, observation: Observation) {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${surveyId}/observations`)
      .doc(observation.id)
      .set(FirebaseDataConverter.observationToJS(observation));
  }

  /**
   * Returns an Observable that loads and emits the feature with the specified
   * uuid.
   *
   * @param surveyId the id of the survey in which requested feature is.
   * @param featureId the id of the requested feature.
   */
  loadFeature$(surveyId: string, featureId: string): Observable<Feature> {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${surveyId}/features`)
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

  features$({ id }: Survey): Observable<List<Feature>> {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${id}/features`)
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
   * @param id the id of the requested survey (it should have tasks inside).
   * @param featureId the id of the requested feature.
   */
  observations$(
    survey: Survey,
    feature: Feature
  ): Observable<List<Observation>> {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${survey.id}/observations`, ref =>
        ref.where('featureId', '==', feature.id)
      )
      .valueChanges({ idField: 'id' })
      .pipe(
        map(array =>
          List(
            array.map(obj => {
              return FirebaseDataConverter.toObservation(
                survey
                  .getJob(feature.jobId)!
                  .getTask((obj as DocumentData).taskId)!,
                obj.id,
                obj
              );
            })
          )
        )
      );
  }

  // TODO: Define return type here and throughout.
  loadObservation$(survey: Survey, feature: Feature, observationId: string) {
    return this.db
      .collection(`${SURVEYS_COLLECTION_NAME}/${survey.id}/observations`)
      .doc(observationId)
      .get()
      .pipe(
        map(doc => {
          return FirebaseDataConverter.toObservation(
            survey
              .getJob(feature.jobId)!
              .getTask((doc.data()! as DocumentData).taskId)!,
            doc.id,
            doc.data()! as DocumentData
          );
        })
      );
  }

  /**
   * Adds or overwrites the role of the specified user in the survey with the
   * specified id.
   * @param surveyId the id of the survey to be updated.
   * @param email the email of the user whose role is to be updated.
   * @param role the new role of the specified user.
   */
  updateAcl(surveyId: string, acl: Map<string, Role>): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .update({ acl: FirebaseDataConverter.aclToJs(acl) });
  }

  generateId() {
    return this.db.collection('ids').ref.doc().id;
  }

  getServerTimestamp() {
    return firebase.firestore.FieldValue.serverTimestamp();
  }

  updateFeature(surveyId: string, feature: Feature): Promise<void> {
    return this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .collection('features')
      .doc(feature.id)
      .set(FirebaseDataConverter.featureToJS(feature));
  }

  /**
   * Creates a new survey in the remote db using the specified title,
   * returning the id of the newly created survey. ACLs are initialized
   * to include the specified user email as survey owner.
   */
  async createSurvey(
    ownerEmail: string,
    title: string,
    offlineBaseMapSources?: OfflineBaseMapSource[]
  ): Promise<string> {
    const surveyId = this.generateId();
    await this.updateSurveyTitle(surveyId, title);
    await this.db
      .collection(SURVEYS_COLLECTION_NAME)
      .doc(surveyId)
      .set(
        FirebaseDataConverter.newSurveyJS(
          ownerEmail,
          title,
          offlineBaseMapSources
        )
      );
    return Promise.resolve(surveyId);
  }

  getImageDownloadURL(path: string) {
    return firebase.storage().ref().child(path).getDownloadURL();
  }
}
