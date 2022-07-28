/**
 * @license
 * Copyright 2022 Google LLC
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

var assert = require("assert");
const Datastore = require("../common/datastore");

describe("Array", function () {
  describe("#indexOf()", function () {
    it("should return -1 when the value is not present", function () {
      console.log(
        JSON.stringify(
          Datastore.toFirestoreMap({
            type: "Point",
            coordinates: [-12.34, 45.67],
          })
        )
      );
      console.log(
        JSON.stringify(
          Datastore.toFirestoreMap({
            type: "Polygon",
            coordinates: [
              [-1.2, 3.4],
              [-5.6, 7.8],
            ],
          })
        )
      );
      console.log(
        JSON.stringify(
          Datastore.toFirestoreMap({
            type: "Polygon",
            coordinates: [
              [
                [-1.2, 3.4],
                [-5.6, 7.8],
              ],
              [
                [-9.0, 10.11],
                [-12.13, 14.15],
              ],
            ],
          })
        )
      );
      console.log(
        JSON.stringify(
          Datastore.toFirestoreMap({
            type: "MultiPolygon",
            coordinates: [
              [
                [
                  [-1.1, 1.2],
                  [-1.3, 1.4],
                ],
                [
                  [-2.1, 2.2],
                  [-2.3, 2.4],
                ],
              ],
              [
                [
                  [-3.1, 3.2],
                  [-3.3, 3.4],
                ],
                [
                  [-4.1, 4.2],
                  [-4.3, 4.4],
                ],
              ],
            ],
          })
        )
      );
      //assert.equal([1, 2, 3].indexOf(4), -1);
    });
  });
});
