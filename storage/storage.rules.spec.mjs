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

import { Buffer } from 'node:buffer';
import process from 'node:process';
import { URLSearchParams } from 'node:url';

const { fetch } = globalThis;

const PROJECT_ID = process.env.GCLOUD_PROJECT || 'demo-storage-rules';
const BUCKET = `${PROJECT_ID}.appspot.com`;
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST;
const STORAGE_HOST = process.env.FIREBASE_STORAGE_EMULATOR_HOST;

const GeneralAccess = {
  RESTRICTED: 1,
  UNLISTED: 2,
  PUBLIC: 3,
};

const DataVisibility = {
  CONTRIBUTOR_AND_ORGANIZERS: 1,
  ALL_SURVEY_PARTICIPANTS: 2,
};

const Role = {
  VIEWER: 1,
  DATA_COLLECTOR: 2,
  SURVEY_ORGANIZER: 3,
};

/** Creates a user in the Auth Emulator with a deterministic uid and optional email. */
async function createUser({ uid, email }) {
  if (!email) {
    // Anonymous sign-up produces a token with no `email` claim.
    const res = await fetch(
      `http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-key`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ returnSecureToken: true }),
      }
    );
    const body = await res.json();
    if (!res.ok) {
      throw new Error(`Anonymous signUp failed: ${JSON.stringify(body)}`);
    }
    return { uid: body.localId, idToken: body.idToken };
  }

  const password = 'test-password-123';
  const createRes = await fetch(
    `http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/projects/${PROJECT_ID}/accounts`,
    {
      method: 'POST',
      headers: {
        Authorization: 'Bearer owner',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        localId: uid,
        email,
        emailVerified: true,
        password,
      }),
    }
  );
  if (!createRes.ok) {
    throw new Error(
      `Admin account create failed (${createRes.status}): ${await createRes.text()}`
    );
  }

  const signInRes = await fetch(
    `http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-key`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, returnSecureToken: true }),
    }
  );
  const signInBody = await signInRes.json();
  if (!signInRes.ok) {
    throw new Error(`signInWithPassword failed: ${JSON.stringify(signInBody)}`);
  }
  return { uid: signInBody.localId, email, idToken: signInBody.idToken };
}

/** Converts a JS primitive/map into Firestore REST Value representation. */
function toFirestoreValue(value) {
  if (typeof value === 'string') return { stringValue: value };
  if (typeof value === 'number') return { integerValue: String(value) };
  if (typeof value === 'boolean') return { booleanValue: value };
  if (value && typeof value === 'object') {
    const fields = {};
    for (const [k, v] of Object.entries(value)) {
      if (v !== undefined) fields[k] = toFirestoreValue(v);
    }
    return { mapValue: { fields } };
  }
  throw new Error(`Unsupported Firestore test value: ${String(value)}`);
}

/** Seeds a `/surveys/{surveyId}` document bypassing Firestore security rules. */
async function seedSurvey(surveyId, { generalAccess, dataVisibility, acl }) {
  const fields = {
    1: toFirestoreValue(surveyId),
    2: toFirestoreValue(`Survey ${surveyId}`),
  };
  if (acl !== undefined) {
    fields['4'] = toFirestoreValue(acl);
  }
  if (generalAccess !== undefined) {
    fields['8'] = toFirestoreValue(generalAccess);
  }
  if (dataVisibility !== undefined) {
    fields['9'] = toFirestoreValue(dataVisibility);
  }

  const res = await fetch(
    `http://${FIRESTORE_HOST}/v1/projects/${PROJECT_ID}/databases/(default)/documents/surveys/${encodeURIComponent(surveyId)}`,
    {
      method: 'PATCH',
      headers: {
        Authorization: 'Bearer owner',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ fields }),
    }
  );
  if (!res.ok) {
    throw new Error(
      `Failed to seed survey ${surveyId}: ${res.status} ${await res.text()}`
    );
  }
}

function authHeaders(idToken, extra = {}) {
  return idToken
    ? { Authorization: `Bearer ${idToken}`, ...extra }
    : { ...extra };
}

/** Seeds an object directly into Storage Emulator bypassing Storage security rules. */
async function seedObject(objectPath, content = 'secret-photo-bytes') {
  const params = new URLSearchParams({
    uploadType: 'media',
    name: objectPath,
  });
  const res = await fetch(`http://${STORAGE_HOST}/v0/b/${BUCKET}/o?${params}`, {
    method: 'POST',
    headers: {
      Authorization: 'Bearer owner',
      'Content-Type': 'image/jpeg',
    },
    body: Buffer.from(content),
  });
  if (!res.ok) {
    throw new Error(
      `Failed to seed object ${objectPath}: ${res.status} ${await res.text()}`
    );
  }
}

