import { Map } from 'immutable';
/**
 * @license
 * Copyright 2018 Google LLC
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

import 'module-alias/register';
import * as functions from 'firebase-functions';
import * as cors from 'cors';
import { handleCreateUser } from '@/on-create-user';
import { importCsvHandler } from '@/import-csv';
import { importGeoJsonHandler } from '@/import-geojson';
import { exportCsvHandler } from '@/export-csv';
import { sendUpdateMessage } from '@/common/messaging';
import { Change, EventContext } from 'firebase-functions';
import { DocumentSnapshot } from 'firebase-functions/v1/firestore';

const corsOptions = { origin: true };
const corsMiddleware = cors(corsOptions);

function onHttpsRequest(handler: any) {
  return functions.https.onRequest((req, res) =>
    corsMiddleware(req, res, () =>
      handler(req, res).catch((error: any) => onError(res, error))
    )
  );
}

function onError(res: any, err: any) {
  console.error(err);
  res.status(500).send('Internal error');
}

// Create user profile in database when user first logs in.
export const onCreateUser = functions.auth.user().onCreate(handleCreateUser);

export const importCsv = onHttpsRequest(importCsvHandler);

export const importGeoJson = onHttpsRequest(importGeoJsonHandler);

export const exportCsv = onHttpsRequest(exportCsvHandler);

async function onWriteHandler(
  _: Change<DocumentSnapshot>,
  context: EventContext
) {
  const { survey, ...otherIds } = context.params;
  sendUpdateMessage(survey, context.timestamp, Map(otherIds));
}

export const onWriteSurvey = functions.firestore
  .document('surveys/{survey}')
  .onWrite(onWriteHandler);

export const onWriteLoi = functions.firestore
  .document('surveys/{survey}/lois/{loi}')
  .onWrite(onWriteHandler);
