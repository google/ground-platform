'use strict';

// The Cloud Functions for Firebase SDK to create Cloud Functions and setup triggers.
const functions = require('firebase-functions');

// The Firebase Admin SDK to access the Firebase Realtime Database. 
const admin = require('firebase-admin');
const GndSheets = require('./gnd-sheets');
const GndAuth = require('./gnd-auth');
const GndDatastore = require('./gnd-datastore')

// functions.config().firebase is auto-populated with configuration needed to
// initialize the firebase-admin SDK when deploying via Firebase CLI.
admin.initializeApp(functions.config().firebase);

// TODO: Use firebase functions:config:set to configure your googleapi object:
// googleapi.sheet_id = Google Sheet id (long string in middle of sheet URL)
const CONFIG_SHEET_ID = '12qqPuHs7UYN0HlwfxDl3HtO-8ushkGOqs8_5p6lPwAM';

const db = new GndDatastore(admin.firestore());
const auth = new GndAuth();

// TODO: Make dynamic
const sheet = new GndSheets(auth, CONFIG_SHEET_ID);

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
// onCreateRecord({featureTypeId: 'households', formId: '1', responses: {'interviewer': 'Nikola Tesla'}}, {params: {projectId: 'R06MucQJSWvERdE7SiL1', featureId: 'p9lyePfXYPOByUFpnIVp', recordId: 'newRecord'}});
exports.onCreateRecord = functions.firestore
  .document('projects/{projectId}/features/{featureId}/records/{recordId}')
  .onCreate((change, context) => {
    const {projectId, featureId, recordId} = context.params;
    const record = change.data();
    const {featureTypeId, formId} = record;
    return sheet.getColumnIds().then(colIds => {
        if (colIds.length == 0) {
          console.log("No columns to update");
          return;
        }
        return db.fetchFeature(projectId, featureId).then(feature => {
          if (!feature) {
            console.log('Feature not found: ', featureId, ' Project: ', projectId);
            return;
          }
          sheet.addRow(feature, recordId, record, colIds);
        });
      });
    });

// onUpdateRecord({after: {featureTypeId: 'households', formId: '1', responses: {'interviewer': 'George Washington'}}}, {params: {projectId: 'R06MucQJSWvERdE7SiL1', featureId: 'p9lyePfXYPOByUFpnIVp', recordId: 'newRecord'}});
exports.onUpdateRecord = functions.firestore
  .document('projects/{projectId}/features/{featureId}/records/{recordId}')
  .onUpdate((change, context) => {
    const {projectId, featureId, recordId} = context.params;
    const record = change.after.data();
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