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

import { Request } from 'firebase-functions/v2/https';

/** Top-level prefix under which clients upload survey media. */
export const USER_MEDIA_SURVEYS_PREFIX = 'user-media/surveys/';

/**
 * Path of the media endpoint, as rewritten to the `exportMedia` function in
 * `firebase.json`.
 */
export const EXPORT_MEDIA_PATH = '/exportMedia';

/**
 * Returns the storage prefix under which all media for the specified survey is
 * stored. Clients upload photos to
 * `user-media/surveys/{surveyId}/submissions/{taskId}-{uuid}.jpg`.
 */
export const userMediaPrefix = (surveyId: string) =>
  `${USER_MEDIA_SURVEYS_PREFIX}${surveyId}/`;

/**
 * Returns true iff the specified storage path points at media belonging to the
 * specified survey.
 *
 * Photo paths are chosen by the client and stored verbatim in the submission,
 * so they are treated as untrusted input when used to address storage objects.
 */
export function isMediaPathInSurvey(path: string, surveyId: string): boolean {
  return (
    !!surveyId &&
    !!path &&
    path.startsWith(userMediaPrefix(surveyId)) &&
    !path.split('/').includes('..')
  );
}

/**
 * Returns the URL at which the photo submitted for the specified task can be
 * fetched. The URL is stable: it names the submission rather than the storage
 * object, so access is re-checked against the survey each time it is followed.
 *
 * `baseUrl` is empty when the host serving the request can't be determined, in
 * which case a relative URL is returned.
 */
export function getExportMediaUrl(
  baseUrl: string,
  surveyId: string,
  submissionId: string,
  taskId: string
): string {
  const params = new URLSearchParams({
    survey: surveyId,
    submission: submissionId,
    task: taskId,
  });
  return `${baseUrl}${EXPORT_MEDIA_PATH}?${params}`;
}

/**
 * Returns the scheme and authority at which the specified request was received,
 * for use in building absolute URLs back to this deployment. Returns an empty
 * string if the host can't be determined.
 *
 * The `Host` header is client-controlled, but it is only ever used to build
 * links written into the requesting user's own export, so a forged value
 * affects nobody else.
 */
export function getRequestBaseUrl(req: Request): string {
  const headers = req.headers ?? {};
  const host =
    firstHeaderValue(headers['x-forwarded-host']) ??
    firstHeaderValue(headers['host']);
  if (!host) return '';
  const rawProto = firstHeaderValue(headers['x-forwarded-proto']) ?? 'https';
  const proto = rawProto === 'http' || rawProto === 'https' ? rawProto : 'https';
  return `${proto}://${host}`;
}

/**
 * Returns the first value of a possibly repeated or comma-separated header, or
 * `undefined` if absent or empty.
 */
function firstHeaderValue(
  value: string | string[] | undefined
): string | undefined {
  const first = Array.isArray(value) ? value[0] : value;
  return first?.split(',')[0].trim() || undefined;
}
