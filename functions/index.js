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

const admin = require('firebase-admin');
const functions = require('firebase-functions');
const Auth = require('./common/auth');
const CloudFunctions = require('./cloud-functions')
const Datastore = require('./common/datastore')

// functions.config().firebase is auto-populated with configuration needed to
// initialize the firebase-admin SDK when deploying via Firebase CLI.
admin.initializeApp(functions.config().firebase);

const db = new Datastore(admin.firestore());
const auth = new Auth();
const fns = new CloudFunctions(db, auth);

exports.exportKml = functions.https.onRequest((req, res) =>
	fns.exportKml(req, res).catch(err => res.status(500).send(`${err}`)));

exports.exportCsv = functions.https.onRequest((req, res) =>
	fns.exportCsv(req, res).catch(err => res.status(500).send(`${err}`)));

// Test via shell:
// updateColumns.get('/?project=R06MucQJSWvERdE7SiL1&featureType=aaaaaaaa&form=1234567')
exports.updateColumns = functions.https.onRequest((req, res) => 
  fns.updateSpreadsheetColumns(req, res)
    .catch(err => res.status(500).send(`${err}`)));

// Test via shell:
// onCreateRecord({featureTypeId: 'households', formId: '1', responses: {'interviewer': 'Nikola Tesla'}}, {params: {projectId: 'R06MucQJSWvERdE7SiL1', featureId: 'p9lyePfXYPOByUFpnIVp', recordId: 'newRecord'}});
exports.onCreateRecord = functions.firestore
  .document('projects/{projectId}/features/{featureId}/records/{recordId}')
  .onCreate((change, context) => 
     fns.onCreateRecord(change, context));

// Test via shell:
// onUpdateRecord({after: {featureTypeId: 'households', formId: '1', responses: {'interviewer': 'George Washington'}}}, {params: {projectId: 'R06MucQJSWvERdE7SiL1', featureId: 'p9lyePfXYPOByUFpnIVp', recordId: 'newRecord'}});
exports.onUpdateRecord = functions.firestore
  .document('projects/{projectId}/features/{featureId}/records/{recordId}')
  .onUpdate((change, context) => 
     fns.onUpdateRecord(change, context));