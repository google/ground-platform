/*
 * Copyright 2026 The Ground Authors.
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

test('signs in, lists surveys, and opens a survey in the dashboard', async ({ page }) => {
  await page.goto('/');
  const signIn = page.getByRole('button', { name: 'Sign in with Google' });
  await expect(signIn).toBeVisible();
  await expectScreen(page, 'signIn.png');

  await tap(page, signIn);
  const surveyCard = page.getByRole('button', { name: 'Simple Point & Land Use Survey' });
  await expect(surveyCard).toBeVisible();
  await expectScreen(page, 'surveys.png');

  await tap(page, surveyCard);
  await expectScreen(page, 'dashboard.png');
});
