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

// Builds what the tests run: the user data service binary and the production bundles of the web
// console and the mobile app. The builds share the included shared/ builds, so they run one after
// another. To rerun the tests against the last builds, run `npx playwright test` instead.
//
// Usage: node harness/build-apps.mjs

import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { GRADLE_BUILDS, GRADLE_WRAPPER, REPO_ROOT } from './config.mjs';

for (const { dir, task } of GRADLE_BUILDS) {
  console.log(`> ${path.relative(REPO_ROOT, dir)}: ./${GRADLE_WRAPPER} ${task}`);
  const result = spawnSync(path.join(dir, GRADLE_WRAPPER), [task, '--console=plain', '--quiet'], {
    cwd: dir,
    stdio: 'inherit',
    // gradlew.bat needs a shell on Windows.
    shell: process.platform === 'win32',
  });
  if (result.status !== 0) {
    console.error(`Building ${path.relative(REPO_ROOT, dir)} failed.`);
    process.exit(result.status ?? 1);
  }
}
