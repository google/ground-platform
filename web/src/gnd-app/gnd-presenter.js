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
 * Implements the Presenter in "passive view" MVP.
 */
class GndPresenter {
  constructor(model, view) {
    this.model_ = model;
    this.view_ = view;
    // Event listeners get added in the constructor rather than in attachView()
    // because events can be triggered before the root app element is connected.
    this.view_.addRouteChangeListener(this.onRouteChange_.bind(this));
    this.model_.setFeatureChangeListener(this.onFeatureChange_.bind(this))
  }

  onRouteChange_(event) {
    this.view_.getComponentsPromise().then(components => {
      const {projectId, featureType, page}  = event.detail.route;
      this.model_.activateProject(projectId, this.onFeatureChange_.bind(this)).then((projectDefn) => {
        components.app.set('projectId', projectId);
        components.panel.set('projectDefn', projectDefn);
        this.model_.selectFeatureType(featureType);
        // If selected type changes, load feature summaries into list.
        components.panel.set('selectedFeatureType', featureType);
        components.panel.set('page', page);
      });
    });
  }

  onFeatureChange_(changeType, featureId, feature) {
    this.view_.getComponentsPromise().then(components => {
      try {
        if (changeType === 'added' || changeType === 'modified') {
          components.map.addOrUpdateMarker(featureId,
            feature.center.latitude, feature.center.longitude, 'star-circle');
        } else if (changeType === 'removed') {
          components.map.removeMarker(featureId);
        }
      } catch (e) {
        console.error('Invalid feature (' + featureId + ') in db:', e.message);
      }
    });
  }
}