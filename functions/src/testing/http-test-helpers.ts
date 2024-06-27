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

import functions from 'firebase-functions';
import {buffer} from 'node:stream/consumers';
import {FormDataEncoder} from 'form-data-encoder';

export async function createPostRequestSpy(
  args: object,
  form: FormData
): Promise<functions.https.Request> {
  const encoder = new FormDataEncoder(form);
  return jasmine.createSpyObj<functions.https.Request>('request', ['unpipe'], {
    ...args,
    method: 'POST',
    headers: encoder.headers,
    rawBody: await buffer(encoder),
  });
}

export function createResponseSpy(): functions.Response<any> {
  const res = jasmine.createSpyObj<functions.Response<any>>('response', [
    'send',
    'status',
    'end',
  ]);
  res.status.and.returnValue(res);
  res.end.and.returnValue(res);
  return res;
}
