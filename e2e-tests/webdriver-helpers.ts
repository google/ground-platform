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

import {
  Browser,
  Builder,
  By,
  Key,
  WebDriver,
  WebElement,
  until,
} from 'selenium-webdriver';
import * as chrome from 'selenium-webdriver/chrome';

import {
  EXPECTED_SUBMISSION_COUNT,
  JOB_NAME,
  LONG_TIMEOUT,
  DEFAULT_TASK_TYPES,
  MULTIPLE_CHOICE_ADD_OTHER,
  MULTIPLE_CHOICE_COUNT,
  SHORT_TIMEOUT,
  SURVEY_TITLE,
  WAIT_FOR_SUBMISSION_TRIES,
} from './test-config.js';
import { LoiType, Role, TaskType } from './test-utils.js';

class WebDriverHelperException extends Error {}

function assertWebDriverInitialized(
  driver: WebDriver | undefined
): asserts driver is WebDriver {
  if (!driver) {
    throw new WebDriverHelperException('Driver was not started.');
  }
}

export class WebDriverHelper {
  private driver?: WebDriver;

  async start(url: string) {
    const builder = new Builder().forBrowser(Browser.CHROME);
    const chromeOptions = new chrome.Options();
    const chromePath = process.env.CHROME_PATH;
    console.log(`Chrome Path: ${chromePath}`);
    if (chromePath) {
      chromeOptions.setChromeBinaryPath(chromePath);
    }
    chromeOptions.addArguments(
      // '--headless',
      '--disable-gpu',
      '--window-size=1920,1200',
      '--ignore-certificate-errors',
      '--disable-extensions',
      '--no-sandbox',
      '--disable-dev-shm-usage'
    );
    this.driver = await builder.setChromeOptions(chromeOptions).build();
    this.driver.manage().setTimeouts({ implicit: SHORT_TIMEOUT });
    return this.driver.get(url);
  }

  async quit() {
    assertWebDriverInitialized(this.driver);
    return this.driver.quit();
  }

  async waitUntilPageReady() {
    assertWebDriverInitialized(this.driver);
    await this.waitUntilPresent(By.css('#add-card'), LONG_TIMEOUT);
    return this.delay(LONG_TIMEOUT);
  }

  async addNewSurvey() {
    try {
      const el = await this.findElementById('add-card');
      await el.click();
    } catch (e) {
      console.error(e);
      throw new Error(`Unable to addNewSurvey: ${(e as Error).message}`);
    }
  }

  // TODO: Add predefined GeoJSON import support.
  async setSurveyMetadata(
    title: string,
    description: string,
    jobName: string,
    toggleAdhoc: boolean
  ) {
    try {
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
    } catch (e) {
      console.error(e);
      throw new Error(`Unable to setSurveyMetadata: ${(e as Error).message}`);
    }
  }

  async addAllTasks(loiType: LoiType | null = null) {
    try {
      assertWebDriverInitialized(this.driver);
      await this.waitUntilPresent(By.css('task-details'));
      if (loiType !== null) {
        await this.setLoiOption(loiType);
        await this.setLoiInstructions(`Instructions for ${loiType}`);
      }
      let taskIndex = 0;
      const taskTypesList = [...DEFAULT_TASK_TYPES, ...Object.values(TaskType)];
      const taskButtons = await this.driver.findElements(
        By.css('add-task-button button')
      );
      for (const taskType of taskTypesList) {
        // Create a new task, if not a default task.
        if (taskIndex >= DEFAULT_TASK_TYPES.length) {
          switch (taskType) {
            case TaskType.PHOTO:
              await taskButtons[1].click();
              break;
            case TaskType.CAPTURE_LOCATION:
              await taskButtons[2].click();
              break;
            default:
              await taskButtons[0].click();
          }
          // Set to required (don't need to if it's default).
          await this.setRequired(taskIndex, true);
        }
        await this.setInstructions(taskIndex, `Instructions for ${taskType}`);
        switch (taskType) {
          case TaskType.PHOTO:
          case TaskType.CAPTURE_LOCATION:
            // Do nothing;
            break;
          case TaskType.SELECT_ONE:
          case TaskType.SELECT_MULTIPLE:
            await this.setTaskOption(taskIndex, taskType as TaskType);
            await this.setMultipleChoiceOptions(taskIndex);
            break;
          default:
            await this.setTaskOption(taskIndex, taskType as TaskType);
        }
        taskIndex++;
      }
      await this.clickContinue();
    } catch (e) {
      console.error(e);
      throw new Error(`Unable to addAllTasks: ${(e as Error).message}`);
    }
  }

