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

import { EventContext } from 'firebase-functions';
import { QueryDocumentSnapshot } from 'firebase-functions/v1/firestore';
import { getDatastore } from './common/context';
import { Datastore } from './common/datastore';
import { broadcastSurveyUpdate } from './common/broadcast-survey-update';
import { GroundProtos } from '@ground/proto';
import { toDocumentData, toGeoJsonGeometry, toMessage } from '@ground/lib';
import { geojsonToWKT } from '@terraformer/wkt';
import { toLoiPbProperties } from './import-geojson';

import Pb = GroundProtos.ground.v1beta1;

type Properties = { [key: string]: string | number };

type Headers = { [key: string]: string };

type Body = { [key: string]: any };

type PropertyGenerator = {
  headers?: Headers;
  body?: Body;
  name: string;
  prefix: string;
  url: string;
};

const defaultHeaders = { 'Content-Type': 'application/json' };

/**
 * Handles the creation of a Location of Interest (LOI) document in Firestore.
 * This function is triggered by a Cloud Function on Firestore document creation.
 *
 * @param snapshot The QueryDocumentSnapshot object containing the created LOI data.
 * @param context The EventContext object provided by the Cloud Functions framework.
 */
export async function onCreateLoiHandler(
  snapshot: QueryDocumentSnapshot,
  context: EventContext
) {
  const surveyId = context.params.surveyId;
  const loiId = context.params.loiId;
  const data = snapshot.data();

  if (!loiId || !data) return;

  const loiPb = toMessage(data, Pb.LocationOfInterest) as Pb.LocationOfInterest;

  const geometry = toGeoJsonGeometry(loiPb.geometry!);

  const db = getDatastore();

  let properties = propertiesPbToObject(loiPb.properties) || {};

  const propertyGenerators = await db.fetchPropertyGenerators();

  for (const propertyGeneratorDoc of propertyGenerators.docs) {
    const propertyGenerator = propertyGeneratorDoc.data() as PropertyGenerator;

    if (propertyGeneratorDoc.id === 'whisp') {
      const { body, headers, prefix, url } = propertyGenerator;

      const wkt = geojsonToWKT(Datastore.fromFirestoreMap(geometry));

      const newProperties = await fetchWhispProperties(
        url,
        { ...defaultHeaders, ...headers },
        { wkt, ...body }
      );

      properties = await updateProperties(properties, newProperties, prefix);
    }

    Object.keys(properties)
      .filter(key => typeof properties[key] === 'object')
      .forEach(key => (properties[key] = JSON.stringify(properties[key])));
  }

  await db.updateLoiProperties(
    surveyId,
    loiId,
    toDocumentData(
      new Pb.LocationOfInterest({ properties: toLoiPbProperties(properties) })
    )
  );

  await broadcastSurveyUpdate(context.params.surveyId);
}

async function updateProperties(
  properties: Properties,
  newProperties: Properties,
  prefix?: string
): Promise<Properties> {
  if (prefix) properties = removePrefixedKeys(properties, prefix);

  return {
    ...properties,
    ...(prefix ? prefixKeys(newProperties, prefix) : newProperties),
  };
}

async function fetchWhispProperties(
  url: string,
  headers: Headers,
  body: Body
): Promise<Properties> {
  // eslint-disable-next-line n/no-unsupported-features/node-builtins
  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  });

  if (!response.ok) return {};

  const json = (await response.json()) as any;

  if (json?.code !== 'analysis_completed') return {};

  return json?.data?.features[0]?.properties || {};
}

/**
 * Returns a new object with all keys of the original object prefixed with the given value.
 */
function prefixKeys(obj: Properties, prefix: string): Properties {
  return Object.keys(obj).reduce(
    (a, k) => ((a[`${prefix}${k}`] = obj[k]), a),
    {} as Properties
  );
}

/**
 * Returns a new object containing only the keys that do not start with the specified prefix.
 */
function removePrefixedKeys(obj: Properties, prefix: string): Properties {
  Object.keys(obj).forEach(k => {
    if (k.startsWith(prefix)) delete obj[k];
  });
  return obj;
}

function propertiesPbToObject(pb: {
  [k: string]: Pb.LocationOfInterest.IProperty;
}): Properties {
  const properties: { [k: string]: string | number } = {};
  for (const k of Object.keys(pb)) {
    const v = pb[k].stringValue || pb[k].numericValue;
    if (v !== null && v !== undefined) {
      properties[k] = v;
    }
  }
  return properties;
}
