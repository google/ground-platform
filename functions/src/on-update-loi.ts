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

import { DocumentData } from 'firebase-admin/firestore';
import {
  Change,
  DocumentSnapshot,
  FirestoreEvent,
} from 'firebase-functions/v2/firestore';
import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';

import Pb = GroundProtos.ground.v1beta1;
import { broadcastUpdate } from './common/broadcast';

const l = registry.getFieldIds(Pb.LocationOfInterest);

/**
 * Fields a server-side fix-up rewrites on an imported LOI without changing
 * anything a client renders: the properties `onCreateLoi` regenerates over a
 * freshly imported LOI, and the audit info `sanitize-imported-loi-audit-info`
 * backfills onto LOIs imported before the import stamped it. Both run over a
 * whole survey at once, so announcing them per LOI would put back the flood
 * that `importGeoJson`'s single batch message exists to replace.
 */
const IMPORTED_LOI_FIX_UP_FIELDS = [l.properties, l.created, l.lastModified];

/**
 * Announces an updated Location of Interest.
 *
 * This must announce every update to a client-created LOI, the fix-up write
 * `onCreateLoi` makes included: that handler stays silent when it writes and
 * relies on this one, so filtering those out would leave created LOIs
 * unannounced.
 *
 * Imported LOIs are announced too, and only a write confined to
 * `IMPORTED_LOI_FIX_UP_FIELDS` is passed over - what identifies a fix-up is the
 * fields it leaves alone, not the LOI being imported. Everything else about an
 * imported LOI is announced like any other, the submission count a submission
 * write leaves behind among them.
 */
export async function onUpdateLoiHandler(
  event: FirestoreEvent<Change<DocumentSnapshot> | undefined>
) {
  const { surveyId, loiId } = event.params;

  if (!surveyId || !loiId) return;

  const { before, after } = event.data ?? {};
  const isImported =
    after?.get(l.source) === Pb.LocationOfInterest.Source.IMPORTED;

  if (isImported && onlyFixUpFieldsChanged(before, after)) return;

  return broadcastUpdate(
    { type: 'loi', surveyId, loiId, deleted: false },
    event.time
  );
}

/**
 * Returns whether the update left every field outside
 * `IMPORTED_LOI_FIX_UP_FIELDS` as it was.
 */
function onlyFixUpFieldsChanged(
  before?: DocumentSnapshot,
  after?: DocumentSnapshot
): boolean {
  const beforeData = before?.data() ?? {};
  const afterData = after?.data() ?? {};
  const fields = new Set([
    ...Object.keys(beforeData),
    ...Object.keys(afterData),
  ]);

  for (const field of IMPORTED_LOI_FIX_UP_FIELDS) fields.delete(field);

  return [...fields].every(f => valuesEqual(beforeData[f], afterData[f]));
}

/**
 * Compares two Firestore field values structurally. Each snapshot is
 * deserialized on its own, so the maps a geometry nests and the `GeoPoint`s at
 * the bottom of it are always distinct objects even when the write left them
 * untouched.
 */
function valuesEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true;

  // GeoPoint and Timestamp both compare themselves.
  if (isSelfComparable(a) && isSelfComparable(b)) return a.isEqual(b);

  if (Array.isArray(a) && Array.isArray(b))
    return a.length === b.length && a.every((v, i) => valuesEqual(v, b[i]));

  if (isMap(a) && isMap(b)) {
    const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
    return [...keys].every(k => valuesEqual(a[k], b[k]));
  }

  return false;
}

function isSelfComparable(
  value: unknown
): value is { isEqual(other: unknown): boolean } {
  return typeof (value as { isEqual?: unknown })?.isEqual === 'function';
}

function isMap(value: unknown): value is DocumentData {
  return typeof value === 'object' && value !== null;
}
