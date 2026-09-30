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
  Change,
  DocumentSnapshot,
  FirestoreEvent,
} from 'firebase-functions/v2/firestore';
import { getDatastore } from './common/context';
import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';

import Pb = GroundProtos.ground.v1beta1;
const sb = registry.getFieldIds(Pb.Submission);

export async function onWriteSubmissionHandler(
  event: FirestoreEvent<Change<DocumentSnapshot> | undefined>
) {
  const surveyId = event.params.surveyId;
  const change = event.data;
  const loiId = change?.after?.get(sb.loiId) || change?.before?.get(sb.loiId);
  if (!loiId) return;

  const hadData = !!change?.before?.exists;
  const hasData = !!change?.after?.exists;

  if (hadData === hasData) return;

  const delta = hasData ? 1 : -1;

  const db = getDatastore();
  await db.adjustSubmissionCount(surveyId, loiId, delta);
}
