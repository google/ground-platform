/**
 * @license
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as functions from 'firebase-functions';
import {firestore} from 'firebase-admin';
import {GeoPoint} from 'firebase-admin/firestore';

export class Datastore {
  private db_: firestore.Firestore;

  constructor(db: firestore.Firestore) {
    this.db_ = db;
  }

  /**
   * Stores user email, name, and avatar to db for use in application LOIs.
   * These attributes are merged with other existing ones if already present.
   */
  async mergeUserProfile(user: functions.auth.UserRecord) {
    const {uid, email, displayName, photoURL} = user;
    await this.db_
      .doc(`users/${uid}`)
      .set({email, displayName, photoURL}, {merge: true});
  }

  async fetch_(
    docRef: firestore.DocumentReference
  ): Promise<firestore.DocumentData | null | undefined> {
    const doc = await docRef.get();
    return doc.exists ? doc.data() : null;
  }

  fetchDoc_(path: string) {
    return this.fetch_(this.db_.doc(path));
  }

  fetchCollection_(path: string) {
    return this.db_.collection(path).get();
  }

  fetchSurvey(surveyId: string) {
    return this.db_.doc(`surveys/${surveyId}`).get();
  }

  fetchRecord(surveyId: string, loiId: string, recordId: string) {
    return this.fetchDoc_(
      `surveys/${surveyId}/lois/${loiId}/records/${recordId}`
    );
  }

  fetchSubmissionsByJobId(surveyId: string, jobId: string) {
    return this.db_
      .collection(`surveys/${surveyId}/submissions`)
      .where('jobId', '==', jobId)
      .get();
  }

  fetchLocationOfInterest(surveyId: string, loiId: string) {
    return this.fetchDoc_(`surveys/${surveyId}/lois/${loiId}`);
  }

  fetchLocationsOfInterestByJobId(surveyId: string, jobId: string) {
    return this.db_
      .collection(`surveys/${surveyId}/lois`)
      .where('jobId', '==', jobId)
      .get();
  }

  fetchTask(surveyId: string, loiTypeId: string, taskId: string) {
    return this.fetchDoc_(
      `surveys/${surveyId}/loiTypes/${loiTypeId}/tasks/${taskId}`
    );
  }

  fetchSheetsConfig(surveyId: string) {
    return this.fetchDoc_(`surveys/${surveyId}/sheets/config`);
  }

  async insertLocationOfInterest(surveyId: string, loi: any) {
    const loiDoc = {
      ...loi,
      geometry: Datastore.toFirestoreMap(loi.geometry),
    };
    const docRef = this.db_.collection('surveys').doc(surveyId);
    const doc = await docRef.get();
    if (!doc.exists) {
      throw new Error(`/surveys/${surveyId} not found`);
    }
    await docRef.collection('lois').add(loiDoc);
  }

  async countSubmissionsForLoi(surveyId: string, loiId: string): Promise<number> {
    const submissionsRef = this.db_.collection('surveys').doc(surveyId).collection('submissions');
    const submissionsForLoiQuery = submissionsRef.where("loiId", "==", loiId);
    const snapshot = await submissionsForLoiQuery.count().get();
    return snapshot.data().count;
  }
  
  async updateSubmissionCount(surveyId: string, loiId: string, count:number) {
    const loiRef = this.db_.collection('surveys').doc(surveyId).collection('lois').doc(loiId);
    await loiRef.update({submissionCount: count});
  }

  static toFirestoreMap(geometry: any) {
    return Object.fromEntries(
      Object.entries(geometry).map(([key, value]) => [
        key,
        Datastore.toFirestoreValue(value),
      ])
    );
  }

  static toFirestoreValue(value: any): any {
    if (value === null) {
      return null;
    }
    if (Array.isArray(value)) {
      if (value.length === 2 && value.every(x => typeof x === 'number')) {
        // Note: GeoJSON coordinates are in lng-lat order. We reverse that order for GeoPoint, which uses
        // lat-lng order.
        return new GeoPoint(value[1] as number, value[0] as number);
      }
      // Convert array to map.
      return Object.fromEntries(
        value.map((x, i) => [i, Datastore.toFirestoreValue(x)])
      );
    }
    return value;
  }
}
