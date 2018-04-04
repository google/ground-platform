/**
 * @customElement
 * @polymer
 */
class GndFeatureTypeList extends Polymer.Element {

  static get is() { return 'gnd-feature-type-list'; }

  static get properties() {
    return {
      basePath: String,
      featureTypes: Array
    };
  }
}

window.customElements.define(GndFeatureTypeList.is, GndFeatureTypeList);