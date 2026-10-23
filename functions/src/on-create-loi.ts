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

/**
 * Fills in a newly created Location of Interest with generated properties and
 * corrects the server timestamps its client could only guess at.
 *
 * That fix-up is itself a write, which `onUpdateLoi` announces - so the
 * broadcast is left to it, and clients never see the half-populated document.
 * When there is nothing to fix up no update follows, so this announces the LOI
 * itself. Between them the two handlers announce a created LOI exactly once.
 *
 * Imported LOIs are the exception, and are announced by neither handler:
 * `importGeoJson` sends a single message for the whole batch once the import
 * finishes. Their audit info is left alone too - the import stamps it just
 * before writing, so there is nothing to correct. Their properties are still
 * generated like everyone else's; that write simply goes unannounced.
 */
export async function onCreateLoiHandler(
  event: FirestoreEvent<DocumentSnapshot | undefined>
) {
  const { surveyId, loiId } = event.params;
  const data = event.data?.data();

  if (!surveyId || !loiId || !data) return;

  const loiPb = toMessage(data, Pb.LocationOfInterest) as Pb.LocationOfInterest;
  const db = getDatastore();

  const isImported = loiPb.source === Pb.LocationOfInterest.Source.IMPORTED;

  const properties = await regenerateLoiProperties(db, surveyId, loiId, loiPb);
  const auditInfo = isImported ? {} : correctedAuditInfo(loiPb, event.time);
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

  // importGeoJson announces the batch this LOI came in.
  if (isImported) return;

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
