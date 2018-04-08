'use strict';

const admin = require('firebase-admin');
const functions = require('firebase-functions');
const util = require('util');
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