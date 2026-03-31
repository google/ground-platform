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

import {
  FirestoreEvent,
  QueryDocumentSnapshot,
} from 'firebase-functions/v2/firestore';
import { getDatastore } from './common/context';
import { broadcastSurveyUpdate } from './common/broadcast-survey-update';
import { GroundProtos } from '@ground/proto';
import { toDocumentData, toGeoJsonGeometry, toMessage } from '@ground/lib';
import { toLoiPbProperties } from './import-geojson';
import {
  Properties,
  PropertyGeneratorConfig,
  propertyGeneratorHandlers,
} from './property-generators';

import Pb = GroundProtos.ground.v1beta1;

/**
 * Handles the creation of a Location of Interest (LOI) document in Firestore.
 * This function is triggered by a Cloud Function on Firestore document creation.
 *
 * @param snapshot The QueryDocumentSnapshot object containing the created LOI data.
 * @param context The EventContext object provided by the Cloud Functions framework.
 */
export async function onCreateLoiHandler(
  event: FirestoreEvent<QueryDocumentSnapshot | undefined>
) {
  const surveyId = event.params.surveyId;
  const loiId = event.params.loiId;
  const data = event.data?.data();

  if (!loiId || !data) return;

  const loiPb = toMessage(data, Pb.LocationOfInterest) as Pb.LocationOfInterest;

  const geometry = toGeoJsonGeometry(loiPb.geometry!);

  const db = getDatastore();

  let properties = propertiesPbToObject(loiPb.properties) || {};

  const propertyGenerators = await db.fetchPropertyGenerators();

  for (const propertyGeneratorDoc of propertyGenerators.docs) {
    const config = propertyGeneratorDoc.data() as PropertyGeneratorConfig;
    const handler = propertyGeneratorHandlers[propertyGeneratorDoc.id];

    if (!handler) continue;

    const newProperties = await handler(config, geometry);

    properties = updateProperties(properties, newProperties, config.prefix);

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

  await broadcastSurveyUpdate(surveyId);
}

function updateProperties(
  properties: Properties,
  newProperties: Properties,
  prefix?: string
): Properties {
  if (prefix) properties = removePrefixedKeys(properties, prefix);

  return {
    ...properties,
    ...(prefix ? prefixKeys(newProperties, prefix) : newProperties),
  };
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
