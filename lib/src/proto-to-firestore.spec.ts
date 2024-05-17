/**
 * Copyright 2024 The Ground Authors.
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

import { Role, Survey } from "./generated/ground-protos";
import { toDocumentData } from "./proto-to-firestore";

describe("toDocumentData()", () => {
  [
    {
      desc: "converts string fields",
      input: new Survey({
        name: "Survey name",
        description: "Survey desc",
      }),
      expected: {
        "2": "Survey name",
        "3": "Survey desc",
      },
    },
    {
        desc: "converts map<string, enum>",
        input: new Survey({
          acl: {
            "email1": Role.DATA_COLLECTOR,
            "email2": Role.SURVEY_ORGANIZER
          }
        }),
        expected: {
          "4": {
            "email1": 2,
            "email2": 3,
          }
        },
      },
  ].forEach(({ desc, input, expected }) =>
    it(desc, () => {
      const output = toDocumentData(input);
      console.log(output);
      expect(output).toEqual(expected);
    })
  );
});
