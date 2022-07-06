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
  firebase: {
    apiKey: process.env.FIREBASE_CONFIG_API_KEY,
    authDomain: process.env.FIREBASE_CONFIG_AUTH_DOMAIN,
    databaseUrl: process.env.FIREBASE_CONFIG_DATABASE_URL,
    projectId: process.env.FIREBASE_CONFIG_PROJECT_ID,
    storageBucket: process.env.FIREBASE_CONFIG_STORAGE_BUCKET,
    messagingSenderId: process.env.FIREBASE_CONFIG_MESSAGING_SENDER_ID,
    configAppId: process.env.FIREBASE_CONFIG_APP_ID,
  },
  cloudFunctionsUrl: process.env.CLOUD_FUNCTIONS_URL,
  offlineBaseMapSources: [
    {
      url: process.env.OFFLINE_BASE_MAP_SOURCES_URL,
    },
  ],
  useEmulators: false,
  env: Env.Prod,
};