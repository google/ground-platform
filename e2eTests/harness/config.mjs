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

// Paths and ports shared by the harness scripts and playwright.config.ts. Ports avoid the
// development defaults (8080, 8092, 8093, 9010, 9020), so the tests can run while the launchpad is
// up; set E2E_SERVER_PORT, E2E_WEB_CONSOLE_PORT, E2E_MOBILE_APP_PORT, E2E_SPANNER_EMULATOR_PORT,
// or E2E_SPANNER_EMULATOR_REST_PORT to move them.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

const port = (name, fallback) => Number(process.env[name] || fallback);

// E2E_USER_DATA_SERVICE_PORT is the variable's name from before the server was renamed.
export const SERVER_PORT = port('E2E_SERVER_PORT', port('E2E_USER_DATA_SERVICE_PORT', 18180));
export const SERVER_URL = `http://127.0.0.1:${SERVER_PORT}`;

/** The Spanner emulator that stores the server's data (gRPC and REST ports). */
export const SPANNER_EMULATOR_PORT = port('E2E_SPANNER_EMULATOR_PORT', 18110);
export const SPANNER_EMULATOR_REST_PORT = port('E2E_SPANNER_EMULATOR_REST_PORT', 18120);
export const SPANNER_EMULATOR_SCRIPT = path.join(REPO_ROOT, 'scripts/spanner-emulator.sh');

const isWindows = process.platform === 'win32';

/** The Collect One server binary that `installDist` builds. */
export const SERVER_BINARY = path.join(
  REPO_ROOT,
  'server/build/install/c1-server/bin',
  isWindows ? 'c1-server.bat' : 'c1-server',
);

const hasModule = (dir) => fs.existsSync(path.join(REPO_ROOT, dir, 'build.gradle.kts'));

/** The apps under test that exist in the current checkout, served from their production bundles. */
export const APPS = [
  ...(hasModule('webApp')
    ? [
        {
          name: 'webConsole',
          gradleTask: ':webApp:wasmJsBrowserDistribution',
          port: port('E2E_WEB_CONSOLE_PORT', 18192),
          distDir: path.join(REPO_ROOT, 'webApp/build/dist/wasmJs/productionExecutable'),
        },
      ]
    : []),
  ...(hasModule('mobileWebApp')
    ? [
        {
          name: 'mobileApp',
          gradleTask: ':mobileWebApp:wasmJsBrowserDistribution',
          port: port('E2E_MOBILE_APP_PORT', 18193),
          distDir: path.join(REPO_ROOT, 'mobileWebApp/build/dist/wasmJs/productionExecutable'),
        },
      ]
    : []),
];

/** The Gradle tasks that build what the tests run, in the repository's root build. */
export const GRADLE_TASKS = [':server:installDist', ...APPS.map((app) => app.gradleTask)];

export const GRADLE_WRAPPER = isWindows ? 'gradlew.bat' : 'gradlew';

export const appUrl = (name) => `http://127.0.0.1:${APPS.find((a) => a.name === name).port}`;
