/**
 * @license
 * Copyright 2023 The Ground Authors.
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

import { DocumentData } from "firebase-admin/firestore";
import * as admin from "firebase-admin";
import * as fs from "firebase-admin/firestore";

export class TestDocumentSnapshot {
    constructor(private readonly testData: DocumentData) { }

    get(key: string) {
        return this.testData[key]
    }

    data() {
        return this.testData;
    }
}


export function installMockFirestore() {
    const firestoreMock: admin.firestore.Firestore = {
        settings: () => {},
        doc: jasmine.createSpy('doc'),
        collection: jasmine.createSpy('collection')
    } as any;
    const appSpy = jasmine.createSpyObj('App', [], { firestore: () => firestoreMock });
    spyOn(admin, 'initializeApp').and.returnValue(appSpy);
    spyOn(fs, 'getFirestore').and.returnValue(firestoreMock);
    return firestoreMock;
}

export function createTestCountQuery(result: Number) {
    return {
      count: () => ({
        get: async () => ({
          data: () => ({
            count: result
          }) 
        }) 
      })
    };
  }
  
  export class TestEventContext {
    constructor(public readonly params: Record<string, any>) { }
  }
  