class GndDatastore {
  constructor() {
    this.db_ = firebase.firestore();
    this.unsubscribeFromFeatureChanges_ = null;
  }

  unsubscribeFromFeatureChanges() {
    if (this.unsubscribeFromFeatureChanges_) {
      this.unsubscribeFromFeatureChanges_();
      this.unsubscribeFromFeatureChanges_ = null;
    }
  }

  subscribeToFeatureChanges(projectId, onChange, scope) {
    this.unsubscribeFromFeatureChanges();
    const featuresCollection =
    this.db_.collection(`projects/${projectId}/features`);
    this.unsubscribeFromFeatureChanges_ =
    featuresCollection.onSnapshot((snapshot) => {
      snapshot.docChanges.forEach((change) => {
        onChange(change.type, change.doc.id, change.doc.data());
      });
    });
  }

  loadProjectDefn(projectId) {
    return this.db_.collection('projects').doc(projectId).get()
      .then(projectDoc => 
        this.loadFeatureTypes_(projectDoc.ref)
          .then((featureTypes) => 
            ({project: projectDoc.data(), featureTypes: featureTypes})
        ));
  }

  loadFeatureTypes_(projectDoc) {
    return projectDoc.collection('featureTypes').get()
      .then(snapshot => snapshot.docs.map(doc => doc.data()));
  }
}
