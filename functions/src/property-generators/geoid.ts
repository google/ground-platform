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

import type { Geometry } from 'geojson';
import type { Headers, Properties, PropertyGeneratorConfig } from './types';

const CATALOG_ID = 'ground';
const COLLECTION_ID = 'ground';

const defaultHeaders = { 'Content-Type': 'application/json' };

const GEOID_ID_PATTERN = /\bid='([^']+)'/;

export async function geoidHandler(
  config: PropertyGeneratorConfig,
  geometry: Geometry
): Promise<Properties> {
  const { headers, url } = config;
  const endpoint = `${url}/features/catalogs/${CATALOG_ID}/collections/${COLLECTION_ID}/items`;

  return fetchGeoidProperties(
    endpoint,
    { ...defaultHeaders, ...headers },
    { type: 'Feature', geometry, properties: {} }
  );
}

async function fetchGeoidProperties(
  url: string,
  headers: Headers,
  body: object
): Promise<Properties> {
  const bodyJson = JSON.stringify(body);
  console.log(`geoid: POST ${url} body=${bodyJson}`);

  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: bodyJson,
  });

  if (!response.ok) {
    const errorBody = await response.text();
    console.error(
      `geoid: request failed with status ${response.status}: ${errorBody}`
    );
    return {};
  }

  const responseText = await response.text();
  const id = GEOID_ID_PATTERN.exec(responseText)?.[1];
  if (!id) {
    console.error(`geoid: response missing id field, body=${responseText}`);
    return {};
  }
  console.log(`geoid: received id=${id}`);

  return { id };
}
