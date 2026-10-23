/**
 * Copyright 2022 The Ground Authors.
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
import { GroundProtos } from '@ground/proto';
import { toDocumentData, toMessage } from '@ground/lib';
import { withServerTimestamp } from './common/audit-info';
import { broadcastUpdate } from './common/broadcast';
import { getDatastore } from './common/context';

import Pb = GroundProtos.ground.v1beta1;

/**
 * Corrects the server timestamps on a survey's audit info, which clients can
 * only guess from their own clock, then announces the survey.
 *
 * The correction is itself a write, which this handler sees again; the
 * broadcast is left to that second pass so clients never see the uncorrected
 * document. Only audit info a client just wrote (a different user or client
 * time than before) is corrected, which also keeps the second pass from
 * correcting its own write.
 */
export async function onWriteSurveyHandler(
  event: FirestoreEvent<Change<DocumentSnapshot> | undefined>
): Promise<string | void> {
  const surveyId = event.params.surveyId;
  const before = event.data?.before;
  const after = event.data?.after;

  if (after?.exists) {
    const beforePb = before?.exists
      ? (toMessage(before.data()!, Pb.Survey) as Pb.Survey)
      : undefined;
    const afterPb = toMessage(after.data()!, Pb.Survey) as Pb.Survey;
    const corrected = correctedAuditInfo(beforePb, afterPb, event.time);

    if (Object.keys(corrected).length) {
      await getDatastore().updateSurveyAuditInfo(
        surveyId,
        toDocumentData(new Pb.Survey(corrected)) as Record<string, unknown>
      );
      return;
    }
  }

  return broadcastUpdate({ type: 'survey', surveyId }, event.time);
}

function correctedAuditInfo(
  before: Pb.ISurvey | undefined,
  after: Pb.ISurvey,
  eventTime: string
): Partial<Pb.Survey> {
  const corrected: Partial<Pb.Survey> = {};
  if (isNewAuditInfo(before?.created, after.created))
    corrected.created = withServerTimestamp(after.created!, eventTime);
  if (isNewAuditInfo(before?.lastModified, after.lastModified))
    corrected.lastModified = withServerTimestamp(
      after.lastModified!,
      eventTime
    );
  return corrected;
}

/**
 * Returns true if `after` was written by a client since `before`. The server
 * timestamp is ignored, since it is the one thing the server itself changes.
 */
function isNewAuditInfo(
  before: Pb.IAuditInfo | null | undefined,
  after: Pb.IAuditInfo | null | undefined
): boolean {
  if (!after) return false;
  if (!before) return true;
  return (
    before.userId !== after.userId ||
    Number(before.clientTimestamp?.seconds ?? 0) !==
      Number(after.clientTimestamp?.seconds ?? 0)
  );
}
