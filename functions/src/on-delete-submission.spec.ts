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

import { newDocumentSnapshot } from '@ground/lib/testing/firestore';
import { DocumentSnapshot } from 'firebase-admin/firestore';
import * as context from './common/context';
import { onDeleteSubmissionHandler } from './on-delete-submission';

describe('onDeleteSubmission()', () => {
  const SURVEY_ID = 'survey001';
  const PHOTO_1 = `user-media/surveys/${SURVEY_ID}/submissions/subm001-task006.jpg`;
  const PHOTO_2 = `user-media/surveys/${SURVEY_ID}/submissions/subm001-task007.jpg`;

  let mockBucket: jasmine.SpyObj<{ file: (path: string) => unknown }>;
  let deletedPaths: string[];
  let deleteSpy: jasmine.Spy;

  beforeEach(() => {
    deletedPaths = [];
    deleteSpy = jasmine.createSpy('delete').and.resolveTo(undefined);
    mockBucket = jasmine.createSpyObj('bucket', ['file']);
    mockBucket.file.and.callFake((path: string) => {
      deletedPaths.push(path);
      return { delete: deleteSpy };
    });
    spyOn(context, 'getStorageBucket').and.returnValue(mockBucket as any);
  });

  /**
   * Builds a deletion event for a submission whose task data is keyed by proto
   * field id, mirroring how submissions are stored in Firestore.
   */
  function deletionEvent(taskData: unknown[]) {
    return {
      data: newDocumentSnapshot({
        '1': 'subm001',
        '2': 'loi001',
        '5': 'user001',
        '8': taskData,
      }) as DocumentSnapshot,
      params: { surveyId: SURVEY_ID, submissionId: 'subm001' },
    } as any;
  }

  /** Task data entry holding a photo result at the given path. */
  function photoTaskData(taskId: string, photoPath: string) {
    return { '1': taskId, '2': taskId, '10': { '1': photoPath } };
  }

  it('deletes every photo attached to the submission', async () => {
    await onDeleteSubmissionHandler(
      deletionEvent([
        photoTaskData('task006', PHOTO_1),
        photoTaskData('task007', PHOTO_2),
      ])
    );

    expect(deletedPaths).toEqual([PHOTO_1, PHOTO_2]);
    expect(deleteSpy).toHaveBeenCalledTimes(2);
    expect(deleteSpy).toHaveBeenCalledWith({ ignoreNotFound: true });
  });

  it('ignores task data without photos', async () => {
    await onDeleteSubmissionHandler(
      deletionEvent([
        { '1': 'task001', '2': 'task001', '4': { '1': 'some text' } },
        photoTaskData('task006', PHOTO_1),
      ])
    );

    expect(deletedPaths).toEqual([PHOTO_1]);
  });

  it('does not touch storage when the submission has no photos', async () => {
    await onDeleteSubmissionHandler(
      deletionEvent([{ '1': 'task001', '2': 'task001', '4': { '1': 'text' } }])
    );

    expect(context.getStorageBucket).not.toHaveBeenCalled();
    expect(deletedPaths).toEqual([]);
  });

  it('does nothing when the deleted document is missing', async () => {
    await onDeleteSubmissionHandler({
      data: undefined,
      params: { surveyId: SURVEY_ID, submissionId: 'subm001' },
    } as any);

    expect(context.getStorageBucket).not.toHaveBeenCalled();
  });

  it('logs and continues when deleting one photo fails', async () => {
    spyOn(console, 'error');
    deleteSpy.and.callFake(() => {
      // Fail the first delete only, so the second must still be attempted.
      deleteSpy.and.resolveTo(undefined);
      return Promise.reject(new Error('boom'));
    });

    await expectAsync(
      onDeleteSubmissionHandler(
        deletionEvent([
          photoTaskData('task006', PHOTO_1),
          photoTaskData('task007', PHOTO_2),
        ])
      )
    ).toBeResolved();

    expect(deletedPaths).toEqual([PHOTO_1, PHOTO_2]);
    expect(console.error).toHaveBeenCalled();
  });
});
