/**
 * @license
 * Copyright 2022 Google LLC
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
import { Change, EventContext } from 'firebase-functions';
import { DocumentSnapshot } from 'firebase-functions/v1/firestore';
import { survey, loi } from './common/datastore';

/** Template for survey write triggers capturing survey id. */
export const surveyPathTemplate = survey('{surveyId}');

/** Template for LOI write triggers capturing survey and LOI ids. */
export const loiPathTemplate = loi('{surveyId}', '{loiId}');

export function onWriteSurveyHandler(
  _: Change<DocumentSnapshot>,
  context: EventContext
): Promise<string> {
  // Messages are sent without a payload so that they can collapsed. Collapsible
  // messages are more performant, and they may be replaced by newer messages if
  // necessary. This is important when importing LOIs, which may trigger
  // hundred of updates in a short period of time.
  // See also: https://firebase.google.com/docs/cloud-messaging/concept-options#collapsible_and_non-collapsible_messages
  const topic = context.params.surveyId;
  console.debug(`Sending message to ${topic}`);
  return admin.messaging().send({ topic });
}
