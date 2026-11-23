/*
 * Copyright 2026 The Collect One Authors.
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

// Bundles the worker that runs the local database (shared/app/data/web/sqlite-worker/), which
// BrowserSqlDriverFactory starts with `new Worker(new URL("collect-one-sqlite-worker/...",
// import.meta.url))`. Webpack emits the worker and its SQLite WebAssembly build as separate assets.
(() => {
  const path = require('path');
  config.resolve = config.resolve || {};
  // __dirname is the app's package in the root build's npm workspace, build/wasm/packages/<package>.
  config.resolve.alias = Object.assign({}, config.resolve.alias, {
    'collect-one-sqlite-worker': path.resolve(
      __dirname,
      '../../../../shared/app/data/web/sqlite-worker'
    ),
  });
  // The worker lives outside this build, so resolve its `@sqlite.org/sqlite-wasm` import from the
  // build's own node_modules.
  config.resolve.modules = (config.resolve.modules || ['node_modules']).concat(
    path.resolve(__dirname, '../../node_modules')
  );
})();
