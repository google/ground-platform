/**
 * Copyright 2018 The Ground Authors.
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

/**
 *
 */
type pseudoGeoJsonGeometry = {
  type: string;
  coordinates: any;
};

/**
 * Returns path to config colection.
 */
export const config = () => 'config';

/**
 * Returns the path of integrations doc.
 */
export const integrations = () => config() + '/integrations';

/**
 * Returns path to survey colection. This is a function for consistency with other path functions.
 */
export const surveys = () => 'surveys';

/**
 * Returns the path of survey doc with the specified id.
 */
export const survey = (surveyId: string) => surveys() + '/' + surveyId;

/**
 * Returns the path of the survey collection in the survey with the specified id.
 */
export const lois = (surveyId: string) => survey(surveyId) + '/lois';

/**
 * Returns the path of the LOI doc with the specified id.
 */
export const loi = (surveyId: string, loiId: string) =>
  lois(surveyId) + '/' + loiId;

/**
 * Returns the path of the submissions collection in the survey with the specified id.
 */
export const submissions = (surveyId: string) =>
  survey(surveyId) + '/submissions';

/**
 * Returns the path of the submission doc with the specified id.
 */
export const submission = (surveyId: string, submissionId: string) =>
  submissions(surveyId) + '/' + submissionId;

export class Datastore {
  private db_: firestore.Firestore;

  constructor(db: firestore.Firestore) {
    this.db_ = db;
    db.settings({ignoreUndefinedProperties: true});
  }

  /**
   * Stores user email, name, and avatar to db for use in application LOIs.
   * These attributes are merged with other existing ones if already present.
   */
  async mergeUserProfile(user: functions.auth.UserRecord) {
    const {uid, email, displayName, photoURL} = user;
    await this.db_.doc(`users/${uid}`).set(
      {
        email,
        displayName,
        photoURL: photoURL && Datastore.trimPhotoURLSizeSuffix(photoURL),
      },
      {merge: true}
    );
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

  fetchPropertyGenerators() {
    return this.db_.collection(integrations() + '/propertyGenerators').get();
  }

  fetchSurvey(surveyId: string) {
    return this.db_.doc(survey(surveyId)).get();
  }

  fetchSubmissionsByJobId(surveyId: string, jobId: string) {
    return this.db_
      .collection(submissions(surveyId))
      .where('jobId', '==', jobId)
      .get();
  }

  fetchLocationOfInterest(surveyId: string, loiId: string) {
    return this.fetchDoc_(loi(surveyId, loiId));
  }

  fetchLocationsOfInterestByJobId(surveyId: string, jobId: string) {
    return this.db_
      .collection(lois(surveyId))
      .where('jobId', '==', jobId)
      .get();
  }

  fetchSheetsConfig(surveyId: string) {
    return this.fetchDoc_(`${survey(surveyId)}/sheets/config`);
  }

  async insertLocationOfInterest(surveyId: string, loi: any) {
    const loiDoc = {
      ...loi,
      geometry: Datastore.toFirestoreMap(loi.geometry),
    };
    const docRef = this.db_.doc(survey(surveyId));
    const doc = await docRef.get();
    if (!doc.exists) {
      throw new Error(`${survey(surveyId)} not found`);
    }
    await docRef.collection('lois').add(loiDoc);
  }

  async countSubmissionsForLoi(
    surveyId: string,
    loiId: string
  ): Promise<number> {
    const submissionsRef = this.db_.collection(submissions(surveyId));
    const submissionsForLoiQuery = submissionsRef.where('loiId', '==', loiId);
    const snapshot = await submissionsForLoiQuery.count().get();
    return snapshot.data().count;
  }

  async updateSubmissionCount(surveyId: string, loiId: string, count: number) {
    const loiRef = this.db_.doc(loi(surveyId, loiId));
    await loiRef.update({submissionCount: count});
  }

  async updateLoiProperties(
    surveyId: string,
    loiId: string,
    properties: {[key: string]: string}
  ) {
    const loiRef = this.db_.doc(loi(surveyId, loiId));
    await loiRef.update({properties});
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

  /**
   *
   * @param geoJsonGeometry pseudo GeoJSON geometry object, should have the following fields:
   * {
   *    type: string
   *    geometry: any (note that this is expected to be a map rather than a list of lists because of how we store data in Firestore)
   * }
   *
   * @returns GeoJSON geometry object (with geometry as list of lists)
   */
  static fromFirestoreMap(geoJsonGeometry: any): any {
    const geometryObject = geoJsonGeometry as pseudoGeoJsonGeometry;
    if (!geometryObject) {
      throw new Error(
        `${geoJsonGeometry} is not of type pseudoGeoJsonGeometry`
      );
    }

    geometryObject.coordinates = this.fromFirestoreValue(
      geometryObject.coordinates
    );

    return geometryObject;
  }

  static fromFirestoreValue(coordinates: any) {
    if (coordinates instanceof GeoPoint) {
      // Note: GeoJSON coordinates are in lng-lat order.
      return [coordinates.longitude, coordinates.latitude];
    }

    if (typeof coordinates !== 'object') {
      return coordinates;
    }
    const result = new Array<any>(coordinates.length);

    Object.entries(coordinates).map(([i, nestedValue]) => {
      const index = Number.parseInt(i);
      if (!Number.isInteger(index)) {
        return coordinates;
      }

      result[index] = this.fromFirestoreValue(nestedValue);
    });

    return result;
  }

  /**
   * Removes a possible size suffix from the user's photo URL.
   *
   * @param photoURL The user's photo URL.
   */
  static trimPhotoURLSizeSuffix(photoURL: string): string {
    return photoURL.replace(/=s.*$/g, '');
  }
}
