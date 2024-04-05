/** Configuration for test constants. */

export class TestConfig {
  static WEB_URL = 'http://localhost:5000';
  static SHORT_TIME_OUT = 1000; // 1s
  static LONG_TIME_OUT = TestConfig.SHORT_TIME_OUT * 5; // 5s
  static SURVEY_TITLE = 'A test title';
  static SURVEY_DESCRIPTION = 'A test description';
  static JOB_NAME = 'A job name';
  static ADHOC = true;
  static MULTIPLE_CHOICE_COUNT = 3;
  static MULTIPLE_CHOICE_ADD_OTHER = true;
  static USER = 'test-user@google.com';
  static EXPECTED_SUBMISSION_COUNT = 2;
  static WAIT_FOR_SUBMISSION_TRIES = 30;
}