  async shareSurvey(user: string) {
    try {
      assertWebDriverInitialized(this.driver);
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
    } catch (e) {
      console.error(e);
      throw new Error(`Unable to shareSurvey: ${(e as Error).message}`);
    }
  }

  async verifySurveyCreated() {
    try {
      assertWebDriverInitialized(this.driver);
      await this.waitUntilPresent(By.css('.job-list-item-container'));
      const jobElement = await this.driver.findElement(
        By.css('.job-icon ~ div')
      );
      await this.waitUntilTextPresent(jobElement, JOB_NAME);
    } catch (e) {
      console.error(e);
      throw new Error(`Unable to verifySurveyCreated: ${(e as Error).message}`);
    }
  }

  async selectTestSurvey() {
    try {
      assertWebDriverInitialized(this.driver);
      const titleElement = await this.findElementByText(
        By.css('mat-card-title'),
        SURVEY_TITLE
      );
      await titleElement?.click();
    } catch (e) {
      console.error(e);
      throw new Error(`Unable to selectTestSurvey: ${(e as Error).message}`);
    }
  }

  async waitForSurveySubmissions() {
    try {
      assertWebDriverInitialized(this.driver);
      let lastError: Error | null = null;
      let tries = WAIT_FOR_SUBMISSION_TRIES;
      do {
        // Wait for the first LOI submission.
        try {
          try {
            await this.waitUntilPresent(By.css('.loi-icon'), SHORT_TIMEOUT);
          } catch (e) {
            const expandButtonSelector = By.css(
              '.job-list-item-container button'
            );
            await this.waitUntilPresent(expandButtonSelector, LONG_TIMEOUT);
            const expandSubmissionsButton = await this.driver.findElement(
              expandButtonSelector
            );
            await expandSubmissionsButton.click();
            await this.waitUntilPresent(By.css('.loi-icon'), LONG_TIMEOUT);
          }
          // Wait for expected number of submissions.
          const loiIcon = await this.driver.findElement(By.css('.loi-icon'));
          await loiIcon.click();
          await this.waitUntilPresent(By.css('.submission-item'));
          const submissionItems = await this.driver.findElements(
            By.css('.submission-item')
          );
          if (submissionItems.length < EXPECTED_SUBMISSION_COUNT) {
            await this.delay(LONG_TIMEOUT);
            throw new WebDriverHelperException('Not enough survey submissions');
          }
          return;
        } catch (e) {
          lastError = e as Error;
        }
      } while (tries-- > 0);
      fail(`No survey submissions appeared: ${lastError}`);
    } catch (e) {
      console.error(e);
      throw new Error(`Unable to selectTestSurvey: ${(e as Error).message}`);
    }
  }

  async verifySubmissions() {
    // TODO: Verify submission data.
  }

  private delay(timeout = SHORT_TIMEOUT) {
    return new Promise((resolve) => {
      setTimeout(resolve, timeout);
    });
  }

  private waitUntilPresent(selector: By, timeout = SHORT_TIMEOUT) {
    assertWebDriverInitialized(this.driver);
    return this.driver.wait(until.elementsLocated(selector), timeout);
  }

  private waitUntilTextPresent(
    element: WebElement,
    text: string,
    timeout = SHORT_TIMEOUT
  ) {
    assertWebDriverInitialized(this.driver);
    return this.driver.wait(
      until.elementTextMatches(element, new RegExp(text, 'i')),
      timeout
    );
  }

  private findElementById(id: string) {
    assertWebDriverInitialized(this.driver);
    return this.driver.findElement(By.id(id));
  }

  private async findElementByText(selector: By, text: string) {
    assertWebDriverInitialized(this.driver);
    const elements = await this.driver.findElements(selector);
    for (const element of elements) {
      if ((await element.getText()).includes(text)) {
        return element;
      }
    }
    return null;
  }

