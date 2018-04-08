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

/**
 * @author gmiceli@google.com (Gino Miceli)
 */ 
 
 'use strict';

const admin = require('firebase-admin');
const functions = require('firebase-functions');
const GndAuth = require('./gnd-auth');
const GndCloudFunctions = require('./gnd-cloud-functions')
const GndDatastore = require('./gnd-datastore')

// functions.config().firebase is auto-populated with configuration needed to
// initialize the firebase-admin SDK when deploying via Firebase CLI.
admin.initializeApp(functions.config().firebase);

const db = new GndDatastore(admin.firestore());
const auth = new GndAuth();
const gnd = new GndCloudFunctions(db, auth);

// Test via shell:
// updateColumns.get('/?project=R06MucQJSWvERdE7SiL1&featureType=aaaaaaaa&form=1234567')
exports.updateColumns = functions.https.onRequest((req, res) => 
  gnd.updateSpreadsheetColumns(req, res)
    .catch(err => res.status(500).send(`${err}`)));

// Test via shell:
// onCreateRecord({featureTypeId: 'households', formId: '1', responses: {'interviewer': 'Nikola Tesla'}}, {params: {projectId: 'R06MucQJSWvERdE7SiL1', featureId: 'p9lyePfXYPOByUFpnIVp', recordId: 'newRecord'}});
exports.onCreateRecord = functions.firestore
  .document('projects/{projectId}/features/{featureId}/records/{recordId}')
  .onCreate((change, context) => 
     gnd.onCreateRecord(change, context));

// Test via shell:
// onUpdateRecord({after: {featureTypeId: 'households', formId: '1', responses: {'interviewer': 'George Washington'}}}, {params: {projectId: 'R06MucQJSWvERdE7SiL1', featureId: 'p9lyePfXYPOByUFpnIVp', recordId: 'newRecord'}});
exports.onUpdateRecord = functions.firestore
  .document('projects/{projectId}/features/{featureId}/records/{recordId}')
  .onUpdate((change, context) => 
     gnd.onUpdateRecord(change, context));