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

// Survey fields:
export const [$title, $description] = [2, 3];
// LocationOfInterest fields:
export const [$job_id, $geometry, $submission_count, $source, $properties] = [
  2, 3, 4, 9, 10,
];
// Geometry fields:
export const [$point, $polygon, $multi_polygon] = [1, 2, 3];
// Polygon fields:
export const [$shell] = [1];
// LinearRing fields:
export const [$coordinates] = [1];
// MultiPolygon fields:
export const [$polygons] = [1];
// Coordinates fields:
export const [$latitude, $longitude] = [1, 2];
// Job fields:
export const [$index, $style] = [2, 4];
// Style fields:
export const [$color] = [1];
// Task fields:
export const [$required, $level, $textQuestion] = [4, 5, 7];
// DateTimeQuestion:
export const [$dtq$type] = [1];
