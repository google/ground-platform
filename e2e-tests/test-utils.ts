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

/**
 * Maps keywords in the UI to the LOI types.
 */
export enum LoiType {
  DROP_PIN = 'pin',
  DRAW_AREA = 'draw',
}

/**
 * Maps keywords in the UI to the task types.
 */
export enum TaskType {
  TEXT = 'text',
  SELECT_ONE = 'select one',
  SELECT_MULTIPLE = 'select multiple',
  NUMBER = 'number',
  DATE = 'date',
  TIME = 'time',
  PHOTO = 'photo',
  CAPTURE_LOCATION = 'capture location',
}

/**
 * Maps keywords in the UI to the role options.
 */
export enum Role {
  DATA_COLLECTOR = 'data collector',
  SURVEY_ORGANIZER = 'survey organizer',
  VIEWER = 'viewer',
}
