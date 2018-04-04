/**
 * @customElement
 * @polymer
 */
class GndApp extends Polymer.Element {
  constructor() {
    super();
    this.model_ = new GndModel(new GndDatastore());
    this.view_ = new GndView(this);
    this.presenter_ = new GndPresenter(this.model_, this.view_);
  }

  static get is() { return 'gnd-app'; }

  static get properties() {
    return {
      projectId: {
        type: String
      },
      basePath: {
        type: String
      }
    };
  }

  connectedCallback() {
    super.connectedCallback();
    this.view_.setComponents({
      app: this,
      panel: this.$.panel,
      map: this.$.map
    });
  }

  getBasePath_(projectId) {
    return `/projects/${projectId}`;
  }
}

window.customElements.define(GndApp.is, GndApp);