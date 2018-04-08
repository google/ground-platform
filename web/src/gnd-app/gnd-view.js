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
 * @author gmiceli@google.com (Gino Miceli)
 */ 

 class GndView {
  constructor(appElement) {
    this.appElement_ = appElement;
    this.componentsDeferred_ = new Deferred();
  }

  getComponentsPromise() {
    return this.componentsDeferred_.getPromise();
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