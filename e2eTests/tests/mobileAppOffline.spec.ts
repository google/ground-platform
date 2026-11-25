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
import { SERVER_URL } from '../harness/config.mjs';
import { acceptTermsOfService, expect, tap, test } from './fixtures';

// Offline use of the mobile app in the browser: edits made offline upload soon after the connection
// returns, without a reload, and relaunching offline goes straight back into the app, or to
// Download survey with an offline banner when no survey is on the device.

// The form below needs a GPS fix; this one is at the sample survey's location.
test.use({
  geolocation: { latitude: -0.4198, longitude: 36.9512, accuracy: 3 },
  permissions: ['geolocation'],
});

/** How soon after the connection returns the outbox must be sent. */
const DRAIN_TIMEOUT_MS = 5_000;

const allChangesSaved = (page: Page) =>
  page.getByRole('button', { name: 'All changes saved', exact: true }).first();

/** Signs in, accepts the terms, and opens the sample survey's map. */
async function openSurvey(page: Page): Promise<void> {
  await page.goto('/?chrome=0');
  await tap(page, page.getByRole('button', { name: 'Sign in with Google' }));
  await acceptTermsOfService(page);
  await tap(page, page.getByRole('button', { name: 'Open Simple Point & Land Use Survey' }));
  await expect(allChangesSaved(page)).toBeVisible();
}

/** Records one observation with the sample survey's form. */
async function recordObservation(page: Page): Promise<void> {
  await tap(page, page.getByRole('button', { name: 'Collect data' }));
  await tap(page, page.getByRole('button', { name: 'Collect Point & Land Use' }));
  const understood = page.getByRole('button', { name: 'I understand, continue', exact: true });
  await expect(understood).toBeVisible();
  // Lets the dialog finish appearing, so the tap lands on the button.
  await page.waitForTimeout(1_000);
  await tap(page, understood);
  await tap(page, page.getByRole('button', { name: 'Capture location' }));
  await expect(page.getByText(/Location captured/)).toBeVisible();
  await tap(page, page.getByRole('button', { name: 'Next', exact: true }));
  await tap(page, page.getByRole('button', { name: 'Grazing Pasture / Grassland', exact: true }));
  await tap(page, page.getByRole('button', { name: 'Next', exact: true }));
  await tap(page, page.getByRole('button', { name: 'Review', exact: true }));
  await tap(page, page.getByRole('button', { name: 'Submit', exact: true }));
  // The success screen; Continue returns to the map, where the sync status shows.
  await expect(page.getByText('Data collection complete')).toBeVisible();
  await tap(page, page.getByRole('button', { name: 'Continue', exact: true }));
}

/** Resolves when the server answers a `MutateData` call successfully. */
function nextUpload(page: Page) {
  return page.waitForResponse(
    (response) => response.url().includes('/MutateData') && response.ok(),
    { timeout: 60_000 },
  );
}

/** Makes every call to the server fail, as if it were unreachable, until the returned function. */
async function blockServer(page: Page): Promise<() => Promise<void>> {
  const pattern = `${SERVER_URL}/**`;
  await page.route(pattern, (route) => route.abort('connectionrefused'));
  return () => page.unroute(pattern);
}

test('sends a backed-off edit within seconds of reconnecting', async ({ page, context }) => {
  test.setTimeout(120_000);
  await openSurvey(page);

  // The edit fails to upload long enough to back off for more than the drain timeout, then the
  // device goes offline.
  const unblock = await blockServer(page);
  await recordObservation(page);
  await page.waitForTimeout(20_000);
  await context.setOffline(true);
  await unblock();
  await page.waitForTimeout(1_000);
  await expect(allChangesSaved(page)).toBeHidden();

  const upload = nextUpload(page);
  const reconnectedAt = Date.now();
  await context.setOffline(false);
  await upload;

  expect(Date.now() - reconnectedAt).toBeLessThan(DRAIN_TIMEOUT_MS);
  await expect(allChangesSaved(page)).toBeVisible();
});

test('sends an edit soon after an unreachable server is back', async ({ page }) => {
  await openSurvey(page);

  const unblock = await blockServer(page);
  await recordObservation(page);
  // A few failed attempts, with the browser still reporting it is online.
  await page.waitForTimeout(6_000);
  await expect(allChangesSaved(page)).toBeHidden();

  const upload = nextUpload(page);
  await unblock();
  await upload;

  await expect(allChangesSaved(page)).toBeVisible();
});

test('relaunches into the app without the server', async ({ page }) => {
  await openSurvey(page);

  await blockServer(page);
  await page.reload();

  // Back on the survey's map that the address names (`#survey`), without asking to sign in or
  // accept the terms again.
  await expect(page.getByRole('button', { name: 'Collect data' })).toBeVisible();
  expect(new URL(page.url()).hash).toBe('#survey');
  await expect(page.getByRole('button', { name: 'Sign in with Google' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Continue', exact: true })).toHaveCount(0);
});

test('relaunches without a survey on the device into Download survey, with an offline banner', async ({
  page,
  context,
}) => {
  // The subtitle counts the surveys shared with the user that the app has listed.
  const noSurveyListed = page.getByText(/(^|\s)0 shared with you/);
  const surveysListed = page.getByText(/(^|\s)[1-9]\d* shared with you/);
  const offlineBanner = page.getByText(
    "You're offline. Connect to the internet to download surveys for offline use.",
  );
  // Signing in without the server leaves no survey on the device.
  const unblock = await blockServer(page);
  await page.goto('/?chrome=0');
  await tap(page, page.getByRole('button', { name: 'Sign in with Google' }));
  await acceptTermsOfService(page);
  await expect(noSurveyListed).toBeVisible();

  // Leaving the page first makes this a new launch, not a navigation within the app's history.
  await page.goto('about:blank');
  await page.goto('/?chrome=0');

  await expect(page.getByText('Download survey', { exact: true })).toBeVisible();
  expect(new URL(page.url()).hash).toBe('#download-survey');
  await expect(offlineBanner).toHaveCount(0);

  await context.setOffline(true);
  await expect(offlineBanner).toBeVisible();

  await context.setOffline(false);
  await unblock();
  await expect(offlineBanner).toHaveCount(0);
  await expect(surveysListed).toBeVisible();
});
