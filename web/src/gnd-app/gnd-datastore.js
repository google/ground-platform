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
