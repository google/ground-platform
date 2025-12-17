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

export function stubAdminApi(mockFirestore: Firestore) {
  spyOn(require('firebase-admin'), 'initializeApp').and.returnValue({
    firestore: () => mockFirestore,
  });
  spyOn(require('firebase-admin/firestore'), 'getFirestore').and.returnValue(
    mockFirestore
  );
}

export function resetSpies() {
  require('firebase-admin').initializeApp.and.callThrough();
  require('firebase-admin/firestore').getFirestore.and.callThrough();
}
