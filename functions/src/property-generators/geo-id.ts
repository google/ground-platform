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
import type { Geometry } from 'geojson';
import type { Headers, Properties, PropertyGeneratorConfig } from './types';

const defaultHeaders = { 'Content-Type': 'application/json' };

export async function geoIdHandler(
  config: PropertyGeneratorConfig,
  geometry: Geometry
): Promise<Properties> {
  const { headers, url } = config;

  return fetchGeoIdProperties(
    url,
    { ...defaultHeaders, ...headers },
    { type: 'Feature', geometry, properties: {} }
  );
}

async function fetchGeoIdProperties(
  url: string,
  headers: Headers,
  body: object
): Promise<Properties> {
  const bodyJson = JSON.stringify(body);
  logger.debug(`geoId: POST ${url} body=${bodyJson}`);

  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: bodyJson,
  });

  if (!response.ok) {
    const errorBody = await response.text();
    logger.error(
      `geoId: request failed with status ${response.status}: ${errorBody}`
    );
    return {};
  }

  const responseJson = await response.json();
  const id = responseJson?.id;
  if (!id) {
    logger.error(
      `geoId: response missing id field, body=${JSON.stringify(responseJson)}`
    );
    return {};
  }
  logger.debug(`geoId: received id=${id}`);

  return { id };
}
