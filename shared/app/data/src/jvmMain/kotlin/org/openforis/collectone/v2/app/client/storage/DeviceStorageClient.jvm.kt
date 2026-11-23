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
package org.openforis.collectone.v2.app.client.storage

import java.io.File

/** The file system of the user's home directory. */
actual fun defaultDeviceStorageClient(): DeviceStorageClient = DeviceStorageClient {
  val home = File(System.getProperty("user.home"))
  home.totalSpace.takeIf { it > 0 }?.let { DeviceStorageSpace(home.usableSpace, it) }
}
