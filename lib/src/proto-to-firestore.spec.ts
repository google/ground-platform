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

import { Survey } from "./generated/ground-protos";
import { toDocumentData } from "./proto-to-firestore";

describe('toDocumentData()', () => {
    it('converts string fields', () => {        
        const output = toDocumentData(
            new Survey({
                name: "Survey name",
                description: "Survey desc"
            }));
        const expected = {
            "2": "Survey name",
            "3": "Survey desc"
        };
        expect(output).toEqual(expected);
    });
});