/**
 * Copyright 2026 The Ground Authors.
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

import * as logger from 'firebase-functions/logger';
import { Datastore } from './datastore';
import { GroundProtos } from '@ground/proto';
import { toGeoJsonGeometry, toMessage } from '@ground/lib';
import {
  Properties,
  PropertyGeneratorConfig,
  propertyGeneratorHandlers,
} from '../property-generators';

import Pb = GroundProtos.ground.v1beta1;

/**
 * Returns the properties of `loiPb` with those of every property generator
 * enabled on its job merged in. Generators are called over the network, so a
 * failing one is logged and skipped rather than failing the whole set.
 */
export async function regenerateLoiProperties(
  db: Datastore,
  surveyId: string,
  loiId: string,
  loiPb: Pb.LocationOfInterest
): Promise<Properties> {
  const geometry = toGeoJsonGeometry(loiPb.geometry!);

  let properties = propertiesPbToObject(loiPb.properties) || {};

  const jobDoc = await db.fetchJob(surveyId, loiPb.jobId);
  const jobPb = toMessage(jobDoc.data()!, Pb.Job) as Pb.Job;
  const enabledIntegrationIds = new Set(
    jobPb.enabledIntegrations.map(i => i.id)
  );

  const propertyGenerators = await db.fetchPropertyGenerators();

  for (const propertyGeneratorDoc of propertyGenerators.docs) {
    const generatorId = propertyGeneratorDoc.id;
    const config = propertyGeneratorDoc.data() as PropertyGeneratorConfig;
    const handler = propertyGeneratorHandlers[generatorId];

    if (!handler) {
      continue;
    }

    if (!enabledIntegrationIds.has(generatorId)) {
      continue;
    }

    try {
      const newProperties = await handler(config, geometry, loiId);
      properties = updateProperties(properties, newProperties, config.prefix);
    } catch (e) {
      logger.error(
        `loiId=${loiId} property generator '${generatorId}' failed:`,
        e
      );
    }

    Object.keys(properties)
      .filter(key => typeof properties[key] === 'object')
      .forEach(key => (properties[key] = JSON.stringify(properties[key])));
  }

  return properties;
}

/** Returns whether both property maps hold the same keys and values. */
export function propertiesEqual(a: Properties, b: Properties): boolean {
  const keys = Object.keys(a);

  return (
    keys.length === Object.keys(b).length && keys.every(k => a[k] === b[k])
  );
}

export function propertiesPbToObject(pb: {
  [k: string]: Pb.LocationOfInterest.IProperty;
}): Properties {
  const properties: { [k: string]: string | number } = {};
  for (const k of Object.keys(pb).sort()) {
    const v = pb[k].stringValue || pb[k].numericValue;
    if (v !== null && v !== undefined) {
      properties[k] = v;
    }
  }
  return properties;
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
