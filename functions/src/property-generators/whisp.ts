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

import { geojsonToWKT } from '@terraformer/wkt';
import type { Geometry } from 'geojson';
import type {
  Body,
  Headers,
  Properties,
  PropertyGeneratorConfig,
} from './types';

type WhispResponse = {
  code: string;
  data?: {
    features?: Array<{
      properties?: Properties;
    }>;
  };
};

const defaultHeaders = { 'Content-Type': 'application/json' };

export async function whispHandler(
  config: PropertyGeneratorConfig,
  geometry: Geometry
): Promise<Properties> {
  const { body, headers, url } = config;

  const wkt = geojsonToWKT(geometry);

  return fetchWhispProperties(
    url,
    { ...defaultHeaders, ...headers },
    { wkt, ...body }
  );
}

async function fetchWhispProperties(
  url: string,
  headers: Headers,
  body: Body
): Promise<Properties> {
  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  });

  if (!response.ok) return {};

  const json = (await response.json()) as WhispResponse;

  if (json.code !== 'analysis_completed') return {};

  return json.data?.features?.[0]?.properties || {};
}
