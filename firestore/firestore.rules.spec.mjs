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

import { Buffer } from 'buffer';
import { spawn } from 'child_process';
import fs from 'fs';
import http from 'http';
import net from 'net';
import os from 'os';
import path from 'path';
import process from 'process';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PROJECT_ID = `demo-firestore-rules-${process.pid}`;

// Proto field numbers from proto/src/ground/v1beta1/*.proto.
const SURVEY = {
  ACL: '4',
  GENERAL_ACCESS: '8',
  DATA_VISIBILITY: '9',
};

const ROLE = {
  VIEWER: 1,
  DATA_COLLECTOR: 2,
  SURVEY_ORGANIZER: 3,
};

const GENERAL_ACCESS = {
  RESTRICTED: 1,
  UNLISTED: 2,
  PUBLIC: 3,
};

const DATA_VISIBILITY = {
  CONTRIBUTOR_AND_ORGANIZERS: 1,
  ALL_SURVEY_PARTICIPANTS: 2,
};

const SUBMISSION = {
  ID: '1',
  LOI_ID: '2',
  JOB_ID: '4',
  OWNER_ID: '5',
  CREATED: '6',
  LAST_MODIFIED: '7',
  TASK_DATA: '8',
};

const AUDIT_INFO = {
  USER_ID: '1',
  CLIENT_TIMESTAMP: '2',
  SERVER_TIMESTAMP: '3',
  DISPLAY_NAME: '4',
  PHOTO_URL: '5',
  EMAIL_ADDRESS: '6',
};

const ORGANIZER = {
  uid: 'uidOrganizer',
  email: 'organizer@ground.test',
  name: 'Survey Organizer',
};

const COLLECTOR = {
  uid: 'uidCollectorA',
  email: 'collector-a@ground.test',
  name: 'Collector A',
};

const VICTIM = {
  uid: 'uidVictimZ',
  email: 'victim@corp.example',
  name: 'Victim Name',
};

/**
 * Allocates `count` distinct free ephemeral TCP ports on 127.0.0.1.
 */
async function getFreePorts(count) {
  const servers = [];
  try {
    for (let i = 0; i < count; i++) {
      const server = net.createServer();
      await new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(0, '127.0.0.1', resolve);
      });
      servers.push(server);
    }
    return servers.map(server => server.address().port);
  } finally {
    await Promise.all(
      servers.map(server => new Promise(resolve => server.close(resolve)))
    );
  }
}

/**
 * Performs an HTTP request against the local Firestore emulator.
 */
function httpRequest(port, method, urlPath, { headers = {}, body } = {}) {
  return new Promise((resolve, reject) => {
    const payload = body === undefined ? undefined : JSON.stringify(body);
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port,
        path: urlPath,
        method,
        headers: {
          ...(payload !== undefined
            ? {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(payload),
              }
            : {}),
          ...headers,
        },
      },
      res => {
        const chunks = [];
        res.on('data', chunk => chunks.push(chunk));
        res.on('end', () => {
          const text = Buffer.concat(chunks).toString('utf8');
          resolve({ status: res.statusCode ?? 0, text });
        });
      }
    );
    req.on('error', reject);
    if (payload !== undefined) {
      req.write(payload);
    }
    req.end();
  });
}

/**
 * Encodes a JS value into Firestore REST v1 Value format.
 */
function toFirestoreValue(value) {
  if (value === null) {
    return { nullValue: null };
  }
  if (typeof value === 'boolean') {
    return { booleanValue: value };
  }
  if (typeof value === 'number') {
    return Number.isInteger(value)
      ? { integerValue: String(value) }
      : { doubleValue: value };
  }
  if (typeof value === 'string') {
    return { stringValue: value };
  }
  if (Array.isArray(value)) {
    return { arrayValue: { values: value.map(toFirestoreValue) } };
  }
  if (typeof value === 'object') {
    return { mapValue: { fields: toFirestoreFields(value) } };
  }
  throw new Error(`Unsupported Firestore value: ${typeof value}`);
}