  private enterText(text: string, selector: By) {
    assertWebDriverInitialized(this.driver);
    return this.driver.findElement(selector).sendKeys(text);
  }

  private async clickContinue() {
    const el = await this.findElementById('continue-button');
    await el.click();
  }

  private async clickToggle(element: WebElement | null = null) {
    assertWebDriverInitialized(this.driver);
    if (element) {
      const el = await element.findElement(By.css('button[role="switch"]'));
      return el.click();
    }
    const el = await this.driver.findElement(By.css('button[role="switch"]'));
    return el.click();
  }

  private async setSelectOption(
    element: () => Promise<WebElement>,
    optionText: string
  ) {
    assertWebDriverInitialized(this.driver);
    try {
      await (await element()).click();
    } catch (e) {
      // Susceptible to StaleElementReferenceError. Delay and try again.
      await this.delay();
      await (await element()).click();
    }
    await this.waitUntilPresent(By.css('div[role="listbox"] mat-option'));
    const optionElements = await this.driver.findElements(
      By.css('div[role="listbox"] mat-option')
    );
    // Make sure element is open before clicking on it.
    await this.delay();
    for (const el of optionElements) {
      const elementText = (await el.getText()).toLowerCase();
      if (elementText.includes(optionText)) {
        await el.click();
        break;
      }
    }
    this.driver.actions().keyDown(Key.ESCAPE);
    return this.waitUntilTextPresent(await element(), optionText);
  }

  private async setLoiOption(loiType: LoiType) {
    assertWebDriverInitialized(this.driver);
    const loiSelectSelector = By.css('.loi-task-container mat-select');
    const element = () => this.driver!.findElement(loiSelectSelector);
    await this.setSelectOption(element, loiType);
  }

  private async setTaskOption(taskIndex: number, taskType: TaskType) {
    assertWebDriverInitialized(this.driver);
    const taskContainerSelector = By.css(
      '.task-container:not(.loi-task-container)'
    );
    const taskSelectSelector = By.css('.task-type mat-select');
    const element = async () => {
      const taskContainers = await this.driver!.findElements(
        taskContainerSelector
      );
      return taskContainers[taskIndex].findElement(taskSelectSelector);
    };
    return this.setSelectOption(element, taskType);
  }

  private async setLoiInstructions(instructions: string) {
    assertWebDriverInitialized(this.driver);
    await this.driver
      .findElement(By.css('.loi-task-container input[ng-reflect-name="label"]'))
      .sendKeys(instructions);
  }

  private async setInstructions(taskIndex: number, instructions: string) {
    assertWebDriverInitialized(this.driver);
    const taskContainer = await this.driver.findElements(
      By.css('.task-container:not(.loi-task-container)')
    );
    const inputElement = await taskContainer[taskIndex].findElement(
      By.css('input[ng-reflect-name="label"]')
    );
    return inputElement.sendKeys(instructions);
  }

  private async setRequired(taskIndex: number, required: boolean) {
    assertWebDriverInitialized(this.driver);
    const taskContainer = await this.driver.findElements(
      By.css('.task-container:not(.loi-task-container)')
    );
    if (required) {
      return this.clickToggle(taskContainer[taskIndex]);
    }
  }

  private async setMultipleChoiceOptions(taskIndex: number) {
    assertWebDriverInitialized(this.driver);
    const taskContainer = (
      await this.driver.findElements(
        By.css('.task-container:not(.loi-task-container)')
      )
    )[taskIndex];
    for (let i = 0; i < MULTIPLE_CHOICE_COUNT; i++) {
      const inputElements = await taskContainer.findElements(
        By.css('.edit-options [ng-reflect-name="label"]')
      );
      await inputElements[inputElements.length - 1].sendKeys(`Option ${i + 1}`);
      if (i < MULTIPLE_CHOICE_COUNT - 1) {
        const addOptionButton = await taskContainer.findElement(
          By.css('button.add-option')
        );
        await addOptionButton.click();
      }
    }
    if (MULTIPLE_CHOICE_ADD_OTHER) {
      const buttons = await taskContainer.findElements(
        By.css('button.add-option')
      );
      await buttons[1].click();
    }
  }
}
