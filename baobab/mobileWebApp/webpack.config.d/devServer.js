/*
 * Copyright 2026 The Ground Authors.
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

// Bind webpack-dev-server to 0.0.0.0 and allow all host headers so the app
// can be accessed both via localhost:8090 and via remote workstation URLs.
if (config.devServer) {
  config.devServer.host = '0.0.0.0';
  config.devServer.allowedHosts = 'all';
  config.devServer.open = false;
  const path = require('path');
  const sharedUiComposeResources = path.resolve(
    __dirname,
    '../../../../../shared/ui/src/commonMain/composeResources'
  );
  const staticList = Array.isArray(config.devServer.static)
    ? config.devServer.static
    : config.devServer.static
      ? [config.devServer.static]
      : [];
  staticList.push({
    directory: sharedUiComposeResources,
    publicPath: '/composeResources/org.groundplatform.v2.core.forms.ui.resources',
  });
  config.devServer.static = staticList;

  // The dev server proxies the user data service, so the page calls its own origin and never
  // needs a route to the service: this works through port forwarding, the Cloudtop HTTPS proxy, or
  // a hostname alike, without mixed-content or CORS issues. The target is USER_DATA_SERVICE_URL from
  // the environment the dev server was started with, else from local.properties, re-read on every
  // request so edits take effect on the next page load. Production bundles have no dev server and
  // call the URL baked in at build time instead (see build.gradle.kts).
  const fs = require('fs');
  const readUserDataServiceUrl = () => {
    if (process.env.USER_DATA_SERVICE_URL) return process.env.USER_DATA_SERVICE_URL;
    let url = null;
    // baobab/local.properties, then this app's own local.properties, which takes precedence.
    for (const file of [
      path.resolve(__dirname, '../../../../../local.properties'),
      path.resolve(__dirname, '../../../../local.properties'),
    ]) {
      if (!fs.existsSync(file)) continue;
      for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
        const match = line.match(/^\s*USER_DATA_SERVICE_URL\s*[=:]\s*(.*?)\s*$/);
        // Java properties may escape ':' and '=' (e.g. http\://host).
        if (match) url = match[1].replace(/\\([:=])/g, '$1');
      }
    }
    return url || null;
  };

  // Tells the app to call the user data service through this dev server (its own origin), or that
  // none is configured, in which case the app shows setup instructions instead of starting.
  const previousSetupMiddlewares = config.devServer.setupMiddlewares;
  config.devServer.setupMiddlewares = (middlewares, devServer) => {
    const list = previousSetupMiddlewares
      ? previousSetupMiddlewares(middlewares, devServer)
      : middlewares;
    list.unshift({
      name: 'user-data-service-config',
      path: '/user-data-service-config.js',
      middleware: (req, res) => {
        res.setHeader('Content-Type', 'application/javascript');
        res.setHeader('Cache-Control', 'no-store');
        res.end(
          'window.GROUND_USER_DATA_SERVICE_URL = ' +
            (readUserDataServiceUrl() ? 'window.location.origin' : 'null') +
            ';\n'
        );
      },
    });
    return list;
  };

  const proxies = Array.isArray(config.devServer.proxy) ? config.devServer.proxy : [];
  proxies.push({
    // gRPC-Web calls: /<package>.<Service>/<Method>.
    context: (pathname) => pathname.startsWith('/groundplatform.v2.'),
    router: () => readUserDataServiceUrl() || 'http://127.0.0.1:9',
    changeOrigin: true,
    on: {
      // Answers with a gRPC-Web error the app shows to the user, instead of an opaque 502.
      error: (err, req, res) => {
        const target = readUserDataServiceUrl();
        const message = target
          ? `Can't reach the user data service at ${target} (${err.code || err.message}). ` +
            'Is it running?'
          : 'No user data service is configured (USER_DATA_SERVICE_URL).';
        console.error(`[user-data-service proxy] ${req.url}: ${message}`);
        if (res.headersSent || typeof res.writeHead !== 'function') return;
        res.writeHead(200, {
          'content-type': 'application/grpc-web+proto',
          'grpc-status': '14',
          'grpc-message': encodeURIComponent(message),
        });
        res.end();
      },
    },
  });
  config.devServer.proxy = proxies;
}
