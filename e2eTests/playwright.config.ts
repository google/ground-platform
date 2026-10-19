/*
 * Copyright 2026 The Ground Authors.
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

import { defineConfig } from '@playwright/test';
import {
  APPS,
  USER_DATA_SERVICE_BINARY,
  USER_DATA_SERVICE_PORT,
  USER_DATA_SERVICE_URL,
  appUrl,
} from './harness/config.mjs';

export default defineConfig({
  testDir: './tests',
  // Goldens are shared by every OS: Compose draws text with its bundled fonts in Skia (compiled to
  // Wasm), and Chromium rasterizes WebGL with its bundled SwiftShader, so the pixels don't depend on
  // the host's fonts or GPU.
  snapshotPathTemplate: '{testDir}/../goldens/{testFileName}/{arg}{ext}',
  outputDir: './build/test-results',
  reporter: [['list'], ['html', { outputFolder: './build/report', open: 'never' }]],
  forbidOnly: !!process.env.CI,
  // Tests share a single local userDataService instance and reset its state before each test.
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: {
    timeout: 20_000,
    toHaveScreenshot: { maxDiffPixelRatio: 0.001 },
  },
  use: {
    deviceScaleFactor: 1,
    locale: 'en-US',
    timezoneId: 'UTC',
    colorScheme: 'light',
    launchOptions: {
      // Software WebGL, the same on every machine.
      args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--force-color-profile=srgb'],
    },
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'webConsole',
      testMatch: 'webConsole.spec.ts',
      use: { baseURL: appUrl('webConsole'), viewport: { width: 1280, height: 800 } },
    },
    {
      name: 'mobileApp',
      testMatch: 'mobileApp.spec.ts',
      // A typical Android phone. `?chrome=0` in the tests hides the workbench around the app.
      use: { baseURL: appUrl('mobileApp'), viewport: { width: 412, height: 915 }, hasTouch: true },
    },
  ],
  webServer: [
    {
      name: 'userDataService',
      command: `"${USER_DATA_SERVICE_BINARY}"`,
      url: `${USER_DATA_SERVICE_URL}/healthz`,
      env: {
        PORT: String(USER_DATA_SERVICE_PORT),
        USER_DATA_STORE: 'memory',
        AUTH_MODE: 'dev',
      },
      reuseExistingServer: false,
      timeout: 60_000,
    },
    {
      name: 'apps',
      command: 'node harness/serve-apps.mjs',
      url: `${appUrl(APPS[0].name)}/index.html`,
      reuseExistingServer: false,
    },
  ],
});
