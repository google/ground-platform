/**
 * Copyright 2026 The Ground Authors.
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

import { getStorageBucket } from './common/context';
import { TEMP_MAX_AGE_MS, TEMP_PREFIX } from './common/temp-storage';

/**
 * Deletes temporary files older than MAX_AGE_MS from the temp/ prefix in the
 * default storage bucket.
 */
export async function cleanTempHandler() {
  const bucket = getStorageBucket();
  const [files] = await bucket.getFiles({ prefix: TEMP_PREFIX });
  const cutoff = Date.now() - TEMP_MAX_AGE_MS;
  const deletions = files
    .filter(f => {
      const updated = f.metadata?.updated;
      return updated && new Date(updated).getTime() < cutoff;
    })
    .map(f => f.delete());
  await Promise.all(deletions);
  console.log(`Deleted ${deletions.length} expired temp file(s).`);
}
