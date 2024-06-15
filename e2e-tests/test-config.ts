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

import { LoiType, TaskType } from './test-utils';

export const WEB_URL = 'http://localhost:5000';
export const TEST_TIMEOUT = 5 * 60 * 1000; // 5m
export const SHORT_TIMEOUT = 1000; // 1s
export const LONG_TIMEOUT = SHORT_TIMEOUT * 5; // 5s
export const SURVEY_TITLE = 'A test title';
export const SURVEY_DESCRIPTION = 'A test description';
export const JOB_NAME = 'A job name';
export const AD_HOC = true;
export const DEFAULT_TASK_TYPES = [TaskType.CAPTURE_LOCATION];
export const LOI_TASK_TYPE = LoiType.DROP_PIN;
export const MULTIPLE_CHOICE_COUNT = 3;
export const MULTIPLE_CHOICE_ADD_OTHER = true;
export const USER = 'test-user@google.com';
export const EXPECTED_SUBMISSION_COUNT = 2;
export const WAIT_FOR_SUBMISSION_TRIES = 30;
