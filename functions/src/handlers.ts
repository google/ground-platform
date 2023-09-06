/**
 * Copyright 2023 Google LLC
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

import * as cors from 'cors';
import * as express from "express";
import { DecodedIdToken } from 'firebase-admin/auth';
import * as functions from 'firebase-functions';
import { decodeIdToken } from './common/auth';
import { UNAUTHORIZED } from 'http-status-codes';

const corsOptions = {origin: true};
const corsMiddleware = cors(corsOptions);

async function requireIdToken(req: functions.https.Request, res: express.Response, next: (decodedIdToken: DecodedIdToken) => Promise<any>): Promise<any> {
  const decodedIdToken = await decodeIdToken(req);
  if (decodedIdToken) {
    return next(decodedIdToken);
  } else {
    return res.status(UNAUTHORIZED).send('Unauthorized');
  }
}

function onError(res: any, err: any) {
    console.error(err);
    res.status(500).send('Internal error');
}

export type HttpsRequestHandler = (req: functions.https.Request, res: express.Response, idToken: DecodedIdToken) => Promise<any>

export function onHttpsRequest(handler: HttpsRequestHandler) {
  return functions.https.onRequest((req: functions.https.Request, res: express.Response) =>
    corsMiddleware(req, res, () =>
      requireIdToken(req, res, (idToken: DecodedIdToken) => 
        handler(req, res, idToken).catch((error: any) => onError(res, error))
      )
    )
  );
}
