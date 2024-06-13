/**
 * Copyright 2023 The Ground Authors.
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

import cors from 'cors';
import {DecodedIdToken} from 'firebase-admin/auth';
import {https, Response} from 'firebase-functions';
import {getDecodedIdToken} from './common/auth';
import {INTERNAL_SERVER_ERROR, UNAUTHORIZED} from 'http-status-codes';
import cookieParser from 'cookie-parser';

const corsOptions = {origin: true};
const corsMiddleware = cors(corsOptions);

/** Token to be used when running on local emulator for debugging. */
export class EmulatorIdToken implements DecodedIdToken {
  aud = '';
  auth_time = 0;
  exp = 0;
  firebase = {identities: {}, sign_in_provider: ''};
  iat = 0;
  iss = '';
  sub = '';
  uid = 'emu';
}

/**
 * Checks for and extracts the current user's details, passing them to the provided function if found.
 * Sends an UNAUTHORIZED HTTP response code if not present or invalid.
 */
async function requireIdToken(
  req: https.Request,
  res: Response,
  next: (decodedIdToken: DecodedIdToken) => Promise<any>
): Promise<any> {
  if (process.env.FUNCTIONS_EMULATOR === 'true') {
    console.warn('Local emulator detected. Bypassing authentication.');
    return next(new EmulatorIdToken());
  }
  const token = await getDecodedIdToken(req);
  if (token) {
    return next(token);
  } else {
    return res.status(UNAUTHORIZED).send('Unauthorized');
  }
}

function onError(res: any, err: any) {
  console.error(err);
  res.status(INTERNAL_SERVER_ERROR).end(`Internal error: ${err.message}`);
}

export type HttpsRequestHandler = (
  req: https.Request,
  res: Response,
  idToken: DecodedIdToken
) => Promise<any>;

export function onHttpsRequest(handler: HttpsRequestHandler) {
  return https.onRequest((req: https.Request, res: Response) =>
    corsMiddleware(req, res, () =>
      cookieParser()(
        req,
        res,
        async () =>
          await requireIdToken(req, res, async (idToken: DecodedIdToken) => {
            try {
              await handler(req, res, idToken);
            } catch (error) {
              onError(res, error);
            }
          })
      )
    )
  );
}
