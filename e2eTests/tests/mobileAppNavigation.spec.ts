/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import type { Page } from '@playwright/test';
import { acceptTermsOfService, expect, tap, test } from './fixtures';

// The mobile app in the browser, without the workbench chrome, mirrors its screens in the browser
// history: the URL fragment names the screen, and Back and Forward move between screens.

const surveyListItem = 'Open Simple Point & Land Use Survey';

test('browser Back and Forward move between the survey list and the survey', async ({ page }) => {
  await page.goto('/?chrome=0');
  await tap(page, page.getByRole('button', { name: 'Sign in with Google' }));
  await acceptTermsOfService(page);
  await tap(page, page.getByRole('button', { name: surveyListItem }));
  await expect(page.getByRole('button', { name: 'Collect data' })).toBeVisible();
  expect(new URL(page.url()).hash).toBe('#survey');

  await page.goBack();
  await expect(page.getByRole('button', { name: surveyListItem })).toBeVisible();
  expect(new URL(page.url()).hash).toBe('#download-survey');

  await page.goForward();
  await expect(page.getByRole('button', { name: 'Collect data' })).toBeVisible();
  expect(new URL(page.url()).hash).toBe('#survey');
});

test('a reload while signed out starts at sign-in', async ({ page }) => {
  await page.goto('/?chrome=0#survey');

  await expect(page.getByRole('button', { name: 'Sign in with Google' })).toBeVisible();
  expect(new URL(page.url()).hash).not.toBe('#survey');
});

/** Checks that the Main Survey is on screen, showing [title]'s map layers sheet. */
async function expectSurveyShown(page: Page, title: string): Promise<void> {
  await expect(page.getByRole('button', { name: 'Open Navigation Drawer' })).toBeVisible();
  await expect(page.getByText(title).first()).toBeVisible();
  expect(new URL(page.url()).hash).toBe('#survey');
}

test('a reload at the survey reopens the survey that was open', async ({ page }) => {
  // Not the first survey on the device, which a reload showed before it kept the open survey.
  const survey = 'Household Panel Survey (Past Individuals)';
  await page.goto('/?chrome=0');
  await tap(page, page.getByRole('button', { name: 'Sign in with Google' }));
  await acceptTermsOfService(page);
  await tap(page, page.getByRole('button', { name: `Open ${survey}` }));
  await expectSurveyShown(page, survey);

  await page.reload();

  await expectSurveyShown(page, survey);
  await expect(page.getByText('Simple Point & Land Use Survey')).toHaveCount(0);
});

/** Closes the app and opens it again at its address without a route, as a new launch does. */
async function relaunch(page: Page): Promise<void> {
  // Leaving the page first makes this a new launch, not a navigation within the app's history.
  await page.goto('about:blank');
  await page.goto('/?chrome=0');
}

/** Signs in and accepts the terms, which shows Download survey. */
async function signIn(page: Page): Promise<void> {
  await page.goto('/?chrome=0');
  await tap(page, page.getByRole('button', { name: 'Sign in with Google' }));
  await acceptTermsOfService(page);
}

test('a relaunch opens the survey that was open', async ({ page }) => {
  const survey = 'Household Panel Survey (Past Individuals)';
  await signIn(page);
  await tap(page, page.getByRole('button', { name: `Open ${survey}` }));
  await expectSurveyShown(page, survey);

  await relaunch(page);

  await expectSurveyShown(page, survey);
});

test('a relaunch with surveys on the device but none open shows them', async ({ page }) => {
  // The sample user has this survey downloaded, so the first sync stores it on the device.
  const survey = 'Simple Point & Land Use Survey';
  await signIn(page);
  // Only a survey on the device has an Open button; the others have Download.
  await expect(page.getByRole('button', { name: `Open ${survey}`, exact: true })).toBeVisible();

  await relaunch(page);

  await expect(page.getByText('Downloaded Surveys')).toBeVisible();
  await expect(page.getByText(survey).first()).toBeVisible();
  expect(new URL(page.url()).hash).toBe('#surveys');
});
