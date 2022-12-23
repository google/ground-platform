/**
 * @license
 * Copyright 2020 Google LLC
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

import * as admin from 'firebase-admin';
import { Map } from 'immutable';

export async function sendUpdateMessage(
  topic: string,
  timestamp: string,
  ids: Map<string, string>
): Promise<string> {
  if (!/^[a-zA-Z0-9-_.~%]+$/.test(topic)) {
    throw new Error(`Malformed topic name: ${topic}`);
  }
  const message = {
    topic,
    data: {
      ...ids.toJS(),
      timestamp,
    },
  };
  console.debug(`Sending message: ${JSON.stringify(message)}`);
  return await admin.messaging().send(message);
}
