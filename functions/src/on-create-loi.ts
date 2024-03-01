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

import {EventContext} from 'firebase-functions';
import {QueryDocumentSnapshot} from 'firebase-functions/v1/firestore';
import {db} from '@/common/context';
import {Datastore} from '@/common/datastore';
import {broadcastSurveyUpdate} from '@/common/broadcast-survey-update';
import {geojsonToWKT} from '@terraformer/wkt';

export async function onCreateLoiHandler(
  snapshot: QueryDocumentSnapshot,
  context: EventContext
) {
  const surveyId = context.params.surveyId;
  const loiId = context.params.loiId;
  const loi = snapshot.data();

  if (!loiId || !loi) return;

  let properties = loi.properties || {};

  const propertyGenerators = await db.fetchPropertyGenerators();

  await Promise.all(
    propertyGenerators.docs.map(async propertyGeneratorDoc => {
      const propertyGenerator = propertyGeneratorDoc.data();

      const {url, prefix} = propertyGenerator as Partial<{
        url: string;
        prefix: string;
      }>;

      if (prefix) properties = removePrefixedKeys(properties, prefix);

      if (url) {
        const wkt = geojsonToWKT(Datastore.fromFirestoreMap(loi.geometry));

        const newProperties = await getIntegrationProperties(url, wkt);

        properties = {
          ...properties,
          ...(prefix ? prefixKeys(newProperties, prefix) : newProperties),
        };
      }
    })
  );

  await db.updateLoiProperties(surveyId, loiId, properties);

  await broadcastSurveyUpdate(context.params.surveyId);
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

const removePrefixedKeys = (obj: {[key: string]: string}, prefix: string) => {
  Object.keys(obj).forEach(k => {
    if (k.startsWith(prefix)) delete obj[k];
  });
  return obj;
};
