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

"use strict";

class Datastore {
  constructor(db) {
    this.db_ = db;
  }

  /**
   * Stores user email, name, and avatar to db for use in application features.
   * These attributes are merged with other existing ones if already present.
   */
  mergeUserProfile({ uid, email, displayName, photoURL }) {
    return this.db_
      .doc(`users/${uid}`)
      .set({ uid, email, displayName, photoURL }, { merge: true });
  }

  fetch_(docRef) {
    return docRef.get().then((doc) => (doc.exists ? doc.data() : null));
  }

  fetchDoc_(path) {
    return this.fetch_(this.db_.doc(path));
  }

  fetchCollection_(path) {
    return this.db_.collection(path).get();
  }

  fetchSurvey(surveyId) {
    return this.db_.doc(`surveys/${surveyId}`).get();
  }

  fetchRecord(surveyId, featureId, recordId) {
    return this.fetchDoc_(
      `surveys/${surveyId}/features/${featureId}/records/${recordId}`
    );
  }

  fetchObservationsByJobId(surveyId, jobId) {
    return this.db_
      .collection(`surveys/${surveyId}/observations`)
      .where("jobId", "==", jobId)
      .get();
  }

  fetchFeature(surveyId, featureId) {
    return this.fetchDoc_(`surveys/${surveyId}/features/${featureId}`);
  }

  fetchFeaturesByJobId(surveyId, jobId) {
    return this.db_
      .collection(`surveys/${surveyId}/features`)
      .where("jobId", "==", jobId)
      .get();
  }

  fetchTask(surveyId, featureTypeId, taskId) {
    return this.fetchDoc_(
      `surveys/${surveyId}/featureTypes/${featureTypeId}/tasks/${taskId}`
    );
  }

  fetchSheetsConfig(surveyId) {
    return this.fetchDoc_(`surveys/${surveyId}/sheets/config`);
  }

  async insertFeature(surveyId, feature) {
    const docRef = await this.db_.collection("surveys").doc(surveyId);
    const doc = await docRef.get();
    if (!doc.exists) {
      throw new Error(`/surveys/${surveyId} not found`);
    }
    await docRef.collection("features").add(feature);
  }
}

module.exports = Datastore;
