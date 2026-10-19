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

import { expect, expectScreen, tap, test } from './fixtures';

test('signs in, accepts the terms, lists surveys, and opens a survey', async ({ page }) => {
  // Full window, without the workbench's simulated device.
  await page.goto('/?chrome=0');
  const signIn = page.getByRole('button', { name: 'Sign in with Google' });
  await expect(signIn).toBeVisible();
  await expectScreen(page, 'signIn.png');

  await tap(page, signIn);
  const agree = page.getByRole('button', { name: 'Agree & continue' });
  await expect(agree).toBeVisible();
  await expectScreen(page, 'termsOfService.png');

  await tap(page, agree);
  const openSurvey = page.getByRole('button', { name: 'Open Simple Point & Land Use Survey' });
  await expect(openSurvey).toBeVisible();
  await expectScreen(page, 'surveys.png');

  await tap(page, openSurvey);
  await expectScreen(page, 'surveyMap.png');
});
