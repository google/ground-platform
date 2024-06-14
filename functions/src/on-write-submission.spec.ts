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
  stubAdminApi,
  newEventContext,
  newDocumentSnapshot,
  testFirestore,
  newCountQuery,
} from '@ground/lib/dist/testing/firestore';

const test = require('firebase-functions-test')();

describe('onWriteSubmission()', () => {
  let functions: any;

  const SURVEY_ID = 'survey1';
  const SUBMISSION = newDocumentSnapshot({loiId: 'loi1'});
  const CONTEXT = newEventContext({surveyId: SURVEY_ID});
  const SURVEY_PATH = `surveys/${SURVEY_ID}`;
  const SUBMISSIONS_PATH = `${SURVEY_PATH}/submissions`;
  const LOI_ID = 'loi1';
  const LOI_PATH = `${SURVEY_PATH}/lois/${LOI_ID}`;

  beforeAll(() => {
    stubAdminApi();
    // Module must be loaded after Admin API has been stubbed.
    functions = require('./index');
  });

  afterAll(() => {
    test.cleanup();
  });

  function installSubmissionCountSpy(
    submissionsPath: string,
    loiId: string,
    count: number
  ) {
    testFirestore.collection.withArgs(submissionsPath).and.returnValue({
      where: jasmine
        .createSpy('where')
        .withArgs('loiId', '==', loiId)
        .and.returnValue(newCountQuery(count)),
    });
  }

  function installLoiUpdateSpy(loiPath: string) {
    const loiUpdateSpy = jasmine.createSpy('update');
    testFirestore.doc.withArgs(loiPath).and.returnValue({update: loiUpdateSpy});
    return loiUpdateSpy;
  }

  it('update submission count on create', async () => {
    installSubmissionCountSpy(SUBMISSIONS_PATH, LOI_ID, 2);
    const loiUpdateSpy = installLoiUpdateSpy(LOI_PATH);

    await test.wrap(functions.onWriteSubmission)(
      {before: undefined, after: SUBMISSION},
      CONTEXT
    );

    expect(loiUpdateSpy).toHaveBeenCalledOnceWith({submissionCount: 2});
  });

  it('update submission count on delete', async () => {
    installSubmissionCountSpy(SUBMISSIONS_PATH, LOI_ID, 1);
    const loiUpdateSpy = installLoiUpdateSpy(LOI_PATH);

    await test.wrap(functions.onWriteSubmission)(
      {before: SUBMISSION, after: undefined},
      CONTEXT
    );

    expect(loiUpdateSpy).toHaveBeenCalledOnceWith({submissionCount: 1});
  });

  it('do nothing on invalid change', async () => {
    installSubmissionCountSpy(SUBMISSIONS_PATH, LOI_ID, 1);
    const loiUpdateSpy = installLoiUpdateSpy(LOI_PATH);

    await test.wrap(functions.onWriteSubmission)(
      {before: undefined, after: undefined},
      CONTEXT
    );

    expect(loiUpdateSpy).not.toHaveBeenCalled();
  });

  it('throw error on failed update', async () => {
    installSubmissionCountSpy(SUBMISSIONS_PATH, LOI_ID, 1);
    const loiUpdateSpy = installLoiUpdateSpy(LOI_PATH);
    loiUpdateSpy.and.throwError('LOI update failed');

    await expectAsync(
      test.wrap(functions.onWriteSubmission)(
        {before: undefined, after: SUBMISSION},
        CONTEXT
      )
    ).toBeRejected();
  });
});
