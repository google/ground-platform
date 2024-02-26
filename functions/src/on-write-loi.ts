/**
 * @license
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

import {Change, EventContext} from 'firebase-functions';
import {DocumentSnapshot} from 'firebase-functions/v1/firestore';
import {db} from '@/common/context';
// eslint-disable-next-line absolute-imports/only-absolute-imports
import {loi} from './common/datastore';
// eslint-disable-next-line absolute-imports/only-absolute-imports
import {broadcastSurveyUpdate} from './common/broadcast-survey-update';

/** Template for LOI write triggers capturing survey and LOI ids. */
export const loiPathTemplate = loi('{surveyId}', '{loiId}');

export async function onWriteLoiHandler(
  change: Change<DocumentSnapshot>,
  context: EventContext
) {
  const surveyId = context.params.surveyId;
  const loiId = context.params.loiId;
  const loi = change.after.data();

  await broadcastSurveyUpdate(context.params.surveyId);

  if (!loiId || !loi) return;

  const whisp = true;

  if (whisp) {
    const hasAddLoiTask = await db.hasAddLoiTask(surveyId, loi.jobId);

    if (hasAddLoiTask) {
      const properties = getWhispIntegrationProperties();

      console.log(properties);

      await db.updateLoiProperties(surveyId, loiId, properties);
    }
  }

  const loiPropertyGenerators = await db.fetchLoiPropertyGenerators();

  loiPropertyGenerators.docs.forEach(loiPropertyGenerator => {
    console.log(loiPropertyGenerator.data());
  });
}

const getWhispIntegrationProperties = () => {
  return {whisp_test: 'test'};
};
