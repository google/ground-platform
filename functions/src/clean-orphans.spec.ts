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

import {
  createMockFirestore,
  stubAdminApi,
} from '@ground/lib/testing/firestore';
import { Firestore } from 'firebase-admin/firestore';
import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';

import { cleanOrphansHandler } from './clean-orphans';
import { resetDatastore } from './common/context';

import Pb = GroundProtos.ground.v1beta1;

const l = registry.getFieldIds(Pb.LocationOfInterest);
const sb = registry.getFieldIds(Pb.Submission);

describe('cleanOrphansHandler()', () => {
  let mockFirestore: Firestore;
  const SURVEY_ID = 'survey1';
  const LIVE_JOB_ID = 'jobLive';
  const DEAD_JOB_ID = 'jobDead';

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    stubAdminApi(mockFirestore);
  });

  afterEach(() => {
    resetDatastore();
  });

  async function seed(path: string, data: object) {
    await mockFirestore.doc(path).set(data);
  }

  async function exists(path: string): Promise<boolean> {
    const snap = await mockFirestore.doc(path).get();
    return snap.exists;
  }

  it('deletes LOIs and submissions whose jobId no longer exists', async () => {
    await seed(`surveys/${SURVEY_ID}`, { name: 'Test' });
    await seed(`surveys/${SURVEY_ID}/jobs/${LIVE_JOB_ID}`, { name: 'Live' });
    await seed(`surveys/${SURVEY_ID}/lois/loiLive`, { [l.jobId]: LIVE_JOB_ID });
    await seed(`surveys/${SURVEY_ID}/lois/loiDead`, { [l.jobId]: DEAD_JOB_ID });
    await seed(`surveys/${SURVEY_ID}/submissions/subLive`, {
      [sb.jobId]: LIVE_JOB_ID,
    });
    await seed(`surveys/${SURVEY_ID}/submissions/subDead`, {
      [sb.jobId]: DEAD_JOB_ID,
    });

    await cleanOrphansHandler();

    expect(await exists(`surveys/${SURVEY_ID}/lois/loiLive`)).toBeTrue();
    expect(await exists(`surveys/${SURVEY_ID}/lois/loiDead`)).toBeFalse();
    expect(await exists(`surveys/${SURVEY_ID}/submissions/subLive`)).toBeTrue();
    expect(
      await exists(`surveys/${SURVEY_ID}/submissions/subDead`)
    ).toBeFalse();
  });

  it('leaves docs with missing or non-string jobId untouched', async () => {
    await seed(`surveys/${SURVEY_ID}`, {});
    await seed(`surveys/${SURVEY_ID}/jobs/${LIVE_JOB_ID}`, {});
    await seed(`surveys/${SURVEY_ID}/lois/loiNoJob`, { other: 'field' });
    await seed(`surveys/${SURVEY_ID}/lois/loiWeirdJob`, { [l.jobId]: 42 });

    await cleanOrphansHandler();

    expect(await exists(`surveys/${SURVEY_ID}/lois/loiNoJob`)).toBeTrue();
    expect(await exists(`surveys/${SURVEY_ID}/lois/loiWeirdJob`)).toBeTrue();
  });

  it('scopes cleanup per survey — a job id live in one survey does not save an LOI in another', async () => {
    await seed('surveys/s1', {});
    await seed('surveys/s2', {});
    await seed(`surveys/s1/jobs/${LIVE_JOB_ID}`, {});
    await seed(`surveys/s1/lois/ok`, { [l.jobId]: LIVE_JOB_ID });
    await seed(`surveys/s2/lois/orphan`, { [l.jobId]: LIVE_JOB_ID });

    await cleanOrphansHandler();

    expect(await exists('surveys/s1/lois/ok')).toBeTrue();
    expect(await exists('surveys/s2/lois/orphan')).toBeFalse();
  });

  it('is a no-op when no surveys exist', async () => {
    await expectAsync(cleanOrphansHandler()).toBeResolved();
  });
});
