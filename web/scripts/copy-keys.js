/**
 * Copyright 2023 The Ground Authors.
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

import { readFileSync, writeFileSync } from 'fs';

// This script copies keys from `web/keys/<project-id>/firebase-config.js` to the local 
// `environments` folder before build. It expects the keys to be copied verbatim from
// the "CDN" tab of the web app in the Firebase console.

const config = process.env['npm_config_config'];

// Keys are already provided by local emu config.
if (config === 'local') process.exit(0);

const project = process.env['npm_config_project'];
const originalConfig = readFileSync(`../keys/${project}/firebase-config.js`, 'utf8');
const updatedConfig = originalConfig.replace('const firebaseConfig', 'export const firebaseConfig');
writeFileSync('../src/environments/.firebase-config.ts', updatedConfig);