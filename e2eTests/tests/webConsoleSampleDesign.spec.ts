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

import { SERVER_URL } from '../harness/config.mjs';
import { acceptTermsOfService, expect, tap, test } from './fixtures';

// Creating a survey with a sample design generates its sample plots when the wizard finishes. The
// wizard must stay open until the server has stored every plot, and only then open the survey.

/** How many map features (entities) the server has stored, across all surveys. */
async function storedEntityCount(): Promise<number> {
  const response = await fetch(`${SERVER_URL}/debug/spanner/api/tables/Entities/count`);
  if (!response.ok) throw new Error(`Entities count failed: ${response.status}`);
  return ((await response.json()) as { count: number }).count;
}

test('a survey with a sample design opens only after its plots are saved', async ({ page }) => {
  test.setTimeout(120_000);
  const entitiesBefore = await storedEntityCount();

  await page.goto('/');
  await tap(page, page.getByRole('button', { name: 'Sign in with Google' }));
  await acceptTermsOfService(page);
  await tap(page, page.getByRole('button', { name: 'Create survey' }));

  // Overview → Purpose: a purpose whose map features are sample plots.
  await tap(page, page.getByRole('textbox', { name: 'Survey title' }));
  await page.keyboard.type('Sampled plots survey');
  await tap(page, page.getByRole('button', { name: 'Next', exact: true }));
  await tap(
    page,
    page.getByRole('checkbox', { name: 'Deforestation & land-use change (Activity Data)' }),
  );
  // Survey area (the organization's default) → Map features (a sample design) → Forms → Sharing.
  await tap(page, page.getByRole('button', { name: 'Next', exact: true }));
  await expect(page.getByRole('button', { name: 'Edit survey area' })).toBeVisible();
  await tap(page, page.getByRole('button', { name: 'Next', exact: true }));
  await expect(page.getByRole('radio', { name: 'Grid' }).first()).toBeVisible();
  await tap(page, page.getByRole('button', { name: 'Next', exact: true }));
  await expect(page.getByRole('button', { name: '6 Sharing', exact: true })).toBeEnabled();
  await tap(page, page.getByRole('button', { name: '6 Sharing', exact: true }));

  await tap(page, page.getByRole('button', { name: 'Create survey', exact: true }));

  await expect(page).toHaveURL(/\/surveys\/[0-9a-f-]+\/dashboard$/, { timeout: 90_000 });
  // By the time the survey opens, the server already holds every generated plot.
  const plotsTab = page.getByText(/^Sample plots \(\d+\)$/);
  await expect(plotsTab).toBeVisible();
  const plotCount = Number((await plotsTab.textContent())?.match(/\((\d+)\)/)?.[1]);
  expect(plotCount).toBeGreaterThan(0);
  expect((await storedEntityCount()) - entitiesBefore).toBe(plotCount);
  await expect(page.getByText("Couldn't save changes")).toHaveCount(0);
});
