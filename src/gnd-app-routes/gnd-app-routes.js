/**
 * @customElement
 * @polymer
 */
class GndAppRoutes extends Polymer.Element {
  constructor() {
    super();
  }
  static get is() { return 'gnd-app-routes'; }

  static get properties() {
    return {
      route: {
        type: Object,
        observer: 'onRouteChange_'
      }
    };
  }

  onRouteChange_(val) {
    this.dispatchEvent(new CustomEvent('route-change', {
      detail: {route: this.parseRoute_(val.path)},
      bubbles: true, composed: true }));
  }

  parseRoute_(path) {
    let parts = path.split('/').reverse();
    parts.pop(); // Empty string before first '/'.
    let route = {mainView: parts.pop()};
    if (route.mainView == 'projects') {
      route.page = parts.length - 1;
      route.projectId = parts.pop();
      route.featureType = parts.pop();
      route.featureId = parts.pop();
    }
    return route;
  }
}

window.customElements.define(GndAppRoutes.is, GndAppRoutes);