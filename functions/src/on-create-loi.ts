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
import {
  DocumentSnapshot,
  QueryDocumentSnapshot,
} from 'firebase-functions/v1/firestore';
import {db} from '@/common/context';
// eslint-disable-next-line absolute-imports/only-absolute-imports
import {loi} from './common/datastore';
// eslint-disable-next-line absolute-imports/only-absolute-imports
// import {broadcastSurveyUpdate} from './common/broadcast-survey-update';
import {geojsonToWKT} from '@terraformer/wkt';

/** Template for LOI write triggers capturing survey and LOI ids. */
export const loiPathTemplate = loi('{surveyId}', '{loiId}');

export async function onCreateLoiHandler(
  snapshot: QueryDocumentSnapshot,
  context: EventContext
) {
  const surveyId = context.params.surveyId;
  const loiId = context.params.loiId;
  const loi = snapshot.data();

  // await broadcastSurveyUpdate(context.params.surveyId);

  if (!loiId || !loi) return;

  const hasAddLoiTask = await db.hasAddLoiTask(surveyId, loi.jobId);

  if (hasAddLoiTask) {
    let properties = loi.properties || {};

    const loiPropertyGenerators = await db.fetchLoiPropertyGenerators();

    await Promise.all(
      loiPropertyGenerators.docs.map(async loiPropertyGeneratorDoc => {
        const loiPropertyGenerator = loiPropertyGeneratorDoc.data();

        const {url, prefix} = loiPropertyGenerator as Partial<{
          url: string;
          prefix: string;
        }>;

        const wkt = geojsonToWKT(loi.geometry || '');

        const newProperties = await getIntegrationProperties(url || '', wkt);

        properties = {
          ...properties,
          ...(prefix ? prefixKeys(newProperties, prefix) : newProperties),
        };
      })
    );

    await db.updateLoiProperties(surveyId, loiId, properties);
  }
}

const getIntegrationProperties = async (
  url: string,
  wkt: string
): Promise<{[key: string]: string}> => {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({wkt}),
  });

  return response.json() || {};
};

const prefixKeys = (obj: {[key: string]: string}, prefix: string) =>
  Object.keys(obj).reduce(
    (a, k) => ((a[`${prefix}${k}`] = obj[k]), a),
    {} as {[key: string]: string}
  );
