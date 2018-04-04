/**
 * @customElement
 * @polymer
 */
class GndPanelOverlay extends Polymer.Element {

  // TODO: Rename GndPanel?
  static get is() { return 'gnd-panel-overlay'; }

  static get properties() {
    return {
      page: {
        type: String
      },
      basePath: {
        type: String
      },
      projectDefn: {
        type: Object
      },
      selectedFeatureType: {
        type: String
      },
      selectedFeatureTypeDefn: {
        type: Object,
        computed: 'getFeatureTypeDefn_(projectDefn, selectedFeatureType)'
      }
    }
  }

  getFeatureTypeDefn_(projectDefn, selectedFeatureType) {
    return projectDefn.featureTypes.find(e => e.pathKey === selectedFeatureType);
  }
}

window.customElements.define(GndPanelOverlay.is, GndPanelOverlay);