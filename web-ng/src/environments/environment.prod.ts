/**
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Env } from './environment-enums';

export const environment = {
  production: true,
  googleMapsApiKey: process.env.GOOGLE_MAPS_API_KEY,
  firebase: process.env.FIREBASE_CONFIG,
  cloudFunctionsUrl: process.env.CLOUD_FUNCTIONS_URL,
  offlineBaseMapSources: process.env.OFFLINE_BASE_MAP_SOURCES,
  useEmulators: false,
  env: Env.Prod,
};
