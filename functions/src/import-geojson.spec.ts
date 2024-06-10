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

const test = require('firebase-functions-test')();

describe('importGeoJson()', () => {
  let functions: any;

  const surveyId = 'survey001';
  const loiId = 'loi123';
  // const SUBMISSION = new TestDocumentSnapshot({ loiId });
  // const CONTEXT = new TestEventContext({ surveyId });
  // const SURVEY_PATH = `surveys/${surveyId}`;
  // const SUBMISSIONS_PATH = `${SURVEY_PATH}/submissions`;
  // const LOI_PATH = `${SURVEY_PATH}/lois/${loiId}`;

  beforeAll(() => {
    // firestoreMock = installMockFirestore();
    functions = require('./index');
  });


  afterAll(() => {
    test.cleanup();
  });

  it('imports points', () => {
    const req = { query: { surveyId, loiId }};
    functions.importGeoJson(req, {
      status: (code: number) => {
        expect(code).toEqual(200);
      }
    });
  });
});