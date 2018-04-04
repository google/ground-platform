class GndView {
  constructor(appElement) {
    this.appElement_ = appElement;
    this.componentsDeferred_ = new Deferred();
  }

  getComponentsPromise() {
    return this.componentsDeferred_.promise;
  }

  setComponents(components) {
    this.componentsDeferred_.resolve(components);
  }

  addRouteChangeListener(callback) {
    this.addEventListener_('route-change', callback);
  }

  addEventListener_(eventName, callback) {
    this.appElement_.addEventListener(eventName, callback);
  }

  removeEventListeners() {
    // TODO remove listeners, call when app element is disconnected.
    // "It is important to remove the event listener in disconnectedCallback()
    // to prevent memory leaks."
  }
}