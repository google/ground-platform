/**
 * Copyright 2026 The Ground Authors.
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

import {
  createMockFirestore,
  stubAdminApi,
} from '@ground/lib/testing/firestore';
import { DocumentSnapshot, Firestore } from 'firebase-admin/firestore';
import { getDatastore, resetDatastore } from './common/context';
import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';
import { onWriteSubmissionHandler } from './on-write-submission';

import Pb = GroundProtos.ground.v1beta1;
const l = registry.getFieldIds(Pb.LocationOfInterest);
const sb = registry.getFieldIds(Pb.Submission);
const ai = registry.getFieldIds(Pb.AuditInfo);
const ts = registry.getFieldIds(GroundProtos.google.protobuf.Timestamp);

describe('onWriteSubmission()', () => {
  let mockFirestore: Firestore;
  const SURVEY_ID = 'survey1';
  const LOI_ID = 'loi1';
  const LOI_PATH = `surveys/${SURVEY_ID}/lois/${LOI_ID}`;
  const NOW = Date.UTC(2026, 8, 10);
  const NOW_TIMESTAMP = { [ts.seconds]: NOW / 1000, [ts.nanos]: 0 };
  const EARLIER_TIMESTAMP = { [ts.seconds]: Date.UTC(2026, 0, 2) / 1000 };

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    stubAdminApi(mockFirestore);
    spyOn(Date, 'now').and.returnValue(NOW);
  });

  afterEach(() => {
    resetDatastore();
  });

  function createMockSnapshot(exists: boolean, data?: any): DocumentSnapshot {
    return {
      exists,
      data: () => data,
      get: (field: string) => data?.[field],
      id: 'mock-id',
      ref: {} as any,
      isEqual: () => false,
      readTime: {} as any,
      updateTime: {} as any,
      createTime: {} as any,
    } as any;
  }

  it('update submission count on create', async () => {
    const adjustSpy = spyOn(
      getDatastore(),
      'adjustSubmissionCount'
    ).and.returnValue(Promise.resolve());

    const emptySnapshot = createMockSnapshot(false);
    const afterSnapshot = createMockSnapshot(true, { [sb.loiId]: LOI_ID });

    await onWriteSubmissionHandler({
      data: { before: emptySnapshot, after: afterSnapshot },
      params: { surveyId: SURVEY_ID },
    } as any);

    expect(adjustSpy).toHaveBeenCalledWith(SURVEY_ID, LOI_ID, 1);
  });

  it('update submission count on delete', async () => {
    const adjustSpy = spyOn(
      getDatastore(),
      'adjustSubmissionCount'
    ).and.returnValue(Promise.resolve());

    const beforeSnapshot = createMockSnapshot(true, { [sb.loiId]: LOI_ID });
    const emptySnapshot = createMockSnapshot(false);

    await onWriteSubmissionHandler({
      data: { before: beforeSnapshot, after: emptySnapshot },
      params: { surveyId: SURVEY_ID },
    } as any);

    expect(adjustSpy).toHaveBeenCalledWith(SURVEY_ID, LOI_ID, -1);
  });

  it('do nothing on invalid change', async () => {
    const adjustSpy = spyOn(
      getDatastore(),
      'adjustSubmissionCount'
    ).and.returnValue(Promise.resolve());

    const emptySnapshotBefore = createMockSnapshot(false);
    const emptySnapshotAfter = createMockSnapshot(false);

    await onWriteSubmissionHandler({
      data: {
        before: emptySnapshotBefore,
        after: emptySnapshotAfter,
      },
      params: { surveyId: SURVEY_ID },
    } as any);

    expect(adjustSpy).not.toHaveBeenCalled();
  });

  it('throw error on failed update', async () => {
    spyOn(getDatastore(), 'adjustSubmissionCount').and.callFake(() => {
      throw new Error('Test error');
    });

    const emptySnapshot = createMockSnapshot(false);
    const afterSnapshot = createMockSnapshot(true, { [sb.loiId]: LOI_ID });

    await expectAsync(
      onWriteSubmissionHandler({
        data: {
          before: emptySnapshot,
          after: afterSnapshot,
        },
        params: { surveyId: SURVEY_ID },
      } as any)
    ).toBeRejectedWithError('Test error');
  });

  describe('adjustSubmissionCount()', () => {
    it('moves the last modified server timestamp along with the count', async () => {
      await mockFirestore.doc(LOI_PATH).set({
        [l.lastModified]: {
          [ai.userId]: 'user1',
          [ai.serverTimestamp]: EARLIER_TIMESTAMP,
        },
        [l.submissionCount]: 1,
      });

      await getDatastore().adjustSubmissionCount(SURVEY_ID, LOI_ID, 1);

      const loi = await mockFirestore.doc(LOI_PATH).get();
      expect(loi.data()).toEqual({
        [l.lastModified]: {
          [ai.userId]: 'user1',
          [ai.serverTimestamp]: NOW_TIMESTAMP,
        },
        [l.submissionCount]: jasmine.anything(),
      });
    });

    it('starts last modified from created when the LOI was never modified', async () => {
      const created = {
        [ai.userId]: 'user1',
        [ai.serverTimestamp]: EARLIER_TIMESTAMP,
      };
      await mockFirestore.doc(LOI_PATH).set({
        [l.created]: created,
        [l.submissionCount]: 1,
      });

      await getDatastore().adjustSubmissionCount(SURVEY_ID, LOI_ID, -1);

      const loi = await mockFirestore.doc(LOI_PATH).get();
      expect(loi.data()).toEqual({
        [l.created]: created,
        [l.lastModified]: {
          [ai.userId]: 'user1',
          [ai.serverTimestamp]: NOW_TIMESTAMP,
        },
        [l.submissionCount]: jasmine.anything(),
      });
    });

    it('does nothing when the LOI is gone', async () => {
      await getDatastore().adjustSubmissionCount(SURVEY_ID, LOI_ID, -1);

      const loi = await mockFirestore.doc(LOI_PATH).get();
      expect(loi.exists).toBeFalse();
    });
  });
});
