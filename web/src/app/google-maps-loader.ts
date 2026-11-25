/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { environment } from 'environments/environment';
import { Env } from 'environments/environment-enums';

/** Libraries the app uses through the global `google.maps` namespace. */
const LIBRARIES = ['core', 'maps', 'marker', 'geometry'];

/** Name of the global function the Maps API calls once it is ready. */
const CALLBACK_NAME = '__onGoogleMapsLoaded';

/**
 * Loads the Google Maps JavaScript API with `loading=async`, and resolves once
 * the libraries in `LIBRARIES` are ready.
 *
 * With `loading=async` the script only bootstraps the API, and keeps loading
 * after its `load` event: `google.maps.importLibrary` is defined only when the
 * API calls the `callback` function. Classes such as `google.maps.LatLng` or
 * `google.maps.Polygon` exist only after their library has been imported, so
 * the app must not start using them before then.
 *
 * Never rejects: the app still works without a map, so a failure is only
 * logged.
 */
export async function loadGoogleMaps(doc: Document): Promise<void> {
  if (environment.env === Env.Test) return;
  try {
    await new Promise<void>((resolve, reject) => {
      const win = doc.defaultView as unknown as Record<string, unknown>;
      win[CALLBACK_NAME] = () => {
        delete win[CALLBACK_NAME];
        resolve();
      };
      const script = doc.createElement('script');
      script.src =
        'https://maps.googleapis.com/maps/api/js' +
        `?key=${environment.googleMapsApiKey}&loading=async&callback=${CALLBACK_NAME}`;
      script.onerror = () =>
        reject(new Error('Failed to load the Google Maps JavaScript API'));
      doc.head.appendChild(script);
    });
    await Promise.all(LIBRARIES.map(name => google.maps.importLibrary(name)));
  } catch (error) {
    console.error(error);
  }
}
