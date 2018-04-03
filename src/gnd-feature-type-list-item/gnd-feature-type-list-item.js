/**
 * @customElement
 * @polymer
 */
class GndFeatureTypeListItem extends Polymer.Element {

  static get is() { return 'gnd-feature-type-list-item'; }

  static get properties() {
    return {
      featureTypeId: String,
      icon: String,
      basePath: String,
      page: {
        type: String,
        notify: true
      }
    };
  }

  getIconPath_(icon) {
    return 'feature-icons:' + icon;
  }

  getFeatureListUri_(featureTypeId) {
    return `${this.basePath}/${featureTypeId}`;
  }
}

window.customElements.define(GndFeatureTypeListItem.is, GndFeatureTypeListItem);