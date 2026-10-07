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

import { GeoPoint } from 'firebase-admin/firestore';
import {
  Change,
  DocumentSnapshot,
  FirestoreEvent,
} from 'firebase-functions/v2/firestore';
import { newDocumentSnapshot } from '@ground/lib/testing/firestore';
import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';
import { onUpdateLoiHandler } from './on-update-loi';
import * as broadcastModule from './common/broadcast';

import Pb = GroundProtos.ground.v1beta1;

const l = registry.getFieldIds(Pb.LocationOfInterest);
const ai = registry.getFieldIds(Pb.AuditInfo);
const ts = registry.getFieldIds(GroundProtos.google.protobuf.Timestamp);

describe('onUpdateLoiHandler()', () => {
  const SURVEY_ID = 'survey1';
  const LOI_ID = 'loi1';
  const EVENT_TIME = '2026-01-02T03:04:06.000Z';
  const IMPORTED = Pb.LocationOfInterest.Source.IMPORTED;

  let broadcastSpy: jasmine.Spy;

  beforeEach(() => {
    broadcastSpy = spyOn(broadcastModule, 'broadcastUpdate').and.returnValue(
      Promise.resolve('')
    );
  });

  function updatedEvent(
    params: Record<string, string> = {},
    after: Record<string, unknown> = {},
    before: Record<string, unknown> = {}
  ) {
    return {
      data: {
        before: newDocumentSnapshot(before),
        after: newDocumentSnapshot(after),
      },
      params: { surveyId: SURVEY_ID, loiId: LOI_ID, ...params },
      time: EVENT_TIME,
    } as unknown as FirestoreEvent<Change<DocumentSnapshot> | undefined>;
  }

  /** A geometry as Firestore hands it back: nested maps around a GeoPoint. */
  function geometry() {
    return { '1': { '1': new GeoPoint(10, 20) } };
  }

  function expectAnnounced() {
    expect(broadcastSpy).toHaveBeenCalledOnceWith(
      { type: 'loi', surveyId: SURVEY_ID, loiId: LOI_ID, deleted: false },
      EVENT_TIME
    );
  }

  it('announces the updated LOI', async () => {
    await onUpdateLoiHandler(updatedEvent());

    expectAnnounced();
  });

  it('announces an imported LOI whose submission count changed', async () => {
    await onUpdateLoiHandler(
      updatedEvent(
        {},
        { [l.source]: IMPORTED, [l.submissionCount]: 1 },
        { [l.source]: IMPORTED, [l.submissionCount]: 0 }
      )
    );

    expectAnnounced();
  });

  it('announces an imported LOI whose properties and geometry both changed', async () => {
    await onUpdateLoiHandler(
      updatedEvent(
        {},
        {
          [l.source]: IMPORTED,
          [l.geometry]: { '1': { '1': new GeoPoint(30, 40) } },
          [l.properties]: { area: 2 },
        },
        {
          [l.source]: IMPORTED,
          [l.geometry]: geometry(),
          [l.properties]: { area: 1 },
        }
      )
    );

    expectAnnounced();
  });

  it('leaves the post-import property regeneration to the batch announcement', async () => {
    await onUpdateLoiHandler(
      updatedEvent(
        {},
        {
          [l.source]: IMPORTED,
          [l.geometry]: geometry(),
          [l.properties]: { area: 1 },
        },
        { [l.source]: IMPORTED, [l.geometry]: geometry(), [l.properties]: {} }
      )
    );

    expect(broadcastSpy).not.toHaveBeenCalled();
  });

  it('leaves the audit info backfill unannounced', async () => {
    const auditInfo = {
      [ai.userId]: 'user1',
      [ai.serverTimestamp]: { [ts.seconds]: Date.UTC(2026, 0, 2) / 1000 },
    };

    await onUpdateLoiHandler(
      updatedEvent(
        {},
        {
          [l.source]: IMPORTED,
          [l.geometry]: geometry(),
          [l.created]: auditInfo,
          [l.lastModified]: auditInfo,
        },
        { [l.source]: IMPORTED, [l.geometry]: geometry() }
      )
    );

    expect(broadcastSpy).not.toHaveBeenCalled();
  });

  it('announces an imported LOI whose audit info and submission count both changed', async () => {
    const auditInfo = {
      [ai.userId]: 'user1',
      [ai.serverTimestamp]: { [ts.seconds]: Date.UTC(2026, 0, 2) / 1000 },
    };

    await onUpdateLoiHandler(
      updatedEvent(
        {},
        {
          [l.source]: IMPORTED,
          [l.lastModified]: auditInfo,
          [l.submissionCount]: 1,
        },
        { [l.source]: IMPORTED, [l.submissionCount]: 0 }
      )
    );

    expectAnnounced();
  });

  it('announces a client-created LOI whose properties alone changed', async () => {
    await onUpdateLoiHandler(
      updatedEvent(
        {},
        {
          [l.source]: Pb.LocationOfInterest.Source.FIELD_DATA,
          [l.properties]: { area: 1 },
        },
        { [l.source]: Pb.LocationOfInterest.Source.FIELD_DATA }
      )
    );

    expectAnnounced();
  });

  it('does nothing when the event carries no loi id', async () => {
    await onUpdateLoiHandler(updatedEvent({ loiId: '' }));

    expect(broadcastSpy).not.toHaveBeenCalled();
  });
});
