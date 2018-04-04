class FeatureCache {
  constructor() {
    this.featuresByIdByType_ = {};
  }

  reset() {
    this.featuresByIdByType_ = {};    
  }

  // getFeaturesByType(featureType) {
  //   return this.featuresByIdByType_[featureType];
  // }

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