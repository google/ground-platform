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
import { DecodedIdToken } from 'firebase-admin/auth';
import { HttpsOptions, Request, onRequest } from 'firebase-functions/v2/https';
import type { Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { getDecodedIdToken } from './common/auth';
import { HttpError } from './common/http-error';
import cookieParser from 'cookie-parser';

const corsOptions = { origin: true };
const corsMiddleware = cors(corsOptions);

/** Token to be used when running on local emulator for debugging. */
export class EmulatorIdToken implements DecodedIdToken {
  aud = '';
  auth_time = 0;
  exp = 0;
  firebase = { identities: {}, sign_in_provider: '' };
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
  req: Request,
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
    return res.status(StatusCodes.UNAUTHORIZED).send('Unauthorized');
  }
}

export type HttpsRequestHandler = (
  req: Request,
  res: Response,
  idToken: DecodedIdToken
) => Promise<any>;

/**
 * Wraps an async HTTPS request handler with CORS, cookie parsing, and ID-token
 * authentication. Translates `HttpError` thrown by the handler into the
 * corresponding HTTP response. Generic errors become 500. On success, sends
 * 200 OK if the handler did not already write headers (e.g. via `res.send`,
 * `res.json`, `res.redirect`, or streaming).
 */
export function onHttpsRequest(
  handler: HttpsRequestHandler,
  options: HttpsOptions = {}
) {
  return onRequest(options, (req: Request, res: Response) =>
    corsMiddleware(req, res, () =>
      cookieParser()(
        req as any,
        res as any,
        async () =>
          await requireIdToken(req, res, async (idToken: DecodedIdToken) => {
            try {
              await handler(req, res, idToken);
              if (!res.headersSent) res.status(StatusCodes.OK).end();
            } catch (err) {
              if (err instanceof HttpError) {
                res.status(err.statusCode).send(err.message);
              } else {
                console.error(err);
                res
                  .status(StatusCodes.INTERNAL_SERVER_ERROR)
                  .end(`Internal error: ${(err as Error).message}`);
              }
            }
          })
      )
    )
  );
}