function toFirestoreFields(obj) {
  const fields = {};
  for (const [key, val] of Object.entries(obj)) {
    if (val !== undefined) {
      fields[key] = toFirestoreValue(val);
    }
  }
  return fields;
}

/**
 * Builds an unsecured Firebase Auth JWT accepted by the Firestore Emulator.
 */
function makeAuthToken(uid, claims = {}) {
  const header = Buffer.from(
    JSON.stringify({ alg: 'none', typ: 'JWT' })
  ).toString('base64url');
  const payload = Buffer.from(
    JSON.stringify({
      iss: `https://securetoken.google.com/${PROJECT_ID}`,
      aud: PROJECT_ID,
      iat: 1700000000,
      exp: 1900000000,
      sub: uid,
      user_id: uid,
      firebase: {
        identities: {},
        sign_in_provider: 'google.com',
      },
      ...claims,
    })
  ).toString('base64url');
  return `${header}.${payload}.`;
}

function makeAuditInfo({ uid, email, name, photoUrl }, seconds = 1700000100) {
  return {
    [AUDIT_INFO.USER_ID]: uid,
    [AUDIT_INFO.CLIENT_TIMESTAMP]: { 1: seconds },
    [AUDIT_INFO.SERVER_TIMESTAMP]: { 1: seconds },
    ...(name !== undefined ? { [AUDIT_INFO.DISPLAY_NAME]: name } : {}),
    ...(photoUrl !== undefined ? { [AUDIT_INFO.PHOTO_URL]: photoUrl } : {}),
    ...(email !== undefined ? { [AUDIT_INFO.EMAIL_ADDRESS]: email } : {}),
  };
}

function makeSubmission({
  id = 'sub-1',
  loiId = 'loi-1',
  jobId = 'job-1',
  ownerId,
  created,
  lastModified,
  answer = 'initial answer',
}) {
  return {
    [SUBMISSION.ID]: id,
    [SUBMISSION.LOI_ID]: loiId,
    [SUBMISSION.JOB_ID]: jobId,
    ...(ownerId !== undefined ? { [SUBMISSION.OWNER_ID]: ownerId } : {}),
    ...(created !== undefined ? { [SUBMISSION.CREATED]: created } : {}),
    ...(lastModified !== undefined
      ? { [SUBMISSION.LAST_MODIFIED]: lastModified }
      : {}),
    [SUBMISSION.TASK_DATA]: [
      {
        1: 'td-1',
        2: 'task-1',
        4: { 1: answer },
      },
    ],
  };
}

