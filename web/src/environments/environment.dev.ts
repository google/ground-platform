/**
 * Copyright 2022 Google LLC
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

import { googleMapsConfig } from './.google-maps-config';
import { firebaseConfig } from './.firebase-config';
import { offlineBaseMapSources } from './.backend-config.json';
import { Env } from './environment-enums';

export const environment = {
  production: false,
  googleMapsApiKey: googleMapsConfig.apiKey,
  firebase: firebaseConfig,
  cloudFunctionsUrl: '',
  offlineBaseMapSources,
  useEmulators: false,
  env: Env.Dev,
};