/** Client GET object metadata (`getDownloadURL` equivalent). */
async function getMetadata(objectPath, idToken) {
  return fetch(
    `http://${STORAGE_HOST}/v0/b/${BUCKET}/o/${encodeURIComponent(objectPath)}`,
    { headers: authHeaders(idToken) }
  );
}

/** Client GET object bytes (`getBytes` equivalent). */
async function getBytes(objectPath, idToken) {
  return fetch(
    `http://${STORAGE_HOST}/v0/b/${BUCKET}/o/${encodeURIComponent(objectPath)}?alt=media`,
    { headers: authHeaders(idToken) }
  );
}

/** Client LIST objects under prefix (`listAll` equivalent). */
async function listObjects(prefix, idToken) {
  const params = new URLSearchParams({ prefix });
  return fetch(`http://${STORAGE_HOST}/v0/b/${BUCKET}/o?${params}`, {
    headers: authHeaders(idToken),
  });
}

/** Client single-request media upload (`uploadBytes` equivalent). */
async function uploadMedia(objectPath, idToken, content = 'photo-bytes') {
  const params = new URLSearchParams({
    uploadType: 'media',
    name: objectPath,
  });
  return fetch(`http://${STORAGE_HOST}/v0/b/${BUCKET}/o?${params}`, {
    method: 'POST',
    headers: authHeaders(idToken, { 'Content-Type': 'image/jpeg' }),
    body: Buffer.from(content),
  });
}

/** Client resumable upload (`StorageReference.putFile` on Android equivalent). */
async function uploadResumable(objectPath, idToken, content = 'photo-bytes') {
  const body = Buffer.from(content);
  const params = new URLSearchParams({
    uploadType: 'resumable',
    name: objectPath,
  });
  const startRes = await fetch(
    `http://${STORAGE_HOST}/v0/b/${BUCKET}/o?${params}`,
    {
      method: 'POST',
      headers: authHeaders(idToken, {
        'Content-Type': 'application/json; charset=utf-8',
        'X-Goog-Upload-Protocol': 'resumable',
        'X-Goog-Upload-Command': 'start',
        'X-Goog-Upload-Header-Content-Length': String(body.length),
        'X-Goog-Upload-Header-Content-Type': 'image/jpeg',
      }),
      body: JSON.stringify({ name: objectPath, contentType: 'image/jpeg' }),
    }
  );
  if (!startRes.ok) {
    return startRes;
  }
  const uploadUrl = startRes.headers.get('x-goog-upload-url');
  if (!uploadUrl) {
    throw new Error('Missing x-goog-upload-url header on resumable start');
  }
  return fetch(uploadUrl, {
    method: 'PUT',
    headers: authHeaders(idToken, {
      'X-Goog-Upload-Command': 'upload, finalize',
      'X-Goog-Upload-Offset': '0',
    }),
    body,
  });
}

