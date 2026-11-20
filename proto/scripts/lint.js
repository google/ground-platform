/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const { execSync } = require('child_process');

const args = process.argv.slice(2);
const fixIndex = args.indexOf('--fix');
const shouldFix = fixIndex !== -1;

const extraArgs = [...args];
if (shouldFix) {
  extraArgs.splice(fixIndex, 1);
}

const argsStr = extraArgs.join(' ');

try {
  if (shouldFix) {
    console.log('Running buf format...');
    // buf format -w writes changes to files
    execSync(`npx buf format -w ${argsStr}`, { stdio: 'inherit' });
  }
  
  console.log('Running buf lint...');
  execSync(`npx buf lint ${argsStr}`, { stdio: 'inherit' });
} catch (error) {
  process.exit(1);
}
