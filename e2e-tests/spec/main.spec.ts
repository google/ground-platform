import 'jasmine';

import {WebDriverHelper} from '../webdriver-helpers.js';
import {TestConfig} from '../test_config.js';
import {LoiType} from '../ground-helpers.js';

// Increase default timeout.
jasmine.DEFAULT_TIMEOUT_INTERVAL = 5 * 60 * 1000; // ms

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
    await helper.addAllTasks(TestConfig.ADHOC ? LoiType.DROP_PIN : null);
    await helper.shareSurvey(TestConfig.USER);
    await helper.verifySurveyCreated();
  });

  it('displays submissions', async () => {
    await helper.waitForSurveySubmissions();
    await helper.verifySurveySubmissions();
  });
});
