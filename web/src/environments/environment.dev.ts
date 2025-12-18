/**
 * Copyright 2022 The Ground Authors.
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

import { Env } from 'environments/environment-enums';

// For Firebase JS SDK v7.20.0 and later, measurementId is optional
const firebaseConfig = {
  apiKey: 'AIzaSyDFMmOycj7L1Mqw_KYfn_IlFt4KgmYuO6k',
  authDomain: 'ground-dev-sig.firebaseapp.com',
  projectId: 'ground-dev-sig',
  storageBucket: 'ground-dev-sig.appspot.com',
  messagingSenderId: '429183886479',
  appId: '1:429183886479:web:96481f53f1202bc2293cfa',
  measurementId: 'G-7CZYYG7L4D',
};

export const environment = {
  production: false,
  googleMapsApiKey: firebaseConfig.apiKey,
  firebase: firebaseConfig,
  cloudFunctionsUrl: `https://${firebaseConfig.projectId}.web.app`,
  useEmulators: false,
  env: Env.Dev,
};