describe('firestore.rules submission AuditInfo validation', () => {
  let firestorePort;
  let emulatorProcess;
  let tmpDir;

  const docsBasePath = `/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

  function authHeaders(token) {
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  async function seedDoc(docPath, data) {
    const res = await httpRequest(
      firestorePort,
      'PATCH',
      `${docsBasePath}/${docPath}`,
      {
        headers: { Authorization: 'Bearer owner' },
        body: { fields: toFirestoreFields(data) },
      }
    );
    expect(res.status).toBe(200);
  }

  async function createDoc(collectionPath, docId, data, token) {
    return httpRequest(
      firestorePort,
      'POST',
      `${docsBasePath}/${collectionPath}?documentId=${encodeURIComponent(docId)}`,
      {
        headers: authHeaders(token),
        body: { fields: toFirestoreFields(data) },
      }
    );
  }

  /**
   * Mirrors Android's `batch.set(ref, values, SetOptions.merge())` and Web's
   * `setDoc(ref, values, { merge: true })` using top-level `updateMask` keys.
   */
  async function mergeDoc(docPath, data, token) {
    const query = Object.keys(data)
      .map(key => `updateMask.fieldPaths=${encodeURIComponent(`\`${key}\``)}`)
      .join('&');
    return httpRequest(
      firestorePort,
      'PATCH',
      `${docsBasePath}/${docPath}?${query}`,
      {
        headers: authHeaders(token),
        body: { fields: toFirestoreFields(data) },
      }
    );
  }

  async function overwriteDoc(docPath, data, token) {
    return httpRequest(firestorePort, 'PATCH', `${docsBasePath}/${docPath}`, {
      headers: authHeaders(token),
      body: { fields: toFirestoreFields(data) },
    });
  }

  async function deleteDocAs(docPath, token) {
    return httpRequest(firestorePort, 'DELETE', `${docsBasePath}/${docPath}`, {
      headers: authHeaders(token),
    });
  }

  beforeAll(async () => {
    const [fsPort, wsPort, hubPort, logPort] = await getFreePorts(4);
    firestorePort = fsPort;

    tmpDir = fs.mkdtempSync(
      path.join(os.tmpdir(), 'ground-firestore-rules-test-')
    );
    const configPath = path.join(tmpDir, 'firebase.json');
    fs.writeFileSync(
      configPath,
      JSON.stringify(
        {
          firestore: {
            rules: path.resolve(__dirname, 'firestore.rules'),
          },
          emulators: {
            firestore: {
              host: '127.0.0.1',
              port: firestorePort,
              websocketPort: wsPort,
            },
            hub: {
              host: '127.0.0.1',
              port: hubPort,
            },
            logging: {
              host: '127.0.0.1',
              port: logPort,
            },
            ui: {
              enabled: false,
            },
          },
        },
        null,
        2
      )
    );

    const firebaseBin = path.resolve(
      __dirname,
      '../node_modules/.bin/firebase'
    );
    let emulatorLogs = '';
    emulatorProcess = spawn(
      firebaseBin,
      [
        'emulators:start',
        '--only',
        'firestore',
        '--project',
        PROJECT_ID,
        '--config',
        configPath,
      ],
      {
        cwd: tmpDir,
        stdio: ['ignore', 'pipe', 'pipe'],
      }
    );
    emulatorProcess.stdout.on('data', chunk => {
      emulatorLogs += chunk.toString();
    });
    emulatorProcess.stderr.on('data', chunk => {
      emulatorLogs += chunk.toString();
    });

    const deadline = Date.now() + 45_000;
    let ready = false;
    while (Date.now() < deadline) {
      if (emulatorProcess.exitCode !== null) {
        throw new Error(
          `Firestore emulator exited early (${emulatorProcess.exitCode}):\n${emulatorLogs}`
        );
      }
      try {
        const res = await httpRequest(firestorePort, 'GET', '/');
        if (res.status === 200) {
          ready = true;
          break;
        }
      } catch {
        // Emulator still starting up.
      }
      await new Promise(resolve => setTimeout(resolve, 150));
    }

    if (!ready) {
      throw new Error(
        `Timed out waiting for Firestore emulator on port ${firestorePort}:\n${emulatorLogs}`
      );
    }
  }, 60_000);

  afterAll(async () => {
    if (emulatorProcess && emulatorProcess.exitCode === null) {
      await new Promise(resolve => {
        emulatorProcess.once('exit', resolve);
        emulatorProcess.kill('SIGTERM');
      });
    }
    if (tmpDir) {
      fs.rmSync(tmpDir, { recursive: true, force: true });
    }
  }, 30_000);

  beforeEach(async () => {
    const clearRes = await httpRequest(
      firestorePort,
      'DELETE',
      `/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`,
      { headers: { Authorization: 'Bearer owner' } }
    );
    expect(clearRes.status).toBe(200);

    await seedDoc('surveys/restricted-survey', {
      [SURVEY.ACL]: {
        [ORGANIZER.email]: ROLE.SURVEY_ORGANIZER,
        [COLLECTOR.email]: ROLE.DATA_COLLECTOR,
      },
      [SURVEY.GENERAL_ACCESS]: GENERAL_ACCESS.RESTRICTED,
      [SURVEY.DATA_VISIBILITY]: DATA_VISIBILITY.CONTRIBUTOR_AND_ORGANIZERS,
    });

    await seedDoc('surveys/unlisted-survey', {
      [SURVEY.ACL]: {
        [ORGANIZER.email]: ROLE.SURVEY_ORGANIZER,
      },
      [SURVEY.GENERAL_ACCESS]: GENERAL_ACCESS.UNLISTED,
      [SURVEY.DATA_VISIBILITY]: DATA_VISIBILITY.CONTRIBUTOR_AND_ORGANIZERS,
    });

    await seedDoc('surveys/public-survey', {
      [SURVEY.ACL]: {
        [ORGANIZER.email]: ROLE.SURVEY_ORGANIZER,
      },
      [SURVEY.GENERAL_ACCESS]: GENERAL_ACCESS.PUBLIC,
      [SURVEY.DATA_VISIBILITY]: DATA_VISIBILITY.ALL_SURVEY_PARTICIPANTS,
    });
  });

  describe('allow create', () => {
    it('allows a data collector to create a submission with matching owner_id, created, and last_modified AuditInfo', async () => {
      const token = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
        name: COLLECTOR.name,
      });
      const audit = makeAuditInfo(COLLECTOR);

      const createRes = await createDoc(
        'surveys/restricted-survey/submissions',
        'sub-valid-create',
        makeSubmission({
          id: 'sub-valid-create',
          ownerId: COLLECTOR.uid,
          created: audit,
          lastModified: audit,
        }),
        token
      );
      expect(createRes.status).toBe(200);

      // Android writes new submissions via SetOptions.merge().
      const mergeCreateRes = await mergeDoc(
        'surveys/restricted-survey/submissions/sub-valid-merge',
        makeSubmission({
          id: 'sub-valid-merge',
          ownerId: COLLECTOR.uid,
          created: audit,
          lastModified: audit,
        }),
        token
      );
      expect(mergeCreateRes.status).toBe(200);
    });

    it('allows submission creation when optional last_modified is omitted or display_name differs from token.name', async () => {
      const token = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
        name: 'Updated Server Name',
      });
      const created = makeAuditInfo({
        uid: COLLECTOR.uid,
        email: COLLECTOR.email,
        name: 'Cached Device Name',
      });

      const res = await createDoc(
        'surveys/unlisted-survey/submissions',
        'sub-no-last-modified',
        makeSubmission({
          id: 'sub-no-last-modified',
          ownerId: COLLECTOR.uid,
          created,
        }),
        token
      );
      expect(res.status).toBe(200);
    });

    it('allows submission creation on unlisted/public surveys when auth token has no email claim and email_address is omitted or empty', async () => {
      const noEmailUid = 'uidNoEmailProvider';
      const noEmailToken = makeAuthToken(noEmailUid, {
        firebase: { identities: {}, sign_in_provider: 'anonymous' },
      });
      const omittedEmailAudit = makeAuditInfo({ uid: noEmailUid });

      const omittedRes = await createDoc(
        'surveys/unlisted-survey/submissions',
        'sub-no-email-claim',
        makeSubmission({
          id: 'sub-no-email-claim',
          ownerId: noEmailUid,
          created: omittedEmailAudit,
          lastModified: omittedEmailAudit,
        }),
        noEmailToken
      );
      expect(omittedRes.status).toBe(200);

      const nullEmailToken = makeAuthToken(noEmailUid, { email: null });
      const emptyEmailAudit = makeAuditInfo({ uid: noEmailUid, email: '' });
      const emptyRes = await createDoc(
        'surveys/public-survey/submissions',
        'sub-null-email-claim',
        makeSubmission({
          id: 'sub-null-email-claim',
          ownerId: noEmailUid,
          created: emptyEmailAudit,
        }),
        nullEmailToken
      );
      expect(emptyRes.status).toBe(200);
    });

    it('rejects unauthenticated submission creation', async () => {
      const audit = makeAuditInfo(COLLECTOR);
      const res = await createDoc(
        'surveys/unlisted-survey/submissions',
        'sub-unauth',
        makeSubmission({
          id: 'sub-unauth',
          ownerId: COLLECTOR.uid,
          created: audit,
        }),
        null
      );
      expect(res.status).toBe(403);
    });

    it('rejects submission creation with forged created user_id and email_address (b/584427032 PoC)', async () => {
      const attackerToken = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
        name: COLLECTOR.name,
      });
      const forgedCreated = makeAuditInfo(VICTIM);

      const res = await createDoc(
        'surveys/unlisted-survey/submissions',
        'sub-forged-both',
        makeSubmission({
          id: 'sub-forged-both',
          ownerId: COLLECTOR.uid,
          created: forgedCreated,
        }),
        attackerToken
      );
      expect(res.status).toBe(403);
    });

    it('rejects submission creation when created.user_id is forged even if created.email_address matches caller', async () => {
      const attackerToken = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
      });
      const res = await createDoc(
        'surveys/restricted-survey/submissions',
        'sub-forged-uid',
        makeSubmission({
          id: 'sub-forged-uid',
          ownerId: COLLECTOR.uid,
          created: makeAuditInfo({
            uid: VICTIM.uid,
            email: COLLECTOR.email,
          }),
        }),
        attackerToken
      );
      expect(res.status).toBe(403);
    });

    it('rejects submission creation when created.email_address is forged even if created.user_id matches caller', async () => {
      const attackerToken = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
      });
      const res = await createDoc(
        'surveys/restricted-survey/submissions',
        'sub-forged-email',
        makeSubmission({
          id: 'sub-forged-email',
          ownerId: COLLECTOR.uid,
          created: makeAuditInfo({
            uid: COLLECTOR.uid,
            email: VICTIM.email,
            name: VICTIM.name,
          }),
        }),
        attackerToken
      );
      expect(res.status).toBe(403);
    });

    it('rejects submission creation when caller has token.email but omits or clears created.email_address', async () => {
      const attackerToken = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
      });

      const omittedRes = await createDoc(
        'surveys/unlisted-survey/submissions',
        'sub-omitted-email',
        makeSubmission({
          id: 'sub-omitted-email',
          ownerId: COLLECTOR.uid,
          created: makeAuditInfo({ uid: COLLECTOR.uid }),
        }),
        attackerToken
      );
      expect(omittedRes.status).toBe(403);

      const emptyRes = await createDoc(
        'surveys/unlisted-survey/submissions',
        'sub-empty-email',
        makeSubmission({
          id: 'sub-empty-email',
          ownerId: COLLECTOR.uid,
          created: makeAuditInfo({ uid: COLLECTOR.uid, email: '' }),
        }),
        attackerToken
      );
      expect(emptyRes.status).toBe(403);
    });

    it('rejects submission creation when caller has no token.email but supplies a forged created.email_address', async () => {
      const noEmailUid = 'uidNoEmailAttacker';
      const token = makeAuthToken(noEmailUid);

      const res = await createDoc(
        'surveys/unlisted-survey/submissions',
        'sub-no-claim-forged-email',
        makeSubmission({
          id: 'sub-no-claim-forged-email',
          ownerId: noEmailUid,
          created: makeAuditInfo({
            uid: noEmailUid,
            email: VICTIM.email,
          }),
        }),
        token
      );
      expect(res.status).toBe(403);
    });

    it('rejects submission creation when created AuditInfo is missing, non-map, has non-string display_name, or has forged last_modified', async () => {
      const token = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
      });

      const missingCreatedRes = await createDoc(
        'surveys/restricted-survey/submissions',
        'sub-missing-created',
        makeSubmission({
          id: 'sub-missing-created',
          ownerId: COLLECTOR.uid,
        }),
        token
      );
      expect(missingCreatedRes.status).toBe(403);

      const invalidTypeRes = await createDoc(
        'surveys/restricted-survey/submissions',
        'sub-invalid-created',
        makeSubmission({
          id: 'sub-invalid-created',
          ownerId: COLLECTOR.uid,
          created: 'not-a-map',
        }),
        token
      );
      expect(invalidTypeRes.status).toBe(403);

      const nonStringNameRes = await createDoc(
        'surveys/restricted-survey/submissions',
        'sub-nonstring-name',
        makeSubmission({
          id: 'sub-nonstring-name',
          ownerId: COLLECTOR.uid,
          created: makeAuditInfo({
            uid: COLLECTOR.uid,
            email: COLLECTOR.email,
            name: 12345,
          }),
        }),
        token
      );
      expect(nonStringNameRes.status).toBe(403);

      const forgedLastModifiedRes = await createDoc(
        'surveys/restricted-survey/submissions',
        'sub-forged-last-mod',
        makeSubmission({
          id: 'sub-forged-last-mod',
          ownerId: COLLECTOR.uid,
          created: makeAuditInfo(COLLECTOR),
          lastModified: makeAuditInfo(VICTIM),
        }),
        token
      );
      expect(forgedLastModifiedRes.status).toBe(403);
    });
  });

  describe('allow update and delete', () => {
    const subPath = 'surveys/restricted-survey/submissions/sub-existing';
    let initialCreated;

    beforeEach(async () => {
      initialCreated = makeAuditInfo(COLLECTOR, 1700000100);
      await seedDoc(
        subPath,
        makeSubmission({
          id: 'sub-existing',
          ownerId: COLLECTOR.uid,
          created: initialCreated,
          lastModified: initialCreated,
          answer: 'original answer',
        })
      );
    });

    it('allows the owner to update task_data and last_modified via Android SetOptions.merge() when created is untouched', async () => {
      const token = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
        name: COLLECTOR.name,
      });

      // Mirrors ModelToProtoExt.createSubmissionMessage on Mutation.Type.UPDATE,
      // which omits `created` ('6') and merges `lastModified` ('7') + `taskData` ('8').
      const res = await mergeDoc(
        subPath,
        makeSubmission({
          id: 'sub-existing',
          ownerId: COLLECTOR.uid,
          lastModified: makeAuditInfo(COLLECTOR, 1700000200),
          answer: 'updated answer',
        }),
        token
      );
      expect(res.status).toBe(200);
    });

    it('allows the owner to update task_data without modifying last_modified', async () => {
      const token = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
      });

      const res = await mergeDoc(
        subPath,
        {
          [SUBMISSION.TASK_DATA]: [{ 1: 'td-1', 2: 'task-1', 4: { 1: 'v2' } }],
        },
        token
      );
      expect(res.status).toBe(200);
    });

    it('allows a survey organizer to update another collector submission while keeping owner_id and created unchanged', async () => {
      const organizerToken = makeAuthToken(ORGANIZER.uid, {
        email: ORGANIZER.email,
        name: ORGANIZER.name,
      });

      const res = await overwriteDoc(
        subPath,
        makeSubmission({
          id: 'sub-existing',
          ownerId: COLLECTOR.uid,
          created: initialCreated,
          lastModified: makeAuditInfo(ORGANIZER, 1700000300),
          answer: 'organizer corrected answer',
        }),
        organizerToken
      );
      expect(res.status).toBe(200);
    });

    it('rejects collector update that mutates created AuditInfo (email_address, user_id, or display_name)', async () => {
      const token = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
        name: COLLECTOR.name,
      });

      const forgedEmailRes = await overwriteDoc(
        subPath,
        makeSubmission({
          id: 'sub-existing',
          ownerId: COLLECTOR.uid,
          created: makeAuditInfo(VICTIM, 1700000100),
          lastModified: makeAuditInfo(COLLECTOR, 1700000200),
          answer: 'tampered',
        }),
        token
      );
      expect(forgedEmailRes.status).toBe(403);

      const forgedDisplayNameMergeRes = await mergeDoc(
        subPath,
        {
          [SUBMISSION.CREATED]: {
            ...initialCreated,
            [AUDIT_INFO.DISPLAY_NAME]: VICTIM.name,
          },
        },
        token
      );
      expect(forgedDisplayNameMergeRes.status).toBe(403);
    });

    it('rejects collector update that rewrites owner_id', async () => {
      const collectorToken = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
      });
      const res = await mergeDoc(
        subPath,
        { [SUBMISSION.OWNER_ID]: VICTIM.uid },
        collectorToken
      );
      expect(res.status).toBe(403);
    });

    it('rejects collector update that forges last_modified AuditInfo', async () => {
      const collectorToken = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
      });
      const res = await mergeDoc(
        subPath,
        { [SUBMISSION.LAST_MODIFIED]: makeAuditInfo(VICTIM, 1700000250) },
        collectorToken
      );
      expect(res.status).toBe(403);
    });

    it('rejects survey organizer update that mutates created AuditInfo or owner_id', async () => {
      const organizerToken = makeAuthToken(ORGANIZER.uid, {
        email: ORGANIZER.email,
      });

      const forgeCreatedRes = await mergeDoc(
        subPath,
        { [SUBMISSION.CREATED]: makeAuditInfo(VICTIM, 1700000100) },
        organizerToken
      );
      expect(forgeCreatedRes.status).toBe(403);

      const transferOwnerRes = await mergeDoc(
        subPath,
        { [SUBMISSION.OWNER_ID]: VICTIM.uid },
        organizerToken
      );
      expect(transferOwnerRes.status).toBe(403);
    });

    it('rejects non-owner collector updating another collector submission on an unlisted survey', async () => {
      const existingUnlistedSub = 'surveys/unlisted-survey/submissions/sub-u1';
      await seedDoc(
        existingUnlistedSub,
        makeSubmission({
          id: 'sub-u1',
          ownerId: COLLECTOR.uid,
          created: initialCreated,
          lastModified: initialCreated,
        })
      );

      const otherUserToken = makeAuthToken(VICTIM.uid, {
        email: VICTIM.email,
      });
      const res = await mergeDoc(
        existingUnlistedSub,
        {
          [SUBMISSION.LAST_MODIFIED]: makeAuditInfo(VICTIM, 1700000400),
          [SUBMISSION.TASK_DATA]: [
            { 1: 'td-1', 2: 'task-1', 4: { 1: 'tampered' } },
          ],
        },
        otherUserToken
      );
      expect(res.status).toBe(403);
    });

    it('allows deletion by the submission owner or survey organizer and rejects deletion by another collector', async () => {
      const otherCollectorToken = makeAuthToken(VICTIM.uid, {
        email: VICTIM.email,
      });
      const deniedRes = await deleteDocAs(subPath, otherCollectorToken);
      expect(deniedRes.status).toBe(403);

      const ownerToken = makeAuthToken(COLLECTOR.uid, {
        email: COLLECTOR.email,
      });
      const ownerDeleteRes = await deleteDocAs(subPath, ownerToken);
      expect(ownerDeleteRes.status).toBe(200);

      await seedDoc(
        subPath,
        makeSubmission({
          id: 'sub-existing',
          ownerId: COLLECTOR.uid,
          created: initialCreated,
        })
      );
      const organizerToken = makeAuthToken(ORGANIZER.uid, {
        email: ORGANIZER.email,
      });
      const organizerDeleteRes = await deleteDocAs(subPath, organizerToken);
      expect(organizerDeleteRes.status).toBe(200);
    });
  });
});
