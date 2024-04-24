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

import {
  Browser,
  Builder,
  By,
  Key,
  WebDriver,
  WebElement,
  until,
} from 'selenium-webdriver';
import {TestConfig} from './test_config.js';
import {LoiType, Role, TaskType} from './ground-helpers.js';

class WebDriverHelperException extends Error {}

function driverInitialized(
  driver: WebDriver | undefined
): asserts driver is WebDriver {
  if (!driver) {
    throw new WebDriverHelperException('Driver was not started.');
  }
}

export class WebDriverHelper {
  private driver?: WebDriver;

  async start(url: string) {
    this.driver = await new Builder().forBrowser(Browser.CHROME).build();
    this.driver.manage().setTimeouts({implicit: TestConfig.SHORT_TIME_OUT});
    return this.driver.get(url);
  }

  async quit() {
    driverInitialized(this.driver);
    return this.driver.quit();
  }

  async waitUntilPageReady() {
    driverInitialized(this.driver);
    await this.waitUntilPresent(By.css('#add-card'), TestConfig.LONG_TIME_OUT);
    return this.delay(TestConfig.LONG_TIME_OUT);
  }

  async addNewSurvey() {
    await this.findElementById('add-card').click();
  }

  // TODO: Add predefined GeoJSON import support.
  async setSurveyMetadata(
    title: string,
    description: string,
    jobName: string,
    toggleAdhoc: boolean
  ) {
    await this.waitUntilPresent(By.css('survey-details'));
    await this.enterText(title, By.css('input[ng-reflect-name="title"]'));
    await this.enterText(
      description,
      By.css('input[ng-reflect-name="description"]')
    );
    await this.clickContinue();
    await this.enterText(jobName, By.css('input[ng-reflect-name="name"]'));
    await this.clickContinue();
    if (toggleAdhoc) {
      await this.clickToggle();
    }
    await this.clickContinue();
  }

  async addAllTasks(loiType: LoiType | null = null) {
    driverInitialized(this.driver);
    await this.waitUntilPresent(By.css('task-details'));
    if (loiType !== null) {
      await this.setLoiOption(loiType);
      await this.setLoiInstructions(`Instructions for ${loiType}`);
    }
    let taskIndex = 0;
    const taskButtons = await this.driver.findElements(
      By.css('add-task-button button')
    );
    for (const taskType of Object.values(TaskType)) {
      switch (taskType) {
        case TaskType.PHOTO:
          await taskButtons[1].click();
          await this.setInstructions(
            taskIndex,
            `Instructions for ${TaskType.PHOTO}`
          );
          await this.setRequired(taskIndex, true);
          break;
        case TaskType.CAPTURE_LOCATION:
          await taskButtons[2].click();
          await this.setInstructions(
            taskIndex,
            `Instructions for ${TaskType.CAPTURE_LOCATION}`
          );
          await this.setRequired(taskIndex, true);
          break;
        case TaskType.SELECT_ONE:
        case TaskType.SELECT_MULTIPLE:
          await taskButtons[0].click();
          await this.setInstructions(taskIndex, `Instructions for ${taskType}`);
          await this.setRequired(taskIndex, true);
          await this.setTaskOption(taskIndex, taskType as TaskType);
          await this.setMultipleChoiceOptions(taskIndex);
          break;
        default:
          await taskButtons[0].click();
          await this.setInstructions(taskIndex, `Instructions for ${taskType}`);
          await this.setRequired(taskIndex, true);
          await this.setTaskOption(taskIndex, taskType as TaskType);
      }
      taskIndex++;
    }
    await this.clickContinue();
  }

  async shareSurvey(user: string) {
    driverInitialized(this.driver);
    const shareButton = await this.driver.findElement(
      By.css('button.share-survey-button')
    );
    await shareButton.click();
    await this.waitUntilPresent(By.css('.share-form .email-input input'));
    await this.enterText(user, By.css('.share-form .email-input input'));
    const roleSelector = () =>
      this.driver!.findElement(By.css('.share-form mat-select'));
    await this.setSelectOption(roleSelector, Role.DATA_COLLECTOR);
    const doneButton = (
      await this.driver.findElements(By.css('mat-dialog-container button'))
    )[1];
    await doneButton.click();
    await this.delay();
    await this.clickContinue();
  }

  async verifySurveyCreated() {
    driverInitialized(this.driver);
    await this.waitUntilPresent(By.css('.job-list-item-container'));
    const jobElement = await this.driver.findElement(By.css('.job-icon ~ div'));
    await this.waitUntilTextPresent(jobElement, TestConfig.JOB_NAME);
  }

  async selectTestSurvey() {
    driverInitialized(this.driver);
    const titleElement = await this.findElementByText(By.css('mat-card-title'), TestConfig.SURVEY_TITLE);
    await titleElement?.click();
  }

  async waitForSurveySubmissions() {
    driverInitialized(this.driver);
    let lastError: Error | null = null;
    let tries = TestConfig.WAIT_FOR_SUBMISSION_TRIES;
    do {
      // Wait for the first LOI submission.
      try {
        try {
          await this.waitUntilPresent(
            By.css('.loi-icon'),
            TestConfig.SHORT_TIME_OUT
          );
        } catch (e) {
          const expandButtonSelector = By.css(
            '.job-list-item-container button'
          );
          await this.waitUntilPresent(
            expandButtonSelector,
            TestConfig.LONG_TIME_OUT
          );
          const expandSubmissionsButton = await this.driver.findElement(
            expandButtonSelector
          );
          await expandSubmissionsButton.click();
          await this.waitUntilPresent(
            By.css('.loi-icon'),
            TestConfig.LONG_TIME_OUT
          );
        }
        // Wait for expected number of submissions.
        const loiIcon = await this.driver.findElement(By.css('.loi-icon'));
        await loiIcon.click();
        await this.waitUntilPresent(By.css('.submission-item'));
        const submissionItems = await this.driver.findElements(
          By.css('.submission-item')
        );
        if (submissionItems.length < TestConfig.EXPECTED_SUBMISSION_COUNT) {
          await this.delay(TestConfig.LONG_TIME_OUT);
          throw new WebDriverHelperException('Not enough survey submissions');
        }
        return;
      } catch (e) {
        lastError = e as Error;
      }
    } while (tries-- > 0);
    fail(`No survey submissions appeared: ${lastError}`);
  }

