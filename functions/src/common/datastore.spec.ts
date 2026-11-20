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

import { registry } from '@ground/lib';
import { createMockFirestore } from '@ground/lib/testing/firestore';
import { GroundProtos } from '@ground/proto';
import { DocumentReference, Firestore } from 'firebase-admin/firestore';
import { Datastore } from './datastore';

import Pb = GroundProtos.ground.v1beta1;

const sv = registry.getFieldIds(Pb.Survey);

describe('Datastore.updateSurveyAuditInfo()', () => {
  const SURVEY_ID = 'survey1';
  const SURVEY_PATH = `surveys/${SURVEY_ID}`;
  const CREATED = { audit: 'created' };
  const LAST_MODIFIED = { audit: 'lastModified' };

  let mockFirestore: Firestore;
  let datastore: Datastore;

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    datastore = new Datastore(mockFirestore);
  });

  it('writes the audit info it is given', async () => {
    await mockFirestore.doc(SURVEY_PATH).set({ [sv.name]: 'Survey' });

    await datastore.updateSurveyAuditInfo(SURVEY_ID, {
      [sv.created]: CREATED,
      [sv.lastModified]: LAST_MODIFIED,
    });

    expect((await mockFirestore.doc(SURVEY_PATH).get()).data()).toEqual({
      [sv.name]: 'Survey',
      [sv.created]: CREATED,
      [sv.lastModified]: LAST_MODIFIED,
    });
  });

  it('writes only the audit info fields, leaving the rest of the document', async () => {
    await mockFirestore.doc(SURVEY_PATH).set({
      [sv.name]: 'Survey',
      [sv.created]: CREATED,
    });

    await datastore.updateSurveyAuditInfo(SURVEY_ID, {
      [sv.name]: 'Not written',
      [sv.lastModified]: LAST_MODIFIED,
    });

    expect((await mockFirestore.doc(SURVEY_PATH).get()).data()).toEqual({
      [sv.name]: 'Survey',
      [sv.created]: CREATED,
      [sv.lastModified]: LAST_MODIFIED,
    });
  });

  it('does not update the survey when it has no audit info, as Firestore rejects an empty update', async () => {
    const update = jasmine.createSpy('update').and.resolveTo();
    spyOn(mockFirestore, 'doc').and.returnValue({
      update,
    } as unknown as DocumentReference);

    await datastore.updateSurveyAuditInfo(SURVEY_ID, { [sv.name]: 'Survey' });

    expect(update).not.toHaveBeenCalled();
  });
});
