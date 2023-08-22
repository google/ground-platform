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

import * as admin from "firebase-admin";
import * as firebaseFunctionsTest from "firebase-functions-test";

const test = firebaseFunctionsTest();

describe('Cloud Functions', () => {
  let functions: any, initializeAppSpy: any;

  before(() => {    
    const docSpy = jasmine.createSpy('doc');
    const collectionSpy = jasmine.createSpy('collection');
    const firestoreMock: admin.firestore.Firestore = {
      doc: docSpy,
      collection: collectionSpy,
    } as any;
    const appSpy = jasmine.createSpyObj('App', [], {firestore: () => firestoreMock});    
    initializeAppSpy = spyOn(admin, 'initializeApp').and.returnValue(appSpy);
    functions = require('../src/index');
  });

  after(() => {
    initializeAppSpy.calls.reset();
    test.cleanup();
  });


describe("onWriteSubmissionTest", async () => {
    it("should update count on new submission", async () => {
      const before = {
        get: () => {}
      };
      const after = {
        get: () => {}
      }
      // TODO: Test actual behaviors once implemented.
      await test.wrap(functions.onWriteSubmission)({before, after});
    });
  });
});