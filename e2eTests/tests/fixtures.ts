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

import { test as base, expect, type Locator, type Page } from '@playwright/test';
import { USER_DATA_SERVICE_URL } from '../harness/config.mjs';

/** How long the user data service must have no calls in flight for the app to count as idle. */
const QUIET_MS = 750;

/** How long [expectScreen] keeps retrying before it reports the difference. */
const SCREEN_TIMEOUT_MS = 30_000;

/** Calls to the user data service in flight, and when the last one ended, per page. */
const serviceCalls = new WeakMap<Page, { inFlight: number; lastActivity: number }>();

export const test = base.extend({
  page: async ({ page }, use) => {
    const reset = await fetch(`${USER_DATA_SERVICE_URL}/debug/reset`, { method: 'POST' });
    if (!reset.ok) {
      throw new Error(`POST /debug/reset failed: ${reset.status} ${reset.statusText}`);
    }

    // Only the local apps and user data service. Without the Mapbox GL JS script the maps fall back
    // to Compose's own renderer, and Compose uses the fonts bundled with the app, so nothing on
    // screen depends on the network. Fulfilling external requests with 404 instead of aborting
    // avoids browser fetch() rejections in background thumbnail loaders.
    await page.route('**/*', (route) => {
      const { hostname } = new URL(route.request().url());
      return hostname === '127.0.0.1' || hostname === 'localhost'
        ? route.continue()
        : route.fulfill({ status: 404, body: '' });
    });
    const calls = { inFlight: 0, lastActivity: Date.now() };
    serviceCalls.set(page, calls);
    const isServiceCall = (url: string) => url.startsWith(USER_DATA_SERVICE_URL);
    page.on('request', (request) => {
      if (!isServiceCall(request.url())) return;
      calls.inFlight++;
      calls.lastActivity = Date.now();
    });
    const onDone = (request: { url(): string }) => {
      if (!isServiceCall(request.url())) return;
      calls.inFlight--;
      calls.lastActivity = Date.now();
    };
    page.on('requestfinished', onDone);
    page.on('requestfailed', onDone);
    await use(page);
  },
});

/**
 * Compose draws the whole UI into one canvas and mirrors its semantics into a hidden accessibility
 * tree, so tests find nodes with Playwright's role and text locators, then press where the node is
 * drawn: the canvas sits on top of the accessibility tree and receives the real pointer events.
 */
export async function tap(page: Page, locator: Locator): Promise<void> {
  const node = locator.first();
  await node.waitFor();
  const box = await node.boundingBox();
  if (!box) throw new Error(`${locator} has no bounds`);
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
}

/**
 * Waits until the app has made no calls to the user data service for a moment, counting from now
 * at the earliest, so calls that an action just triggered have time to start.
 */
export async function waitForServiceIdle(page: Page): Promise<void> {
  const calls = serviceCalls.get(page)!;
  const start = Date.now();
  await expect
    .poll(
      () =>
        calls.inFlight === 0 && Date.now() - Math.max(calls.lastActivity, start) >= QUIET_MS,
      { intervals: [100], timeout: SCREEN_TIMEOUT_MS },
    )
    .toBe(true);
}

/**
 * Checks the page against the golden [name] once the app is idle.
 *
 * Playwright's screenshot assertion stops at the first stable screenshot, and a busy Wasm main
 * thread (for example while it applies a sync response) briefly looks stable. So this retries
 * until the screen matches or [SCREEN_TIMEOUT_MS] passes, and only then reports the difference.
 */
export async function expectScreen(page: Page, name: string): Promise<void> {
  await waitForServiceIdle(page);
  const deadline = Date.now() + SCREEN_TIMEOUT_MS;
  for (;;) {
    try {
      await expect(page).toHaveScreenshot(name, { timeout: 5_000 });
      return;
    } catch (error) {
      // A missing golden won't appear by waiting.
      if (Date.now() >= deadline || String(error).includes("doesn't exist")) throw error;
      await page.waitForTimeout(500);
    }
  }
}

export { expect };
