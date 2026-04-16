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
import { Firestore } from 'firebase-admin/firestore';
import {
  FirestoreEvent,
  QueryDocumentSnapshot,
} from 'firebase-functions/v2/firestore';
import { resetDatastore } from './common/context';
import { GroundProtos } from '@ground/proto';
import { onCreateLoiHandler } from './on-create-loi';
import * as broadcastModule from './common/broadcast-survey-update';

import Pb = GroundProtos.ground.v1beta1;

const l = registry.getFieldIds(Pb.LocationOfInterest);
const pr = registry.getFieldIds(Pb.LocationOfInterest.Property);
const g = registry.getFieldIds(Pb.Geometry);
const p = registry.getFieldIds(Pb.Point);
const c = registry.getFieldIds(Pb.Coordinates);
const j = registry.getFieldIds(Pb.Job);
const intgr = registry.getFieldIds(Pb.Integration);

describe('onCreateLoiHandler()', () => {
  let mockFirestore: Firestore;

  const SURVEY_ID = 'survey1';
  const JOB_ID = 'job1';
  const LOI_ID = 'loi1';
  const LOI_PATH = `surveys/${SURVEY_ID}/lois/${LOI_ID}`;
  const JOB_PATH = `surveys/${SURVEY_ID}/jobs/${JOB_ID}`;

  const loiDoc = {
    [l.jobId]: JOB_ID,
    [l.geometry]: {
      [g.point]: {
        [p.coordinates]: { [c.latitude]: 10.0, [c.longitude]: 20.0 },
      },
    },
    [l.properties]: { name: { [pr.stringValue]: 'Test LOI' } },
  };

  const whispConfig = {
    name: 'whisp',
    prefix: 'whisp_',
    url: 'https://whisp.example.com/api',
  };

  const geoidConfig = {
    name: 'geoid',
    prefix: 'geoid_',
    url: 'https://geoid.example.com/api',
  };

  beforeEach(() => {
    mockFirestore = createMockFirestore();
    stubAdminApi(mockFirestore);
    spyOn(broadcastModule, 'broadcastSurveyUpdate').and.returnValue(
      Promise.resolve('')
    );
    mockFirestore.doc(LOI_PATH).set(loiDoc);
    mockFirestore
      .doc('config/integrations/propertyGenerators/whisp')
      .set(whispConfig);
    mockFirestore
      .doc('config/integrations/propertyGenerators/geoid')
      .set(geoidConfig);
  });

  afterEach(() => {
    resetDatastore();
  });

  it('skips property generator when integration not enabled in job', async () => {
    mockFirestore.doc(JOB_PATH).set({});
    const fetchSpy = spyOn(globalThis, 'fetch');

    await onCreateLoiHandler({
      data: newDocumentSnapshot(loiDoc) as unknown as QueryDocumentSnapshot,
      params: { surveyId: SURVEY_ID, loiId: LOI_ID },
    } as unknown as FirestoreEvent<QueryDocumentSnapshot | undefined>);

    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('runs property generator and updates LOI properties when integration is enabled', async () => {
    mockFirestore.doc(JOB_PATH).set({
      [j.enabledIntegrations]: [{ [intgr.id]: 'whisp' }],
    });
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve({
        ok: true,
        json: () =>
          Promise.resolve({
            code: 'analysis_completed',
            data: { features: [{ properties: { area: 100 } }] },
          }),
      } as Response)
    );

    await onCreateLoiHandler({
      data: newDocumentSnapshot(loiDoc) as unknown as QueryDocumentSnapshot,
      params: { surveyId: SURVEY_ID, loiId: LOI_ID },
    } as unknown as FirestoreEvent<QueryDocumentSnapshot | undefined>);

    const loiData = (await mockFirestore.doc(LOI_PATH).get()).data();
    expect(loiData?.[l.properties]?.['whisp_area']).toEqual({
      [pr.numericValue]: 100,
    });
  });

  it('runs geoid property generator and updates LOI properties when integration is enabled', async () => {
    mockFirestore.doc(JOB_PATH).set({
      [j.enabledIntegrations]: [{ [intgr.id]: 'geoid' }],
    });
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve({
        ok: true,
        json: () =>
          Promise.resolve({ id: '019d4e5a-d6bb-7000-99d2-3c0d4081586b' }),
      } as Response)
    );

    await onCreateLoiHandler({
      data: newDocumentSnapshot(loiDoc) as unknown as QueryDocumentSnapshot,
      params: { surveyId: SURVEY_ID, loiId: LOI_ID },
    } as unknown as FirestoreEvent<QueryDocumentSnapshot | undefined>);

    const loiData = (await mockFirestore.doc(LOI_PATH).get()).data();
    expect(loiData?.[l.properties]?.['geoid_id']).toEqual({
      [pr.stringValue]: '019d4e5a-d6bb-7000-99d2-3c0d4081586b',
    });
  });

  it('skips geoid property generator when fetch fails', async () => {
    mockFirestore.doc(JOB_PATH).set({
      [j.enabledIntegrations]: [{ [intgr.id]: 'geoid' }],
    });
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve({
        ok: false,
        status: 500,
        text: () => Promise.resolve('Internal Server Error'),
      } as Response)
    );

    await onCreateLoiHandler({
      data: newDocumentSnapshot(loiDoc) as unknown as QueryDocumentSnapshot,
      params: { surveyId: SURVEY_ID, loiId: LOI_ID },
    } as unknown as FirestoreEvent<QueryDocumentSnapshot | undefined>);

    const loiData = (await mockFirestore.doc(LOI_PATH).get()).data();
    expect(loiData?.[l.properties]?.['geoid_geoid_code']).toBeUndefined();
  });
});
