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

 class GndModel {
  constructor(datastore) {
    this.datastore_ = datastore;
    // TODO: Define business model classes for objects exposed by this class
    // and the datastore. These can be consumed by the view but need not follow
    // the structure of how objects are stored in the datastore.
    this.activeProjectId_ = null;
    this.selectedFeatureType_ = null;
    this.projectDefn_ = {featureTypes: []};
    this.featureChangeListener_ = null; 
    this.featureCache_ = new FeatureCache();
  }

  setFeatureChangeListener(featureChangeListener) {
    this.featureChangeListener_ = featureChangeListener;
  }

  activateProject(projectId, onFeatureChange) {
    if (this.activeProjectId_ == projectId) {
      return Promise.resolve(this.projectDefn_);
    }
    this.datastore_.unsubscribeFromFeatureChanges();
    return this.datastore_.loadProjectDefn(projectId).then((projectDefn) => {
      this.featureCache_.reset();
      this.activeProjectId_ = projectId;
      this.selectedFeatureType_ = null;
      this.projectDefn_ = projectDefn;
      this.featureChangeListener_ = onFeatureChange;
      this.datastore_.subscribeToFeatureChanges(projectId, 
        this.onFeatureChange_.bind(this));
      return projectDefn;
    }, this);
  }

  onFeatureChange_(changeType, featureId, feature) {
    // console.log(`Feature ${featureId} ${changeType}: `, feature);
    // TODO: make feature type dynamic.
    // TODO: Use feature type id instead of short name.
    // TODO: Abstract Firebase "changeType"?
    if (changeType === 'added' || changeType === 'modified') {
      this.featureCache_.putFeature('casas', featureId, feature);
    } else if (changeType === 'removed') {
      this.featureCache_.removeFeature('casas', featureId);
    }
    this.featureChangeListener_(changeType, featureId, feature);
  }

  selectFeatureType(featureType) {
    this.selectedFeatureType_ = featureType;
  }
}