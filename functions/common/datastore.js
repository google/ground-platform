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

'use strict';

const { firestore } = require('firebase-admin');

class Datastore {
  constructor(db) {
    this.db_ = db;
  }

  /**
   * Stores user email, name, and avatar to db for use in application features.
   * These attributes are merged with other existing ones if already present.
   */
  mergeUserProfile({ uid, email, displayName, photoURL }) {
    return this.db_.doc(`users/${uid}`)
      .set({ uid, email, displayName, photoURL }, { merge: true });
  }

  fetch_(docRef) {
    return docRef.get().then(doc => doc.exists ? doc.data() : null);
  }

  fetchDoc_(path) {
    return this.fetch_(this.db_.doc(path));
  }

  fetchCollection_(path) {
    return this.db_.collection(path).get();
  }

  fetchProject(projectId) {
    return this.db_.doc(`projects/${projectId}`).get();
  }

  fetchRecord(projectId, featureId, recordId) {
    return this.fetchDoc_(
      `projects/${projectId}/features/${featureId}/records/${recordId}`);
  }

  fetchRecords(projectId, featureId) {
    return this.fetchCollection_(`projects/${projectId}/records`);
  }

  fetchFeature(projectId, featureId) {
    return this.fetchDoc_(`projects/${projectId}/features/${featureId}`);
  }

  fetchFeatures(projectId) {
    return this.fetchCollection_(`projects/${projectId}/features`);
  }

  fetchForm(projectId, featureTypeId, formId) {
    return this.fetchDoc_(
      `projects/${projectId}/featureTypes/${featureTypeId}/forms/${formId}`);
  }

  fetchSheetsConfig(projectId) {
    return this.fetchDoc_(`projects/${projectId}/sheets/config`);
  }

  insertFeature(projectId, layerId, {caption, location}) {
    var docRef = this.db_.collection('projects').doc(projectId);
    return docRef.get().then((docSnapshot) => {
      if (docSnapshot.exists) {
        docRef.collection('features').add({
          layerId: layerId,
          caption: caption,
          location: location
        })
        console.log("Feature successfully written!");
      } else {
        console.log("Project " + projectId + 
          " does not exist! Please make sure project exists before adding features!");
        // For debug/test reasons uncomment below to add the project.
        docRef.set({project: projectId}); 
        docRef.collection('features').add({
          layerId: layerId,
          caption: caption,
          location: location
        })
        console.log("Feature successfully written!");
        // TODO(tiyara): For prod throw a 404 error.
      }
    }).catch(function (error) {
      console.log("Error getting document:", error);
    });
  }

  // Function to test local firestore instance.
  hardcodedInsert() {
    return this.db_.collection('test').doc('1234').set({ testdoc: 123 })
      .then(function () {
        console.log("Hardcoded value inserted successfully!");
      })
      .catch(function (error) {
        console.error("Error writing feature: ", error);
      });
  }
}

module.exports = Datastore;