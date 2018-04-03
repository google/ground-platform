'use strict';

class GndDatastore {
  constructor(db) {
    this.db_ = db;
  }

  fetch_(docRef) {
    return docRef.get().then(doc => doc.exists ? doc.data() : null);
  }

  fetchRecord(projectId, featureId, recordId) {
    return this.fetch_(this.db_.doc(`projects/${projectId}/features/${featureId}/records/${recordId}`));
  }

  fetchForm(projectId, featureTypeId, formId) {
    return this.fetch_(this.db_.doc(`projects/${projectId}/featureTypes/${featureTypeId}/forms/${formId}`));
  }

  fetchFeature(projectId, featureId) {
    return this.fetch_(this.db_.doc(`projects/${projectId}/features/${featureId}`));
  }
}

module.exports = GndDatastore;