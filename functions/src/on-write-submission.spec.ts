/**
 * Copyright 2023 The Ground Authors.
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
  newCountQuery,
  newDocumentSnapshot,
  stubAdminApi,
} from '@ground/lib/testing/firestore';
import { loi } from './common/datastore';
import { DocumentSnapshot, Firestore } from 'firebase-admin/firestore';
import { resetDatastore } from './common/context';
import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';
import { onWriteSubmissionHandler } from './on-write-submission';

import Pb = GroundProtos.ground.v1beta1;
const l = registry.getFieldIds(Pb.LocationOfInterest);
const sb = registry.getFieldIds(Pb.Submission);

describe('onWriteSubmission()', () => {
  let mockFirestore: Firestore;
  const SURVEY_ID = 'survey1';
  const SUBMISSION = newDocumentSnapshot({ loiId: 'loi1', [sb.loiId]: 'loi1' });
  const SURVEY_PATH = `surveys/${SURVEY_ID}`;
  const SUBMISSIONS_PATH = `${SURVEY_PATH}/submissions`;
  const LOI_ID = 'loi1';
  const LOI_PATH = `${SURVEY_PATH}/lois/${LOI_ID}`;

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    stubAdminApi(mockFirestore);
  });

  afterEach(() => {
    resetDatastore();
  });

  function installSubmissionCountSpy(
    submissionsPath: string,
    loiId: string,
    count: number
  ) {
    mockFirestore.doc(loi(SURVEY_ID, loiId)).set({});
    spyOn(mockFirestore, 'collection')
      .withArgs(submissionsPath)
      .and.returnValue({
        where: jasmine
          .createSpy('where')
          .withArgs(sb.loiId, '==', loiId)
          .and.returnValue(newCountQuery(count)),
      } as any);
  }

  it('update submission count on create', async () => {
    installSubmissionCountSpy(SUBMISSIONS_PATH, LOI_ID, 2);

    await onWriteSubmissionHandler({
      data: { before: null as unknown as DocumentSnapshot, after: SUBMISSION },
      params: { surveyId: SURVEY_ID },
    } as any);

    const loi = await mockFirestore.doc(LOI_PATH).get();
    expect(loi.data()).toEqual({ [l.submissionCount]: 2 });
  });

  it('update submission count on delete', async () => {
    installSubmissionCountSpy(SUBMISSIONS_PATH, LOI_ID, 1);

    await onWriteSubmissionHandler({
      data: { before: SUBMISSION, after: null as unknown as DocumentSnapshot },
      params: { surveyId: SURVEY_ID },
    } as any);

    const loi = await mockFirestore.doc(LOI_PATH).get();
    expect(loi.data()).toEqual({ [l.submissionCount]: 1 });
  });

  it('do nothing on invalid change', async () => {
    installSubmissionCountSpy(SUBMISSIONS_PATH, LOI_ID, 1);

    await onWriteSubmissionHandler({
      data: {
        before: null as unknown as DocumentSnapshot,
        after: null as unknown as DocumentSnapshot,
      },
      params: { surveyId: SURVEY_ID },
    } as any);

    const loi = await mockFirestore.doc(LOI_PATH).get();
    expect(loi.data()).toEqual({});
  });

  it('throw error on failed update', async () => {
    installSubmissionCountSpy(SUBMISSIONS_PATH, LOI_ID, 1);
    spyOn(mockFirestore, 'doc')
      .withArgs(LOI_PATH)
      .and.callFake(() => {
        throw new Error();
      });

    await expectAsync(
      onWriteSubmissionHandler({
        data: { before: null as unknown as DocumentSnapshot, after: SUBMISSION },
        params: { surveyId: SURVEY_ID },
      } as any)
    ).toBeRejected();
  });
});
