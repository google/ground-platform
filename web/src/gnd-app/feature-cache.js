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

/**
 * @author gmiceli@google.com (Gino Miceli)
 */ 

 class FeatureCache {
  constructor() {
    this.featuresByIdByType_ = {};
  }

  reset() {
    this.featuresByIdByType_ = {};    
  }

  putFeature(featureType, featureId, feature) {
    let featuresById = this.featuresByIdByType_[featureType] || {};
    featuresById[featureId] = feature;
    this.featuresByIdByType_[featureType] = featuresById;
  }

  removeFeature(featureType, featureId) {
    let featuresById = this.featuresByIdByType_[featureType];
    if (featuresById) {
      delete featuresById[featureId];
    }
  }
}