/**
 * Copyright 2019 The Ground Authors.
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

import { LoiType } from "./ground-helpers";

export class TestConfig {
  static WEB_URL = 'http://localhost:5000';
  static TEST_TIME_OUT = 5 * 60 * 1000; // 5m
  static SHORT_TIME_OUT = 1000; // 1s
  static LONG_TIME_OUT = TestConfig.SHORT_TIME_OUT * 5; // 5s
  static SURVEY_TITLE = 'A test title';
  static SURVEY_DESCRIPTION = 'A test description';
  static JOB_NAME = 'A job name';
  static ADHOC = true;
  static LOI_TASK_TYPE = LoiType.DROP_PIN;
  static MULTIPLE_CHOICE_COUNT = 3;
  static MULTIPLE_CHOICE_ADD_OTHER = true;
  static USER = 'test-user@google.com';
  static EXPECTED_SUBMISSION_COUNT = 2;
  static WAIT_FOR_SUBMISSION_TRIES = 30;
}
