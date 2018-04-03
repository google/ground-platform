'use strict';

// The Cloud Functions for Firebase SDK to create Cloud Functions and setup triggers.
const functions = require('firebase-functions');

// The Firebase Admin SDK to access the Firebase Realtime Database. 
const admin = require('firebase-admin');
const GndSheets = require('./gnd-sheets');
const GndAuth = require('./gnd-auth');
const GndDatastore = require('./gnd-datastore')

admin.initializeApp(functions.config().firebase);

// TODO: Use firebase functions:config:set to configure your googleapi object:
// googleapi.sheet_id = Google Sheet id (long string in middle of sheet URL)
const CONFIG_SHEET_ID = functions.config().googleapi.sheet_id;

const auth = new GndAuth(admin.database());
const db = new GndDatastore(admin.firestore());
const sheet = new GndSheets(auth, CONFIG_SHEET_ID);

// Debug function.
function dump(obj) {
  console.log(JSON.stringify(obj, null, '\t'));
}

// Test:
// updateColumns.get('/?project=R06MucQJSWvERdE7SiL1&featureType=aaaaaaaa&form=1234567')
exports.updateColumns = functions.https.onRequest((req, res) => {
  const {
    project: projectId,
    featureType: featureTypeId,
    form: formId
  } = req.query;
  return sheet.getColumnsByElementId().then(colResp => {
    if (!colResp) {
      res.status(500).send('Could not fetch existing sheet columns');
      return;
    }
    const mds = colResp.matchedDeveloperMetadata || [];
    // TODO: Use actual max startIndex, not len.
    const insertAt = mds.length == 0 ? 0 : 
      mds[mds.length-1].developerMetadata.location.dimensionRange.endIndex;
      dump(mds);
    const existingColumns = mds.map(md => md.developerMetadata.metadataValue);
    db.fetchForm(projectId, featureTypeId, formId).then(form => {
      if (!form) {
        res.status(400).send('Form definition not found');
        return;
      }
      if (!form.elements || form.elements.length ==0) {
        res.status(500).send('Form definition contains no elements');
        return;
      }
      // TODO: Update existing column headings when label changes.
      return sheet
        .updateColumns(existingColumns, form.elements, insertAt)
        .then(cnt => res.send(`${cnt} columns added`));
    });
  });
});

// Test:
// onCreateRecord({featureTypeId: 'aaaaaaaa', formId: '1234567', responses: {'abcxyz0001': ['a','b','c']}}, {params: {projectId: 'R06MucQJSWvERdE7SiL1', featureId: '4TXn12fhWgHLRDJxghVY', recordId: 'Dgdie0FEDMgdAdHXnGtg'}});
exports.onCreateRecord = functions.firestore
  .document('projects/{projectId}/features/{featureId}/records/{recordId}')
  .onCreate(event => {
    const {projectId, featureId, recordId} = event.params;
    const record = event.data.data();
    const {featureTypeId, formId} = record;
    return sheet.getColumnIds().then(colIds => {
        if (colIds.length == 0) {
          console.log("No columns to update");
          return;
        }
        return db.fetchFeature(projectId, featureId).then(feature =>  
          sheet.addRow(feature, recordId, record, colIds));
      });
    });

// onUpdateRecord({before: {}, after: {featureTypeId: 'aaaaaaaa', formId: '1234567', responses: {'abcxyz0001': 'Newer place'}}}, {params: {projectId: 'R06MucQJSWvERdE7SiL1', featureId: '4TXn12fhWgHLRDJxghVY', recordId: 'Dgdie0FEDMgdAdHXnGtg'}});
exports.onUpdateRecord = functions.firestore
  .document('projects/{projectId}/features/{featureId}/records/{recordId}')
  .onUpdate(event => {
    const {projectId, featureId, recordId} = event.params;
    const record = event.data.data();
    dump(record);
    const {featureTypeId, formId} = record;
    return sheet.getColumnIds().then(colIds => {
        if (colIds.length == 0) {
          console.log("No columns to update");
          return;
        }
        return db.fetchFeature(projectId, featureId).then(feature => 
          sheet.updateRow(feature, recordId, record, colIds));
      });
    });

// visit the URL for this Function to request tokens
exports.authgoogleapi = functions.https.onRequest(auth.authgoogleapi.bind(auth));

// after you grant access, you will be redirected to the URL for this Function
// this Function stores the tokens to your Firebase database
exports.oauthcallback = functions.https.onRequest(auth.oauthcallback.bind(auth));
