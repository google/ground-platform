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

import {FieldNumbers} from '@ground/lib';
import {
  stubAdminApi,
  newEventContext,
  newDocumentSnapshot,
  newCountQuery,
  createMockFirestore,
} from '@ground/lib/dist/testing/firestore';
import * as functions from './index';
import {loi} from './common/datastore';
import {Firestore} from 'firebase-admin/firestore';
import {resetDatastore} from './common/context';

const test = require('firebase-functions-test')();

describe('onWriteSubmission()', () => {
  let mockFirestore: Firestore;
  const SURVEY_ID = 'survey1';
  const SUBMISSION = newDocumentSnapshot({loiId: 'loi1'});
  const CONTEXT = newEventContext({surveyId: SURVEY_ID});
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

  afterAll(() => {
    test.cleanup();
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
          .withArgs(FieldNumbers.Submission.loi_id, '==', loiId)
          .and.returnValue(newCountQuery(count)),
      } as any);
  }

  it('update submission count on create', async () => {
    installSubmissionCountSpy(SUBMISSIONS_PATH, LOI_ID, 2);

    await test.wrap(functions.onWriteSubmission)(
      {before: undefined, after: SUBMISSION},
      CONTEXT
    );

    const loi = await mockFirestore.doc(LOI_PATH).get();
    expect(loi.data()).toEqual({submissionCount: 2});
  });

  it('update submission count on delete', async () => {
    installSubmissionCountSpy(SUBMISSIONS_PATH, LOI_ID, 1);

    await test.wrap(functions.onWriteSubmission)(
      {before: SUBMISSION, after: undefined},
      CONTEXT
    );

    const loi = await mockFirestore.doc(LOI_PATH).get();
    expect(loi.data()).toEqual({submissionCount: 1});
  });

  it('do nothing on invalid change', async () => {
    installSubmissionCountSpy(SUBMISSIONS_PATH, LOI_ID, 1);

    await test.wrap(functions.onWriteSubmission)(
      {before: undefined, after: undefined},
      CONTEXT
    );

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
      test.wrap(functions.onWriteSubmission)(
        {before: undefined, after: SUBMISSION},
        CONTEXT
      )
    ).toBeRejected();
  });
});
