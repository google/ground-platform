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

// Every page of the web console has its own URL: links open their page directly, the browser's
// Back and Forward buttons move between pages, and reloading keeps the page.

/** The sample "Simple Point & Land Use Survey". */
const SURVEY_ID = '00000036-1a1c-5c51-997f-28b67d813aa3';
const DASHBOARD_PATH = `/surveys/${SURVEY_ID}/dashboard`;

/** Signs in and accepts the Terms of Service, which the console asks for after every sign-in. */
async function signIn(page: Page): Promise<void> {
  await tap(page, page.getByRole('button', { name: 'Sign in with Google' }));
  await acceptTermsOfService(page);
}

/** The dashboard's survey switcher, which names the open survey. */
const dashboardOfTheSurvey = (page: Page) =>
  page.getByRole('button', { name: /^Switch survey/ }).filter({ hasText: 'Simple Point' });

const surveyCard = (page: Page) =>
  page.getByRole('button', { name: 'Simple Point & Land Use Survey', exact: true });

const path = (page: Page) => new URL(page.url()).pathname;

test('a survey dashboard link opens the dashboard after signing in', async ({ page }) => {
  await page.goto(DASHBOARD_PATH);
  await signIn(page);

  await expect(dashboardOfTheSurvey(page)).toBeVisible();
  expect(path(page)).toBe(DASHBOARD_PATH);
});

test('browser Back and Forward move between pages', async ({ page }) => {
  await page.goto(DASHBOARD_PATH);
  await signIn(page);
  await expect(dashboardOfTheSurvey(page)).toBeVisible();

  await tap(page, page.getByRole('button', { name: 'OPEN FORIS Collect One' }));
  await expect(surveyCard(page)).toBeVisible();
  expect(path(page)).toBe('/surveys');

  await page.goBack();
  await expect(dashboardOfTheSurvey(page)).toBeVisible();
  expect(path(page)).toBe(DASHBOARD_PATH);

  await page.goForward();
  await expect(surveyCard(page)).toBeVisible();
  expect(path(page)).toBe('/surveys');
});

test('a reload keeps the page', async ({ page }) => {
  await page.goto(DASHBOARD_PATH);
  await signIn(page);
  await expect(dashboardOfTheSurvey(page)).toBeVisible();

  await page.reload();
  // The development sign-in isn't kept across reloads; the page waits behind sign-in (and the
  // Terms of Service, asked for again after signing in again).
  await signIn(page);

  await expect(dashboardOfTheSurvey(page)).toBeVisible();
  expect(path(page)).toBe(DASHBOARD_PATH);
});
