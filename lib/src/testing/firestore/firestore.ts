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

import { Firestore } from '@google-cloud/firestore';

// eslint-disable-next-line n/no-extraneous-require
const MockFirebase = require('mock-cloud-firestore');

// Set project as workaround for https://github.com/firebase/firebase-functions/issues/437.
process.env.GCLOUD_PROJECT = 'fake-project';

export function createMockFirestore(): Firestore {
  return new MockFirebase().firestore();
}

/**
 * Returns a new Object with the specified coordinates. Use in place of GeoPoint
 * in tests to work around lack of support in MockFirebase lib.
 */
export function TestGeoPoint(_latitude: number, _longitude: number) {
  return {
    _latitude,
    _longitude,
  };
}
