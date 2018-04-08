'use strict';

class GndDatastore {
  constructor(db) {
    this.db_ = db;
  }

  fetch_(docRef) {
    return docRef.get().then(doc => doc.exists ? doc.data() : null);
  }

  fetchDoc_(path) {
    return this.fetch_(this.db_.doc(path));    
  }

  fetchRecord(projectId, featureId, recordId) {
    return this.fetchDoc_(`projects/${projectId}/features/${featureId}/records/${recordId}`);
  }

  fetchForm(projectId, featureTypeId, formId) {
    return this.fetchDoc_(`projects/${projectId}/featureTypes/${featureTypeId}/forms/${formId}`);
  }

  fetchFeature(projectId, featureId) {
    return this.fetchDoc_(`projects/${projectId}/features/${featureId}`);
  }

  fetchSheetsConfig(projectId) {
    return this.fetchDoc_(`projects/${projectId}/sheets/config`);
  }
}

module.exports = GndDatastore;