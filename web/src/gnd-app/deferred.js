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
 * @author Gino Miceli <gmiceli@google.com>
 */

/**
 * A wrapper around Promise that allows it to be resolved outside of the scope
 * where the Promise was created.
 */
class Deferred {
  constructor() {
    this.onResolve_ = null;
    this.onReject_ = null;
    this.promise_ = new Promise(function(resolve, reject) {
      this.onResolve_ = resolve;
      this.onReject_ = reject;
    }.bind(this));
  }

  getPromise() {
    return this.promise_;
  }

  resolve(result) {
    return this.onResolve_(result);
  }

  reject(reason) {
    this.onReject_(reason);
  }
}