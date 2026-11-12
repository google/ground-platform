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
  newDocumentSnapshot,
  stubAdminApi,
} from '@ground/lib/testing/firestore';
import { registry } from '@ground/lib';
import { DocumentData, Firestore } from 'firebase-admin/firestore';
import {
  Change,
  DocumentSnapshot,
  FirestoreEvent,
} from 'firebase-functions/v2/firestore';
import { resetDatastore } from './common/context';
import { GroundProtos } from '@ground/proto';
import { onWriteSurveyHandler } from './on-write-survey';
import * as broadcastModule from './common/broadcast';

import Pb = GroundProtos.ground.v1beta1;

const sv = registry.getFieldIds(Pb.Survey);
const ai = registry.getFieldIds(Pb.AuditInfo);
const ts = registry.getFieldIds(GroundProtos.google.protobuf.Timestamp);

describe('onWriteSurveyHandler()', () => {
  let mockFirestore: Firestore;
  let broadcastSpy: jasmine.Spy;

  const SURVEY_ID = 'survey1';
  const SURVEY_PATH = `surveys/${SURVEY_ID}`;
  const EVENT_TIME = '2026-01-02T03:04:06.000Z';
  const EVENT_SECONDS = Date.parse(EVENT_TIME) / 1000;
  const CLIENT_SECONDS = Date.UTC(2026, 0, 2, 3, 4, 5) / 1000;

  function auditInfo(userId: string, clientSeconds = CLIENT_SECONDS) {
    return {
      [ai.userId]: userId,
      [ai.clientTimestamp]: { [ts.seconds]: clientSeconds },
      // What a client can do is guess the server time from its own clock.
      [ai.serverTimestamp]: { [ts.seconds]: clientSeconds },
    };
  }

  // The mock snapshot lacks `exists`, which the handler uses to tell a
  // deleted survey from a written one.
  function snapshot(data?: DocumentData) {
    return data
      ? Object.assign(newDocumentSnapshot(data), { exists: true })
      : { exists: false, data: () => undefined };
  }

  function writeEvent(before?: DocumentData, after?: DocumentData) {
    return {
      data: {
        before: snapshot(before),
        after: snapshot(after),
      },
      params: { surveyId: SURVEY_ID },
      time: EVENT_TIME,
    } as unknown as FirestoreEvent<Change<DocumentSnapshot>>;
  }

  async function storedSurvey() {
    return (await mockFirestore.doc(SURVEY_PATH).get()).data();
  }

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    stubAdminApi(mockFirestore);
    broadcastSpy = spyOn(broadcastModule, 'broadcastUpdate').and.returnValue(
      Promise.resolve('')
    );
  });

  afterEach(() => {
    resetDatastore();
  });

  it('broadcasts a survey without audit info', async () => {
    const survey = { [sv.name]: 'Survey' };
    await mockFirestore.doc(SURVEY_PATH).set(survey);

    await onWriteSurveyHandler(writeEvent(undefined, survey));

    expect(broadcastSpy).toHaveBeenCalledOnceWith(
      { type: 'survey', surveyId: SURVEY_ID },
      EVENT_TIME
    );
  });

  it('corrects the server timestamps of a created survey and defers the broadcast', async () => {
    const survey = {
      [sv.name]: 'Survey',
      [sv.created]: auditInfo('user1'),
      [sv.lastModified]: auditInfo('user1'),
    };
    await mockFirestore.doc(SURVEY_PATH).set(survey);

    await onWriteSurveyHandler(writeEvent(undefined, survey));

    const stored = await storedSurvey();
    for (const field of [sv.created, sv.lastModified]) {
      expect(stored?.[field][ai.userId]).toBe('user1');
      expect(stored?.[field][ai.clientTimestamp][ts.seconds]).toBe(
        CLIENT_SECONDS
      );
      expect(stored?.[field][ai.serverTimestamp][ts.seconds]).toBe(
        EVENT_SECONDS
      );
    }
    expect(broadcastSpy).not.toHaveBeenCalled();
  });

  it('corrects only lastModified when a survey is updated', async () => {
    const created = auditInfo('user1');
    created[ai.serverTimestamp] = { [ts.seconds]: 1 };
    const before = { [sv.name]: 'Survey', [sv.created]: created };
    const after = {
      ...before,
      [sv.lastModified]: auditInfo('user2', CLIENT_SECONDS + 60),
    };
    await mockFirestore.doc(SURVEY_PATH).set(after);

    await onWriteSurveyHandler(writeEvent(before, after));

    const stored = await storedSurvey();
    expect(stored?.[sv.created]).toEqual(created);
    expect(stored?.[sv.lastModified][ai.userId]).toBe('user2');
    expect(stored?.[sv.lastModified][ai.serverTimestamp][ts.seconds]).toBe(
      EVENT_SECONDS
    );
    expect(broadcastSpy).not.toHaveBeenCalled();
  });

  it('broadcasts without writing when the audit info was already corrected', async () => {
    const lastModified = auditInfo('user1');
    lastModified[ai.serverTimestamp] = { [ts.seconds]: EVENT_SECONDS };
    const before = {
      [sv.name]: 'Survey',
      [sv.lastModified]: auditInfo('user1'),
    };
    const after = { ...before, [sv.lastModified]: lastModified };
    await mockFirestore.doc(SURVEY_PATH).set(after);

    await onWriteSurveyHandler(writeEvent(before, after));

    expect(await storedSurvey()).toEqual(after);
    expect(broadcastSpy).toHaveBeenCalledOnceWith(
      { type: 'survey', surveyId: SURVEY_ID },
      EVENT_TIME
    );
  });

  it('broadcasts when a survey is deleted', async () => {
    await onWriteSurveyHandler(writeEvent({ [sv.name]: 'Survey' }, undefined));

    expect(broadcastSpy).toHaveBeenCalledOnceWith(
      { type: 'survey', surveyId: SURVEY_ID },
      EVENT_TIME
    );
  });
});
