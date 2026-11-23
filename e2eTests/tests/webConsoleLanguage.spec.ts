/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { acceptTermsOfService, expect, tap, test } from './fixtures';

// Compose mirrors the semantics of the page and of each open popup into the browser's
// accessibility tree. Closing the language menu must hand that tree back to the page, now in the
// chosen language, so role-based queries (and screen readers) find it.
test('the header language menu switches the page and its accessibility tree', async ({ page }) => {
  await page.goto('/');
  await tap(page, page.getByRole('button', { name: 'Sign in with Google' }));
  await acceptTermsOfService(page);
  await expect(page.getByRole('button', { name: 'Create survey' })).toBeVisible();

  await tap(page, page.getByRole('button', { name: 'Language: English' }));
  await tap(page, page.getByRole('button', { name: 'Français', exact: true }));

  // The page in French is found by role, and the closed menu's items are gone.
  await expect(page.getByRole('button', { name: 'Créer une enquête' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Langue : Français' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Español', exact: true })).toHaveCount(0);
});
