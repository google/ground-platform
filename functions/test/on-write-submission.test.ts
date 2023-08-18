/**
 * @license
 * Copyright 2023 Google LLC
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
import * as firebaseFunctionsTest from "firebase-functions-test";

// At the top of test/index.test.js
const test = firebaseFunctionsTest({
  databaseURL: 'https://gnd-dev.firebaseio.com',
  storageBucket: 'gnd-dev.appspot.com',
  projectId: 'gnd-dev',
}, '.test-service-account-key.json');

// import { describe, it } from "node:test";
 
// import assert from 'chai';
import { onWriteSubmission } from '../src/index'; // relative path to functions code


// describe("onWriteSubmissionTest", async () => {
  it("should update count on new submission", async () => {
    const before = test.firestore.makeDocumentSnapshot({}, 'survey/100/submissions/123');
    const after = test.firestore.makeDocumentSnapshot({loiId: '999'}, 'survey/100/submissions/123');
    const change = test.makeChange(before, after);
    const wrapped = test.wrap(onWriteSubmission);
    await wrapped(change);
  });
// });