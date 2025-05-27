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

import fs from 'fs';
import fse from 'fs-extra';
import path from 'path';
import { fileURLToPath } from 'url';

// This script the English language build of the web application from `dist/web/en`
// to `dist/web`. This is useful for single-language deployments or when
// the default language should be served directly from the root web path.

// __dirname workaround for ES modules
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const srcDir = path.join(__dirname, '..', 'dist', 'web', 'en');
const destDir = path.join(__dirname, '..', 'dist', 'web');

async function copyEnToRoot() {
  try {
    if (fs.existsSync(srcDir)) {
      await fse.copy(srcDir, destDir, { overwrite: true });
      await fse.rm(srcDir, { recursive: true });
      console.log('Copied English build to root');
    } else {
      console.warn('English build folder not found at:', srcDir);
    }
  } catch (err) {
    console.error('Failed to copy English build:', err);
    process.exit(1);
  }
}

copyEnToRoot();
