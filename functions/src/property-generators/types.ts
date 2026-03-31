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

export type Properties = Record<string, string | number>;

export type Headers = Record<string, string>;

export type Body = Record<string, unknown>;

export type PropertyGeneratorConfig = {
  headers?: Headers;
  body?: Body;
  name: string;
  prefix: string;
  url: string;
};

export type PropertyGeneratorHandler = (
  config: PropertyGeneratorConfig,
  geometry: object
) => Promise<Properties>;
