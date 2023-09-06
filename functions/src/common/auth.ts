/**
 * @license
 * Copyright 2023 Google LLC
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

import {DecodedIdToken, getAuth} from 'firebase-admin/auth';
import {https} from 'firebase-functions/v1';

async function getIdToken(req: https.Request): Promise<string | undefined> {
  const authHeader = req.headers.authorization;
  if (authHeader?.startsWith('Bearer ')) {
    // Read the ID Token from the Authorization header.
    return authHeader.split('Bearer ')[1];
  } else if (req.cookies) {
    // Read the ID Token from cookie.
    return req.cookies.__session;
  } else {
    return;
  }
}

// The Firebase ID token needs to be passed as a Bearer token in the Authorization HTTP header like this:
// `Authorization: Bearer <Firebase ID Token>`.
// when decoded successfully, the ID Token content will be added as `req.user`.
// Based on https://github.com/firebase/functions-samples/blob/main/Node-1st-gen/authorized-https-endpoint/functions/index.js
export async function decodeIdToken(
  req: https.Request
): Promise<DecodedIdToken | undefined> {
  const idToken = await getIdToken(req);
  if (!idToken) return;
  return await getAuth().verifyIdToken(idToken);
}
