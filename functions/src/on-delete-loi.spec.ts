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
  DocumentSnapshot,
  FirestoreEvent,
} from 'firebase-functions/v2/firestore';
import { onDeleteLoiHandler } from './on-delete-loi';
import * as broadcastModule from './common/broadcast';

describe('onDeleteLoiHandler()', () => {
  const SURVEY_ID = 'survey1';
  const LOI_ID = 'loi1';
  const EVENT_TIME = '2026-01-02T03:04:06.000Z';

  let broadcastSpy: jasmine.Spy;

  beforeEach(() => {
    broadcastSpy = spyOn(broadcastModule, 'broadcastUpdate').and.returnValue(
      Promise.resolve('')
    );
  });

  function deletedEvent(params: Record<string, string> = {}) {
    return {
      data: undefined,
      params: { surveyId: SURVEY_ID, loiId: LOI_ID, ...params },
      time: EVENT_TIME,
    } as unknown as FirestoreEvent<DocumentSnapshot | undefined>;
  }

  it('announces the deleted LOI', async () => {
    await onDeleteLoiHandler(deletedEvent());

    expect(broadcastSpy).toHaveBeenCalledOnceWith(
      { type: 'loi', surveyId: SURVEY_ID, loiId: LOI_ID, deleted: true },
      EVENT_TIME
    );
  });

  it('does nothing when the event carries no loi id', async () => {
    await onDeleteLoiHandler(deletedEvent({ loiId: '' }));

    expect(broadcastSpy).not.toHaveBeenCalled();
  });
});
