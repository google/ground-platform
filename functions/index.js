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

const functions = require('firebase-functions')
const onCreateUser = require('./on-create-user')
const exportCsv = require('./export-csv')
const exportKml = require('./export-kml')
const importCsv = require('./import-csv')
const updateColumns = require('./update-columns')
const onCreateRecord = require('./on-create-record')
const onUpdateRecord = require('./on-update-record')

// Create user profile in database when user first logs in.
exports.onCreateUser = functions.auth.user().onCreate(onCreateUser);

exports.exportCsv = functions.https.onRequest(
    (req, res) =>
        exportCsv(req, res).catch(err => res.status(500).send(`${err}`)));
exports.exportKml = functions.https.onRequest(
    (req, res) =>
        exportKml(req, res).catch(err => res.status(500).send(`${err}`)));

// Test via shell:
// updateColumns.get('/?project=R06MucQJSWvERdE7SiL1&featureType=aaaaaaaa&form=1234567')
exports.updateColumns = functions.https.onRequest(
    (req, res) =>
        updateColumns(req, res).catch(err => res.status(500).send(`${err}`)));

// Test via shell:
// onCreateRecord({featureTypeId: 'households', formId: '1', responses: {'interviewer': 'Nikola Tesla'}}, {params: {projectId: 'R06MucQJSWvERdE7SiL1', featureId: 'p9lyePfXYPOByUFpnIVp', recordId: 'newRecord'}});
exports.onCreateRecord =
    functions.firestore
        .document(
            'projects/{projectId}/features/{featureId}/records/{recordId}')
        .onCreate((change, context) => onCreateRecord(change, context));

// Test via shell:
// onUpdateRecord({after: {featureTypeId: 'households', formId: '1', responses: {'interviewer': 'George Washington'}}}, {params: {projectId: 'R06MucQJSWvERdE7SiL1', featureId: 'p9lyePfXYPOByUFpnIVp', recordId: 'newRecord'}});
exports.onUpdateRecord =
    functions.firestore
        .document(
            'projects/{projectId}/features/{featureId}/records/{recordId}')
        .onUpdate((change, context) => onUpdateRecord(change, context));

exports.importCsv = functions.https.onRequest(importCsv);