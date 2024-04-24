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

import {WebDriverHelper} from '../webdriver-helpers.js';
import {TestConfig} from '../test_config.js';

// Increase default timeout.
jasmine.DEFAULT_TIMEOUT_INTERVAL = TestConfig.TEST_TIME_OUT;

describe('ground-platform', () => {
  let helper: WebDriverHelper;

  beforeAll(async () => {
    helper = new WebDriverHelper();
    await helper.start(TestConfig.WEB_URL);
    await helper.waitUntilPageReady();
  });

  afterAll(() => {
    return helper.quit();
  });

  it('adds a new survey', async () => {
    await helper.addNewSurvey();
    await helper.setSurveyMetadata(
      TestConfig.SURVEY_TITLE,
      TestConfig.SURVEY_DESCRIPTION,
      TestConfig.JOB_NAME,
      TestConfig.ADHOC
    );
    await helper.addAllTasks(
      TestConfig.ADHOC ? TestConfig.LOI_TASK_TYPE : null
    );
    await helper.shareSurvey(TestConfig.USER);
    await helper.verifySurveyCreated();
  });
});
