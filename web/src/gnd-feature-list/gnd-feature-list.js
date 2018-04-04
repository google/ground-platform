/**
 * @customElement
 * @polymer
 */
class GndFeatureList extends Polymer.Element {

  static get is() { return 'gnd-feature-list'; }

  static get properties() {
    return {
      basePath: {
        type: String
      },
      featureType: {
        type: Object
      }
    };
  }
}

window.customElements.define(GndFeatureList.is, GndFeatureList);