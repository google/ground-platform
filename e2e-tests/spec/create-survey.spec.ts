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

import 'jasmine';

import { WebDriverHelper } from '../webdriver-helpers.js';
import {
  AD_HOC,
  JOB_NAME,
  LOI_TASK_TYPE,
  SURVEY_DESCRIPTION,
  SURVEY_TITLE,
  TEST_TIMEOUT,
  WEB_URL,
  USER,
} from '../test-config.js';

// Increase default timeout.
jasmine.DEFAULT_TIMEOUT_INTERVAL = TEST_TIMEOUT;

describe('create survey flow', () => {
  let helper: WebDriverHelper;

  beforeAll(async () => {
    helper = new WebDriverHelper();
    await helper.start(WEB_URL);
    await helper.waitUntilPageReady();
  });

  afterAll(() => {
    return helper.quit();
  });

  it('adds a new survey', async () => {
    await helper.addNewSurvey();
    await helper.setSurveyMetadata(
      SURVEY_TITLE,
      SURVEY_DESCRIPTION,
      JOB_NAME,
      AD_HOC
    );
    await helper.addAllTasks(AD_HOC ? LOI_TASK_TYPE : null);
    await helper.shareSurvey(USER);
    await helper.verifySurveyCreated();
  });
});
