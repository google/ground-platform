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
package org.openforis.collectone.v2.app.client.lifecycle

/*
 * Platform hooks the sync engine uses (`docs/technical/sync-engine.md`, "Platform Schedulers"):
 * foreground state for polling, and a warning before the web page unloads with unsent changes.
 */

/**
 * Calls [onChange] with whether the app is in the foreground (on Android, any activity started; on
 * the web, the tab visible), once now and on every change. Returns a function that stops observing.
 */
expect fun observeAppForeground(onChange: (Boolean) -> Unit): () -> Unit

/**
 * On the web, asks the user to confirm leaving the page while [hasUnsentChanges] returns true,
 * because the in-memory outbox is lost on unload. Does nothing on other platforms. Returns a
 * function that removes the guard.
 */
expect fun guardUnsentChangesOnUnload(hasUnsentChanges: () -> Boolean): () -> Unit
