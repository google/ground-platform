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
            feature.center.latitude, feature.center.longitude, 'house-map-marker');
        } else if (changeType === 'removed') {
          components.map.removeMarker(featureId);
        }
      } catch (e) {
        console.error('Invalid feature (' + featureId + ') in db:', e.message);
      }
    });
  }
}