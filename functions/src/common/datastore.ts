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
import {DocumentData, GeoPoint} from 'firebase-admin/firestore';
import {registry} from '@ground/lib';
import {GroundProtos} from '@ground/proto';

import Pb = GroundProtos.ground.v1beta1;
import {leftOuterJoinSorted, QueryIterator} from './query-iterator';

const l = registry.getFieldIds(Pb.LocationOfInterest);
const sb = registry.getFieldIds(Pb.Submission);

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
 * Returns the path of job doc with the specified id.
 */
export const job = (surveyId: string, jobId: string) =>
  `${survey(surveyId)}/jobs/${jobId}`;

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

  fetchJob(surveyId: string, jobId: string) {
    return this.db_.doc(job(surveyId, jobId)).get();
  }

  fetchLocationOfInterest(surveyId: string, loiId: string) {
    return this.fetchDoc_(loi(surveyId, loiId));
  }

  fetchLocationsOfInterest(surveyId: string, jobId: string) {
    return this.db_
      .collection(lois(surveyId))
      .where(l.jobId, '==', jobId)
      .get();
  }

  fetchSheetsConfig(surveyId: string) {
    return this.fetchDoc_(`${survey(surveyId)}/sheets/config`);
  }

  async fetchLoisSubmissions(
    surveyId: string,
    jobId: string,
    ownerId: string | undefined,
    page: number
  ) {
    const loisQuery = this.db_
      .collection(lois(surveyId))
      .where(l.jobId, '==', jobId)
      .orderBy(l.id);
    let submissionsQuery = this.db_
      .collection(submissions(surveyId))
      .where(sb.jobId, '==', jobId)
      .orderBy(sb.loiId);
    if (ownerId) {
      submissionsQuery = submissionsQuery.where(sb.ownerId, '==', ownerId);
    }
    const loisIterator = new QueryIterator(loisQuery, page, l.id);
    const submissionsIterator = new QueryIterator(
      submissionsQuery,
      page,
      sb.loiId
    );
    return leftOuterJoinSorted(
      loisIterator,
      loiDoc => loiDoc.get(l.id),
      submissionsIterator,
      submissionDoc => submissionDoc.get(sb.loiId)
    );
  }

  async insertLocationOfInterest(surveyId: string, loiDoc: DocumentData) {
    await this.db_.doc(survey(surveyId)).collection('lois').add(loiDoc);
  }

  async countSubmissionsForLoi(
    surveyId: string,
    loiId: string
  ): Promise<number> {
    const submissionsRef = this.db_.collection(submissions(surveyId));
    const submissionsForLoiQuery = submissionsRef.where(sb.loiId, '==', loiId);
    const snapshot = await submissionsForLoiQuery.count().get();
    return snapshot.data().count;
  }

  async updateSubmissionCount(surveyId: string, loiId: string, count: number) {
    const loiRef = this.db_.doc(loi(surveyId, loiId));
    await loiRef.update({[l.submissionCount]: count});
  }

  async updateLoiProperties(
    surveyId: string,
    loiId: string,
    loiDoc: DocumentData
  ) {
    const loiRef = this.db_.doc(loi(surveyId, loiId));
    await loiRef.update({[l.properties]: loiDoc[l.properties]});
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
