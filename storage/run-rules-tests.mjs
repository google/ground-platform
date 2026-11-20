/**
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

import { spawn } from 'node:child_process';
import fs from 'node:fs/promises';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '..');

/** Reserves an unused ephemeral TCP port on loopback. */
function getFreePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.unref();
    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = typeof address === 'object' && address ? address.port : 0;
      server.close(err => (err ? reject(err) : resolve(port)));
    });
  });
}

async function main() {
  const [
    authPort,
    firestorePort,
    firestoreWsPort,
    storagePort,
    hubPort,
    loggingPort,
  ] = await Promise.all([
    getFreePort(),
    getFreePort(),
    getFreePort(),
    getFreePort(),
    getFreePort(),
    getFreePort(),
  ]);

  const tmpDir = await fs.mkdtemp(
    path.join(os.tmpdir(), 'ground-storage-rules-')
  );
  const configPath = path.join(tmpDir, 'firebase.json');
  const firebaseBin = path.join(repoRoot, 'node_modules', '.bin', 'firebase');
  const jasmineBin = path.join(
    repoRoot,
    'node_modules',
    'jasmine',
    'bin',
    'jasmine.js'
  );
  const specPath = path.join(__dirname, 'storage.rules.spec.mjs');

  const config = {
    firestore: {
      rules: path.join(repoRoot, 'firestore', 'firestore.rules'),
    },
    storage: {
      rules: path.join(__dirname, 'storage.rules'),
    },
    emulators: {
      singleProjectMode: true,
      hub: { host: '127.0.0.1', port: hubPort },
      logging: { host: '127.0.0.1', port: loggingPort },
      auth: { host: '127.0.0.1', port: authPort },
      firestore: {
        host: '127.0.0.1',
        port: firestorePort,
        websocketPort: firestoreWsPort,
      },
      storage: { host: '127.0.0.1', port: storagePort },
    },
  };

  await fs.writeFile(configPath, JSON.stringify(config, null, 2));

  const projectId = 'demo-storage-rules';
  const testCmd = `${JSON.stringify(process.execPath)} ${JSON.stringify(jasmineBin)} ${JSON.stringify(specPath)}`;

  try {
    const exitCode = await new Promise((resolve, reject) => {
      const child = spawn(
        firebaseBin,
        [
          'emulators:exec',
          '--project',
          projectId,
          '--only',
          'auth,firestore,storage',
          '--config',
          configPath,
          testCmd,
        ],
        {
          cwd: tmpDir,
          stdio: 'inherit',
          env: {
            ...process.env,
            GCLOUD_PROJECT: projectId,
          },
        }
      );
      child.on('error', reject);
      child.on('close', code => resolve(code ?? 1));
    });
    process.exitCode = exitCode;
  } finally {
    await fs.rm(tmpDir, { recursive: true, force: true });
  }
}

await main();
