/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Serves the production bundles of the web console and the mobile app on two local ports, the way
// a static host would, except for two generated scripts that the harness supplies instead of the
// build:
//
// - `user-data-service-config.js` points the app at the user data service the tests started.
// - `firebase-config.js` turns Firebase and development sign-in off, so signing in uses the
//   service's development user (`FakeAuthClient`, `AUTH_MODE=dev`) without a role dialog.
//
// So the tests run the same bundles `wasmJsBrowserDistribution` produces for deployment, whatever
// USER_DATA_SERVICE_URL and FIREBASE_WEB_CONFIG they were built with.
//
// Usage: node harness/serve-apps.mjs (ports and the service URL come from harness/config.mjs).

import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { APPS, USER_DATA_SERVICE_URL } from './config.mjs';

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.wasm': 'application/wasm',
  '.json': 'application/json',
  '.map': 'application/json',
  '.css': 'text/css',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ttf': 'font/ttf',
  '.otf': 'font/otf',
  '.xml': 'application/xml',
  '.txt': 'text/plain; charset=utf-8',
};

const GENERATED_SCRIPTS = {
  '/user-data-service-config.js': `window.GROUND_USER_DATA_SERVICE_URL = ${JSON.stringify(
    USER_DATA_SERVICE_URL,
  )};\n`,
  '/firebase-config.js': 'window.GROUND_FIREBASE_CONFIG = null;\nwindow.GROUND_DEV_SIGN_IN = false;\n',
};

function serve(app) {
  if (!fs.existsSync(path.join(app.distDir, 'index.html'))) {
    console.error(`${app.name}: no bundle in ${app.distDir}. Run \`npm run build\` first.`);
    process.exit(1);
  }
  const server = http.createServer((req, res) => {
    const { pathname } = new URL(req.url, 'http://localhost');
    const generated = GENERATED_SCRIPTS[pathname];
    if (generated !== undefined) {
      res.writeHead(200, { 'content-type': MIME_TYPES['.js'], 'cache-control': 'no-store' });
      res.end(generated);
      return;
    }
    const relative = pathname === '/' ? 'index.html' : decodeURIComponent(pathname).slice(1);
    const file = path.resolve(app.distDir, relative);
    if (!file.startsWith(app.distDir + path.sep)) {
      res.writeHead(403).end();
      return;
    }
    fs.readFile(file, (error, data) => {
      if (error) {
        res.writeHead(404).end();
        return;
      }
      res.writeHead(200, {
        'content-type': MIME_TYPES[path.extname(file)] ?? 'application/octet-stream',
      });
      res.end(data);
    });
  });
  server.listen(app.port, '127.0.0.1', () =>
    console.log(`${app.name} bundle served at http://127.0.0.1:${app.port}/`),
  );
}

APPS.forEach(serve);