describe('storage.rules', () => {
  let organizer;
  let victimA;
  let attackerB;
  let viewer;
  let outsider;
  let anonymousUser;

  beforeAll(async () => {
    jasmine.DEFAULT_TIMEOUT_INTERVAL = 30000;
    [organizer, victimA, attackerB, viewer, outsider, anonymousUser] =
      await Promise.all([
        createUser({ uid: 'uidOrganizer', email: 'organizer@ground.test' }),
        createUser({ uid: 'uidVictimA', email: 'victim-a@ground.test' }),
        createUser({ uid: 'uidAttackerB', email: 'attacker-b@ground.test' }),
        createUser({ uid: 'uidViewer', email: 'viewer@ground.test' }),
        createUser({ uid: 'uidOutsider', email: 'outsider@ground.test' }),
        createUser({ uid: 'uidAnon' }),
      ]);

    await Promise.all([
      // UNLISTED survey with explicit CONTRIBUTOR_AND_ORGANIZERS visibility.
      seedSurvey('unlisted-restricted-vis', {
        generalAccess: GeneralAccess.UNLISTED,
        dataVisibility: DataVisibility.CONTRIBUTOR_AND_ORGANIZERS,
        acl: {
          [organizer.email]: Role.SURVEY_ORGANIZER,
          [victimA.email]: Role.DATA_COLLECTOR,
        },
      }),
      // UNLISTED survey where field '9' (dataVisibility) is omitted (proto3 default 0).
      seedSurvey('unlisted-unset-vis', {
        generalAccess: GeneralAccess.UNLISTED,
        acl: {
          [organizer.email]: Role.SURVEY_ORGANIZER,
        },
      }),
      // UNLISTED survey with ALL_SURVEY_PARTICIPANTS visibility.
      seedSurvey('unlisted-all-vis', {
        generalAccess: GeneralAccess.UNLISTED,
        dataVisibility: DataVisibility.ALL_SURVEY_PARTICIPANTS,
        acl: {
          [organizer.email]: Role.SURVEY_ORGANIZER,
        },
      }),
      // RESTRICTED survey with CONTRIBUTOR_AND_ORGANIZERS visibility.
      seedSurvey('restricted-contrib-vis', {
        generalAccess: GeneralAccess.RESTRICTED,
        dataVisibility: DataVisibility.CONTRIBUTOR_AND_ORGANIZERS,
        acl: {
          [organizer.email]: Role.SURVEY_ORGANIZER,
          [victimA.email]: Role.DATA_COLLECTOR,
          [attackerB.email]: Role.DATA_COLLECTOR,
          [viewer.email]: Role.VIEWER,
        },
      }),
      // RESTRICTED survey with ALL_SURVEY_PARTICIPANTS visibility.
      seedSurvey('restricted-all-vis', {
        generalAccess: GeneralAccess.RESTRICTED,
        dataVisibility: DataVisibility.ALL_SURVEY_PARTICIPANTS,
        acl: {
          [organizer.email]: Role.SURVEY_ORGANIZER,
          [victimA.email]: Role.DATA_COLLECTOR,
          [viewer.email]: Role.VIEWER,
        },
      }),
    ]);

    await Promise.all([
      seedObject(
        'user-media/surveys/unlisted-restricted-vis/users/uidVictimA/p.jpg',
        'victim-per-user-photo'
      ),
      seedObject(
        'user-media/surveys/unlisted-unset-vis/users/uidVictimA/p.jpg',
        'victim-unset-vis-user-photo'
      ),
      seedObject(
        'user-media/surveys/unlisted-all-vis/users/uidVictimA/p.jpg',
        'victim-all-vis-user-photo'
      ),
      seedObject(
        'user-media/surveys/restricted-contrib-vis/users/uidVictimA/p.jpg',
        'victim-restricted-user-photo'
      ),
      seedObject(
        'user-media/surveys/restricted-all-vis/users/uidVictimA/p.jpg',
        'victim-restricted-all-user-photo'
      ),
      seedObject(
        'user-media/surveys/restricted-all-vis/users/uidOutsider/p.jpg',
        'revoked-contributor-photo'
      ),
      seedObject(
        'user-media/surveys/unlisted-restricted-vis/submissions/task006-uuid.jpg',
        'victim-submission-photo'
      ),
      seedObject(
        'user-media/surveys/unlisted-unset-vis/submissions/task006-uuid.jpg',
        'victim-unset-vis-submission-photo'
      ),
      seedObject(
        'user-media/surveys/unlisted-all-vis/submissions/task006-uuid.jpg',
        'victim-all-vis-submission-photo'
      ),
      seedObject(
        'user-media/surveys/restricted-contrib-vis/submissions/task006-uuid.jpg',
        'victim-restricted-submission-photo'
      ),
      seedObject(
        'user-media/surveys/restricted-all-vis/submissions/task006-uuid.jpg',
        'victim-restricted-all-submission-photo'
      ),
      seedObject('temp/uidVictimA/export.csv', 'private-export'),
    ]);
  });

  describe('per-user survey media (/user-media/surveys/{surveyId}/users/{userId}/**)', () => {
    it('denies cross-user reads on UNLISTED surveys when dataVisibility is CONTRIBUTOR_AND_ORGANIZERS', async () => {
      const path =
        'user-media/surveys/unlisted-restricted-vis/users/uidVictimA/p.jpg';

      const metaRes = await getMetadata(path, attackerB.idToken);
      const bytesRes = await getBytes(path, attackerB.idToken);

      expect(metaRes.status).toBe(403);
      expect(bytesRes.status).toBe(403);
    });

    it('denies cross-user reads when dataVisibility field is omitted (defaults to CONTRIBUTOR_AND_ORGANIZERS)', async () => {
      const path =
        'user-media/surveys/unlisted-unset-vis/users/uidVictimA/p.jpg';

      const bytesRes = await getBytes(path, attackerB.idToken);

      expect(bytesRes.status).toBe(403);
    });

    it('denies cross-user reads by other ACL collectors and viewers on RESTRICTED surveys with CONTRIBUTOR_AND_ORGANIZERS', async () => {
      const path =
        'user-media/surveys/restricted-contrib-vis/users/uidVictimA/p.jpg';

      const collectorRes = await getBytes(path, attackerB.idToken);
      const viewerRes = await getBytes(path, viewer.idToken);

      expect(collectorRes.status).toBe(403);
      expect(viewerRes.status).toBe(403);
    });

    it('allows the resource owner to read their own media when dataVisibility is CONTRIBUTOR_AND_ORGANIZERS or unset', async () => {
      const explicitRes = await getBytes(
        'user-media/surveys/unlisted-restricted-vis/users/uidVictimA/p.jpg',
        victimA.idToken
      );
      const unsetRes = await getBytes(
        'user-media/surveys/unlisted-unset-vis/users/uidVictimA/p.jpg',
        victimA.idToken
      );

      expect(explicitRes.status).toBe(200);
      expect(await explicitRes.text()).toBe('victim-per-user-photo');
      expect(unsetRes.status).toBe(200);
      expect(await unsetRes.text()).toBe('victim-unset-vis-user-photo');
    });

    it('allows survey organizers to read other participants media when dataVisibility is CONTRIBUTOR_AND_ORGANIZERS or unset', async () => {
      const explicitRes = await getBytes(
        'user-media/surveys/unlisted-restricted-vis/users/uidVictimA/p.jpg',
        organizer.idToken
      );
      const unsetRes = await getBytes(
        'user-media/surveys/unlisted-unset-vis/users/uidVictimA/p.jpg',
        organizer.idToken
      );

      expect(explicitRes.status).toBe(200);
      expect(await explicitRes.text()).toBe('victim-per-user-photo');
      expect(unsetRes.status).toBe(200);
      expect(await unsetRes.text()).toBe('victim-unset-vis-user-photo');
    });

    it('allows survey participants to read other participants media when dataVisibility is ALL_SURVEY_PARTICIPANTS', async () => {
      const unlistedRes = await getBytes(
        'user-media/surveys/unlisted-all-vis/users/uidVictimA/p.jpg',
        attackerB.idToken
      );
      const restrictedViewerRes = await getBytes(
        'user-media/surveys/restricted-all-vis/users/uidVictimA/p.jpg',
        viewer.idToken
      );

      expect(unlistedRes.status).toBe(200);
      expect(await unlistedRes.text()).toBe('victim-all-vis-user-photo');
      expect(restrictedViewerRes.status).toBe(200);
      expect(await restrictedViewerRes.text()).toBe(
        'victim-restricted-all-user-photo'
      );
    });

    it('denies non-ACL outsiders on RESTRICTED surveys even when dataVisibility is ALL_SURVEY_PARTICIPANTS or uid matches', async () => {
      const crossUserRes = await getBytes(
        'user-media/surveys/restricted-all-vis/users/uidVictimA/p.jpg',
        outsider.idToken
      );
      const revokedOwnerRes = await getBytes(
        'user-media/surveys/restricted-all-vis/users/uidOutsider/p.jpg',
        outsider.idToken
      );

      expect(crossUserRes.status).toBe(403);
      expect(revokedOwnerRes.status).toBe(403);
    });

    it('denies listing per-user media directories even for organizers', async () => {
      const collectorList = await listObjects(
        'user-media/surveys/unlisted-all-vis/users/',
        attackerB.idToken
      );
      const organizerList = await listObjects(
        'user-media/surveys/unlisted-restricted-vis/users/',
        organizer.idToken
      );

      expect(collectorList.status).toBe(403);
      expect(organizerList.status).toBe(403);
    });

    it('allows owners with collector access to upload and denies cross-user or viewer writes', async () => {
      const ownUpload = await uploadMedia(
        'user-media/surveys/restricted-contrib-vis/users/uidVictimA/new.jpg',
        victimA.idToken
      );
      const crossUserUpload = await uploadMedia(
        'user-media/surveys/restricted-contrib-vis/users/uidVictimA/overwrite.jpg',
        attackerB.idToken
      );
      const viewerUpload = await uploadMedia(
        'user-media/surveys/restricted-contrib-vis/users/uidViewer/new.jpg',
        viewer.idToken
      );

      expect(ownUpload.status).toBe(200);
      expect(crossUserUpload.status).toBe(403);
      expect(viewerUpload.status).toBe(403);
    });
  });

  describe('submission survey media (/user-media/surveys/{surveyId}/submissions/**)', () => {
    it('denies listing submission media for all client roles', async () => {
      const attackerList = await listObjects(
        'user-media/surveys/unlisted-restricted-vis/submissions/',
        attackerB.idToken
      );
      const allVisList = await listObjects(
        'user-media/surveys/unlisted-all-vis/submissions/',
        attackerB.idToken
      );
      const organizerList = await listObjects(
        'user-media/surveys/unlisted-restricted-vis/submissions/',
        organizer.idToken
      );

      expect(attackerList.status).toBe(403);
      expect(allVisList.status).toBe(403);
      expect(organizerList.status).toBe(403);
    });

    it('denies direct client reads by non-organizers when dataVisibility is CONTRIBUTOR_AND_ORGANIZERS or unset', async () => {
      const unlistedAttackerRes = await getBytes(
        'user-media/surveys/unlisted-restricted-vis/submissions/task006-uuid.jpg',
        attackerB.idToken
      );
      const unsetAttackerRes = await getBytes(
        'user-media/surveys/unlisted-unset-vis/submissions/task006-uuid.jpg',
        attackerB.idToken
      );
      const restrictedCollectorRes = await getMetadata(
        'user-media/surveys/restricted-contrib-vis/submissions/task006-uuid.jpg',
        attackerB.idToken
      );
      const restrictedViewerRes = await getBytes(
        'user-media/surveys/restricted-contrib-vis/submissions/task006-uuid.jpg',
        viewer.idToken
      );

      expect(unlistedAttackerRes.status).toBe(403);
      expect(unsetAttackerRes.status).toBe(403);
      expect(restrictedCollectorRes.status).toBe(403);
      expect(restrictedViewerRes.status).toBe(403);
    });

    it('allows survey organizers to read submission media when dataVisibility is CONTRIBUTOR_AND_ORGANIZERS or unset', async () => {
      const explicitMetaRes = await getMetadata(
        'user-media/surveys/unlisted-restricted-vis/submissions/task006-uuid.jpg',
        organizer.idToken
      );
      const explicitBytesRes = await getBytes(
        'user-media/surveys/unlisted-restricted-vis/submissions/task006-uuid.jpg',
        organizer.idToken
      );
      const unsetBytesRes = await getBytes(
        'user-media/surveys/unlisted-unset-vis/submissions/task006-uuid.jpg',
        organizer.idToken
      );

      expect(explicitMetaRes.status).toBe(200);
      expect(explicitBytesRes.status).toBe(200);
      expect(await explicitBytesRes.text()).toBe('victim-submission-photo');
      expect(unsetBytesRes.status).toBe(200);
      expect(await unsetBytesRes.text()).toBe(
        'victim-unset-vis-submission-photo'
      );
    });

    it('allows authorized participants to read submission media when dataVisibility is ALL_SURVEY_PARTICIPANTS and denies outsiders on RESTRICTED surveys', async () => {
      const unlistedParticipantRes = await getBytes(
        'user-media/surveys/unlisted-all-vis/submissions/task006-uuid.jpg',
        attackerB.idToken
      );
      const restrictedViewerRes = await getBytes(
        'user-media/surveys/restricted-all-vis/submissions/task006-uuid.jpg',
        viewer.idToken
      );
      const restrictedOutsiderRes = await getBytes(
        'user-media/surveys/restricted-all-vis/submissions/task006-uuid.jpg',
        outsider.idToken
      );

      expect(unlistedParticipantRes.status).toBe(200);
      expect(await unlistedParticipantRes.text()).toBe(
        'victim-all-vis-submission-photo'
      );
      expect(restrictedViewerRes.status).toBe(200);
      expect(restrictedOutsiderRes.status).toBe(403);
    });

    it('allows collectors to upload via both media and resumable (Android putFile) protocols without get permission', async () => {
      const mediaPath =
        'user-media/surveys/restricted-contrib-vis/submissions/task001-media-uuid.jpg';
      const resumablePath =
        'user-media/surveys/restricted-contrib-vis/submissions/task001-resumable-uuid.jpg';

      const mediaUpload = await uploadMedia(
        mediaPath,
        victimA.idToken,
        'collector-media-upload'
      );
      const resumableUpload = await uploadResumable(
        resumablePath,
        victimA.idToken,
        'collector-resumable-upload'
      );
      const retryResumableUpload = await uploadResumable(
        resumablePath,
        victimA.idToken,
        'collector-resumable-upload'
      );
      const viewerUpload = await uploadResumable(
        'user-media/surveys/restricted-contrib-vis/submissions/viewer-attempt.jpg',
        viewer.idToken
      );
      const outsiderUpload = await uploadResumable(
        'user-media/surveys/restricted-contrib-vis/submissions/outsider-attempt.jpg',
        outsider.idToken
      );

      expect(mediaUpload.status).toBe(200);
      expect(resumableUpload.status).toBe(200);
      expect(retryResumableUpload.status).toBe(200);
      expect(viewerUpload.status).toBe(403);
      expect(outsiderUpload.status).toBe(403);

      // Organizer can fetch the uploaded file afterwards.
      const organizerRead = await getBytes(resumablePath, organizer.idToken);
      expect(organizerRead.status).toBe(200);
      expect(await organizerRead.text()).toBe('collector-resumable-upload');
    });
  });

  describe('edge cases and control paths', () => {
    it('denies unauthenticated access to survey media and temp paths', async () => {
      const userMediaRes = await getBytes(
        'user-media/surveys/unlisted-all-vis/users/uidVictimA/p.jpg',
        undefined
      );
      const submissionMediaRes = await getBytes(
        'user-media/surveys/unlisted-all-vis/submissions/task006-uuid.jpg',
        undefined
      );
      const uploadRes = await uploadMedia(
        'user-media/surveys/unlisted-all-vis/submissions/unauth.jpg',
        undefined
      );
      const tempRes = await getBytes('temp/uidVictimA/export.csv', undefined);

      expect(userMediaRes.status).toBe(403);
      expect(submissionMediaRes.status).toBe(403);
      expect(uploadRes.status).toBe(403);
      expect(tempRes.status).toBe(403);
    });

    it('handles tokens without an email claim safely', async () => {
      const restrictedRes = await getBytes(
        'user-media/surveys/restricted-all-vis/submissions/task006-uuid.jpg',
        anonymousUser.idToken
      );
      const unlistedCrossUserRes = await getBytes(
        'user-media/surveys/unlisted-restricted-vis/users/uidVictimA/p.jpg',
        anonymousUser.idToken
      );
      const unlistedAllVisRes = await getBytes(
        'user-media/surveys/unlisted-all-vis/submissions/task006-uuid.jpg',
        anonymousUser.idToken
      );

      expect(restrictedRes.status).toBe(403);
      expect(unlistedCrossUserRes.status).toBe(403);
      expect(unlistedAllVisRes.status).toBe(200);
    });

    it('denies reads and writes when the survey document does not exist or under unknown subpaths', async () => {
      await seedObject(
        'user-media/surveys/missing-survey/users/uidVictimA/p.jpg',
        'orphan'
      );
      await seedObject(
        'user-media/surveys/unlisted-all-vis/arbitrary/file.jpg',
        'unexpected-subpath'
      );

      const missingSurveyRead = await getBytes(
        'user-media/surveys/missing-survey/users/uidVictimA/p.jpg',
        victimA.idToken
      );
      const missingSurveyWrite = await uploadMedia(
        'user-media/surveys/missing-survey/submissions/new.jpg',
        victimA.idToken
      );
      const unknownSubpathRead = await getBytes(
        'user-media/surveys/unlisted-all-vis/arbitrary/file.jpg',
        organizer.idToken
      );
      const unknownSubpathWrite = await uploadMedia(
        'user-media/surveys/unlisted-all-vis/arbitrary/new.jpg',
        organizer.idToken
      );

      expect(missingSurveyRead.status).toBe(403);
      expect(missingSurveyWrite.status).toBe(403);
      expect(unknownSubpathRead.status).toBe(403);
      expect(unknownSubpathWrite.status).toBe(403);
    });

    it('allows only the owning user to read /temp/{userId}/**', async () => {
      const ownerRes = await getBytes(
        'temp/uidVictimA/export.csv',
        victimA.idToken
      );
      const attackerRes = await getBytes(
        'temp/uidVictimA/export.csv',
        attackerB.idToken
      );

      expect(ownerRes.status).toBe(200);
      expect(await ownerRes.text()).toBe('private-export');
      expect(attackerRes.status).toBe(403);
    });
  });
});
