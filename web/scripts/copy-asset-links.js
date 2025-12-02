/**
 * Copyright 2025 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'fs';

// This script copies the assetLinks.json file from `web/keys/<project-id>/` to the local
// `dist/.well-known` folder before build. This file is used for Digital Asset Links
// to associate the web app with the Android app.

const project = process.env['npm_config_project'];

const assertLinksFilepath = `../keys/${project}/assetLinks.json`;

if (existsSync(assertLinksFilepath)) {
  const assertLinks = readFileSync(
    `../keys/${project}/assetLinks.json`,
    'utf8'
  );

  const wellKnownDir = '../dist/web/.well-known';

  if (!existsSync(wellKnownDir)) {
    mkdirSync(wellKnownDir, { recursive: true });
  }

  writeFileSync(`${wellKnownDir}/assetLinks.json`, assertLinks);
} else {
  console.warn('Warning: Missing assetLinks.json file');
}