  async verifySurveySubmissions() {
    // TODO: Verify submission data.
  }

  private delay(timeout = TestConfig.SHORT_TIME_OUT) {
    return new Promise(resolve => {
      setTimeout(resolve, timeout);
    });
  }

  private waitUntilPresent(selector: By, timeout = TestConfig.SHORT_TIME_OUT) {
    driverInitialized(this.driver);
    return this.driver.wait(until.elementsLocated(selector), timeout);
  }

  private waitUntilTextPresent(
    element: WebElement,
    text: string,
    timeout = TestConfig.SHORT_TIME_OUT
  ) {
    driverInitialized(this.driver);
    return this.driver.wait(
      until.elementTextMatches(element, new RegExp(text, 'i')),
      timeout
    );
  }

  private findElementById(id: string) {
    driverInitialized(this.driver);
    return this.driver.findElement(By.id(id));
  }

  private async findElementByText(selector: By, text: string) {
    driverInitialized(this.driver);
    const elements = await this.driver.findElements(selector);
    for (const element of elements) {
      if ((await element.getText()).includes(text)) {
        return element;
      }
    }
    return null;
  }

  private enterText(text: string, selector: By) {
    driverInitialized(this.driver);
    return this.driver.findElement(selector).sendKeys(text);
  }

  private async clickContinue() {
    await this.findElementById('continue-button').click();
  }

  private clickToggle(element: WebElement | null = null) {
    driverInitialized(this.driver);
    if (element) {
      return element.findElement(By.css('button[role="switch"]')).click();
    }
    return this.driver.findElement(By.css('button[role="switch"]')).click();
  }

  private async setSelectOption(
    element: () => Promise<WebElement>,
    optionText: string
  ) {
    driverInitialized(this.driver);
    await (await element()).click();
    await this.waitUntilPresent(By.css('div[role="listbox"] mat-option'));
    const optionElements = await this.driver.findElements(
      By.css('div[role="listbox"] mat-option')
    );
    // Make sure element is open before clicking on it.
    await this.delay();
    for (const element of optionElements) {
      const elementText = (await element.getText()).toLowerCase();
      if (elementText.includes(optionText)) {
        await element.click();
        break;
      }
    }
    this.driver.actions().keyDown(Key.ESCAPE);
    return this.waitUntilTextPresent(await element(), optionText);
  }

  private async setLoiOption(loiType: LoiType) {
    driverInitialized(this.driver);
    const loiSelectSelector = By.css('.loi-task-container mat-select');
    const element = () => this.driver!.findElement(loiSelectSelector);
    await this.setSelectOption(element, loiType);
  }

  private async setTaskOption(taskIndex: number, taskType: TaskType) {
    driverInitialized(this.driver);
    const taskSelectSelector = By.css('.task-type mat-select');
    const element = async () =>
      (await this.driver!.findElements(taskSelectSelector))[taskIndex];
    return this.setSelectOption(element, taskType);
  }

  private async setLoiInstructions(instructions: string) {
    driverInitialized(this.driver);
    await this.driver
      .findElement(By.css('.loi-task-container input[ng-reflect-name="label"]'))
      .sendKeys(instructions);
  }

  private async setInstructions(taskIndex: number, instructions: string) {
    driverInitialized(this.driver);
    const taskContainer = await this.driver.findElements(
      By.css('.task-container:not(.loi-task-container)')
    );
    const inputElement = await taskContainer[taskIndex].findElement(
      By.css('input[ng-reflect-name="label"]')
    );
    return inputElement.sendKeys(instructions);
  }

  private async setRequired(taskIndex: number, required: boolean) {
    driverInitialized(this.driver);
    const taskContainer = await this.driver.findElements(
      By.css('.task-container:not(.loi-task-container)')
    );
    if (required) {
      return this.clickToggle(taskContainer[taskIndex]);
    }
  }

  private async setMultipleChoiceOptions(taskIndex: number) {
    driverInitialized(this.driver);
    const taskContainer = (
      await this.driver.findElements(
        By.css('.task-container:not(.loi-task-container)')
      )
    )[taskIndex];
    for (let i = 0; i < TestConfig.MULTIPLE_CHOICE_COUNT; i++) {
      const inputElements = await taskContainer.findElements(
        By.css('.edit-options [ng-reflect-name="label"]')
      );
      await inputElements[inputElements.length - 1].sendKeys(`Option ${i + 1}`);
      if (i < TestConfig.MULTIPLE_CHOICE_COUNT - 1) {
        const addOptionButton = await taskContainer.findElement(
          By.css('button.add-option')
        );
        await addOptionButton.click();
      }
    }
    if (TestConfig.MULTIPLE_CHOICE_ADD_OTHER) {
      const buttons = await taskContainer.findElements(
        By.css('button.add-option')
      );
      await buttons[1].click();
    }
  }
}
