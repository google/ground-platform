/**
 * Copyright 2024 The Ground Authors.
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
import { getDatastore } from './common/context';
import { withServerTimestamp } from './common/audit-info';
import { broadcastUpdate } from './common/broadcast';
import {
  propertiesEqual,
  propertiesPbToObject,
  regenerateLoiProperties,
} from './common/loi-properties';
import { GroundProtos } from '@ground/proto';
import { toDocumentData, toMessage } from '@ground/lib';
import { toLoiPbProperties } from './import-geojson';

import Pb = GroundProtos.ground.v1beta1;

export async function onCreateLoiHandler(
  event: FirestoreEvent<DocumentSnapshot | undefined>
) {
  const { surveyId, loiId } = event.params;
  const data = event.data?.data();

  if (!surveyId || !loiId || !data) return;

  const loiPb = toMessage(data, Pb.LocationOfInterest) as Pb.LocationOfInterest;
  const db = getDatastore();

  const properties = await regenerateLoiProperties(db, surveyId, loiId, loiPb);
  const auditInfo = correctedAuditInfo(loiPb, event.time);
  const propertiesChanged = !propertiesEqual(
    propertiesPbToObject(loiPb.properties),
    properties
  );

  if (propertiesChanged || Object.keys(auditInfo).length) {
    await db.updateLoiProperties(
      surveyId,
      loiId,
      toDocumentData(
        new Pb.LocationOfInterest({
          properties: toLoiPbProperties(properties),
          ...auditInfo,
        })
      )
    );

    // onUpdateLoi announces the write just made.
    return;
  }

  return broadcastUpdate(
    { type: 'loi', surveyId, loiId, deleted: false },
    event.time
  );
}

function correctedAuditInfo(
  loiPb: Pb.LocationOfInterest,
  eventTime: string
): Partial<Pb.LocationOfInterest> {
  if (!loiPb.created) return {};

  const created = withServerTimestamp(loiPb.created, eventTime);

  return {
    created,
    lastModified: loiPb.lastModified
      ? withServerTimestamp(loiPb.lastModified, eventTime)
      : created,
  };
}
